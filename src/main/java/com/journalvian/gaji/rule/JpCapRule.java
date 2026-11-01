package com.journalvian.gaji.rule;

import java.math.BigDecimal;

/**
 * The Jaminan Pensiun (JP) wage cap that applied over a given period.
 *
 * <p>The cap is not a constant. It is recalculated every year under PP 45/2015
 * Art. 29(3) as {@code previous cap x (1 + previous year's GDP growth)}, and it
 * takes effect in <strong>March</strong>, not January. Two payslips in the same
 * calendar year can therefore have two different, both correct, answers.
 */
public record JpCapRule(
        EffectivePeriod period,
        BigDecimal wageCap,
        BigDecimal employeeRate,
        BigDecimal employerRate,
        String source) {
}
