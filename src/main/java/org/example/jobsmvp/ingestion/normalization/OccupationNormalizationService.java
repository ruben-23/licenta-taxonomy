//package org.example.jobsmvp.ingestion.normalization;
//
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import dev.langchain4j.data.embedding.Embedding;
//import dev.langchain4j.model.chat.ChatModel;
//import dev.langchain4j.model.embedding.EmbeddingModel;
//import dev.langchain4j.model.input.PromptTemplate;
//import org.example.jobsmvp.models.nodes.Occupation;
//import org.example.jobsmvp.repositories.OccupationRepository;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.time.LocalDate;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//
///**
// * Resolves a raw occupation string (extracted by the LLM from a job posting)
// * to a canonical {@link Occupation} node, using the occupations taxonomy as
// * the authoritative source.
// *
// * Resolution strategy (in priority order):
// *  1. Exact match (case-insensitive) against known Occupation nodes in the graph.
// *  2. Cosine similarity over Occupation node embeddings (threshold: 0.88).
// *  3. LLM classification fallback for low-confidence embedding matches.
// *  4. If nothing matches, the occupation is buffered in memory and flushed to
// *     a single JSON file at the end of the ingestion run:
// *       unmatched-occupations/unmatched-{date}.json
// *     Schema per entry:
// *       { "name": "...", "layer": 3, "type": "Specialized Role", "parent": "..." }
// */
//@Service
//public class OccupationNormalizationService {
//
//    private static final Logger log = LoggerFactory.getLogger(OccupationNormalizationService.class);
//    private static final double EMBEDDING_MATCH_THRESHOLD = 0.88;
//
//    // ── LLM prompt ────────────────────────────────────────────────────────────
//
//    private static final PromptTemplate CLASSIFICATION_TEMPLATE = PromptTemplate.from("""
//            You are classifying a job occupation into a predefined taxonomy.
//
//            The taxonomy has three levels:
//              Layer 1 — Job Family      (e.g. "Software Engineering & Architecture")
//              Layer 2 — Core Occupation (e.g. "Back-End Developer")
//              Layer 3 — Specialized Role (e.g. "Java Developer")
//
//            Known entries (name | layer | parent):
//            {{taxonomyHint}}
//
//            Given the raw occupation below, return ONLY a valid JSON object — no markdown, no explanation.
//
//            If the occupation matches something in the taxonomy, return:
//            { "matched": true, "name": "<canonical name from taxonomy>" }
//
//            If it does NOT match anything in the taxonomy, return:
//            { "matched": false, "name": "<best canonical name>", "layer": <1|2|3>, "type": "<Job Family|Core Occupation|Specialized Role>", "parent": "<direct parent name or null>" }
//
//            Raw occupation: {{rawOccupation}}
//            """);
//
//    // ── Fields ────────────────────────────────────────────────────────────────
//
//    private final EmbeddingModel embeddingModel;
//    private final ChatModel chatModel;
//    private final OccupationRepository occupationRepository;
//    private final ObjectMapper objectMapper;
//    private final String unmatchedOutputDir;
//
//    /** Taxonomy hint string built once at startup from known occupations. */
//    private String taxonomyHint = "";
//
//    /** Per-run cache: raw string → resolved Occupation. Cleared between runs. */
//    private final Map<String, Occupation> cache = new ConcurrentHashMap<>();
//
//    /** Occupations that could not be matched — flushed to JSON at run end. */
//    private final List<Map<String, Object>> unmatchedBuffer = Collections.synchronizedList(new ArrayList<>());
//
//    public OccupationNormalizationService(
//            EmbeddingModel embeddingModel,
//            ChatModel chatModel,
//            OccupationRepository occupationRepository,
//            ObjectMapper objectMapper,
//            @Value("${ingestion.unmatched-occupations-path:unmatched-occupations}") String unmatchedOutputDir
//    ) {
//        this.embeddingModel       = embeddingModel;
//        this.chatModel            = chatModel;
//        this.occupationRepository = occupationRepository;
//        this.objectMapper         = objectMapper;
//        this.unmatchedOutputDir   = unmatchedOutputDir;
//    }
//
//    @PostConstruct
//    public void init() throws IOException {
//        Files.createDirectories(Paths.get(unmatchedOutputDir));
//        rebuildTaxonomyHint();
//    }
//
//    // ── Public API ────────────────────────────────────────────────────────────
//
//    /**
//     * Resolves a raw occupation string to a canonical {@link Occupation} node.
//     * Returns {@code Optional.empty()} only if the input is blank.
//     */
//    public Optional<Occupation> resolveOccupation(String rawOccupation) {
//        if (rawOccupation == null || rawOccupation.isBlank()) return Optional.empty();
//        String raw = rawOccupation.strip();
//
//        // Cache hit
//        Occupation cached = cache.get(raw.toLowerCase());
//        if (cached != null) return Optional.of(cached);
//
//        Occupation resolved = doResolve(raw);
//        cache.put(raw.toLowerCase(), resolved);
//        return Optional.of(resolved);
//    }
//
//    /**
//     * Flushes any unmatched occupations collected during the current run to a
//     * dated JSON file, then resets per-run state ready for the next run.
//     *
//     * Call this at the end of every ingestion run (from the orchestrator).
//     */
//    public void flushAndReset() {
//        flushUnmatched();
//        cache.clear();
//        unmatchedBuffer.clear();
//    }
//
//    /** Rebuilds the taxonomy hint from the current state of the graph. */
//    public void rebuildTaxonomyHint() {
//        List<Occupation> all = occupationRepository.findAll();
//        if (all.isEmpty()) {
//            taxonomyHint = "(taxonomy not yet seeded)";
//            return;
//        }
//        StringBuilder sb = new StringBuilder();
//        for (Occupation o : all) {
//            sb.append(o.getName())
//                    .append(" | layer=").append(o.getLayer())
//                    .append(" | parent=").append(o.getParent() != null ? o.getParent() : "root")
//                    .append("\n");
//        }
//        taxonomyHint = sb.toString();
//    }
//
//    // ── Resolution steps ──────────────────────────────────────────────────────
//
//    private Occupation doResolve(String raw) {
//
//        // Step 1 — exact match
//        Optional<Occupation> exact = occupationRepository.findByNameIgnoreCase(raw);
//        if (exact.isPresent()) {
//            log.debug("Occupation exact match '{}' → '{}'", raw, exact.get().getName());
//            return exact.get();
//        }
//
//        // Step 2 — embedding similarity
//        Optional<Occupation> byEmbedding = findByEmbeddingSimilarity(raw);
//        if (byEmbedding.isPresent()) {
//            log.debug("Occupation embedding match '{}' → '{}'", raw, byEmbedding.get().getName());
//            return byEmbedding.get();
//        }
//
//        // Step 3 — LLM classification
//        return askLlm(raw);
//    }
//
//    private Optional<Occupation> findByEmbeddingSimilarity(String raw) {
//        Embedding queryEmbedding = embeddingModel.embed(raw).content();
//        double[] vector = toDoubleArray(queryEmbedding.vector());
//        return occupationRepository.findMostSimilarOccupation(vector, EMBEDDING_MATCH_THRESHOLD);
//    }
//
//    private Occupation askLlm(String raw) {
//        try {
//            String prompt = CLASSIFICATION_TEMPLATE.apply(Map.of(
//                    "rawOccupation", raw,
//                    "taxonomyHint",  taxonomyHint
//            )).text();
//
//            String response = chatModel.chat(prompt).strip()
//                    .replaceAll("(?s)^```json\\s*", "")
//                    .replaceAll("(?s)```\\s*$", "")
//                    .strip();
//
//            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
//            boolean matched = Boolean.TRUE.equals(parsed.get("matched"));
//
//            if (matched) {
//                // LLM says it maps to a known taxonomy entry
//                String canonicalName = (String) parsed.get("name");
//                Optional<Occupation> existing = occupationRepository.findByNameIgnoreCase(canonicalName);
//                if (existing.isPresent()) return existing.get();
//                // Name returned by LLM differs slightly — create & persist it
//                return createAndPersist(canonicalName, null, null, null);
//            } else {
//                // Genuinely new — buffer for JSON output and create a transient node
//                String name   = (String) parsed.get("name");
//                Object layer  = parsed.get("layer");
//                String type   = (String) parsed.getOrDefault("type", "Specialized Role");
//                String parent = (String) parsed.get("parent");
//
//                bufferUnmatched(name, layer instanceof Number ? ((Number) layer).intValue() : 3, type, parent);
//                return createAndPersist(name, layer instanceof Number ? ((Number) layer).intValue() : 3, type, parent);
//            }
//
//        } catch (Exception e) {
//            log.warn("LLM occupation classification failed for '{}': {}", raw, e.getMessage());
//            // Last resort: create an unclassified occupation
//            bufferUnmatched(raw, 3, "Specialized Role", null);
//            return createAndPersist(raw, 3, "Specialized Role", null);
//        }
//    }
//
//    // ── Persistence helpers ───────────────────────────────────────────────────
//
//    private Occupation createAndPersist(String name, Integer layer, String type, String parent) {
//        // Check the graph once more before inserting (another thread may have just created it)
//        Optional<Occupation> existing = occupationRepository.findByNameIgnoreCase(name);
//        if (existing.isPresent()) return existing.get();
//
//        Occupation occ = new Occupation();
//        occ.setOccupationId(UUID.nameUUIDFromBytes(name.toLowerCase().getBytes()).toString());
//        occ.setName(name);
//        occ.setLayer(layer != null ? layer : 3);
//        occ.setType(type  != null ? type  : "Specialized Role");
//        occ.setParent(parent);
//
//        // Generate embedding
//        Embedding emb = embeddingModel.embed(name).content();
//        occ.setTextEmbedding(emb.vectorAsList().stream().map(Float::doubleValue).toList());
//
//        log.info("Creating new Occupation node: '{}'", name);
//        return occupationRepository.save(occ);
//    }
//
//    // ── Unmatched buffer ──────────────────────────────────────────────────────
//
//    private void bufferUnmatched(String name, int layer, String type, String parent) {
//        Map<String, Object> entry = new LinkedHashMap<>();
//        entry.put("name",   name);
//        entry.put("layer",  layer);
//        entry.put("type",   type);
//        entry.put("parent", parent);
//        unmatchedBuffer.add(entry);
//    }
//
//    private void flushUnmatched() {
//        if (unmatchedBuffer.isEmpty()) return;
//
//        String date     = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
//        Path   filePath = Paths.get(unmatchedOutputDir, "unmatched-" + date + ".json");
//
//        try {
//            ObjectMapper pretty = objectMapper.copy()
//                    .enable(SerializationFeature.INDENT_OUTPUT);
//            pretty.writeValue(filePath.toFile(), unmatchedBuffer);
//            log.info("Flushed {} unmatched occupation(s) to {}", unmatchedBuffer.size(), filePath);
//        } catch (IOException e) {
//            log.error("Failed to write unmatched occupations file: {}", e.getMessage());
//        }
//    }
//
//    // ── Utilities ─────────────────────────────────────────────────────────────
//
//    private static double[] toDoubleArray(float[] f) {
//        double[] d = new double[f.length];
//        for (int i = 0; i < f.length; i++) d[i] = f[i];
//        return d;
//    }
//}

