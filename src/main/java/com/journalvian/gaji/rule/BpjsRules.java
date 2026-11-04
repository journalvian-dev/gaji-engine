package com.journalvian.gaji.rule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * BPJS employee deductions, loaded from {@code rules/bpjs.yaml}.
 *
 * <p>Three programs are modelled: {@code jht}, {@code jp} and {@code kesehatan}.
 * Each is effective-dated, because the JP wage cap changes every March.
 */
@Component
public class BpjsRules {

    static final String JP_RULE_SET = "bpjs.jp";

    private static final String RESOURCE = "rules/bpjs.yaml";

    private final Map<String, List<BpjsProgramRule>> byProgram;
    private final List<JpCapRule> jpCaps;

    public BpjsRules(YamlRuleLoader loader) {
        this.byProgram = load(loader);
        this.jpCaps = rulesFor("jp").stream()
                .map(rule -> new JpCapRule(
                        rule.period(), rule.wageCap(), rule.employeeRate(), rule.employerRate(), rule.source()))
                .toList();
    }

    /**
     * The parameters of a program on {@code date}.
     *
     * @throws NoRuleForDateException if no interval covers the date
     */
    public BpjsProgramRule programOn(String program, LocalDate date) {
        String key = normaliseProgram(program);
        List<BpjsProgramRule> rules = byProgram.get(key);
        if (rules == null) {
            throw new IllegalArgumentException(
                    "Unknown BPJS program '%s'. Known: %s".formatted(program, byProgram.keySet()));
        }
        return rules.stream()
                .filter(rule -> rule.period().covers(date))
                .findFirst()
                .orElseThrow(() -> new NoRuleForDateException("bpjs." + key, date));
    }

    /**
     * The employee's contribution to a program for a monthly wage.
     *
     * <p>Rounded to whole rupiah, as payroll deductions are.
     */
    public BigDecimal employeeDeduction(String program, BigDecimal monthlyWage, LocalDate date) {
        BpjsProgramRule rule = programOn(program, date);
        return rule.contributableBase(monthlyWage)
                .multiply(rule.employeeRate())
                .setScale(0, RoundingMode.HALF_UP);
    }

    /** The JP wage cap in force on {@code date}. */
    public JpCapRule jpCapOn(LocalDate date) {
        return jpCaps.stream()
                .filter(rule -> rule.period().covers(date))
                .findFirst()
                .orElseThrow(() -> new NoRuleForDateException(JP_RULE_SET, date));
    }

    /** Employee JP contribution for a monthly wage, capped at the applicable ceiling. */
    public BigDecimal employeeJpContribution(BigDecimal monthlyWage, LocalDate date) {
        return employeeDeduction("jp", monthlyWage, date);
    }

    public List<JpCapRule> jpCaps() {
        return jpCaps;
    }

    public List<BpjsProgramRule> rulesFor(String program) {
        return byProgram.getOrDefault(normaliseProgram(program), List.of());
    }

    // ------------------------------------------------------------------

    private static String normaliseProgram(String program) {
        if (program == null || program.isBlank()) {
            throw new IllegalArgumentException("BPJS program is required");
        }
        return program.trim().toLowerCase();
    }

    private Map<String, List<BpjsProgramRule>> load(YamlRuleLoader loader) {
        Map<String, List<BpjsProgramRule>> grouped = new LinkedHashMap<>();

        for (Map<String, Object> entry : loader.readList(RESOURCE)) {
            String program = normaliseProgram(RuleValues.string(entry, "program", RESOURCE));
            String where = "%s (%s entry from %s)".formatted(RESOURCE, program, entry.get("from"));

            Map<String, Object> rate = RuleValues.map(entry, "rate", where);
            LocalDate from = RuleValues.dateOrNull(entry, "from", where);
            if (from == null) {
                throw new RuleDataException("%s: 'from' is required".formatted(where));
            }

            grouped.computeIfAbsent(program, ignored -> new ArrayList<>())
                    .add(new BpjsProgramRule(
                            program,
                            new EffectivePeriod(from, RuleValues.dateOrNull(entry, "to", where)),
                            RuleValues.decimal(rate, "employee", where),
                            RuleValues.decimal(rate, "employer", where),
                            RuleValues.decimalOrNull(entry, "wageCap", where),
                            RuleValues.string(entry, "source", where)));
        }

        grouped.forEach((program, rules) -> {
            rules.sort(Comparator.comparing(rule -> rule.period().from()));
            validateContiguous(program, rules);
        });

        Map<String, List<BpjsProgramRule>> frozen = new LinkedHashMap<>();
        grouped.forEach((program, rules) -> frozen.put(program, List.copyOf(rules)));
        return Map.copyOf(frozen);
    }

    /**
     * Intervals must tile the timeline with no overlaps and no gaps.
     *
     * <p>An overlap makes the answer depend on iteration order. A gap silently
     * turns a valid date into an error later. Both are caught at startup, while
     * the stack trace still points at the YAML someone just edited.
     */
    private static void validateContiguous(String program, List<BpjsProgramRule> rules) {
        for (int i = 1; i < rules.size(); i++) {
            EffectivePeriod previous = rules.get(i - 1).period();
            EffectivePeriod current = rules.get(i).period();

            if (previous.isOpenEnded()) {
                throw new RuleDataException(
                        "bpjs.%s: interval starting %s is open-ended, so the next one starting %s can never apply"
                                .formatted(program, previous.from(), current.from()));
            }
            if (previous.to().isAfter(current.from())) {
                throw new RuleDataException(
                        "bpjs.%s: intervals overlap — %s ends %s but the next starts %s"
                                .formatted(program, previous.from(), previous.to(), current.from()));
            }
            if (previous.to().isBefore(current.from())) {
                throw new RuleDataException(
                        "bpjs.%s: gap in rule coverage between %s and %s"
                                .formatted(program, previous.to(), current.from()));
            }
        }
    }
}
