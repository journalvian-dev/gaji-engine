package com.journalvian.gaji.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the engine knows about the Jaminan Pensiun wage cap on a given date.
 *
 * @param date               the date asked about
 * @param wageCap            ceiling used as the contribution base
 * @param employeeRate       employee share, e.g. 0.01
 * @param maxEmployeeAmount  {@code wageCap * employeeRate}: the most an employee can
 *                           have deducted for JP in a month under these rules
 * @param source             primary source of the figure
 * @param validFrom          first date this cap applies
 * @param validTo            first date it stops applying, or {@code null} if still in force
 */
public record JpCapResponse(
        LocalDate date,
        BigDecimal wageCap,
        BigDecimal employeeRate,
        BigDecimal maxEmployeeAmount,
        String source,
        LocalDate validFrom,
        LocalDate validTo) {
}
