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
 * BPJS employee deductions, loaded from {@code rules/bpjs.yaml}.
 *
 * <p>Currently exposed: the Jaminan Pensiun wage cap, which is the piece that
 * changes every March and which makes effective-dating necessary.
 */
@Component
public class BpjsRules {

    static final String JP_RULE_SET = "bpjs.jp";

    private static final String RESOURCE = "rules/bpjs.yaml";

    private final List<JpCapRule> jpCaps;

    public BpjsRules(YamlRuleLoader loader) {
        this.jpCaps = loadJpCaps(loader);
    }

    /**
     * The JP wage cap in force on {@code date}.
     *
     * @throws NoRuleForDateException if no interval covers the date
     */
    public JpCapRule jpCapOn(LocalDate date) {
        return jpCaps.stream()
                .filter(rule -> rule.period().covers(date))
                .findFirst()
                .orElseThrow(() -> new NoRuleForDateException(JP_RULE_SET, date));
    }

    /** Employee JP contribution for a monthly wage, capped at the applicable ceiling. */
    public BigDecimal employeeJpContribution(BigDecimal monthlyWage, LocalDate date) {
        JpCapRule rule = jpCapOn(date);
        BigDecimal base = monthlyWage.min(rule.wageCap());
        return base.multiply(rule.employeeRate()).setScale(0, RoundingMode.HALF_UP);
    }

    public List<JpCapRule> jpCaps() {
        return jpCaps;
    }

    // ------------------------------------------------------------------

    private List<JpCapRule> loadJpCaps(YamlRuleLoader loader) {
        List<JpCapRule> rules = new ArrayList<>();

        for (Map<String, Object> entry : loader.readList(RESOURCE)) {
            String program = RuleValues.string(entry, "program", RESOURCE);
            if (!"jp".equalsIgnoreCase(program)) {
                continue;
            }
            String where = "%s (jp entry from %s)".formatted(RESOURCE, entry.get("from"));

            Map<String, Object> rate = RuleValues.map(entry, "rate", where);
            LocalDate from = RuleValues.dateOrNull(entry, "from", where);
            if (from == null) {
                throw new RuleDataException("%s: 'from' is required".formatted(where));
            }

            rules.add(new JpCapRule(
                    new EffectivePeriod(from, RuleValues.dateOrNull(entry, "to", where)),
                    RuleValues.decimal(entry, "wageCap", where),
                    RuleValues.decimal(rate, "employee", where),
                    RuleValues.decimal(rate, "employer", where),
                    RuleValues.string(entry, "source", where)));
        }

        rules.sort(Comparator.comparing(rule -> rule.period().from()));
        validateContiguous(rules);
        return List.copyOf(rules);
    }

    /**
     * Intervals must tile the timeline with no overlaps and no gaps.
     *
     * <p>An overlap makes the answer depend on iteration order. A gap silently
     * turns a valid date into an error later. Both are caught here, at startup,
     * while the stack trace still points at the YAML someone just edited.
     */
    private void validateContiguous(List<JpCapRule> rules) {
        for (int i = 0; i < rules.size(); i++) {
            EffectivePeriod current = rules.get(i).period();
            if (i == 0) {
                continue;
            }
            EffectivePeriod previous = rules.get(i - 1).period();

            if (previous.isOpenEnded()) {
                throw new RuleDataException(
                        "%s: interval starting %s is open-ended, so the next interval starting %s can never apply"
                                .formatted(JP_RULE_SET, previous.from(), current.from()));
            }
            if (previous.to().isAfter(current.from())) {
                throw new RuleDataException(
                        "%s: intervals overlap — %s ends %s but the next starts %s"
                                .formatted(JP_RULE_SET, previous.from(), previous.to(), current.from()));
            }
            if (previous.to().isBefore(current.from())) {
                throw new RuleDataException(
                        "%s: gap in rule coverage between %s and %s"
                                .formatted(JP_RULE_SET, previous.to(), current.from()));
            }
        }
    }
}
