package com.journalvian.gaji.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.journalvian.gaji.rule.BpjsRules;
import com.journalvian.gaji.rule.JpCapRule;
import com.journalvian.gaji.rule.PtkpRules;

/**
 * Rule lookup endpoints.
 *
 * <p>These exist before the calculator on purpose: the interesting claim of this
 * project is that rules are dated, and that claim should be demonstrable on its own
 * before anything is calculated with them.
 */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final BpjsRules bpjsRules;
    private final PtkpRules ptkpRules;

    public RuleController(BpjsRules bpjsRules, PtkpRules ptkpRules) {
        this.bpjsRules = bpjsRules;
        this.ptkpRules = ptkpRules;
    }

    @GetMapping("/jp-cap")
    public JpCapResponse jpCap(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        JpCapRule rule = bpjsRules.jpCapOn(date);
        BigDecimal maxEmployeeAmount = rule.wageCap()
                .multiply(rule.employeeRate())
                .setScale(0, RoundingMode.HALF_UP);

        return new JpCapResponse(
                date,
                rule.wageCap(),
                rule.employeeRate(),
                maxEmployeeAmount,
                rule.source(),
                rule.period().from(),
                rule.period().to());
    }

    @GetMapping("/ptkp")
    public PtkpResponse ptkp(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String status) {

        return new PtkpResponse(
                date,
                status.trim().toUpperCase(),
                ptkpRules.ptkpOn(date, status),
                "PMK 101/PMK.010/2016");
    }
}
