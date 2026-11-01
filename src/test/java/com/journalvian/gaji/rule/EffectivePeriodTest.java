package com.journalvian.gaji.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EffectivePeriodTest {

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2027, 3, 1);

    @Test
    @DisplayName("starts on its 'from' date")
    void startsInclusive() {
        EffectivePeriod period = new EffectivePeriod(FROM, TO);
        assertThat(period.covers(FROM)).isTrue();
        assertThat(period.covers(FROM.minusDays(1))).isFalse();
    }

    @Test
    @DisplayName("ends before its 'to' date — the boundary belongs to the next period")
    void endsExclusive() {
        EffectivePeriod first = new EffectivePeriod(FROM, TO);
        EffectivePeriod second = new EffectivePeriod(TO, null);

        LocalDate boundary = TO;

        assertThat(first.covers(boundary)).isFalse();
        assertThat(second.covers(boundary)).isTrue();
    }

    @Test
    @DisplayName("an open-ended period covers any later date")
    void openEnded() {
        EffectivePeriod period = new EffectivePeriod(FROM, null);
        assertThat(period.isOpenEnded()).isTrue();
        assertThat(period.covers(LocalDate.of(2099, 1, 1))).isTrue();
    }

    @Test
    @DisplayName("rejects an interval that does not move forward")
    void rejectsEmptyInterval() {
        assertThatThrownBy(() -> new EffectivePeriod(TO, TO))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new EffectivePeriod(TO, FROM))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
