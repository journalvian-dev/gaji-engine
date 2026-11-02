package com.journalvian.gaji.rule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * The TER (Tarif Efektif Rata-rata) tables used for monthly PPh 21 withholding
 * from January 2024 onwards, loaded from {@code rules/pph21-ter.yaml}.
 *
 * <p>Three tables, chosen by PTKP status. For a monthly gross income the table
 * gives an effective rate; withholding is that rate applied to the gross.
 *
 * <p>December is not computed from this table — it uses the annual progressive
 * calculation and then subtracts what was already withheld. Keeping that
 * separate is deliberate: the two methods legitimately disagree, and the
 * disagreement is the reconciliation.
 */
@Component
public class TerRules {

    static final String RULE_SET = "pph21.ter";

    private static final String RESOURCE = "rules/pph21-ter.yaml";

    private final List<Interval> intervals;
    private final Map<String, String> categoryByStatus;

    public TerRules(YamlRuleLoader loader) {
        this.intervals = load(loader);
        this.categoryByStatus = indexStatuses();
    }

    /** The TER table ('A', 'B' or 'C') that applies to a PTKP status. */
    public String categoryFor(String status) {
        String normalised = normalise(status);
        String category = categoryByStatus.get(normalised);
        if (category == null) {
            throw new IllegalArgumentException(
                    "Unknown PTKP status '%s'. Supported: %s"
                            .formatted(status, categoryByStatus.keySet()));
        }
        return category;
    }

    /** The effective rate for a monthly gross income on a given date. */
    public BigDecimal rateOn(LocalDate date, String status, BigDecimal monthlyGross) {
        Table table = tableOn(date, categoryFor(status));
        return layerFor(table, monthlyGross).rate();
    }

    /** The layer that covers a monthly gross income, including its ceiling. */
    public Layer layerOn(LocalDate date, String status, BigDecimal monthlyGross) {
        return layerFor(tableOn(date, categoryFor(status)), monthlyGross);
    }

    public Table tableOn(LocalDate date, String category) {
        String normalised = category.trim().toUpperCase();
        return intervals.stream()
                .filter(interval -> interval.period().covers(date))
                .findFirst()
                .map(interval -> interval.tables().get(normalised))
                .orElseThrow(() -> new NoRuleForDateException(RULE_SET, date));
    }

    public List<Interval> intervals() {
        return intervals;
    }

    /** The primary source that the table applied on {@code date} was transcribed from. */
    public String sourceOn(LocalDate date) {
        return intervals.stream()
                .filter(interval -> interval.period().covers(date))
                .findFirst()
                .map(Interval::source)
                .orElseThrow(() -> new NoRuleForDateException(RULE_SET, date));
    }

    // ------------------------------------------------------------------

