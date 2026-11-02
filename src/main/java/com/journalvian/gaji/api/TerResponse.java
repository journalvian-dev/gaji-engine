package com.journalvian.gaji.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The TER rate that applies to a monthly gross income, and what it withholds.
 *
 * @param date             the date asked about
 * @param status           PTKP status that selected the category
 * @param category         TER table used: {@code A}, {@code B} or {@code C}
 * @param monthlyGross     the gross income looked up
 * @param rate             effective rate from the table
 * @param withholding      {@code monthlyGross * rate}, rounded to whole rupiah
 * @param layerCeiling     inclusive upper bound of the layer, or {@code null} for the top layer
 * @param source           primary source of the table
 */
public record TerResponse(
        LocalDate date,
        String status,
        String category,
        BigDecimal monthlyGross,
        BigDecimal rate,
        BigDecimal withholding,
        BigDecimal layerCeiling,
        String source) {
}
