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
class BpjsRulesTest {

    private BpjsRules rules;

    @BeforeAll
    void load() {
        rules = new BpjsRules(new YamlRuleLoader());
    }

    @Test
    @DisplayName("the JP cap changes in March, not January")
    void capChangesInMarch() {
        assertThat(rules.jpCapOn(LocalDate.of(2026, 2, 28)).wageCap())
                .isEqualByComparingTo("10547400");

        assertThat(rules.jpCapOn(LocalDate.of(2026, 3, 1)).wageCap())
                .isEqualByComparingTo("11086300");
    }

    @Test
    @DisplayName("two payslips in the same year, same salary, different correct answers")
    void contributionDiffersWithinTheSameYear() {
        BigDecimal salary = new BigDecimal("12000000");

        BigDecimal january = rules.employeeJpContribution(salary, LocalDate.of(2026, 1, 15));
        BigDecimal april = rules.employeeJpContribution(salary, LocalDate.of(2026, 4, 15));

        assertThat(january).isEqualByComparingTo("105474");
        assertThat(april).isEqualByComparingTo("110863");
        assertThat(april).isGreaterThan(january);
    }

    @Test
    @DisplayName("wages below the cap are used in full")
    void belowCapUsesFullWage() {
        // 5.000.000 x 1% = 50.000, well under every cap in the data set.
        assertThat(rules.employeeJpContribution(new BigDecimal("5000000"), LocalDate.of(2026, 6, 1)))
                .isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("wages above the cap are clamped to it")
    void aboveCapIsClamped() {
        BigDecimal huge = new BigDecimal("50000000");
        assertThat(rules.employeeJpContribution(huge, LocalDate.of(2026, 6, 1)))
                .isEqualByComparingTo("110863");
    }

    @Test
    @DisplayName("the period boundary belongs to exactly one interval")
    void boundariesDoNotOverlap() {
        LocalDate marchFirst2026 = LocalDate.of(2026, 3, 1);

        JpCapRule onBoundary = rules.jpCapOn(marchFirst2026);
        JpCapRule dayBefore = rules.jpCapOn(marchFirst2026.minusDays(1));

        assertThat(onBoundary.period().from()).isEqualTo(marchFirst2026);
        assertThat(dayBefore.wageCap()).isNotEqualTo(onBoundary.wageCap());
    }

    @Test
    @DisplayName("every cap carries a primary source")
    void everyRuleIsSourced() {
        assertThat(rules.jpCaps())
                .isNotEmpty()
                .allSatisfy(rule -> assertThat(rule.source()).isNotBlank());
    }

    @Test
    @DisplayName("dates before the first known rule are rejected, never guessed")
    void refusesUnknownDates() {
        assertThatThrownBy(() -> rules.jpCapOn(LocalDate.of(2020, 1, 1)))
                .isInstanceOf(NoRuleForDateException.class)
                .hasMessageContaining("2020-01-01");
    }
}
