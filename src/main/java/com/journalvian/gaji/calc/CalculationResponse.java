package com.journalvian.gaji.calc;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One month's payslip, as a breakdown rather than a single number.
 *
 * @param period          the month being paid
 * @param ptkpStatus      the status used
 * @param grossMonthly    gross income before deductions
 * @param jht             employee JHT contribution, 2% with no cap
 * @param jp              employee JP contribution, 1% capped at the applicable ceiling
 * @param kesehatan       employee BPJS Kesehatan contribution, 1% capped
 * @param pph21           income tax withheld for this month
 * @param totalDeductions sum of the four deductions above
 * @param takeHomePay     {@code grossMonthly - totalDeductions}
 * @param pph21Method     {@code TER} for January–November, {@code ANNUAL_RECONCILIATION} for December
 * @param december        derivation of the December figure, {@code null} in every other month
 */
public record CalculationResponse(
        LocalDate period,
        String ptkpStatus,
        BigDecimal grossMonthly,
        BigDecimal jht,
        BigDecimal jp,
        BigDecimal kesehatan,
        BigDecimal pph21,
        BigDecimal totalDeductions,
        BigDecimal takeHomePay,
        String pph21Method,
        DecemberReconciliation december) {
}
