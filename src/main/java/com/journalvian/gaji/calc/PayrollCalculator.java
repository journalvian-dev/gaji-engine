package com.journalvian.gaji.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;

import org.springframework.stereotype.Component;

import com.journalvian.gaji.rule.BpjsRules;
import com.journalvian.gaji.rule.BracketsRules;
import com.journalvian.gaji.rule.Pph21DeductionRule;
import com.journalvian.gaji.rule.Pph21DeductionRules;
import com.journalvian.gaji.rule.PtkpRules;
import com.journalvian.gaji.rule.TerRules;

/**
 * Computes one month's payslip from gross income, PTKP status and period.
 *
 * <p>Two PPh 21 methods coexist, and choosing the wrong one is the classic
 * payroll bug:
 *
 * <ul>
 *   <li><strong>January–November</strong> — the TER rate for the month is applied
 *       directly to that month's gross income.</li>
 *   <li><strong>December</strong> — the whole year is recomputed with the
 *       progressive Pasal 17 brackets, and what was already withheld in the
 *       first eleven months is subtracted. That difference is the December
 *       figure, which is usually not equal to the other months.</li>
 * </ul>
 *
 * <p>The engine assumes a constant monthly gross for the tax year. Variable
 * income — THR, bonus, overtime — is out of scope and would need month-by-month
 * inputs rather than a single figure.
 */
@Component
public class PayrollCalculator {

    private static final BigDecimal MONTHS_IN_YEAR = BigDecimal.valueOf(12);

    private final BpjsRules bpjsRules;
    private final PtkpRules ptkpRules;
    private final TerRules terRules;
    private final BracketsRules bracketsRules;
    private final Pph21DeductionRules deductionRules;

    public PayrollCalculator(
            BpjsRules bpjsRules,
            PtkpRules ptkpRules,
            TerRules terRules,
            BracketsRules bracketsRules,
            Pph21DeductionRules deductionRules) {
        this.bpjsRules = bpjsRules;
        this.ptkpRules = ptkpRules;
        this.terRules = terRules;
        this.bracketsRules = bracketsRules;
        this.deductionRules = deductionRules;
    }

    public CalculationResponse calculate(CalculationRequest request) {
        validate(request);

        BigDecimal gross = request.grossMonthly();
        String status = request.ptkpStatus().trim().toUpperCase();
        LocalDate period = request.period();

        BigDecimal jht = bpjsRules.employeeDeduction("jht", gross, period);
        BigDecimal jp = bpjsRules.employeeDeduction("jp", gross, period);
        BigDecimal kesehatan = bpjsRules.employeeDeduction("kesehatan", gross, period);

        boolean isDecember = period.getMonthValue() == Month.DECEMBER.getValue();

        BigDecimal pph21;
        String method;
        DecemberReconciliation december = null;

        if (isDecember) {
            // PMK 168/2023 Pasal 10(1)(b): the deductible contributions are the
            // employee's own pension and old-age contributions — JHT and JP — paid
            // through the employer to BPJS Ketenagakerjaan. BPJS Kesehatan is a
            // health programme, not a pension or old-age one, so it is not deductible.
            BigDecimal annualContributions = jht.add(jp).multiply(MONTHS_IN_YEAR);
            december = reconcileAnnual(period, status, gross, annualContributions);
            pph21 = december.decemberTax();
            method = "ANNUAL_RECONCILIATION";
        } else {
            pph21 = terWithholding(period, status, gross);
            method = "TER";
        }

        BigDecimal totalDeductions = jht.add(jp).add(kesehatan).add(pph21);

        return new CalculationResponse(
                period,
                status,
                gross,
                jht,
                jp,
                kesehatan,
                pph21,
                totalDeductions,
                gross.subtract(totalDeductions),
                method,
                december);
    }

    // ------------------------------------------------------------------

    /** January–November: gross income times the TER rate for that month. */
    private BigDecimal terWithholding(LocalDate period, String status, BigDecimal gross) {
        return gross.multiply(terRules.rateOn(period, status, gross))
                .setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * December: recompute the whole year, then subtract what was already withheld.
     *
     * <p>The year's deductible contributions are a parameter rather than being
     * derived here, because they are not always the BPJS amounts: PP 58/2023's own
     * worked example states a pension contribution of Rp 100.000 a month, which is
     * an input to the example rather than 1% of anything. Keeping the derivation
     * outside lets the reconciliation arithmetic be checked against that example
     * on its own terms.
     *
     * @param annualDeductibleContributions the employee's pension and old-age
     *                                      contributions for the whole year
     */
    DecemberReconciliation reconcileAnnual(
            LocalDate period,
            String ptkpStatus,
            BigDecimal monthlyGross,
            BigDecimal annualDeductibleContributions) {

        Pph21DeductionRule deductions = deductionRules.ruleOn(period);

        BigDecimal annualGross = monthlyGross.multiply(MONTHS_IN_YEAR);

        BigDecimal biayaJabatan = deductions.biayaJabatanOn(annualGross)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal ptkp = ptkpRules.ptkpOn(period, ptkpStatus);

        BigDecimal neto = annualGross.subtract(biayaJabatan).subtract(annualDeductibleContributions);
        BigDecimal pkp = deductions.roundDownPkp(neto.subtract(ptkp).max(BigDecimal.ZERO));

        BigDecimal annualTax = bracketsRules.taxOn(period, pkp);
        BigDecimal withheld = withheldJanuaryToNovember(period, ptkpStatus, monthlyGross);
        BigDecimal decemberTax = annualTax.subtract(withheld).max(BigDecimal.ZERO);

        return new DecemberReconciliation(
                annualGross,
                biayaJabatan,
                annualDeductibleContributions,
                ptkp,
                pkp,
                annualTax,
                withheld,
                decemberTax,
                bracketsRules.sourceOn(period),
                deductions.source());
    }

    /**
     * Sum of TER withholding for January to November of the same tax year.
     *
     * <p>Each month is looked up on its own date rather than reusing December's
     * rate, so a rate change part-way through the year is accounted for instead
     * of being flattened into a single figure.
     */
    private BigDecimal withheldJanuaryToNovember(LocalDate period, String status, BigDecimal gross) {
        BigDecimal total = BigDecimal.ZERO;
        for (int month = Month.JANUARY.getValue(); month < Month.DECEMBER.getValue(); month++) {
            LocalDate monthDate = LocalDate.of(period.getYear(), month, 1);
            total = total.add(terWithholding(monthDate, status, gross));
        }
        return total;
    }

    private static void validate(CalculationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A calculation request is required");
        }
        if (request.grossMonthly() == null || request.grossMonthly().signum() < 0) {
            throw new IllegalArgumentException("grossMonthly must be zero or positive");
        }
        if (request.ptkpStatus() == null || request.ptkpStatus().isBlank()) {
            throw new IllegalArgumentException("ptkpStatus is required");
        }
        if (request.period() == null) {
            throw new IllegalArgumentException("period is required");
        }
    }
}
