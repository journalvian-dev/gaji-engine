package com.journalvian.gaji.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

/**
 * Metadata for the generated OpenAPI document.
 *
 * <p>The document itself is built from the controllers, so the paths and schemas
 * cannot drift from the code. Only the prose lives here.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI gajiEngineOpenApi() {
        return new OpenAPI().info(new Info()
                .title("gaji-engine")
                .version("0.1.0")
                .description("""
                        Indonesian payroll calculation with effective-dated rules.

                        Rules are data, not code. Every figure carries a primary source \
                        (the regulation itself, not a blog summarising it), and each rule set is \
                        stored as half-open date intervals. A date with no matching interval \
                        returns 400 NO_RULE_FOR_DATE rather than silently falling back to the \
                        nearest one — a wrong payslip is worse than an error.

                        Two PPh 21 methods coexist, and the month decides which applies. \
                        January to November applies the TER rate to that month's gross. \
                        December recomputes the whole year with the Pasal 17 brackets and \
                        subtracts the eleven months already withheld, exposing every \
                        intermediate figure.
                        """)
                .contact(new Contact().name("gaji-engine")));
    }
}
