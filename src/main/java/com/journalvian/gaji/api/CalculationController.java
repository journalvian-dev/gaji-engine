package com.journalvian.gaji.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.journalvian.gaji.calc.CalculationRequest;
import com.journalvian.gaji.calc.CalculationResponse;
import com.journalvian.gaji.calc.PayrollCalculator;

/** Payslip calculation. */
@RestController
@RequestMapping("/api/v1")
public class CalculationController {

    private final PayrollCalculator payrollCalculator;

    public CalculationController(PayrollCalculator payrollCalculator) {
        this.payrollCalculator = payrollCalculator;
    }

    @PostMapping("/calculate")
    public CalculationResponse calculate(@RequestBody CalculationRequest request) {
        return payrollCalculator.calculate(request);
    }
}
