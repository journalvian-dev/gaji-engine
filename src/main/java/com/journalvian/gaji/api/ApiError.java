package com.journalvian.gaji.api;

import java.time.LocalDate;

/** A machine-readable error. Kept small on purpose: no stack traces over the wire. */
public record ApiError(String code, String message, LocalDate date) {
}
