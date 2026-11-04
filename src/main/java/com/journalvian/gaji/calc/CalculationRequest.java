package com.journalvian.gaji.calc;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A request to compute one month's payslip.
 *
 * @param grossMonthly gross income for the month, before any deduction
 * @param ptkpStatus   PTKP status at the start of the tax year, e.g. {@code K/1}
 * @param period       the month being paid; any date inside that month works
 */
@Schema(description = "One month's payslip to compute")
public record CalculationRequest(

        @Schema(
                description = """
                        Gross income for the month, before any deduction. The caller assembles \
                        this: employer-paid JKK, JKM and BPJS Kesehatan premiums are taxable \
                        income and belong here, employer-paid JHT and JP are not.""",
                example = "10000000",
                minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal grossMonthly,

        @Schema(
                description = """
                        PTKP status. K/0 is a married employee with no dependants; TK/0 is \
                        single. Determines the non-taxable threshold and which TER table applies.""",
                example = "K/0",
                allowableValues = {"TK/0", "TK/1", "TK/2", "TK/3",
                        "K/0", "K/1", "K/2", "K/3"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String ptkpStatus,

        @Schema(
                description = """
                        The month being paid. December is computed differently from every \
                        other month: the whole year is recomputed and the eleven months already \
                        withheld are subtracted.""",
                example = "2026-12-01",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate period) {
}
