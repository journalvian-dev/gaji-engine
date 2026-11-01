package com.journalvian.gaji.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The annual PTKP threshold for a status on a given date.
 *
 * @param date   the date asked about
 * @param status PTKP status, e.g. {@code TK/0}
 * @param amount annual non-taxable income, in IDR
 * @param source primary source of the figure
 */
public record PtkpResponse(
        LocalDate date,
        String status,
        BigDecimal amount,
        String source) {
}
