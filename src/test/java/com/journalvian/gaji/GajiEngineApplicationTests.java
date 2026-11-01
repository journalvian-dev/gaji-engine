package com.journalvian.gaji;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.journalvian.gaji.rule.BpjsRules;
import com.journalvian.gaji.rule.PtkpRules;

/**
 * Proves the application context starts with the shipped rule files.
 *
 * <p>Rule validation happens in the constructors of the rule components, so a
 * broken or contradictory YAML file fails here rather than at the first request.
 */
@SpringBootTest
class GajiEngineApplicationTests {

    @Autowired
    private BpjsRules bpjsRules;

    @Autowired
    private PtkpRules ptkpRules;

    @Test
    @DisplayName("the context starts and every rule set loaded")
    void contextLoads() {
        assertThat(bpjsRules.jpCaps()).isNotEmpty();
        assertThat(ptkpRules.supportedStatuses()).hasSize(8);
    }
}
