package com.journalvian.gaji.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BracketsRulesTest {

    private static final LocalDate ANY_DATE = LocalDate.of(2026, 12, 1);

    private BracketsRules rules;

    @BeforeAll
    void load() {
        rules = new BracketsRules(new YamlRuleLoader());
    }

    @Test
    @DisplayName("no taxable income means no tax")
    void zeroPkp() {
        assertThat(rules.taxOn(ANY_DATE, BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @ParameterizedTest(name = "PKP {0} -> tax {1}")
    @CsvSource({
            // The PP 58/2023 worked example: 54.300.000 at 5% falls entirely in bracket 1.
            "54300000,    2715000",
            // Exactly the first bracket ceiling.
            "60000000,    3000000",
            // 60jt at 5%, then 40jt at 15%.
            "100000000,   9000000",
            // 60jt @5% + 190jt @15% + 50jt @25%.
            "300000000,   44000000",
            // Full ladder: 60jt@5, 190jt@15, 250jt@25, 4.5M@30, 1M@35.
            "6000000000,  1794000000"})
    @DisplayName("each bracket only taxes the slice of income inside it")
    void progressiveBrackets(String pkp, String expectedTax) {
        assertThat(rules.taxOn(ANY_DATE, new BigDecimal(pkp)))
                .isEqualByComparingTo(expectedTax);
    }

    @Test
    @DisplayName("a negative PKP is rejected rather than treated as zero")
    void negativePkpFails() {
        assertThatThrownBy(() -> rules.taxOn(ANY_DATE, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the UU HPP ladder is the one loaded for 2026")
    void bracketsMatchUuHpp() {
        var brackets = rules.bracketsOn(ANY_DATE);

        assertThat(brackets).hasSize(5);
        // Compared numerically: BigDecimal equality is scale-sensitive, and 0.30
        // loaded from YAML arrives as 0.3.
        assertThat(brackets.get(0).upTo()).isEqualByComparingTo("60000000");
        assertThat(brackets.get(0).rate()).isEqualByComparingTo("0.05");
        assertThat(brackets.get(1).upTo()).isEqualByComparingTo("250000000");
        assertThat(brackets.get(1).rate()).isEqualByComparingTo("0.15");
        assertThat(brackets.get(2).upTo()).isEqualByComparingTo("500000000");
        assertThat(brackets.get(2).rate()).isEqualByComparingTo("0.25");
        assertThat(brackets.get(3).upTo()).isEqualByComparingTo("5000000000");
        assertThat(brackets.get(3).rate()).isEqualByComparingTo("0.30");
        assertThat(brackets.get(4).isUnbounded()).isTrue();
        assertThat(brackets.get(4).rate()).isEqualByComparingTo("0.35");
    }

    @Test
    @DisplayName("a date before UU HPP is rejected, never approximated")
    void refusesDatesBeforeBracketsExisted() {
        assertThatThrownBy(() -> rules.taxOn(LocalDate.of(2021, 12, 1), new BigDecimal("60000000")))
                .isInstanceOf(NoRuleForDateException.class);
    }
}
