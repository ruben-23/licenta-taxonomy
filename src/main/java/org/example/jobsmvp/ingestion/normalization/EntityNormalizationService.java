package org.example.jobsmvp.ingestion.normalization;

import lombok.AllArgsConstructor;
import org.example.jobsmvp.models.nodes.Technology;
import org.example.jobsmvp.repositories.TechnologyRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps raw technology names extracted by the LLM to canonical Technology nodes.
 *
 * Strategy (in priority order):
 *  1. Exact match (case-insensitive) against known Technology nodes in the graph.
 *  2. Alias dictionary for well-known abbreviations (k8s → Kubernetes, etc.).
 *  3. Cosine similarity over Technology node embeddings (threshold: 0.88).
 *  4. LLM classification fallback for low-confidence embedding matches.
 *  5. Register as a new Technology with a generated tech_id if nothing matches.
 */
@Service
@AllArgsConstructor
public class EntityNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(EntityNormalizationService.class);
    private static final double EMBEDDING_MATCH_THRESHOLD = 0.88;

    // Well-known aliases that are too short or ambiguous for reliable embedding matching
    private static final Map<String, String> ALIAS_MAP = Map.ofEntries(
            Map.entry("k8s",       "Kubernetes"),
            Map.entry("py",        "Python"),
            Map.entry("js",        "JavaScript"),
            Map.entry("ts",        "TypeScript"),
            Map.entry("pg",        "PostgreSQL"),
            Map.entry("psql",      "PostgreSQL"),
            Map.entry("postgres",  "PostgreSQL"),
            Map.entry("mongo",     "MongoDB"),
            Map.entry("es",        "Elasticsearch"),
            Map.entry("aws",       "Amazon Web Services"),
            Map.entry("gcp",       "Google Cloud Platform"),
            Map.entry("az",        "Microsoft Azure"),
            Map.entry("tf",        "Terraform"),
            Map.entry("gh",        "GitHub"),
            Map.entry("ci/cd",     "CI/CD"),
            Map.entry("ml",        "Machine Learning"),
            Map.entry("dl",        "Deep Learning")
    );

    private static final PromptTemplate CLASSIFICATION_TEMPLATE = PromptTemplate.from("""
            You are normalising technology names for a knowledge graph.
            
            Given the raw technology name below, return the single most canonical, 
            correctly-capitalised technology name it refers to.
            
            If it is an abbreviation or alias (e.g. "k8s"), return the full name (e.g. "Kubernetes").
            If it is already canonical, return it unchanged.
            Return ONLY the canonical name — no explanation, no punctuation around it.
            
            Raw name: {{rawName}}
            """);

    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;
    private final TechnologyRepository technologyRepository;

    // In-memory cache: raw name → canonical Technology (cleared per pipeline run)
    private final Map<String, Technology> normalisationCache = new ConcurrentHashMap<>();


    /**
     * Normalises a list of raw technology names to canonical Technology nodes.
     * Returns a list of resolved (or newly created) Technology instances.
     */
    public List<Technology> normaliseTechnologies(List<String> rawNames) {
        if (rawNames == null || rawNames.isEmpty()) return List.of();

        List<Technology> results = new ArrayList<>();
        for (String raw : rawNames) {
            if (raw == null || raw.isBlank()) continue;
            try {
                results.add(resolveOrCreate(raw.strip()));
            } catch (Exception e) {
                log.warn("Failed to normalise technology '{}': {}", raw, e.getMessage());
            }
        }
        return results;
    }

    private Technology resolveOrCreate(String raw) {
        // Cache hit
        Technology cached = normalisationCache.get(raw.toLowerCase());
        if (cached != null) return cached;

        // 1. Exact match
        Optional<Technology> exact = technologyRepository.findTechnologyByNameIgnoreCase(raw);
        if (exact.isPresent()) {
            normalisationCache.put(raw.toLowerCase(), exact.get());
            return exact.get();
        }

        // 2. Alias dictionary
        String aliasResolved = ALIAS_MAP.get(raw.toLowerCase());
        if (aliasResolved != null) {
            Optional<Technology> fromAlias = technologyRepository.findTechnologyByNameIgnoreCase(aliasResolved);
            if (fromAlias.isPresent()) {
                normalisationCache.put(raw.toLowerCase(), fromAlias.get());
                return fromAlias.get();
            }
        }

        // 3. Embedding similarity
        Optional<Technology> embeddingMatch = findByEmbeddingSimilarity(raw);
        if (embeddingMatch.isPresent()) {
            normalisationCache.put(raw.toLowerCase(), embeddingMatch.get());
            return embeddingMatch.get();
        }

        // 4. LLM classification
        String llmCanonical = askLlmForCanonicalName(raw);
        Optional<Technology> llmMatch = technologyRepository.findTechnologyByNameIgnoreCase(llmCanonical);
        if (llmMatch.isPresent()) {
            normalisationCache.put(raw.toLowerCase(), llmMatch.get());
            return llmMatch.get();
        }

        // 5. Create new Technology node
        Technology newTech = createNewTechnology(llmCanonical);
        normalisationCache.put(raw.toLowerCase(), newTech);
        return newTech;
    }

    private Optional<Technology> findByEmbeddingSimilarity(String raw) {

        Embedding queryEmbedding = embeddingModel.embed(raw).content();
        double[] queryVector = toDoubleArray(queryEmbedding.vector());

        Optional<Technology> bestMatch = technologyRepository.findMostSimilarTechnology(
                queryVector,
                EMBEDDING_MATCH_THRESHOLD
        );

        bestMatch.ifPresent(tech ->
                log.debug("Memgraph embedding match '{}' → '{}'", raw, tech.getName())
        );

        return bestMatch;
    }

    private String askLlmForCanonicalName(String raw) {
        try {
            // --- NEW: Wait 1 minute before asking the LLM ---
            log.info("Waiting 10 seconds before classifying new technology '{}' via LLM...", raw);
            Thread.sleep(10000);
            String prompt = CLASSIFICATION_TEMPLATE.apply(Map.of("rawName", raw)).text();
            return chatModel.chat(prompt).strip();
        } catch (Exception e) {
            log.warn("LLM classification failed for '{}': {}", raw, e.getMessage());
            return capitalise(raw); // best-effort fallback
        }
    }

    private Technology createNewTechnology(String canonicalName) {
        Technology tech = new Technology();
        tech.setTech_id(UUID.randomUUID().toString());
        tech.setName(canonicalName);
        tech.setCategory(inferCategory(canonicalName));

        // Generate and store embedding
        Embedding textEmbedding = embeddingModel.embed(canonicalName).content();

        // Convert List<Float> to List<Double>
        List<Double> doubleEmbedding = textEmbedding.vectorAsList()
                .stream()
                .map(Float::doubleValue)
                .toList();

        tech.setTextEmbedding(doubleEmbedding);

        log.info("Creating new Technology node: '{}'", canonicalName);
        return tech;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────



    private static double[] toDoubleArray(float[] f) {
        double[] d = new double[f.length];
        for (int i = 0; i < f.length; i++) d[i] = f[i];
        return d;
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String inferCategory(String name) {
        String lower = name.toLowerCase();
        if (lower.matches(".*(python|java|kotlin|go|rust|c\\+\\+|typescript|javascript|scala|ruby|php|swift).*"))
            return "Language";
        if (lower.matches(".*(react|angular|vue|spring|django|fastapi|express|next\\.js|rails).*"))
            return "Framework";
        if (lower.matches(".*(kubernetes|docker|terraform|ansible|jenkins|github|gitlab|ci/cd).*"))
            return "DevOps";
        if (lower.matches(".*(postgresql|mysql|mongodb|redis|cassandra|elasticsearch|neo4j).*"))
            return "Database";
        if (lower.matches(".*(aws|azure|gcp|google cloud|amazon|azure).*"))
            return "Cloud";
        if (lower.matches(".*(langchain|openai|llm|hugging face|pytorch|tensorflow|mlflow).*"))
            return "AI/ML";
        return "Other";
    }

    /** Clears the in-process normalisation cache (call between pipeline runs). */
    public void clearCache() {
        normalisationCache.clear();
    }
}
