package com.journalvian.gaji.rule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Typed, fail-fast access to a parsed YAML rule entry. */
final class RuleValues {

    private RuleValues() {
    }

    static String string(Map<String, Object> entry, String key, String where) {
        Object value = entry.get(key);
        if (value == null) {
            throw new RuleDataException("%s: missing required key '%s'".formatted(where, key));
        }
        return String.valueOf(value);
    }

    static String stringOrNull(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        return value == null ? null : String.valueOf(value);
    }

    static LocalDate dateOrNull(Map<String, Object> entry, String key, String where) {
        String raw = stringOrNull(entry, key);
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            throw new RuleDataException(
                    "%s: '%s' must be an ISO date (yyyy-MM-dd), got '%s'".formatted(where, key, raw), e);
        }
    }

    static BigDecimal decimal(Map<String, Object> entry, String key, String where) {
        BigDecimal value = decimalOrNull(entry, key, where);
        if (value == null) {
            throw new RuleDataException("%s: missing required key '%s'".formatted(where, key));
        }
        return value;
    }

    static BigDecimal decimalOrNull(Map<String, Object> entry, String key, String where) {
        Object value = entry.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (RuntimeException e) {
            throw new RuleDataException(
                    "%s: '%s' must be numeric, got '%s'".formatted(where, key, value), e);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Map<String, Object> entry, String key, String where) {
        Object value = entry.get(key);
        if (!(value instanceof Map<?, ?> raw)) {
            throw new RuleDataException("%s: '%s' must be a mapping".formatted(where, key));
        }
        return (Map<String, Object>) raw;
    }
}
