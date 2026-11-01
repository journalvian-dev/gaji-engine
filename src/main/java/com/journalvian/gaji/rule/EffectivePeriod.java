package com.journalvian.gaji.rule;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A half-open date interval, {@code [from, to)}.
 *
 * <p>Half-open is deliberate. A rule that runs "from 1 March 2026 to 1 March 2027"
 * and a rule that runs "from 1 March 2027" must not both claim 1 March 2027 —
 * otherwise the resolved answer depends on iteration order, which is exactly the
 * kind of bug that makes payroll irreproducible.
 *
 * <p>{@code to == null} means the interval is still open (no successor yet).
 */
public record EffectivePeriod(LocalDate from, LocalDate to) {

    public EffectivePeriod {
        Objects.requireNonNull(from, "from must not be null");
        if (to != null && !to.isAfter(from)) {
            throw new IllegalArgumentException(
                    "to (%s) must be after from (%s)".formatted(to, from));
        }
    }

    public boolean covers(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return !date.isBefore(from) && (to == null || date.isBefore(to));
    }

    public boolean isOpenEnded() {
        return to == null;
    }
}