package org.example.jobsmvp.ingestion.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import org.example.jobsmvp.models.nodes.Occupation;
import org.example.jobsmvp.repositories.OccupationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a raw occupation string (extracted from a job posting) to a
 * canonical {@link Occupation} node, using the occupations taxonomy as the
 * authoritative source.
 *
 * Resolution strategy (in priority order):
 *  1. Exact match (case-insensitive) against known Occupation nodes in the graph.
 *  2. Cosine similarity over Occupation node embeddings (threshold: 0.88).
 *  3. LLM classification fallback.
 *  4. If nothing matches, the occupation is buffered and written to a single
 *     dated JSON file at the end of the ingestion run via {@link #flushAndReset()}.
 *
 * Wiring:
 *  - {@link org.example.jobsmvp.ingestion.transform.GraphTransformService}
 *    calls {@link #resolveOccupation(String)} per job.
 *  - {@link org.example.jobsmvp.ingestion.orchestrator.IngestionPipelineOrchestrator}
 *    calls {@link #flushAndReset()} after the run completes.
 */
@Service
public class OccupationNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(OccupationNormalizationService.class);
    private static final double EMBEDDING_MATCH_THRESHOLD = 0.88;

    // ── LLM prompt ────────────────────────────────────────────────────────────

    private static final PromptTemplate CLASSIFICATION_TEMPLATE = PromptTemplate.from("""
            You are classifying a job occupation into a predefined taxonomy.

            The taxonomy has three levels:
              Layer 1 — Job Family       (e.g. "Software Engineering & Architecture")
              Layer 2 — Core Occupation  (e.g. "Back-End Developer")
              Layer 3 — Specialized Role (e.g. "Java Developer")

            Known taxonomy entries (name | layer | parent):
            {{taxonomyHint}}

            Given the raw occupation below, return ONLY a valid JSON object — no markdown, no explanation.

            If the occupation matches a known taxonomy entry, return:
            { "matched": true, "name": "<canonical name from taxonomy>" }

            If it does NOT match anything, return:
            { "matched": false, "name": "<best canonical name>", "layer": <1|2|3>, "type": "<Job Family|Core Occupation|Specialized Role>", "parent": "<direct parent name or null>" }

            Raw occupation: {{rawOccupation}}
            """);

    // ── Fields ────────────────────────────────────────────────────────────────

    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;
    private final OccupationRepository occupationRepository;
    private final ObjectMapper objectMapper;
    private final String unmatchedOutputDir;

    /** Taxonomy hint built from all known Occupation nodes; rebuilt on startup. */
    private String taxonomyHint = "";

    /** Per-run cache: lower-cased raw string → resolved Occupation. */
    private final Map<String, Occupation> cache = new ConcurrentHashMap<>();

    /** Unmatched occupations buffered during a run; written to JSON by flushAndReset(). */
    private final List<Map<String, Object>> unmatchedBuffer =
            Collections.synchronizedList(new ArrayList<>());

    public OccupationNormalizationService(
            EmbeddingModel embeddingModel,
            ChatModel chatModel,
            OccupationRepository occupationRepository,
            ObjectMapper objectMapper,
            @Value("${ingestion.unmatched-occupations-path:unmatched-occupations}") String unmatchedOutputDir
    ) {
        this.embeddingModel       = embeddingModel;
        this.chatModel            = chatModel;
        this.occupationRepository = occupationRepository;
        this.objectMapper         = objectMapper;
        this.unmatchedOutputDir   = unmatchedOutputDir;
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(Paths.get(unmatchedOutputDir));
        rebuildTaxonomyHint();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolves a raw occupation string to a canonical {@link Occupation} node.
     *
     * Called by {@link org.example.jobsmvp.ingestion.transform.GraphTransformService}
     * for every job processed in the pipeline.
     *
     * @return the resolved Occupation, or {@link Optional#empty()} if input is blank
     */
    public Optional<Occupation> resolveOccupation(String rawOccupation) {
        if (rawOccupation == null || rawOccupation.isBlank()) return Optional.empty();
        String raw = rawOccupation.strip();

        Occupation cached = cache.get(raw.toLowerCase());
        if (cached != null) return Optional.of(cached);

        Occupation resolved = doResolve(raw);
        cache.put(raw.toLowerCase(), resolved);
        return Optional.of(resolved);
    }

    /**
     * Writes all unmatched occupations collected during the current run to a
     * single dated JSON file, then clears per-run state ready for the next run.
     *
     * Called by {@link org.example.jobsmvp.ingestion.orchestrator.IngestionPipelineOrchestrator}
     * after {@code run()} completes, alongside {@code clearCache()} on
     * {@link EntityNormalizationService}.
     */
    public void flushAndReset() {
        flushUnmatched();
        cache.clear();
        unmatchedBuffer.clear();
        rebuildTaxonomyHint();
    }

    // ── Resolution steps ──────────────────────────────────────────────────────

    private Occupation doResolve(String raw) {
        // Step 1 — exact match
        Optional<Occupation> exact = occupationRepository.findByNameIgnoreCase(raw);
        if (exact.isPresent()) {
            log.debug("Occupation exact match '{}' → '{}'", raw, exact.get().getName());
            return exact.get();
        }

        // Step 2 — embedding similarity
        Optional<Occupation> byEmbedding = findByEmbeddingSimilarity(raw);
        if (byEmbedding.isPresent()) {
            log.debug("Occupation embedding match '{}' → '{}'", raw, byEmbedding.get().getName());
            return byEmbedding.get();
        }

        // Step 3 — LLM classification
        return askLlm(raw);
    }

    private Optional<Occupation> findByEmbeddingSimilarity(String raw) {
        Embedding queryEmbedding = embeddingModel.embed(raw).content();
        double[] vector = toDoubleArray(queryEmbedding.vector());
        return occupationRepository.findMostSimilarOccupation(vector, EMBEDDING_MATCH_THRESHOLD);
    }

    private Occupation askLlm(String raw) {
        try {
            String prompt = CLASSIFICATION_TEMPLATE.apply(Map.of(
                    "rawOccupation", raw,
                    "taxonomyHint",  taxonomyHint
            )).text();

            String response = chatModel.chat(prompt).strip()
                    .replaceAll("(?s)^```json\\s*", "")
                    .replaceAll("(?s)```\\s*$", "")
                    .strip();

            Map<String, Object> parsed  = objectMapper.readValue(response, Map.class);
            boolean   matched = Boolean.TRUE.equals(parsed.get("matched"));

            if (matched) {
                String canonicalName = (String) parsed.get("name");
                Optional<Occupation> existing = occupationRepository.findByNameIgnoreCase(canonicalName);
                if (existing.isPresent()) return existing.get();
                return createAndPersist(canonicalName, null, null, null);
            }

            // Genuinely new occupation
            String name   = (String) parsed.get("name");
            int    layer  = parsed.get("layer") instanceof Number n ? n.intValue() : 3;
            String type   = (String) parsed.getOrDefault("type", "Specialized Role");
            String parent = (String) parsed.get("parent");

            bufferUnmatched(name, layer, type, parent);
            return createAndPersist(name, layer, type, parent);

        } catch (Exception e) {
            log.warn("LLM occupation classification failed for '{}': {}", raw, e.getMessage());
            bufferUnmatched(raw, 3, "Specialized Role", null);
            return createAndPersist(raw, 3, "Specialized Role", null);
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private Occupation createAndPersist(String name, Integer layer, String type, String parent) {
        Optional<Occupation> existing = occupationRepository.findByNameIgnoreCase(name);
        if (existing.isPresent()) return existing.get();

        Occupation occ = new Occupation();
        occ.setOccupationId(UUID.nameUUIDFromBytes(name.toLowerCase().getBytes()).toString());
        occ.setName(name);
        occ.setLayer(layer != null ? layer : 3);
        occ.setType(type  != null ? type  : "Specialized Role");
        occ.setParent(parent);

        Embedding emb = embeddingModel.embed(name).content();
        occ.setTextEmbedding(emb.vectorAsList().stream().map(Float::doubleValue).toList());

        log.info("Creating new Occupation node: '{}'", name);
        return occupationRepository.save(occ);
    }

    // ── Unmatched buffer ──────────────────────────────────────────────────────

    private void bufferUnmatched(String name, int layer, String type, String parent) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name",   name);
        entry.put("layer",  layer);
        entry.put("type",   type);
        entry.put("parent", parent);
        unmatchedBuffer.add(entry);
    }

    private void flushUnmatched() {
        if (unmatchedBuffer.isEmpty()) return;

        String date     = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        Path   filePath = Paths.get(unmatchedOutputDir, "unmatched-" + date + ".json");

        try {
            objectMapper.copy()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .writeValue(filePath.toFile(), unmatchedBuffer);
            log.info("Flushed {} unmatched occupation(s) to {}", unmatchedBuffer.size(), filePath);
        } catch (IOException e) {
            log.error("Failed to write unmatched occupations file: {}", e.getMessage());
        }
    }

    // ── Taxonomy hint ─────────────────────────────────────────────────────────

    private void rebuildTaxonomyHint() {
        List<Occupation> all = occupationRepository.findAll();
        if (all.isEmpty()) {
            taxonomyHint = "(taxonomy not yet seeded)";
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Occupation o : all) {
            sb.append(o.getName())
                    .append(" | layer=").append(o.getLayer())
                    .append(" | parent=").append(o.getParent() != null ? o.getParent() : "root")
                    .append("\n");
        }
        taxonomyHint = sb.toString();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static double[] toDoubleArray(float[] f) {
        double[] d = new double[f.length];
        for (int i = 0; i < f.length; i++) d[i] = f[i];
        return d;
    }
}