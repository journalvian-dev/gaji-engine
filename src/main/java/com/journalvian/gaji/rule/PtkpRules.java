package com.journalvian.gaji.rule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * PTKP (Penghasilan Tidak Kena Pajak) — the annual non-taxable threshold.
 *
 * <p>Loaded from {@code rules/ptkp.yaml}. The amount is selected by the taxpayer's
 * PTKP status at the start of the tax year, which is why the lookup takes a date
 * as well as a status.
 */
@Component
public class PtkpRules {

    static final String RULE_SET = "ptkp";

    private static final String RESOURCE = "rules/ptkp.yaml";

    private final List<Interval> intervals;

    public PtkpRules(YamlRuleLoader loader) {
        this.intervals = load(loader);
    }

    public BigDecimal ptkpOn(LocalDate date, String status) {
        String normalised = normalise(status);

        Interval interval = intervals.stream()
                .filter(candidate -> candidate.period().covers(date))
                .findFirst()
                .orElseThrow(() -> new NoRuleForDateException(RULE_SET, date));

        BigDecimal amount = interval.values().get(normalised);
        if (amount == null) {
            throw new IllegalArgumentException(
                    "Unknown PTKP status '%s'. Supported: %s"
                            .formatted(status, interval.values().keySet()));
        }
        return amount;
    }

    public Set<String> supportedStatuses() {
        return intervals.isEmpty() ? Set.of() : intervals.get(0).values().keySet();
    }

    // ------------------------------------------------------------------

    private static String normalise(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("PTKP status is required");
        }
        return status.trim().toUpperCase().replace(' ', '/');
    }

    private List<Interval> load(YamlRuleLoader loader) {
        List<Interval> parsed = new ArrayList<>();

        for (Map<String, Object> entry : loader.readList(RESOURCE)) {
            String where = "%s (entry from %s)".formatted(RESOURCE, entry.get("from"));
            LocalDate from = RuleValues.dateOrNull(entry, "from", where);
            if (from == null) {
                throw new RuleDataException("%s: 'from' is required".formatted(where));
            }

            Map<String, Object> rawValues = RuleValues.map(entry, "values", where);
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            rawValues.forEach((status, value) -> values.put(
                    normalise(status),
                    RuleValues.decimal(Map.of(status, value), status, where)));

            if (values.isEmpty()) {
                throw new RuleDataException("%s: 'values' must not be empty".formatted(where));
            }

            parsed.add(new Interval(
                    new EffectivePeriod(from, RuleValues.dateOrNull(entry, "to", where)),
                    Map.copyOf(values)));
        }

        parsed.sort(Comparator.comparing(interval -> interval.period().from()));
        return List.copyOf(parsed);
    }

    private record Interval(EffectivePeriod period, Map<String, BigDecimal> values) {
    }
}
