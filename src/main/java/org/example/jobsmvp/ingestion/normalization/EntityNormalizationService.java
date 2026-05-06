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