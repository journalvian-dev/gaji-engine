package com.journalvian.gaji.rule;

import java.time.LocalDate;

/**
 * No rule is defined for the requested date.
 *
 * <p>This is a deliberate refusal, not a fallback. Silently applying today's rules
 * to a 2019 payslip would produce a confident wrong answer, which is worse than
 * an error.
 */
public class NoRuleForDateException extends RuntimeException {

    private final LocalDate date;
    private final String ruleSet;

    public NoRuleForDateException(String ruleSet, LocalDate date) {
        super("No '%s' rule is defined for %s".formatted(ruleSet, date));
        this.ruleSet = ruleSet;
        this.date = date;
    }

    public LocalDate date() {
        return date;
    }

    public String ruleSet() {
        return ruleSet;
    }
}
