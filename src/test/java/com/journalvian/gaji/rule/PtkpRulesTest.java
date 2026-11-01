package com.journalvian.gaji.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PtkpRulesTest {

    private PtkpRules rules;

    @BeforeAll
    void load() {
        rules = new PtkpRules(new YamlRuleLoader());
    }

    @Test
    @DisplayName("TK/0 is 54 million and has been since 2016")
    void tkZero() {
        assertThat(rules.ptkpOn(LocalDate.of(2026, 6, 1), "TK/0"))
                .isEqualByComparingTo("54000000");
    }

    @Test
    @DisplayName("K/3 is 72 million")
    void kThree() {
        assertThat(rules.ptkpOn(LocalDate.of(2026, 6, 1), "K/3"))
                .isEqualByComparingTo("72000000");
    }

    @Test
    @DisplayName("status is case-insensitive and space-tolerant")
    void normalisesStatus() {
        assertThat(rules.ptkpOn(LocalDate.of(2026, 6, 1), "tk/0"))
                .isEqualByComparingTo("54000000");
        assertThat(rules.ptkpOn(LocalDate.of(2026, 6, 1), " k 1 "))
                .isEqualByComparingTo("63000000");
    }

    @Test
    @DisplayName("an unknown status is an error, not a silent zero")
    void unknownStatusFails() {
        assertThatThrownBy(() -> rules.ptkpOn(LocalDate.of(2026, 6, 1), "K/9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("K/9");
    }

    @Test
    @DisplayName("a date before the rule set is rejected")
    void requiresRuleForDate() {
        assertThatThrownBy(() -> rules.ptkpOn(LocalDate.of(2015, 1, 1), "TK/0"))
                .isInstanceOf(NoRuleForDateException.class);
    }

    @Test
    @DisplayName("the eight standard statuses are present")
    void supportsStandardStatuses() {
        assertThat(rules.supportedStatuses())
                .containsExactlyInAnyOrder(
                        "TK/0", "TK/1", "TK/2", "TK/3",
                        "K/0", "K/1", "K/2", "K/3");
    }
}
