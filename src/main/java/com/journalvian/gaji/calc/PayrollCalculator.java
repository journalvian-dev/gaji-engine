package com.journalvian.gaji.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;

import org.springframework.stereotype.Component;

import com.journalvian.gaji.rule.BpjsRules;
import com.journalvian.gaji.rule.BracketsRules;
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

    private static final BigDecimal BIYA_JABATAN_RATE = new BigDecimal("0.05");
    private static final BigDecimal BIYA_JABATAN_ANNUAL_CAP = new BigDecimal("6000000");
    private static final BigDecimal THOUSAND = new BigDecimal("1000");

    private final BpjsRules bpjsRules;
    private final PtkpRules ptkpRules;
    private final TerRules terRules;
    private final BracketsRules bracketsRules;

    public PayrollCalculator(
            BpjsRules bpjsRules,
            PtkpRules ptkpRules,
            TerRules terRules,
            BracketsRules bracketsRules) {
        this.bpjsRules = bpjsRules;
        this.ptkpRules = ptkpRules;
        this.terRules = terRules;
        this.bracketsRules = bracketsRules;
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
            december = reconcileDecember(period, status, gross, jp);
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
     */
    private DecemberReconciliation reconcileDecember(
            LocalDate period, String status, BigDecimal gross, BigDecimal jpThisMonth) {

        BigDecimal annualGross = gross.multiply(BigDecimal.valueOf(12));

        BigDecimal biayaJabatan = annualGross.multiply(BIYA_JABATAN_RATE)
                .min(BIYA_JABATAN_ANNUAL_CAP)
                .setScale(0, RoundingMode.HALF_UP);

        // Only the pension contribution is deductible for PPh 21. JHT is a savings
        // programme, not a pension contribution, and is not deductible.
        BigDecimal iuranPensiun = jpThisMonth.multiply(BigDecimal.valueOf(12));

        BigDecimal ptkp = ptkpRules.ptkpOn(period, status);

        BigDecimal neto = annualGross.subtract(biayaJabatan).subtract(iuranPensiun);
        BigDecimal pkp = neto.subtract(ptkp).max(BigDecimal.ZERO);
        // PKP is rounded down to whole thousands of rupiah.
        pkp = pkp.divide(THOUSAND, 0, RoundingMode.DOWN).multiply(THOUSAND);

        BigDecimal annualTax = bracketsRules.taxOn(period, pkp);
        BigDecimal withheld = withheldJanuaryToNovember(period, status, gross);
        BigDecimal decemberTax = annualTax.subtract(withheld).max(BigDecimal.ZERO);

        return new DecemberReconciliation(
                annualGross,
                biayaJabatan,
                iuranPensiun,
                ptkp,
                pkp,
                annualTax,
                withheld,
                decemberTax,
                bracketsRules.sourceOn(period));
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
