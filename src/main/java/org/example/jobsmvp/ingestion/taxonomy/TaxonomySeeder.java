package org.example.jobsmvp.ingestion.taxonomy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.example.jobsmvp.models.nodes.Occupation;
import org.example.jobsmvp.models.nodes.Skill;
import org.example.jobsmvp.repositories.GraphRepository;
import org.example.jobsmvp.repositories.OccupationRepository;
import org.example.jobsmvp.repositories.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs once at application startup (after the Spring context is ready).
 *
 * Seeds three JSON taxonomies into the graph, in this order:
 *
 *  1. skills.json      → :Skill nodes connected by [:SUBCLASS_OF]
 *  2. occupations.json → :Occupation nodes connected by [:SUBCLASS_OF]
 *  3. mappings.json    → [:REQUIRES] edges from :Occupation → :Skill
 *
 * All operations use MERGE, making the seeder fully idempotent — safe to
 * run on every application restart.
 *
 * ── Skills graph shape ────────────────────────────────────────────────────
 *
 *   (Specific Skill, layer=3) -[:SUBCLASS_OF]->
 *   (Skill Group,    layer=2) -[:SUBCLASS_OF]->
 *   (Skill Category, layer=1)
 *
 * ── Occupations graph shape ───────────────────────────────────────────────
 *
 *   (Specialized Role,  layer=3) -[:SUBCLASS_OF]->
 *   (Core Occupation,   layer=2) -[:SUBCLASS_OF]->
 *   (Job Family,        layer=1)
 *
 * ── Mappings graph shape ──────────────────────────────────────────────────
 *
 *   (Core Occupation)   -[:REQUIRES]-> (Skill)   ← sharedSkills
 *   (Specialized Role)  -[:REQUIRES]-> (Skill)   ← additionalSkills
 *
 * ── Classpath layout (configure via application.properties) ───────────────
 *
 *   src/main/resources/taxonomies/skills.json
 *   src/main/resources/taxonomies/occupations.json
 *   src/main/resources/taxonomies/mappings.json
 *
 * Embeddings are generated only for layer-3 nodes (Specific Skills and
 * Specialized Roles) since those are the nodes matched during ingestion.
 * Layer-1 and layer-2 nodes are matched by name only.
 */
