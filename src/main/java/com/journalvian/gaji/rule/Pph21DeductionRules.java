package com.journalvian.gaji.rule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Annual deductions used by the December reconciliation, loaded from
 * {@code rules/pph21-deductions.yaml}.
 *
 * <p>Kept as data rather than constants so that a change to the occupational
 * cost ceiling is a YAML edit with a citation, not a code change with no trace
 * of where the old number came from.
 */
@Component
public class Pph21DeductionRules {

    static final String RULE_SET = "pph21.deductions";

    private static final String RESOURCE = "rules/pph21-deductions.yaml";

    private final List<Pph21DeductionRule> rules;

    public Pph21DeductionRules(YamlRuleLoader loader) {
        this.rules = load(loader);
    }

    public Pph21DeductionRule ruleOn(LocalDate date) {
        return rules.stream()
                .filter(rule -> rule.period().covers(date))
                .findFirst()
                .orElseThrow(() -> new NoRuleForDateException(RULE_SET, date));
    }

    // ------------------------------------------------------------------

    private List<Pph21DeductionRule> load(YamlRuleLoader loader) {
        List<Pph21DeductionRule> parsed = new ArrayList<>();

        for (Map<String, Object> entry : loader.readList(RESOURCE)) {
            String where = "%s (entry from %s)".formatted(RESOURCE, entry.get("from"));

            LocalDate from = RuleValues.dateOrNull(entry, "from", where);
            if (from == null) {
                throw new RuleDataException("%s: 'from' is required".formatted(where));
            }

            Map<String, Object> biayaJabatan = RuleValues.map(entry, "biayaJabatan", where);

            Pph21DeductionRule rule = new Pph21DeductionRule(
                    new EffectivePeriod(from, RuleValues.dateOrNull(entry, "to", where)),
                    RuleValues.decimal(biayaJabatan, "rate", where),
                    RuleValues.decimal(biayaJabatan, "annualCap", where),
                    RuleValues.decimal(entry, "pkpRoundingIncrement", where),
                    RuleValues.string(entry, "source", where),
                    RuleValues.string(entry, "pkpRoundingSource", where));

            validate(rule, where);
            parsed.add(rule);
        }

        if (parsed.isEmpty()) {
            throw new RuleDataException("%s contains no entries".formatted(RESOURCE));
        }

        parsed.sort(Comparator.comparing(rule -> rule.period().from()));
        return List.copyOf(parsed);
    }

    private static void validate(Pph21DeductionRule rule, String where) {
        if (rule.biayaJabatanRate().signum() < 0) {
            throw new RuleDataException("%s: biayaJabatan.rate cannot be negative".formatted(where));
        }
        if (rule.biayaJabatanAnnualCap().signum() < 0) {
            throw new RuleDataException("%s: biayaJabatan.annualCap cannot be negative".formatted(where));
        }
        if (rule.pkpRoundingIncrement().signum() <= 0) {
            // A zero increment would divide by zero at calculation time.
            throw new RuleDataException("%s: pkpRoundingIncrement must be positive".formatted(where));
        }
        if (rule.source().isBlank()) {
            throw new RuleDataException("%s: 'source' is required — no uncited figures".formatted(where));
        }
    }
}
