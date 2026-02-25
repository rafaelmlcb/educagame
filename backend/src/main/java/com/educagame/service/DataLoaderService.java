package com.educagame.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.stream.Stream;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dynamic theme discovery: data/{theme-name}/*.json with fallback to default.
 */
@ApplicationScoped
public class DataLoaderService {

    private static final Logger LOG = Logger.getLogger(DataLoaderService.class);
    private static final String DATA_BASE = "data";
    private static final String DEFAULT_THEME = "default";

    @Inject
    ObjectMapper objectMapper;

    /**
     * Discover theme names: read data/themes.txt (one theme per line), then verify data/{theme}/ exists via load.
     */
    public List<String> discoverThemes() {
        List<String> themes = new ArrayList<>();
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(DATA_BASE + "/themes.txt")) {
            if (is != null) {
                String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                for (String line : content.split("\n")) {
                    String t = line.trim();
                    if (!t.isEmpty() && !t.startsWith("#")) themes.add(t);
                }
            }
        } catch (Exception e) {
            LOG.debugf("Could not read themes.txt: %s", e.getMessage());
        }
        if (themes.isEmpty()) themes.add(DEFAULT_THEME);
        return themes;
    }

    /**
     * Load JSON array from theme. Tries data/{theme}/{fileName}, then data/default/{fileName}.
     */
    public List<Map<String, Object>> loadJsonArray(String theme, String fileName) {
        List<Map<String, Object>> fromTheme = loadResourceJsonArray(DATA_BASE + "/" + theme + "/" + fileName);
        if (fromTheme != null) return fromTheme;
        if (!DEFAULT_THEME.equals(theme)) {
            fromTheme = loadResourceJsonArray(DATA_BASE + "/" + DEFAULT_THEME + "/" + fileName);
            if (fromTheme != null) {
                LOG.debugf("Fallback: loaded %s from theme default", fileName);
                return fromTheme;
            }
        }
        return List.of();
    }

    public List<Map<String, Object>> getWheelSegments(String theme) {
        return loadJsonArray(theme, "wheel.json");
    }

    public List<Map<String, Object>> getQuizQuestions(String theme) {
        return loadJsonArray(theme, "quiz.json");
    }

    public List<Map<String, Object>> getMillionaireQuestions(String theme) {
        return loadJsonArray(theme, "millionaire.json");
    }

    /** Roletrando: phrases to guess (fallback to default). */
    public List<String> getPhrases(String theme) {
        List<Map<String, Object>> raw = loadJsonArray(theme, "phrases.json");
        if (raw == null || raw.isEmpty()) return List.of("Brasil", "Educacao", "Matematica");
        return raw.stream()
                .map(m -> m.get("phrase") != null ? String.valueOf(m.get("phrase")) : "")
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Sequencing: ordered items for arrangement games. */
    public List<Map<String, Object>> getSequences(String theme) {
        return loadJsonArray(theme, "sequences.json");
    }

    /** Detective: mysteries with progressive clues. */
    public List<Map<String, Object>> getMysteries(String theme) {
        return loadJsonArray(theme, "mysteries.json");
    }

    /** Sensory: sounds, images, and media items. */
    public List<Map<String, Object>> getSensoryItems(String theme) {
        return loadJsonArray(theme, "sensory.json");
    }

    /** Binary Decision: true/false statements. */
    public List<Map<String, Object>> getStatements(String theme) {
        return loadJsonArray(theme, "statements.json");
    }

    /** Combination: multi-stage game configurations. */
    public List<Map<String, Object>> getCombinationStages(String theme) {
        return loadJsonArray(theme, "combination.json");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadResourceJsonArray(String resourcePath) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) return null;
            Object raw = objectMapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
            return (List<Map<String, Object>>) raw;
        } catch (Exception e) {
            LOG.debugf("Could not load %s: %s", resourcePath, e.getMessage());
            return null;
        }
    }
}
