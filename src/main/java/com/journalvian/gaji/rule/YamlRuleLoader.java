package com.journalvian.gaji.rule;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Reads a YAML rule file from the classpath into a list of raw entries.
 *
 * <p>Kept deliberately dumb: it produces maps, and the typed layers above decide
 * what those maps mean. Any shape problem is reported as a {@link RuleDataException}
 * at startup, so a broken rule file never reaches a calculation.
 */
@Component
public class YamlRuleLoader {

    public List<Map<String, Object>> readList(String classpathLocation) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object parsed;
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            parsed = yaml.load(in);
        } catch (IOException e) {
            throw new RuleDataException("Cannot read rule file '%s'".formatted(classpathLocation), e);
        }

        if (parsed == null) {
            return List.of();
        }
        if (!(parsed instanceof List<?> list)) {
            throw new RuleDataException(
                    "Rule file '%s' must contain a YAML list at the top level".formatted(classpathLocation));
        }

        List<Map<String, Object>> entries = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> raw)) {
                throw new RuleDataException(
                        "Rule file '%s': entry #%d is not a mapping".formatted(classpathLocation, i));
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            raw.forEach((key, value) -> entry.put(String.valueOf(key), value));
            // Map.copyOf would reject the null values that mean "open-ended interval".
            entries.add(Collections.unmodifiableMap(entry));
        }
        return List.copyOf(entries);
    }
}
