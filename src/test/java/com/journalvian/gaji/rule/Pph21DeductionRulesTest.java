package com.journalvian.gaji.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Pph21DeductionRulesTest {

    private static final LocalDate ANY_DATE = LocalDate.of(2026, 12, 1);

    private Pph21DeductionRules rules;

    @BeforeAll
    void load() {
        rules = new Pph21DeductionRules(new YamlRuleLoader());
    }

    @Test
    @DisplayName("occupational cost is 5% of gross income, capped at Rp 6.000.000 a year")
    void biayaJabatan() {
        Pph21DeductionRule rule = rules.ruleOn(ANY_DATE);

        assertThat(rule.biayaJabatanRate()).isEqualByComparingTo("0.05");
        assertThat(rule.biayaJabatanAnnualCap()).isEqualByComparingTo("6000000");

        // 5% of 96 million is 4.8 million — under the ceiling.
        assertThat(rule.biayaJabatanOn(new BigDecimal("96000000")))
                .isEqualByComparingTo("4800000");

        // 5% of 120 million is 6 million — exactly the ceiling.
        assertThat(rule.biayaJabatanOn(new BigDecimal("120000000")))
                .isEqualByComparingTo("6000000");

        // 5% of 500 million is 25 million — clamped.
        assertThat(rule.biayaJabatanOn(new BigDecimal("500000000")))
                .isEqualByComparingTo("6000000");
    }

    @Test
    @DisplayName("Penghasilan Kena Pajak is rounded down to whole thousands, never up")
    void pkpRoundsDown() {
        Pph21DeductionRule rule = rules.ruleOn(ANY_DATE);

        assertThat(rule.roundDownPkp(new BigDecimal("54300999")))
                .isEqualByComparingTo("54300000");
        // Already a whole thousand: untouched.
        assertThat(rule.roundDownPkp(new BigDecimal("54300000")))
                .isEqualByComparingTo("54300000");
        // Below one thousand: rounds to nothing, not to 1.000.
        assertThat(rule.roundDownPkp(new BigDecimal("999")))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("every figure carries a citation")
    void everyFigureIsSourced() {
        Pph21DeductionRule rule = rules.ruleOn(ANY_DATE);

        assertThat(rule.source()).isNotBlank();
        assertThat(rule.pkpRoundingSource()).isNotBlank();
    }

    @Test
    @DisplayName("a date before the encoded range is refused")
    void refusesUnknownDates() {
        assertThatThrownBy(() -> rules.ruleOn(LocalDate.of(2023, 12, 1)))
                .isInstanceOf(NoRuleForDateException.class);
    }
}
