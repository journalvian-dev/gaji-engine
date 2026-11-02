package com.journalvian.gaji.calc;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A request to compute one month's payslip.
 *
 * @param grossMonthly gross income for the month, before any deduction
 * @param ptkpStatus   PTKP status at the start of the tax year, e.g. {@code K/1}
 * @param period       the month being paid; any date inside that month works
 */
public record CalculationRequest(
        BigDecimal grossMonthly,
        String ptkpStatus,
        LocalDate period) {
}
