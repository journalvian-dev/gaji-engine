package com.journalvian.gaji.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.journalvian.gaji.rule.BpjsRules;
import com.journalvian.gaji.rule.PtkpRules;
import com.journalvian.gaji.rule.TerRules;
import com.journalvian.gaji.rule.YamlRuleLoader;

class RuleControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        YamlRuleLoader loader = new YamlRuleLoader();
        RuleController controller = new RuleController(
                new BpjsRules(loader),
                new PtkpRules(loader),
                new TerRules(loader));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /jp-cap reports the cap in force on that exact date")
    void reportsCapForDate() throws Exception {
        mockMvc.perform(get("/api/v1/rules/jp-cap").param("date", "2026-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wageCap").value(10547400))
                .andExpect(jsonPath("$.employeeRate").value(0.01))
                .andExpect(jsonPath("$.maxEmployeeAmount").value(105474))
                .andExpect(jsonPath("$.validFrom").value("2025-03-01"))
                .andExpect(jsonPath("$.validTo").value("2026-03-01"));
    }

    @Test
    @DisplayName("the same request one month later answers with the next cap")
    void answersDifferAcrossMarch() throws Exception {
        mockMvc.perform(get("/api/v1/rules/jp-cap").param("date", "2026-04-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wageCap").value(11086300))
                .andExpect(jsonPath("$.validFrom").value("2026-03-01"))
                .andExpect(jsonPath("$.validTo").doesNotExist());
    }

    @Test
    @DisplayName("a date outside the rule set is a 400 with an explicit code")
    void unknownDateIsClientError() throws Exception {
        mockMvc.perform(get("/api/v1/rules/jp-cap").param("date", "2019-05-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_RULE_FOR_DATE"));
    }

    @Test
    @DisplayName("a malformed date is a 400, not a 500")
    void malformedDateIsClientError() throws Exception {
        mockMvc.perform(get("/api/v1/rules/jp-cap").param("date", "15-01-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("GET /ptkp resolves a status to an annual amount")
    void resolvesPtkp() throws Exception {
        mockMvc.perform(get("/api/v1/rules/ptkp")
                        .param("date", "2026-06-01")
                        .param("status", "K/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(63000000));
    }

    @Test
    @DisplayName("an unknown PTKP status is rejected")
    void unknownStatusIsClientError() throws Exception {
        mockMvc.perform(get("/api/v1/rules/ptkp")
                        .param("date", "2026-06-01")
                        .param("status", "K/9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("the worked example from PP 58/2023: Rp10.000.000, K/0, table A layer 9")
    void terMatchesTheOfficialExample() throws Exception {
        mockMvc.perform(get("/api/v1/rules/ter")
                        .param("date", "2026-06-01")
                        .param("status", "K/0")
                        .param("gross", "10000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("A"))
                .andExpect(jsonPath("$.rate").value(0.02))
                .andExpect(jsonPath("$.withholding").value(200000))
                .andExpect(jsonPath("$.layerCeiling").value(10050000));
    }

    @Test
    @DisplayName("a negative gross is rejected")
    void negativeGrossIsClientError() throws Exception {
        mockMvc.perform(get("/api/v1/rules/ter")
                        .param("date", "2026-06-01")
                        .param("status", "K/0")
                        .param("gross", "-1"))
                .andExpect(status().isBadRequest());
    }
}
