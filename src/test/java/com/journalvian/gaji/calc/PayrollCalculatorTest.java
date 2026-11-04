package com.journalvian.gaji.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.journalvian.gaji.rule.BpjsRules;
import com.journalvian.gaji.rule.BracketsRules;
import com.journalvian.gaji.rule.NoRuleForDateException;
import com.journalvian.gaji.rule.Pph21DeductionRules;
import com.journalvian.gaji.rule.PtkpRules;
import com.journalvian.gaji.rule.TerRules;
import com.journalvian.gaji.rule.YamlRuleLoader;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayrollCalculatorTest {

    private PayrollCalculator calculator;

    @BeforeAll
    void setUp() {
        YamlRuleLoader loader = new YamlRuleLoader();
        calculator = new PayrollCalculator(
                new BpjsRules(loader),
                new PtkpRules(loader),
                new TerRules(loader),
                new BracketsRules(loader),
                new Pph21DeductionRules(loader));
    }

    private CalculationResponse calculate(String gross, String status, String period) {
        return calculator.calculate(new CalculationRequest(
                new BigDecimal(gross), status, LocalDate.parse(period)));
    }

    // ------------------------------------------------------------------
    // The worked example from PP 58/2023.
    //
    // The regulation states the pension contribution outright — Rp 100.000 a
    // month for Tuan R — so it is an input to the example, not a BPJS result.
    // January's TER figure therefore reproduces end to end, while December is
    // checked by feeding the same input into the reconciliation.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PP 58/2023 example, January: TER withholds Rp200.000")
    void officialExampleJanuary() {
        CalculationResponse result = calculate("10000000", "K/0", "2026-01-01");

        assertThat(result.pph21Method()).isEqualTo("TER");
        assertThat(result.pph21()).isEqualByComparingTo("200000");
        assertThat(result.december()).isNull();
    }

    @Test
    @DisplayName("PP 58/2023 example, December: reconciliation yields Rp515.000")
    void officialExampleDecember() {
        DecemberReconciliation december = calculator.reconcileAnnual(
                LocalDate.of(2026, 12, 1),
                "K/0",
                new BigDecimal("10000000"),
                new BigDecimal("1200000")); // 12 x the stated Rp 100.000

        assertThat(december.annualGross()).isEqualByComparingTo("120000000");
        assertThat(december.biayaJabatan()).isEqualByComparingTo("6000000");
        assertThat(december.iuranPensiunDanHariTua()).isEqualByComparingTo("1200000");
        assertThat(december.ptkp()).isEqualByComparingTo("58500000");
        assertThat(december.pkp()).isEqualByComparingTo("54300000");
        assertThat(december.annualTax()).isEqualByComparingTo("2715000");
        assertThat(december.withheldJanuaryToNovember()).isEqualByComparingTo("2200000");
        assertThat(december.decemberTax()).isEqualByComparingTo("515000");
    }

    @Test
    @DisplayName("December deducts both JHT and JP; BPJS Kesehatan is not deductible")
    void decemberDeductsJhtAndJp() {
        CalculationResponse result = calculate("10000000", "K/0", "2026-12-01");

        assertThat(result.pph21Method()).isEqualTo("ANNUAL_RECONCILIATION");

        DecemberReconciliation december = result.december();
        // (200.000 JHT + 100.000 JP) x 12. Kesehatan's 100.000 is excluded:
        // PMK 168/2023 Pasal 10(1)(b) covers pension and old-age programmes only.
        assertThat(december.iuranPensiunDanHariTua()).isEqualByComparingTo("3600000");
        assertThat(december.pkp()).isEqualByComparingTo("51900000");
        assertThat(december.annualTax()).isEqualByComparingTo("2595000");
        assertThat(result.pph21()).isEqualByComparingTo("395000");
        assertThat(result.takeHomePay()).isEqualByComparingTo("9205000");
    }

    // ------------------------------------------------------------------
    // The BPJS side
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the four deductions and take-home for a normal month")
    void fullBreakdownJanuary() {
        CalculationResponse result = calculate("10000000", "K/0", "2026-01-15");

        assertThat(result.jht()).isEqualByComparingTo("200000");        // 2%, no cap
        assertThat(result.jp()).isEqualByComparingTo("100000");         // 1%, below cap
        assertThat(result.kesehatan()).isEqualByComparingTo("100000");  // 1%, below cap
        assertThat(result.pph21()).isEqualByComparingTo("200000");
        assertThat(result.totalDeductions()).isEqualByComparingTo("600000");
        assertThat(result.takeHomePay()).isEqualByComparingTo("9400000");
    }

    @Test
    @DisplayName("JP and Kesehatan are capped; JHT is not")
    void capsAreAppliedPerProgram() {
        // April 2026, so the cap is the one that took effect in March 2026.
        CalculationResponse result = calculate("20000000", "K/0", "2026-04-15");

        assertThat(result.jht()).isEqualByComparingTo("400000");        // 2% of 20jt, uncapped
        assertThat(result.jp()).isEqualByComparingTo("110863");         // 1% of the 2026 cap
        assertThat(result.kesehatan()).isEqualByComparingTo("120000");  // 1% of the 12jt cap
    }

    @Test
    @DisplayName("the March cap change moves the JP deduction, not the others")
    void jpChangesInMarch() {
        CalculationResponse january = calculate("12000000", "K/0", "2026-01-15");
        CalculationResponse april = calculate("12000000", "K/0", "2026-04-15");

        assertThat(january.jp()).isEqualByComparingTo("105474");
        assertThat(april.jp()).isEqualByComparingTo("110863");

        // Everything else is unaffected by the March change.
        assertThat(january.jht()).isEqualByComparingTo(april.jht());
        assertThat(january.kesehatan()).isEqualByComparingTo(april.kesehatan());
    }

    @Test
    @DisplayName("a salary below every threshold is deducted only for BPJS")
    void lowSalary() {
        CalculationResponse result = calculate("5000000", "TK/0", "2026-06-01");

        assertThat(result.jht()).isEqualByComparingTo("100000");
        assertThat(result.jp()).isEqualByComparingTo("50000");
        assertThat(result.kesehatan()).isEqualByComparingTo("50000");
        assertThat(result.pph21()).isEqualByComparingTo("0");
        assertThat(result.takeHomePay()).isEqualByComparingTo("4800000");
    }

    @Test
    @DisplayName("a higher PTKP status pays less tax on the same gross")
    void ptkpStatusAffectsTax() {
        CalculationResponse single = calculate("10000000", "TK/0", "2026-06-01");
        CalculationResponse marriedThree = calculate("10000000", "K/3", "2026-06-01");

        assertThat(marriedThree.pph21()).isLessThan(single.pph21());
    }

    @Test
    @DisplayName("December is usually not equal to the other eleven months")
    void decemberDiffersFromOtherMonths() {
        CalculationResponse november = calculate("10000000", "K/0", "2026-11-01");
        CalculationResponse december = calculate("10000000", "K/0", "2026-12-01");

        assertThat(december.pph21()).isNotEqualByComparingTo(november.pph21());
    }

    @Test
    @DisplayName("occupational cost is 5% until it reaches the annual ceiling")
    void biayaJabatanIsCapped() {
        // 72 million a year: 5% is 3.6 million, under the ceiling.
        assertThat(calculate("6000000", "TK/0", "2026-12-01").december().biayaJabatan())
                .isEqualByComparingTo("3600000");

        // 120 million a year: 5% would be 6 million, exactly the ceiling.
        assertThat(calculate("10000000", "K/0", "2026-12-01").december().biayaJabatan())
                .isEqualByComparingTo("6000000");

        // 240 million a year: 5% would be 12 million, clamped to 6.
        assertThat(calculate("20000000", "K/0", "2026-12-01").december().biayaJabatan())
                .isEqualByComparingTo("6000000");
    }

    @Test
    @DisplayName("a period before TER existed is refused, not approximated")
    void refusesPreTerPeriod() {
        assertThatThrownBy(() -> calculate("10000000", "K/0", "2023-06-01"))
                .isInstanceOf(NoRuleForDateException.class);
    }

    @Test
    @DisplayName("invalid input is rejected")
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> calculate("-1", "K/0", "2026-06-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grossMonthly");

        assertThatThrownBy(() -> calculate("10000000", " ", "2026-06-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ptkpStatus");

        assertThatThrownBy(() -> calculate("10000000", "K/9", "2026-06-01"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
