package com.journalvian.gaji.rule;

import java.math.BigDecimal;

/**
 * The deductions and rounding applied to annual gross income when PPh 21 is
 * recomputed at the end of the tax year.
 *
 * @param period               when this rule applies
 * @param biayaJabatanRate     occupational cost as a fraction of gross income
 * @param biayaJabatanAnnualCap ceiling on that occupational cost, per year
 * @param pkpRoundingIncrement Penghasilan Kena Pajak is rounded down to a multiple of this
 * @param source               primary source of the occupational cost figure
 * @param pkpRoundingSource    primary source of the rounding rule
 */
public record Pph21DeductionRule(
        EffectivePeriod period,
        BigDecimal biayaJabatanRate,
        BigDecimal biayaJabatanAnnualCap,
        BigDecimal pkpRoundingIncrement,
        String source,
        String pkpRoundingSource) {

    /** Occupational cost for a year's gross income: the rate, clamped to the cap. */
    public BigDecimal biayaJabatanOn(BigDecimal annualGross) {
        return annualGross.multiply(biayaJabatanRate).min(biayaJabatanAnnualCap);
    }

    /** Rounds a Penghasilan Kena Pajak down to the nearest whole round figure. */
    public BigDecimal roundDownPkp(BigDecimal pkp) {
        return pkp.divide(pkpRoundingIncrement, 0, java.math.RoundingMode.DOWN)
                .multiply(pkpRoundingIncrement);
    }
}
