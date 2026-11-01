package com.journalvian.gaji.rule;

/** A rule file is missing, malformed, or contradicts itself. Fails at startup. */
public class RuleDataException extends RuntimeException {

    public RuleDataException(String message) {
        super(message);
    }

    public RuleDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
