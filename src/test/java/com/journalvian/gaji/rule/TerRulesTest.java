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
class TerRulesTest {

    private static final LocalDate ANY_MONTH = LocalDate.of(2026, 6, 1);

    private TerRules rules;

    @BeforeAll
    void load() {
        rules = new TerRules(new YamlRuleLoader());
    }

    @Test
    @DisplayName("the three tables carry the layer counts published in PP 58/2023")
    void layerCountsMatchTheRegulation() {
        assertThat(rules.tableOn(ANY_MONTH, "A").layers()).hasSize(44);
        assertThat(rules.tableOn(ANY_MONTH, "B").layers()).hasSize(40);
        assertThat(rules.tableOn(ANY_MONTH, "C").layers()).hasSize(41);
    }

    @ParameterizedTest(name = "{0} uses table {1}")
    @CsvSource({
            "TK/0, A",
            "TK/1, A",
            "K/0,  A",
            "TK/2, B",
            "TK/3, B",
            "K/1,  B",
            "K/2,  B",
            "K/3,  C"})
    @DisplayName("PTKP statuses map to the category published in PP 58/2023")
    void statusMapsToCategory(String status, String expectedCategory) {
        assertThat(rules.categoryFor(status)).isEqualTo(expectedCategory);
    }

    @Test
    @DisplayName("the worked example from PP 58/2023 holds: Rp10.000.000, K/0 -> 2%")
    void matchesPublishedWorkedExample() {
        // PP 58/2023 uses Tuan R, gross Rp10.000.000, PTKP K/0, and states
        // "tarif efektif 2% (Tabel A baris 9)".
        assertThat(rules.rateOn(ANY_MONTH, "K/0", new BigDecimal("10000000")))
                .isEqualByComparingTo("0.02");
        assertThat(rules.layerOn(ANY_MONTH, "K/0", new BigDecimal("10000000")).upTo())
                .isEqualByComparingTo("10050000");
    }

    @Test
    @DisplayName("layer boundaries are inclusive at the top and exclusive at the bottom")
    void layerBoundariesAreInclusive() {
        // Layer 8 ends at 9.650.000 (1.75%); layer 9 starts at 9.650.001 (2%).
        assertThat(rules.rateOn(ANY_MONTH, "TK/0", new BigDecimal("9650000")))
                .isEqualByComparingTo("0.0175");
        assertThat(rules.rateOn(ANY_MONTH, "TK/0", new BigDecimal("9650001")))
                .isEqualByComparingTo("0.02");
    }

    @Test
    @DisplayName("the first layer is 0% and covers the lowest incomes")
    void zeroRateForLowIncome() {
        assertThat(rules.rateOn(ANY_MONTH, "TK/0", BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(rules.rateOn(ANY_MONTH, "TK/0", new BigDecimal("5400000"))).isEqualByComparingTo("0");
        assertThat(rules.rateOn(ANY_MONTH, "TK/0", new BigDecimal("5400001")))
                .isEqualByComparingTo("0.0025");
    }

    @Test
    @DisplayName("the top layer is 34% and is unbounded")
    void topLayerIsUnbounded() {
        TerRules.Layer top = rules.layerOn(ANY_MONTH, "K/3", new BigDecimal("99999999999"));
        assertThat(top.isUnbounded()).isTrue();
        assertThat(top.rate()).isEqualByComparingTo("0.34");
    }

    @Test
    @DisplayName("every table is exhaustive: no income falls between layers")
    void everyIncomeLandsInExactlyOneLayer() {
        for (String category : new String[] { "A", "B", "C" }) {
            TerRules.Table table = rules.tableOn(ANY_MONTH, category);
            for (TerRules.Layer layer : table.layers()) {
                if (layer.upTo() == null) {
                    continue;
                }
                // The ceiling itself must be covered by this layer...
                assertThat(layer.covers(layer.upTo())).isTrue();
                // ...and one rupiah above it must not be.
                assertThat(layer.covers(layer.upTo().add(BigDecimal.ONE))).isFalse();
            }
        }
    }

    @Test
    @DisplayName("rates never decrease as income rises")
    void ratesAreMonotonic() {
        for (String category : new String[] { "A", "B", "C" }) {
            var layers = rules.tableOn(ANY_MONTH, category).layers();
            for (int i = 1; i < layers.size(); i++) {
                assertThat(layers.get(i).rate())
                        .as("%s layer %d", category, i + 1)
                        .isGreaterThanOrEqualTo(layers.get(i - 1).rate());
            }
        }
    }

    @Test
    @DisplayName("an unknown PTKP status is an error, not a default table")
    void unknownStatusFails() {
        assertThatThrownBy(() -> rules.categoryFor("K/9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("K/9");
    }

    @Test
    @DisplayName("a date before TER took effect is rejected, never approximated")
    void refusesDatesBeforeTerExisted() {
        assertThatThrownBy(() -> rules.rateOn(LocalDate.of(2023, 6, 1), "TK/0", new BigDecimal("10000000")))
                .isInstanceOf(NoRuleForDateException.class)
                .hasMessageContaining("2023-06-01");
    }

    @Test
    @DisplayName("a negative income is rejected")
    void negativeIncomeFails() {
        assertThatThrownBy(() -> rules.rateOn(ANY_MONTH, "TK/0", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