@Component
@Order(1)
public class TaxonomySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaxonomySeeder.class);

    // ── Mapping aliases ───────────────────────────────────────────────────────
    // Some skill names in mappings.json use slash-shorthand that does not appear
    // verbatim in skills.json. This map resolves them to canonical skill names.
    private static final Map<String, String> MAPPING_SKILL_ALIASES = Map.ofEntries(
            Map.entry("Git / Version Control",              "Git"),
            Map.entry("Teamwork / Collaboration",           "Collaboration"),
            Map.entry("AWS / Azure / GCP",                  "Amazon Web Services (AWS)"),
            Map.entry("Jenkins / GitLab CI",                "Jenkins"),
            Map.entry("Cybersecurity / Information Security","Cybersecurity"),
            Map.entry("Bash / Shell Scripting",             "Bash"),
            Map.entry("Tableau / PowerBI",                  "Tableau"),
            Map.entry("Machine Learning / Deep Learning",   "Machine Learning"),
            Map.entry("Data Modeling / Warehousing",        "Data Modeling"),
            Map.entry("ARIA Standards",
                    "Accessible Rich Internet Applications(ARIA) Standards")
    );

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final SkillRepository      skillRepository;
    private final OccupationRepository occupationRepository;
    private final EmbeddingModel       embeddingModel;
    private final Neo4jClient          neo4jClient;
    private final ObjectMapper         objectMapper;
    private final GraphRepository      graphRepository;


    private final String skillsTaxonomyPath;
    private final String occupationsTaxonomyPath;
    private final String mappingsTaxonomyPath;

    public TaxonomySeeder(
            SkillRepository skillRepository,
            OccupationRepository occupationRepository,
            EmbeddingModel embeddingModel,
            Neo4jClient neo4jClient,
            ObjectMapper objectMapper,
            GraphRepository graphRepository, @Value("${ingestion.skills-taxonomy-path:taxonomies/skills.json}")
            String skillsTaxonomyPath,
            @Value("${ingestion.occupations-taxonomy-path:taxonomies/occupations.json}")
            String occupationsTaxonomyPath,
            @Value("${ingestion.mappings-taxonomy-path:taxonomies/mappings.json}")
            String mappingsTaxonomyPath
    ) {
        this.skillRepository         = skillRepository;
        this.occupationRepository    = occupationRepository;
        this.embeddingModel          = embeddingModel;
        this.neo4jClient             = neo4jClient;
        this.objectMapper            = objectMapper;
        this.graphRepository = graphRepository;
        this.skillsTaxonomyPath      = skillsTaxonomyPath;
        this.occupationsTaxonomyPath = occupationsTaxonomyPath;
        this.mappingsTaxonomyPath    = mappingsTaxonomyPath;
    }

    // ── ApplicationRunner entry point ─────────────────────────────────────────

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=== Creating vector indexes ===");
        graphRepository.createOldStudentVectorIndex();
        graphRepository.createOldJobVectorIndex();
        graphRepository.createStudentGraphSageVectorIndex();
        graphRepository.createJobGraphSageVectorIndex();
        graphRepository.createTechnologyVectorIndex();
        log.info("=== Vector indexes created ===");

        log.info("=== Taxonomy seeding starting ===");

        int[] skillCounts      = seedSkills();
        int[] occupationCounts = seedOccupations();
        int   mappingEdges     = seedMappings();

        log.info("=== Taxonomy seeding complete — " +
                        "skills: created={} skipped={} | " +
                        "occupations: created={} skipped={} | " +
                        "mapping edges merged={}",
                skillCounts[0], skillCounts[1],
                occupationCounts[0], occupationCounts[1],
                mappingEdges);
    }

    // =========================================================================
    // 1. SKILLS
    // =========================================================================

    /**
     * Seeds the full skills.json tree as :Skill nodes with [:SUBCLASS_OF] edges.
     *
     * @return int[]{created, skipped}
     */
    private int[] seedSkills() throws Exception {
        JsonNode root   = loadJson(skillsTaxonomyPath);
        int[]    counts = {0, 0};

        // Layer 1 — Skill Categories  (e.g. "Technical Competencies (Hard Skills)")
        for (JsonNode categoryNode : root.path("children")) {
            String categoryName = categoryNode.path("name").asText();
            Skill  category     = seedSkillNode(categoryName, 1, "Skill Category", null, counts);

            // Layer 2 — Skill Groups  (e.g. "Programming & Scripting")
            for (JsonNode groupNode : categoryNode.path("children")) {
                String groupName = groupNode.path("name").asText();
                Skill  group     = seedSkillNode(groupName, 2, "Skill Group", categoryName, counts);

                mergeSkillBelongsTo(group.getSkillId(), category.getSkillId());

                // Layer 3 — Specific Skills  (e.g. "Java", "Docker")
                for (JsonNode skillNode : groupNode.path("children")) {
                    String skillName = skillNode.path("name").asText();
                    Skill  skill     = seedSkillNode(skillName, 3, "Specific Skill", groupName, counts);

                    mergeSkillBelongsTo(skill.getSkillId(), group.getSkillId());
                }
            }
        }
        return counts;
    }

    /**
     * Finds or creates a :Skill node for the given name + layer.
     * Embeddings are only generated for layer-3 nodes.
     */
    private Skill seedSkillNode(String name, int layer, String type, String parent, int[] counts) {
        Optional<Skill> existing = skillRepository.findByNameIgnoreCaseAndLayer(name, layer);
        if (existing.isPresent()) {
            counts[1]++;
            return existing.get();
        }

        Skill skill = new Skill();
        skill.setSkillId(UUID.nameUUIDFromBytes((name + "|" + layer).getBytes()).toString());
        skill.setName(name);
        skill.setLayer(layer);
        skill.setType(type);
        skill.setParent(parent);

        if (layer == 3) {
            skill.setTextEmbedding(
                    embeddingModel.embed(name).content()
                            .vectorAsList().stream()
                            .map(Float::doubleValue)
                            .toList()
            );
        }

        Skill saved = skillRepository.save(skill);
        counts[0]++;
        log.debug("Seeded Skill [layer={}]: '{}'", layer, name);
        return saved;
    }

    private void mergeSkillBelongsTo(String childId, String parentId) {
        neo4jClient.query("""
                MATCH (child:Skill  {skill_id: $childId})
                MATCH (parent:Skill {skill_id: $parentId})
                MERGE (child)-[:SUBCLASS_OF]->(parent)
                """)
                .bindAll(Map.of("childId", childId, "parentId", parentId))
                .run();
    }

    // =========================================================================
    // 2. OCCUPATIONS
    // =========================================================================

    /**
     * Seeds the full occupations.json tree as :Occupation nodes with
     * [:SUBCLASS_OF] edges.
     *
     * @return int[]{created, skipped}
     */
    private int[] seedOccupations() throws Exception {
        JsonNode root   = loadJson(occupationsTaxonomyPath);
        int[]    counts = {0, 0};

        // Layer 1 — Job Families  (e.g. "Software Engineering & Architecture")
        for (JsonNode familyNode : root.path("children")) {
            String     familyName = familyNode.path("name").asText();
            Occupation family     = seedOccupationNode(familyName, 1, "Job Family", null, counts);

            // Layer 2 — Core Occupations  (e.g. "Back-End Developer")
            for (JsonNode coreNode : familyNode.path("children")) {
                String     coreName = coreNode.path("name").asText();
                Occupation core     = seedOccupationNode(coreName, 2, "Core Occupation", familyName, counts);

                mergeOccupationSubclassOf(core.getOccupationId(), family.getOccupationId());

                // Layer 3 — Specialized Roles  (e.g. "Java Developer")
                for (JsonNode roleNode : coreNode.path("children")) {
                    String     roleName = roleNode.path("name").asText();
                    Occupation role     = seedOccupationNode(roleName, 3, "Specialized Role", coreName, counts);

                    mergeOccupationSubclassOf(role.getOccupationId(), core.getOccupationId());
                }
            }
        }
        return counts;
    }

    /**
     * Finds or creates an :Occupation node for the given name + layer.
     * Embeddings are only generated for layer-3 nodes (Specialized Roles).
     */
    private Occupation seedOccupationNode(String name, int layer, String type, String parent, int[] counts) {
        Optional<Occupation> existing = occupationRepository.findByNameIgnoreCase(name);
        if (existing.isPresent()) {
            counts[1]++;
            return existing.get();
        }

        Occupation occ = new Occupation();
        occ.setOccupationId(UUID.nameUUIDFromBytes((name + "|" + layer).getBytes()).toString());
        occ.setName(name);
        occ.setLayer(layer);
        occ.setType(type);
        occ.setParent(parent);

        if (layer == 3) {
            occ.setTextEmbedding(
                    embeddingModel.embed(name).content()
                            .vectorAsList().stream()
                            .map(Float::doubleValue)
                            .toList()
            );
        }

        Occupation saved = occupationRepository.save(occ);
        counts[0]++;
        log.debug("Seeded Occupation [layer={}]: '{}'", layer, name);
        return saved;
    }

    private void mergeOccupationSubclassOf(String childId, String parentId) {
        neo4jClient.query("""
                MATCH (child:Occupation  {occupation_id: $childId})
                MATCH (parent:Occupation {occupation_id: $parentId})
                MERGE (child)-[:SUBCLASS_OF]->(parent)
                """)
                .bindAll(Map.of("childId", childId, "parentId", parentId))
                .run();
    }

    // =========================================================================
    // 3. MAPPINGS
    // =========================================================================

    /**
     * Seeds mappings.json as [:REQUIRES] edges from :Occupation → :Skill.
     *
     * Two edge kinds are created:
     *  - Core Occupation  -[:REQUIRES]-> Skill  (sharedSkills)
     *  - Specialized Role -[:REQUIRES]-> Skill  (additionalSkills)
     *
     * @return total number of MERGE operations executed
     */
    private int seedMappings() throws Exception {
        JsonNode root  = loadJson(mappingsTaxonomyPath);
        int      total = 0;

        for (JsonNode family : root.path("jobFamilies")) {
            for (JsonNode coreOcc : family.path("coreOccupations")) {
                String coreName = coreOcc.path("name").asText();

                // Shared skills → Core Occupation
                for (JsonNode skillNameNode : coreOcc.path("sharedSkills")) {
                    mergeOccupationRequiresSkill(coreName, skillNameNode.asText());
                    total++;
                }

                // Additional skills → each Specialized Role
                for (JsonNode role : coreOcc.path("specializedRoles")) {
                    String roleName = role.path("name").asText();
                    for (JsonNode skillNameNode : role.path("additionalSkills")) {
                        mergeOccupationRequiresSkill(roleName, skillNameNode.asText());
                        total++;
                    }
                }
            }
        }
        return total;
    }

    /**
     * Merges a single [:REQUIRES] edge from an :Occupation to a :Skill.
     *
     * Resolves the skill name through {@link #MAPPING_SKILL_ALIASES} first,
     * then matches case-insensitively against the :Skill node name.
     * If no matching skill exists the edge is silently skipped (the taxonomy
     * and mappings should be consistent, but this prevents startup failures).
     */
    private void mergeOccupationRequiresSkill(String occupationName, String rawSkillName) {
        String canonicalSkillName = MAPPING_SKILL_ALIASES.getOrDefault(rawSkillName, rawSkillName);

        neo4jClient.query("""
                MATCH (o:Occupation {name: $occName})
                MATCH (s:Skill)
                WHERE toLower(s.name) = toLower($skillName)
                MERGE (o)-[:REQUIRES]->(s)
                """)
                .bindAll(Map.of(
                        "occName",   occupationName,
                        "skillName", canonicalSkillName
                ))
                .run();
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private JsonNode loadJson(String classpathPath) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(classpathPath);
        if (is == null) {
            throw new IllegalStateException(
                    "Taxonomy file not found on classpath: " + classpathPath);
        }
        return objectMapper.readTree(is);
    }
}