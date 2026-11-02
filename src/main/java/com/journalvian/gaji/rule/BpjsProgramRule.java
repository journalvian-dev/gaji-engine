package com.journalvian.gaji.rule;

import java.math.BigDecimal;

/**
 * One BPJS program's parameters over an effective period.
 *
 * <p>Only the employee-borne share is used for take-home pay; the employer share
 * is carried so cost-to-company can be added later without another migration.
 *
 * <p>{@code wageCap} is {@code null} for programs that contribute on the full
 * wage (JHT). Where it is set (JP, Kesehatan) the contribution base is the lower
 * of the wage and the cap.
 */
public record BpjsProgramRule(
        String program,
        EffectivePeriod period,
        BigDecimal employeeRate,
        BigDecimal employerRate,
        BigDecimal wageCap,
        String source) {

    public boolean isCapped() {
        return wageCap != null;
    }

    /** The wage actually contributed on: the monthly wage, clamped to the cap. */
    public BigDecimal contributableBase(BigDecimal monthlyWage) {
        return isCapped() ? monthlyWage.min(wageCap) : monthlyWage;
    }
}
