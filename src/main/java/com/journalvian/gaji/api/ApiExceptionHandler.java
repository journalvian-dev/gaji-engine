package com.journalvian.gaji.api;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.journalvian.gaji.rule.NoRuleForDateException;

@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Asking about a date with no rules is a client error, not a server fault, and it
     * must never be answered by quietly falling back to the nearest rule set.
     */
    @ExceptionHandler(NoRuleForDateException.class)
    public ResponseEntity<ApiError> noRuleForDate(NoRuleForDateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("NO_RULE_FOR_DATE", ex.getMessage(), ex.date()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> badParameter(MethodArgumentTypeMismatchException ex) {
        String message = "Parameter '%s' has an invalid value: '%s'"
                .formatted(ex.getName(), ex.getValue());
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_PARAMETER", message, null));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException ex) {
        String message = "Required parameter '%s' is missing".formatted(ex.getParameterName());
        return ResponseEntity.badRequest()
                .body(new ApiError("MISSING_PARAMETER", message, null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError("BAD_REQUEST", ex.getMessage(), LocalDate.now()));
    }
}
