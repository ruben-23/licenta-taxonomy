package org.example.jobsmvp.ingestion.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.AllArgsConstructor;
import org.example.jobsmvp.models.nodes.Skill;
import org.example.jobsmvp.repositories.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves raw skill names (technical and soft) to canonical {@link Skill} nodes
 * (layer=3, type="Specific Skill") that are already seeded from the taxonomy by
 *
 *
 * When a skill is new (not in the taxonomy), the LLM assigns it to one of the
 * seven canonical Skill Groups. The new node is persisted with its {@code parent}
 * field set to the Skill Group name so that
 * {@link org.example.jobsmvp.ingestion.graph.GraphIngestionService} can create
 * the {@code SUBCLASS_OF} edge to the correct group node.
 *
 * Resolution strategy:
 *  1. Exact match (case-insensitive, layer=3) — taxonomy node preferred.
 *  2. Alias dictionary for common abbreviations.
 *  3. Cosine similarity over layer-3 Skill node embeddings (threshold 0.88).
 *  4. LLM classification — returns canonical name + Skill Group.
 *  5. Create new Skill node with parent set to the LLM-assigned Skill Group.
 */
@Service
@AllArgsConstructor
public class EntityNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(EntityNormalizationService.class);
    private static final double EMBEDDING_MATCH_THRESHOLD = 0.88;

    // ── Alias dictionary ──────────────────────────────────────────────────────

    private static final Map<String, String> ALIAS_MAP = Map.ofEntries(
            Map.entry("k8s",      "Kubernetes"),
            Map.entry("py",       "Python"),
            Map.entry("js",       "JavaScript"),
            Map.entry("ts",       "TypeScript"),
            Map.entry("pg",       "PostgreSQL"),
            Map.entry("psql",     "PostgreSQL"),
            Map.entry("postgres", "PostgreSQL"),
            Map.entry("mongo",    "MongoDB"),
            Map.entry("es",       "Elasticsearch"),
            Map.entry("aws",      "Amazon Web Services (AWS)"),
            Map.entry("gcp",      "Google Cloud Platform (GCP)"),
            Map.entry("az",       "Azure"),
            Map.entry("tf",       "Terraform (IaC)"),
            Map.entry("gh",       "Git"),
            Map.entry("ci/cd",    "CI/CD pipelines"),
            Map.entry("ml",       "Machine Learning"),
            Map.entry("dl",       "Deep Learning"),
            Map.entry("nlp",      "Natural Language Processing (NLP)"),
            Map.entry("iac",      "Terraform (IaC)"),
            Map.entry("rest",     "REST APIs / Web services"),
            Map.entry("restful",  "REST APIs / Web services"),
            Map.entry("tdd",      "Test-driven development (TDD)"),
            Map.entry("bdd",      "BDD (Behavior-Driven Development)"),
            Map.entry("ddd",      "Domain-Driven Design (DDD)"),
            Map.entry("problem-solving", "Problem solving")
    );

    // ── LLM prompt ────────────────────────────────────────────────────────────

    private static final PromptTemplate CLASSIFICATION_TEMPLATE = PromptTemplate.from("""
            You are normalising skill names for a knowledge graph.

            Given the raw skill name below, return ONLY a valid JSON object — no markdown, no explanation.

            Canonical Skill Groups (pick exactly one — these are the only valid values for "skillGroup"):
              Programming & Scripting
              Frameworks & Libraries
              Infrastructure, Cloud & Tools
              Methodologies & Architectures
              Cognitive & Analytical
              Social & Collaborative
              Organizational & Management

            Top-level category:
              "Technical Competencies (Hard Skills)"   — for the first four groups above
              "Transversal Competencies (Soft Skills)" — for the last three groups above

            Return:
            {
              "canonicalName": "<correctly capitalised canonical skill name>",
              "skillGroup":    "<one of the seven Skill Groups above>",
              "category":      "<Technical Competencies (Hard Skills) | Transversal Competencies (Soft Skills)>"
            }

            If the raw name is an abbreviation (e.g. "k8s"), expand it (e.g. "Kubernetes").
            For general concepts and soft skills, prefer spaces over hyphens (e.g., "Problem solving" instead of "Problem-solving").
            If it is already canonical, return it unchanged.

            Raw skill name: {{rawName}}
            """);

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final EmbeddingModel embeddingModel;
    private final ChatModel      chatModel;
    private final SkillRepository skillRepository;

    /** Per-run cache: lower-cased raw name → resolved Skill node. */
    private final Map<String, Skill> normalisationCache = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Normalises a combined list of raw skill names (technical and/or soft)
     * to canonical layer-3 {@link Skill} nodes.
     *
     * Each returned Skill has its {@code parent} field set to its Skill Group name,
     * enabling {@link org.example.jobsmvp.ingestion.graph.GraphIngestionService}
     * to create the {@code SUBCLASS_OF} edge to the correct group node.
     */
    public List<Skill> normaliseSkills(List<String> rawNames) {
        if (rawNames == null || rawNames.isEmpty()) return List.of();

        List<Skill> results = new ArrayList<>();
        for (String raw : rawNames) {
            if (raw == null || raw.isBlank()) continue;
            // Clean up any double spaces, tabs, or newlines from the raw text
            String cleanedRaw = raw.strip().replaceAll("\\s+", " ");
            try {
                results.add(resolveOrCreate(cleanedRaw));
            } catch (Exception e) {
                log.warn("Failed to normalise skill '{}': {}", raw, e.getMessage());
            }
        }
        return results;
    }

    /** Clears the per-run cache. Call at the start of each pipeline run. */
    public void clearCache() {
        normalisationCache.clear();
    }

    // ── Resolution chain ──────────────────────────────────────────────────────

    private Skill resolveOrCreate(String raw) {
        Skill cached = normalisationCache.get(raw.toLowerCase());
        if (cached != null) return cached;

        // 1. Check exact match and common variants (hyphens, spaces, concatenated)
        Optional<Skill> existingVariant = findExistingSkillVariant(raw);
        if (existingVariant.isPresent()) {
            normalisationCache.put(raw.toLowerCase(), existingVariant.get());
            return existingVariant.get();
        }

        // 2. Alias dictionary → exact match at layer 3
        String aliasResolved = ALIAS_MAP.get(raw.toLowerCase());
        if (aliasResolved != null) {
            Optional<Skill> fromAlias = skillRepository.findByNameIgnoreCaseAndLayer(aliasResolved, 3);
            if (fromAlias.isPresent()) {
                normalisationCache.put(raw.toLowerCase(), fromAlias.get());
                return fromAlias.get();
            }
        }

        // 3. Embedding similarity (layer 3 only — see SkillRepository query)
        Optional<Skill> embeddingMatch = findByEmbeddingSimilarity(raw);
        if (embeddingMatch.isPresent()) {
            normalisationCache.put(raw.toLowerCase(), embeddingMatch.get());
            return embeddingMatch.get();
        }

        // 4 + 5. LLM classification → new node with parent set to Skill Group name
        Skill newSkill = classifyAndCreate(raw);
        normalisationCache.put(raw.toLowerCase(), newSkill);
        return newSkill;
    }

    // ── Resolution helpers ────────────────────────────────────────────────────

    /**
     * Checks the database for an exact match, and if not found, checks common formatting variants.
     * This guards against "Problem-solving" vs "Problem solving" or "Team work" vs "Teamwork".
     */
    private Optional<Skill> findExistingSkillVariant(String name) {
        // 1. Exact Match
        Optional<Skill> match = skillRepository.findByNameIgnoreCaseAndLayer(name, 3);
        if (match.isPresent()) return match;

        // 2. Hyphen/Space swap
        String toggled = name.contains("-") ? name.replace("-", " ") : name.replace(" ", "-");
        match = skillRepository.findByNameIgnoreCaseAndLayer(toggled, 3);
        if (match.isPresent()) return match;

        // 3. Concatenated (e.g. "team work" or "team-work" -> "teamwork")
        if (name.contains(" ") || name.contains("-")) {
            String concatenated = name.replaceAll("[- ]", "");
            match = skillRepository.findByNameIgnoreCaseAndLayer(concatenated, 3);
            if (match.isPresent()) return match;
        }

        return Optional.empty();
    }

    private Optional<Skill> findByEmbeddingSimilarity(String raw) {
        Embedding queryEmbedding = embeddingModel.embed(raw).content();
        double[]  queryVector    = toDoubleArray(queryEmbedding.vector());

        Optional<Skill> best = skillRepository.findMostSimilarSkill(queryVector, EMBEDDING_MATCH_THRESHOLD);
        best.ifPresent(s -> log.debug("Embedding match '{}' → '{}'", raw, s.getName()));
        return best;
    }

    /**
     * Asks the LLM for the canonical name and Skill Group, then persists a new
     * layer-3 Skill node. The {@code parent} field is set to the Skill Group name
     * so the caller ({@link org.example.jobsmvp.ingestion.graph.GraphIngestionService})
     * can resolve and link the parent node without an extra lookup here.
     */
    private Skill classifyAndCreate(String raw) {
        String canonicalName = capitalise(raw);
        String skillGroup    = inferSkillGroup(raw);   // local fallback

        try {
            log.info("Classifying unknown skill '{}' via LLM...", raw);
            String prompt = CLASSIFICATION_TEMPLATE.apply(Map.of("rawName", raw)).text();
            String response = chatModel.chat(prompt).strip()
                    .replaceAll("(?s)^```json\\s*", "")
                    .replaceAll("(?s)```\\s*$", "")
                    .strip();

            Map<String, Object> parsed = new ObjectMapper().readValue(response, Map.class);
            canonicalName = parsed.getOrDefault("canonicalName", canonicalName).toString();
            skillGroup    = parsed.getOrDefault("skillGroup",    skillGroup).toString();
            Thread.sleep(2000);
        } catch (Exception e) {
            log.warn("LLM skill classification failed for '{}': {}", raw, e.getMessage());
        }

        // Re-check DB with LLM-resolved canonical name AND its variants before inserting
        Optional<Skill> existing = findExistingSkillVariant(canonicalName);
        if (existing.isPresent()) return existing.get();

        return createNewSkill(canonicalName, skillGroup);
    }

    /**
     * Persists a new layer-3 Skill node.
     *
     * {@code parent} is set to the Skill Group name. The actual
     * {@code SUBCLASS_OF} edge is created in
     * {@link org.example.jobsmvp.ingestion.graph.GraphIngestionService}.
     */
    private Skill createNewSkill(String canonicalName, String skillGroup) {
        Skill skill = new Skill();
        skill.setSkillId(UUID.randomUUID().toString());
        skill.setName(canonicalName);
        skill.setLayer(3);
        skill.setType("Specific Skill");
        skill.setParent(skillGroup);   // ← Skill Group name, used for SUBCLASS_OF wiring

        Embedding emb = embeddingModel.embed(canonicalName).content();
        skill.setTextEmbedding(emb.vectorAsList().stream().map(Float::doubleValue).toList());

        log.info("Creating new Skill node: '{}' → parent group: '{}'", canonicalName, skillGroup);
        return skillRepository.save(skill);
    }

    // ── Local inference fallback ──────────────────────────────────────────────

    private static String inferSkillGroup(String name) {
        String lower = name.toLowerCase();
        if (lower.matches(".*(python|java|kotlin|golang|rust|c\\+\\+|typescript|javascript|scala|ruby|php|swift|dart|\\br\\b|groovy|lua|perl|haskell|elixir|matlab|objective-c|solidity|bash|shell|powershell|graphql|c#).*"))
            return "Programming & Scripting";
        if (lower.matches(".*(react|angular|vue|spring|django|fastapi|express|next\\.js|rails|flutter|tensorflow|pytorch|keras|langchain|nestjs|svelte|tailwind|redux|pyspark|hugging|xgboost|scikit|pandas).*"))
            return "Frameworks & Libraries";
        if (lower.matches(".*(kubernetes|docker|terraform|ansible|jenkins|github|gitlab|kafka|spark|flink|snowflake|bigquery|elasticsearch|grafana|prometheus|datadog|mlflow|kubeflow|helm|argocd|vercel|vite|neo4j|aws|azure|gcp).*"))
            return "Infrastructure, Cloud & Tools";
        if (lower.matches(".*(agile|scrum|microservices|rest|tdd|bdd|ddd|devops|machine learning|deep learning|nlp|etl|data model|serverless|zero trust|owasp|togaf|crisp|event.driven|grpc|chaos).*"))
            return "Methodologies & Architectures";
        if (lower.matches(".*(problem|analytical|adaptab|critical|creative|research|innovat).*"))
            return "Cognitive & Analytical";
        if (lower.matches(".*(communicat|team|collaborat|leadership|stakeholder|interpersonal|mentor|presentation|negotiat).*"))
            return "Social & Collaborative";
        if (lower.matches(".*(project manag|time manag|itil|governance|finops|budget|planning|organiz).*"))
            return "Organizational & Management";
        return "Programming & Scripting";
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static double[] toDoubleArray(float[] f) {
        double[] d = new double[f.length];
        for (int i = 0; i < f.length; i++) d[i] = f[i];
        return d;
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}



//
//
//package org.example.jobsmvp.ingestion.normalization;
//
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import dev.langchain4j.data.embedding.Embedding;
//import dev.langchain4j.model.chat.ChatModel;
//import dev.langchain4j.model.embedding.EmbeddingModel;
//import dev.langchain4j.model.input.PromptTemplate;
//import lombok.AllArgsConstructor;
//import org.example.jobsmvp.models.nodes.Skill;
//import org.example.jobsmvp.repositories.SkillRepository;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//
///**
// * Resolves raw skill names (technical and soft) to canonical {@link Skill} nodes
// * (layer=3, type="Specific Skill") that are already seeded from the taxonomy.
// *
// * Resolution strategy:
// *  1. Exact match / variant match (case-insensitive, layer=3)
// *  2. Alias dictionary for common abbreviations
// *  3. Cosine similarity over layer-3 Skill node embeddings (threshold 0.88)
// *  4. LLM classification — batched: ALL skills that survive steps 1-3 unresolved
// *     are sent to the LLM in a SINGLE request, returning a JSON array.
// *  5. New Skill nodes are created in bulk from the batch response.
// *
// * This eliminates the per-skill LLM round-trip that existed in the previous version,
// * replacing it with one call per job posting regardless of how many unknown skills
// * are present.
// */
//@Service
//@AllArgsConstructor
//public class EntityNormalizationService {
//
//    private static final Logger log = LoggerFactory.getLogger(EntityNormalizationService.class);
//    private static final double EMBEDDING_MATCH_THRESHOLD = 0.88;
//
//    // ── Alias dictionary ──────────────────────────────────────────────────────
//
//    private static final Map<String, String> ALIAS_MAP = Map.ofEntries(
//            Map.entry("k8s",      "Kubernetes"),
//            Map.entry("py",       "Python"),
//            Map.entry("js",       "JavaScript"),
//            Map.entry("ts",       "TypeScript"),
//            Map.entry("pg",       "PostgreSQL"),
//            Map.entry("psql",     "PostgreSQL"),
//            Map.entry("postgres", "PostgreSQL"),
//            Map.entry("mongo",    "MongoDB"),
//            Map.entry("es",       "Elasticsearch"),
//            Map.entry("aws",      "Amazon Web Services (AWS)"),
//            Map.entry("gcp",      "Google Cloud Platform (GCP)"),
//            Map.entry("az",       "Azure"),
//            Map.entry("tf",       "Terraform (IaC)"),
//            Map.entry("gh",       "Git"),
//            Map.entry("ci/cd",    "CI/CD pipelines"),
//            Map.entry("ml",       "Machine Learning"),
//            Map.entry("dl",       "Deep Learning"),
//            Map.entry("nlp",      "Natural Language Processing (NLP)"),
//            Map.entry("iac",      "Terraform (IaC)"),
//            Map.entry("rest",     "REST APIs / Web services"),
//            Map.entry("restful",  "REST APIs / Web services"),
//            Map.entry("tdd",      "Test-driven development (TDD)"),
//            Map.entry("bdd",      "BDD (Behavior-Driven Development)"),
//            Map.entry("ddd",      "Domain-Driven Design (DDD)"),
//            Map.entry("problem-solving", "Problem solving")
//    );
//
//    // ── Batch LLM prompt ──────────────────────────────────────────────────────
//
//    /**
//     * Receives a JSON array of raw skill names and returns a JSON array of
//     * classification objects — one entry per input skill, in the same order.
//     *
//     * Keeping the input/output arrays positionally aligned lets us zip results
//     * back to their raw names without any secondary key lookups.
//     */
//    private static final PromptTemplate BATCH_CLASSIFICATION_TEMPLATE = PromptTemplate.from("""
//            You are normalising skill names for a knowledge graph.
//
//            You will receive a JSON array of raw skill name strings.
//            Return ONLY a valid JSON array — one object per input skill, in the SAME ORDER.
//            Do NOT include markdown code fences, explanations, or any text other than the JSON array.
//
//            Canonical Skill Groups (pick exactly one — these are the only valid values for "skillGroup"):
//              Programming & Scripting
//              Frameworks & Libraries
//              Infrastructure, Cloud & Tools
//              Methodologies & Architectures
//              Cognitive & Analytical
//              Social & Collaborative
//              Organizational & Management
//
//            Each object in your response must follow this schema exactly:
//            {
//              "rawName":       "<the original skill name from the input, unchanged>",
//              "canonicalName": "<correctly capitalised canonical skill name>",
//              "skillGroup":    "<one of the seven Skill Groups above>"
//            }
//
//            Rules:
//            - If a raw name is an abbreviation (e.g. "k8s"), expand it (e.g. "Kubernetes").
//            - For frameworks and libraries, strip unnecessary "js" or ".js" suffixes if they are implied
//              (e.g. "ReactJS" -> "React", "Vue.js" -> "Vue"), unless the suffix is canonically part of the name
//              (e.g. "Node.js").
//            - For general concepts and soft skills, prefer spaces over hyphens
//              (e.g. "Problem solving" not "Problem-solving").
//            - If a name is already canonical, return it unchanged in "canonicalName".
//            - Every element of the input array MUST have a corresponding element in your output array.
//
//            Input array:
//            {{rawNamesJson}}
//            """);
//
//    // ── Dependencies ──────────────────────────────────────────────────────────
//
//    private final EmbeddingModel  embeddingModel;
//    private final ChatModel       chatModel;
//    private final SkillRepository skillRepository;
//    private final ObjectMapper    objectMapper;
//
//    /** Per-run cache: lower-cased raw name → resolved Skill node. */
//    private final Map<String, Skill> normalisationCache = new ConcurrentHashMap<>();
//
//    // ── Public API ────────────────────────────────────────────────────────────
//
//    /**
//     * Normalises a combined list of raw skill names (technical and/or soft) to
//     * canonical layer-3 {@link Skill} nodes.
//     *
//     * Two-pass approach:
//     *  Pass 1 — run every skill through the non-LLM resolution chain
//     *            (cache → exact/variant match → alias → embedding similarity).
//     *  Pass 2 — collect all skills that remain unresolved, send them to the LLM
//     *            in a single batch call, then create all new nodes at once.
//     *
//     * @param rawNames raw skill strings from the LLM extraction step
//     * @return resolved (and newly created where necessary) Skill nodes
//     */
//    public List<Skill> normaliseSkills(List<String> rawNames) {
//        if (rawNames == null || rawNames.isEmpty()) return List.of();
//
//        // Deduplicate and clean input while preserving order for the caller
//        List<String> cleaned = rawNames.stream()
//                .filter(s -> s != null && !s.isBlank())
//                .map(s -> s.strip().replaceAll("\\s+", " "))
//                .distinct()
//                .toList();
//
//        // ── Pass 1: non-LLM resolution ────────────────────────────────────────
//        // resolved[i] is non-null when pass 1 succeeded; null means LLM needed.
//        Skill[]     resolved  = new Skill[cleaned.size()];
//        List<Integer> unknownIdx  = new ArrayList<>();   // indices that need the LLM
//        List<String>  unknownRaws = new ArrayList<>();   // corresponding raw strings
//
//        for (int i = 0; i < cleaned.size(); i++) {
//            String raw = cleaned.get(i);
//            try {
//                Optional<Skill> hit = resolveWithoutLlm(raw);
//                if (hit.isPresent()) {
//                    resolved[i] = hit.get();
//                } else {
//                    unknownIdx.add(i);
//                    unknownRaws.add(raw);
//                }
//            } catch (Exception e) {
//                log.warn("Non-LLM resolution failed for '{}': {}", raw, e.getMessage());
//                unknownIdx.add(i);
//                unknownRaws.add(raw);
//            }
//        }
//
//        // ── Pass 2: batch LLM classification for unknowns ─────────────────────
//        if (!unknownRaws.isEmpty()) {
//            log.info("Sending {} unknown skill(s) to LLM for batch classification: {}",
//                    unknownRaws.size(), unknownRaws);
//
//            Map<String, Skill> batchResults = classifyAndCreateBatch(unknownRaws);
//
//            for (int k = 0; k < unknownIdx.size(); k++) {
//                int    idx = unknownIdx.get(k);
//                String raw = unknownRaws.get(k);
//                Skill  skill = batchResults.getOrDefault(raw.toLowerCase(),
//                        createFallbackSkill(raw));                     // should never be needed
//                resolved[idx] = skill;
//                normalisationCache.put(raw.toLowerCase(), skill);
//            }
//        }
//
//        return Arrays.asList(resolved);
//    }
//
//    /** Clears the per-run cache. Call at the start of each pipeline run. */
//    public void clearCache() {
//        normalisationCache.clear();
//    }
//
//    // ── Pass-1: non-LLM resolution chain ──────────────────────────────────────
//
//    /**
//     * Attempts to resolve {@code raw} using only deterministic / embedding-based
//     * methods. Returns {@link Optional#empty()} when the LLM is needed.
//     */
//    private Optional<Skill> resolveWithoutLlm(String raw) {
//        // Cache
//        Skill cached = normalisationCache.get(raw.toLowerCase());
//        if (cached != null) return Optional.of(cached);
//
//        // 1. Exact / variant match
//        Optional<Skill> match = findExistingSkillVariant(raw);
//        if (match.isPresent()) {
//            normalisationCache.put(raw.toLowerCase(), match.get());
//            return match;
//        }
//
//        // 2. Alias dictionary
//        String aliasResolved = ALIAS_MAP.get(raw.toLowerCase());
//        if (aliasResolved != null) {
//            Optional<Skill> fromAlias = skillRepository.findByNameIgnoreCaseAndLayer(aliasResolved, 3);
//            if (fromAlias.isPresent()) {
//                normalisationCache.put(raw.toLowerCase(), fromAlias.get());
//                return fromAlias;
//            }
//        }
//
//        // 3. Embedding similarity
//        Optional<Skill> embeddingMatch = findByEmbeddingSimilarity(raw);
//        if (embeddingMatch.isPresent()) {
//            normalisationCache.put(raw.toLowerCase(), embeddingMatch.get());
//            return embeddingMatch;
//        }
//
//        return Optional.empty();
//    }
//
//    // ── Pass-2: batch LLM classification ──────────────────────────────────────
//
//    /**
//     * Sends all unresolved raw skill names to the LLM in one request.
//     *
//     * The LLM returns a JSON array of {@code {rawName, canonicalName, skillGroup}}
//     * objects in the same order as the input.  We re-check the DB with the
//     * canonical name before creating anything new to stay idempotent.
//     *
//     * @param rawNames list of unresolved skill strings (no nulls, no blanks)
//     * @return map of lower-cased raw name → resolved-or-created Skill node
//     */
//    private Map<String, Skill> classifyAndCreateBatch(List<String> rawNames) {
//        Map<String, Skill> result = new LinkedHashMap<>();
//
//        // Build per-skill local fallbacks in case the LLM response is missing entries
//        Map<String, String> fallbackGroups = new LinkedHashMap<>();
//        for (String raw : rawNames) {
//            fallbackGroups.put(raw.toLowerCase(), inferSkillGroup(raw));
//        }
//
//        List<Map<String, String>> llmResults = callBatchLlm(rawNames, fallbackGroups);
//
//        // Build result map; for each LLM entry, re-check DB then create if needed
//        Set<String> processedRaws = new HashSet<>();
//        for (Map<String, String> entry : llmResults) {
//            String rawName       = entry.getOrDefault("rawName", "");
//            String canonicalName = entry.getOrDefault("canonicalName", capitalise(rawName));
//            String skillGroup    = entry.getOrDefault("skillGroup",
//                    fallbackGroups.getOrDefault(rawName.toLowerCase(), "Programming & Scripting"));
//
//            if (rawName.isBlank()) continue;
//
//            // Re-check DB with canonical name before inserting
//            Optional<Skill> existing = findExistingSkillVariant(canonicalName);
//            Skill skill = existing.orElseGet(() -> createNewSkill(canonicalName, skillGroup));
//
//            result.put(rawName.toLowerCase(), skill);
//            processedRaws.add(rawName.toLowerCase());
//        }
//
//        // Safety net: any raw that the LLM silently dropped gets a fallback node
//        for (String raw : rawNames) {
//            if (!processedRaws.contains(raw.toLowerCase())) {
//                log.warn("LLM batch response missing entry for '{}', using local fallback.", raw);
//                Skill fallback = findExistingSkillVariant(raw)
//                        .orElseGet(() -> createNewSkill(capitalise(raw),
//                                fallbackGroups.getOrDefault(raw.toLowerCase(), "Programming & Scripting")));
//                result.put(raw.toLowerCase(), fallback);
//            }
//        }
//
//        return result;
//    }
//
//    /**
//     * Makes the single LLM call and parses the JSON array response.
//     * On any failure, falls back to per-skill local inference so the pipeline
//     * never stalls.
//     */
//    private List<Map<String, String>> callBatchLlm(
//            List<String> rawNames,
//            Map<String, String> fallbackGroups
//    ) {
//        try {
//            String rawNamesJson = objectMapper.writeValueAsString(rawNames);
//            String prompt = BATCH_CLASSIFICATION_TEMPLATE
//                    .apply(Map.of("rawNamesJson", rawNamesJson))
//                    .text();
//
//            String response = chatModel.chat(prompt).strip()
//                    .replaceAll("(?s)^```json\\s*", "")
//                    .replaceAll("(?s)```\\s*$", "")
//                    .strip();
//
//            List<Map<String, String>> parsed = objectMapper.readValue(
//                    response, new TypeReference<>() {});
//
//            log.debug("LLM batch classification returned {} entries for {} inputs.",
//                    parsed.size(), rawNames.size());
//            return parsed;
//
//        } catch (Exception e) {
//            log.warn("LLM batch classification failed ({}), building local fallback for all {} skills.",
//                    e.getMessage(), rawNames.size());
//
//            // Build synthetic entries using local inference so the caller can
//            // still create nodes without a second LLM attempt.
//            List<Map<String, String>> fallback = new ArrayList<>();
//            for (String raw : rawNames) {
//                fallback.add(Map.of(
//                        "rawName",       raw,
//                        "canonicalName", capitalise(raw),
//                        "skillGroup",    fallbackGroups.getOrDefault(raw.toLowerCase(), "Programming & Scripting")
//                ));
//            }
//            return fallback;
//        }
//    }
//
//    // ── Resolution helpers ─────────────────────────────────────────────────────
//
//    private Optional<Skill> findExistingSkillVariant(String name) {
//        // 1. Exact match
//        Optional<Skill> match = skillRepository.findByNameIgnoreCaseAndLayer(name, 3);
//        if (match.isPresent()) return match;
//
//        // 2. Hyphen ↔ space swap
//        String toggled = name.contains("-") ? name.replace("-", " ") : name.replace(" ", "-");
//        match = skillRepository.findByNameIgnoreCaseAndLayer(toggled, 3);
//        if (match.isPresent()) return match;
//
//        // 3. Concatenated (e.g. "team work" → "teamwork")
//        if (name.contains(" ") || name.contains("-")) {
//            String concatenated = name.replaceAll("[- ]", "");
//            match = skillRepository.findByNameIgnoreCaseAndLayer(concatenated, 3);
//            if (match.isPresent()) return match;
//        }
//
//        return Optional.empty();
//    }
//
//    private Optional<Skill> findByEmbeddingSimilarity(String raw) {
//        Embedding queryEmbedding = embeddingModel.embed(raw).content();
//        double[]  queryVector    = toDoubleArray(queryEmbedding.vector());
//        Optional<Skill> best = skillRepository.findMostSimilarSkill(queryVector, EMBEDDING_MATCH_THRESHOLD);
//        best.ifPresent(s -> log.debug("Embedding match '{}' → '{}'", raw, s.getName()));
//        return best;
//    }
//
//    // ── Node creation ──────────────────────────────────────────────────────────
//
//    /**
//     * Persists a new layer-3 Skill node.
//     *
//     * {@code parent} is set to the Skill Group name. The actual
//     * {@code SUBCLASS_OF} edge is created in
//     * {@link org.example.jobsmvp.ingestion.graph.GraphIngestionService}.
//     */
//    private Skill createNewSkill(String canonicalName, String skillGroup) {
//        // One final DB check — another thread or a previous batch entry may have
//        // already inserted this node between our last check and now.
//        Optional<Skill> race = skillRepository.findByNameIgnoreCaseAndLayer(canonicalName, 3);
//        if (race.isPresent()) return race.get();
//
//        Skill skill = new Skill();
//        skill.setSkillId(UUID.randomUUID().toString());
//        skill.setName(canonicalName);
//        skill.setLayer(3);
//        skill.setType("Specific Skill");
//        skill.setParent(skillGroup);
//
//        Embedding emb = embeddingModel.embed(canonicalName).content();
//        skill.setTextEmbedding(emb.vectorAsList().stream().map(Float::doubleValue).toList());
//
//        log.info("Creating new Skill node: '{}' → group: '{}'", canonicalName, skillGroup);
//        return skillRepository.save(skill);
//    }
//
//    /** Last-resort fallback: creates a skill using only local inference, no LLM. */
//    private Skill createFallbackSkill(String raw) {
//        return createNewSkill(capitalise(raw), inferSkillGroup(raw));
//    }
//
//    // ── Local group inference (LLM fallback) ──────────────────────────────────
//
//    private static String inferSkillGroup(String name) {
//        String lower = name.toLowerCase();
//        if (lower.matches(".*(python|java|kotlin|golang|rust|c\\+\\+|typescript|javascript|scala|ruby|php|swift|dart|\\br\\b|groovy|lua|perl|haskell|elixir|matlab|objective-c|solidity|bash|shell|powershell|graphql|c#).*"))
//            return "Programming & Scripting";
//        if (lower.matches(".*(react|angular|vue|spring|django|fastapi|express|next\\.js|rails|flutter|tensorflow|pytorch|keras|langchain|nestjs|svelte|tailwind|redux|pyspark|hugging|xgboost|scikit|pandas).*"))
//            return "Frameworks & Libraries";
//        if (lower.matches(".*(kubernetes|docker|terraform|ansible|jenkins|github|gitlab|kafka|spark|flink|snowflake|bigquery|elasticsearch|grafana|prometheus|datadog|mlflow|kubeflow|helm|argocd|vercel|vite|neo4j|aws|azure|gcp).*"))
//            return "Infrastructure, Cloud & Tools";
//        if (lower.matches(".*(agile|scrum|microservices|rest|tdd|bdd|ddd|devops|machine learning|deep learning|nlp|etl|data model|serverless|zero trust|owasp|togaf|crisp|event.driven|grpc|chaos).*"))
//            return "Methodologies & Architectures";
//        if (lower.matches(".*(problem|analytical|adaptab|critical|creative|research|innovat).*"))
//            return "Cognitive & Analytical";
//        if (lower.matches(".*(communicat|team|collaborat|leadership|stakeholder|interpersonal|mentor|presentation|negotiat).*"))
//            return "Social & Collaborative";
//        if (lower.matches(".*(project manag|time manag|itil|governance|finops|budget|planning|organiz).*"))
//            return "Organizational & Management";
//        return "Programming & Scripting";
//    }
//
//    // ── Utilities ──────────────────────────────────────────────────────────────
//
//    private static double[] toDoubleArray(float[] f) {
//        double[] d = new double[f.length];
//        for (int i = 0; i < f.length; i++) d[i] = f[i];
//        return d;
//    }
//
//    private static String capitalise(String s) {
//        if (s == null || s.isEmpty()) return s;
//        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
//    }
//}