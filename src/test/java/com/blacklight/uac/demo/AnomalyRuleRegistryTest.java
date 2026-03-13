package com.blacklight.uac.demo;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AnomalyRuleRegistryTest {

    static class WeirdFault extends Throwable {
        WeirdFault(String message) { super(message); }
    }

    @Test
    void learnsUnknownExceptionAndPersistsRule() throws Exception {
        Path dir = Files.createTempDirectory("uac-rule-test");
        Path base = dir.resolve("anomaly-rules.yaml");
        Path learned = dir.resolve("anomaly-learned.yaml");

        Files.writeString(base, "rules:\n", StandardCharsets.UTF_8);
        Files.writeString(learned, "rules:\n", StandardCharsets.UTF_8);

        AnomalyRuleRegistry reg = new AnomalyRuleRegistry(base, learned);
        assertNull(reg.findMatchingRule("java.lang.MyCustomException: boom"));

        AnomalyRuleRegistry.AnomalyRule rule = reg.ensureLearnedRule("java.lang.MyCustomException");
        assertNotNull(rule);
        assertEquals("MY_CUSTOM_EXCEPTION", rule.anomalyType);

        String persisted = Files.readString(learned, StandardCharsets.UTF_8);
        assertTrue(persisted.contains("MY_CUSTOM_EXCEPTION"));
        assertTrue(persisted.contains("MyCustomException"));

        AnomalyRuleRegistry.AnomalyRule matched = reg.findMatchingRule("ERROR java.lang.MyCustomException: boom");
        assertNotNull(matched);
        assertEquals("MY_CUSTOM_EXCEPTION", matched.anomalyType);
    }

    @Test
    void extractsThrowableSubclassEvenWithoutExceptionSuffix() throws Exception {
        Path dir = Files.createTempDirectory("uac-rule-test-throwable");
        Path base = dir.resolve("anomaly-rules.yaml");
        Path learned = dir.resolve("anomaly-learned.yaml");

        Files.writeString(base, "rules:\n", StandardCharsets.UTF_8);
        Files.writeString(learned, "rules:\n", StandardCharsets.UTF_8);

        AnomalyRuleRegistry reg = new AnomalyRuleRegistry(base, learned);

        String token = reg.extractExceptionClass(
                "ERROR " + WeirdFault.class.getName() + ": synthetic throwable");

        assertEquals(WeirdFault.class.getName(), token);
    }
}

