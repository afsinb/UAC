package com.blacklight.uac.ai;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AIDecisionEngineExternalRecipesTest {

    @Test
    void registerLearnedRecipe_persists_and_is_loadable() throws Exception {
        Path dir = Files.createTempDirectory("uac-ai-recipes");
        Path base = dir.resolve("fix-recipes.yaml");
        Path learned = dir.resolve("fix-recipes-learned.yaml");

        Files.writeString(base, "recipes:\n", StandardCharsets.UTF_8);
        Files.writeString(learned, "recipes:\n", StandardCharsets.UTF_8);

        AIDecisionEngine engine = new AIDecisionEngine(base, learned);
        assertFalse(engine.hasExactRecipe("BRAND_NEW_EXCEPTION"));

        AIDecisionEngine.FixRecipe recipe = engine.registerLearnedRecipe(
                "BRAND_NEW_EXCEPTION", "com.acme.BrandNewException");

        assertNotNull(recipe);
        assertEquals("BRAND_NEW_EXCEPTION", recipe.getAnomalyType());
        assertTrue(engine.hasExactRecipe("BRAND_NEW_EXCEPTION"));

        String persisted = Files.readString(learned, StandardCharsets.UTF_8);
        assertTrue(persisted.contains("LEARNED-BRAND_NEW_EXCEPTION"));
        assertTrue(persisted.contains("BRAND_NEW_EXCEPTION"));

        // Ensure recipes can be loaded by a fresh engine instance.
        AIDecisionEngine reloaded = new AIDecisionEngine(base, learned);
        assertTrue(reloaded.hasExactRecipe("BRAND_NEW_EXCEPTION"));
    }
}

