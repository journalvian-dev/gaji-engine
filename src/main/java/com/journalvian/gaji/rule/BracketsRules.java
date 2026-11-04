package com.journalvian.gaji.rule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Progressive PPh 21 brackets (Pasal 17), loaded from
 * {@code rules/pph21-brackets.yaml}.
 *
 * <p>Not used for routine monthly withholding since 2024 — that is TER. This is
 * what the December reconciliation uses: the brackets are applied to the year's
 * Penghasilan Kena Pajak, and the result is compared against what was already
 * withheld.
 */
@Component
public class BracketsRules {

    static final String RULE_SET = "pph21.brackets";

    private static final String RESOURCE = "rules/pph21-brackets.yaml";

    private final List<Interval> intervals;

    public BracketsRules(YamlRuleLoader loader) {
        this.intervals = load(loader);
    }

    /**
     * Progressive tax on a Penghasilan Kena Pajak, rounded to whole rupiah.
     *
     * <p>Each bracket only taxes the slice of income that falls inside it.
     */
    public BigDecimal taxOn(LocalDate date, BigDecimal pkp) {
        if (pkp == null || pkp.signum() < 0) {
            throw new IllegalArgumentException("Penghasilan Kena Pajak must be zero or positive");
        }

        BigDecimal remaining = pkp;
        BigDecimal lowerBound = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;

        for (Bracket bracket : bracketsOn(date)) {
            if (remaining.signum() <= 0) {
                break;
            }

            BigDecimal slice;
            if (bracket.isUnbounded()) {
                slice = remaining;
            } else {
                slice = remaining.min(bracket.upTo().subtract(lowerBound));
                lowerBound = bracket.upTo();
            }

            if (slice.signum() > 0) {
                tax = tax.add(slice.multiply(bracket.rate()));
                remaining = remaining.subtract(slice);
            }
        }

        return tax.setScale(0, RoundingMode.HALF_UP);
    }

    public List<Bracket> bracketsOn(LocalDate date) {
        return intervals.stream()
                .filter(interval -> interval.period().covers(date))
                .findFirst()
                .map(Interval::brackets)
                .orElseThrow(() -> new NoRuleForDateException(RULE_SET, date));
    }

    public String sourceOn(LocalDate date) {
        return intervals.stream()
                .filter(interval -> interval.period().covers(date))
                .findFirst()
                .map(Interval::source)
                .orElseThrow(() -> new NoRuleForDateException(RULE_SET, date));
    }

    // ------------------------------------------------------------------

    private List<Interval> load(YamlRuleLoader loader) {
        List<Interval> parsed = new ArrayList<>();

        for (Map<String, Object> entry : loader.readList(RESOURCE)) {
            String where = "%s (entry from %s)".formatted(RESOURCE, entry.get("from"));
            LocalDate from = RuleValues.dateOrNull(entry, "from", where);
            if (from == null) {
                throw new RuleDataException("%s: 'from' is required".formatted(where));
            }

            Object rawBrackets = entry.get("brackets");
            if (!(rawBrackets instanceof List<?> list) || list.isEmpty()) {
                throw new RuleDataException("%s: 'brackets' must be a non-empty list".formatted(where));
            }

            List<Bracket> brackets = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof Map<?, ?> raw)) {
                    throw new RuleDataException("%s: bracket %d is not a mapping".formatted(where, i + 1));
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> bracket = (Map<String, Object>) raw;
                brackets.add(new Bracket(
                        RuleValues.decimalOrNull(bracket, "upTo", where),
                        RuleValues.decimal(bracket, "rate", where)));
            }
            validateBrackets(brackets, where);

            parsed.add(new Interval(
                    new EffectivePeriod(from, RuleValues.dateOrNull(entry, "to", where)),
                    List.copyOf(brackets),
                    RuleValues.string(entry, "source", where)));
        }

        parsed.sort(Comparator.comparing(interval -> interval.period().from()));
        if (parsed.isEmpty()) {
            throw new RuleDataException("%s contains no entries".formatted(RESOURCE));
        }
        return List.copyOf(parsed);
    }

    private static void validateBrackets(List<Bracket> brackets, String where) {
        BigDecimal previousCeiling = null;

        for (int i = 0; i < brackets.size(); i++) {
            Bracket bracket = brackets.get(i);
            boolean isLast = i == brackets.size() - 1;

            if (bracket.isUnbounded() && !isLast) {
                throw new RuleDataException("%s: only the final bracket may be unbounded".formatted(where));
            }
            if (!bracket.isUnbounded()) {
                if (previousCeiling != null && bracket.upTo().compareTo(previousCeiling) <= 0) {
                    throw new RuleDataException(
                            "%s: bracket %d ceiling must be greater than the previous one".formatted(where, i + 1));
                }
                previousCeiling = bracket.upTo();
            }
            if (i > 0 && bracket.rate().compareTo(brackets.get(i - 1).rate()) < 0) {
                throw new RuleDataException(
                        "%s: bracket %d rate is lower than the previous rate".formatted(where, i + 1));
            }
        }

        if (!brackets.get(brackets.size() - 1).isUnbounded()) {
            throw new RuleDataException(
                    "%s: the final bracket must be unbounded (upTo: null)".formatted(where));
        }
    }

    // ------------------------------------------------------------------

    /** One progressive bracket: income up to {@code upTo}, taxed at {@code rate}. */
    public record Bracket(BigDecimal upTo, BigDecimal rate) {

        public boolean isUnbounded() {
            return upTo == null;
        }
    }

    public record Interval(EffectivePeriod period, List<Bracket> brackets, String source) {
    }
}