    private Layer layerFor(Table table, BigDecimal monthlyGross) {
        if (monthlyGross == null || monthlyGross.signum() < 0) {
            throw new IllegalArgumentException("Monthly gross income must be zero or positive");
        }
        return table.layers().stream()
                .filter(layer -> layer.covers(monthlyGross))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "TER table %s has no layer for %s — the table is not exhaustive"
                                .formatted(table.category(), monthlyGross)));
    }

    private static String normalise(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("PTKP status is required");
        }
        return status.trim().toUpperCase().replace(' ', '/');
    }

    private Map<String, String> indexStatuses() {
        Map<String, String> index = new LinkedHashMap<>();
        for (Interval interval : intervals) {
            interval.tables().forEach((category, table) -> table.statuses().forEach(status -> {
                String previous = index.putIfAbsent(normalise(status), category);
                if (previous != null && !previous.equals(category)) {
                    throw new RuleDataException(
                            "%s: PTKP status '%s' appears in both category %s and %s"
                                    .formatted(RULE_SET, status, previous, category));
                }
            }));
        }
        return Map.copyOf(index);
    }

    private List<Interval> load(YamlRuleLoader loader) {
        List<Interval> parsed = new ArrayList<>();

        for (Map<String, Object> entry : loader.readList(RESOURCE)) {
            String where = "%s (entry from %s)".formatted(RESOURCE, entry.get("from"));
            LocalDate from = RuleValues.dateOrNull(entry, "from", where);
            if (from == null) {
                throw new RuleDataException("%s: 'from' is required".formatted(where));
            }

            Map<String, Object> rawCategories = RuleValues.map(entry, "categories", where);
            Map<String, Table> tables = new LinkedHashMap<>();
            rawCategories.forEach((category, value) -> {
                String categoryWhere = "%s, category %s".formatted(where, category);
                Map<String, Object> rawTable = asMap(value, categoryWhere);
                tables.put(category.toUpperCase(),
                        new Table(category.toUpperCase(),
                                readStatuses(rawTable, categoryWhere),
                                readLayers(rawTable, categoryWhere)));
            });

            if (tables.size() != 3) {
                throw new RuleDataException(
                        "%s: expected categories A, B and C, found %s"
                                .formatted(where, tables.keySet()));
            }

            parsed.add(new Interval(
                    new EffectivePeriod(from, RuleValues.dateOrNull(entry, "to", where)),
                    Map.copyOf(tables),
                    RuleValues.string(entry, "source", where)));
        }

        parsed.sort(Comparator.comparing(interval -> interval.period().from()));
        if (parsed.isEmpty()) {
            throw new RuleDataException("%s contains no entries".formatted(RESOURCE));
        }
        return List.copyOf(parsed);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value, String where) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new RuleDataException("%s must be a mapping".formatted(where));
        }
        return (Map<String, Object>) raw;
    }

    private static List<String> readStatuses(Map<String, Object> rawTable, String where) {
        Object value = rawTable.get("statuses");
        if (!(value instanceof List<?> raw)) {
            throw new RuleDataException("%s: 'statuses' must be a list".formatted(where));
        }
        return raw.stream().map(String::valueOf).toList();
    }

    private static List<Layer> readLayers(Map<String, Object> rawTable, String where) {
        Object value = rawTable.get("layers");
        if (!(value instanceof List<?> raw) || raw.isEmpty()) {
            throw new RuleDataException("%s: 'layers' must be a non-empty list".formatted(where));
        }

        List<Layer> layers = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            Map<String, Object> rawLayer = asMap(raw.get(i), "%s, layer %d".formatted(where, i + 1));
            layers.add(new Layer(
                    RuleValues.decimalOrNull(rawLayer, "upTo", where),
                    RuleValues.decimal(rawLayer, "rate", where)));
        }
        validateLayers(layers, where);
        return List.copyOf(layers);
    }

    /**
     * A TER table must be exhaustive, ordered and monotonic.
     *
     * <p>Every rupiah of gross income has to land in exactly one layer, and the
     * rate must never fall as income rises. Both are cheap to check here and
     * expensive to discover in a payslip.
     */
    private static void validateLayers(List<Layer> layers, String where) {
        BigDecimal previousCeiling = null;

        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            boolean isLast = i == layers.size() - 1;

            if (layer.upTo() == null) {
                if (!isLast) {
                    throw new RuleDataException(
                            "%s: only the final layer may be unbounded".formatted(where));
                }
            } else if (previousCeiling != null && layer.upTo().compareTo(previousCeiling) <= 0) {
                throw new RuleDataException(
                        "%s: layer %d ceiling %s must be greater than the previous ceiling %s"
                                .formatted(where, i + 1, layer.upTo(), previousCeiling));
            }

            if (!isLast && layer.upTo() == null) {
                throw new RuleDataException("%s: layer %d is unbounded too early".formatted(where, i + 1));
            }
            if (i > 0 && layer.rate().compareTo(layers.get(i - 1).rate()) < 0) {
                throw new RuleDataException(
                        "%s: layer %d rate %s is lower than the previous rate %s"
                                .formatted(where, i + 1, layer.rate(), layers.get(i - 1).rate()));
            }

            previousCeiling = layer.upTo();
        }

        if (layers.get(layers.size() - 1).upTo() != null) {
            throw new RuleDataException(
                    "%s: the final layer must be unbounded (upTo: null)".formatted(where));
        }
    }

    // ------------------------------------------------------------------

    /** One layer of a TER table: an income ceiling and the rate up to it. */
    public record Layer(BigDecimal upTo, BigDecimal rate) {

        public boolean covers(BigDecimal monthlyGross) {
            return upTo == null || monthlyGross.compareTo(upTo) <= 0;
        }

        public boolean isUnbounded() {
            return upTo == null;
        }
    }

    /** One TER table, with the PTKP statuses that select it. */
    public record Table(String category, List<String> statuses, List<Layer> layers) {
    }

    public record Interval(EffectivePeriod period, Map<String, Table> tables, String source) {
    }
}
