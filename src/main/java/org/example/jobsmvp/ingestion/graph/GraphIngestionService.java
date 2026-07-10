package org.example.jobsmvp.ingestion.graph;

import lombok.AllArgsConstructor;
import org.example.jobsmvp.ingestion.transform.JobGraphBundle;
import org.example.jobsmvp.models.nodes.Company;
import org.example.jobsmvp.models.nodes.Job;
import org.example.jobsmvp.models.nodes.Occupation;
import org.example.jobsmvp.models.nodes.Skill;
import org.example.jobsmvp.repositories.CompanyRepository;
import org.example.jobsmvp.repositories.JobRepository;
import org.example.jobsmvp.repositories.OccupationRepository;
import org.example.jobsmvp.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Persists a {@link JobGraphBundle} into Neo4j.
 *
 * All operations for a single bundle are wrapped in one transaction.
 * MERGE semantics ensure idempotency — re-running the pipeline is safe.
 *
 * Graph shape produced per job:
 *
 *   (Company)-[:POSTS]->(Job)-[:REQUIRES]->(Skill, layer=3)
 *                                                 |
 *                                         [:SUBCLASS_OF]
 *                                                 v
 *                                      (Skill, layer=2  — Skill Group)
 *                                                 |
 *                                         [:SUBCLASS_OF]
 *                                                 v
 *                                      (Skill, layer=1  — Skill Category)
 *
 *   (Job)-[:HAS_OCCUPATION]->(Occupation)
 *
 * The layer-1 and layer-2 Skill nodes are guaranteed to exist because
 * the full taxonomy is seeded before the first ingestion run.
 */
@Service
@AllArgsConstructor
public class GraphIngestionService {

    private static final Logger log = LoggerFactory.getLogger(GraphIngestionService.class);

    private final CompanyRepository    companyRepository;
    private final JobRepository        jobRepository;
    private final OccupationRepository occupationRepository;
    private final Neo4jClient          neo4jClient;
    private final EmbeddingService     embeddingService;

    // ── Public entry point ───────────────────────────────────────────────────

    @Transactional
    public void ingest(JobGraphBundle bundle) {
        Company savedCompany = mergeCompany(bundle.company());
        Job     savedJob     = mergeJob(bundle.job());

        mergePostedRelationship(savedCompany.getCompany_id(), savedJob.getJob_id());

        if (bundle.occupation() != null) {
            mergeOccupation(bundle.occupation());
            mergeHasOccupationRelationship(savedJob.getJob_id(), bundle.occupation().getOccupationId());
        }

        for (Skill skill : bundle.skills()) {
            mergeSkill(skill);
            mergeRequiresRelationship(savedJob.getJob_id(), skill.getSkillId());
            mergeSkillHierarchy(skill);
        }

//        embeddingService.processJob(savedJob.getJob_id());
        log.info("Ingested job '{}' by '{}'", savedJob.getTitle(), savedCompany.getName());
    }

    // ── Node merges ──────────────────────────────────────────────────────────

    /**
     * MERGEs the Company node by its stable ID.
     *
     * On CREATE: all fields are written.
     * On MATCH:  only non-null enrichment fields are updated so that a company
     *            node populated by an earlier job posting is never downgraded to
     *            nulls.  {@code description} is treated the same way — it is
     *            written only when the incoming value is non-null, so the first
     *            posting that carries a company blurb wins and subsequent runs
     *            that lack one leave it intact.
     */
    private Company mergeCompany(Company company) {
        Map<String, Object> params = new HashMap<>();
        params.put("companyId", company.getCompany_id());
        params.put("name", company.getName());
        params.put("isRecruitmentAgency", company.getIsRecruitmentAgency());
        params.put("industry", company.getIndustry());
        params.put("size", company.getSize());
        params.put("description", company.getDescription());

        // If the company already exists in the graph, it will just update the fields,
        // otherwise it will create a new one using the company_id determined in GraphTransformService.
        neo4jClient.query("""
                MERGE (c:Company {company_id: $companyId})
                ON CREATE SET
                    c.name        = $name,
                    c.isRecruitmentAgency = COALESCE($isRecruitmentAgency, false),
                    c.industry    = COALESCE($industry, ''),
                    c.size        = COALESCE($size, ''),
                    c.description = COALESCE($description, '')
                ON MATCH SET
                    c.isRecruitmentAgency = CASE WHEN $isRecruitmentAgency IS NOT NULL THEN $isRecruitmentAgency ELSE c.isRecruitmentAgency END,
                    c.industry    = CASE WHEN $industry IS NOT NULL AND $industry <> '' THEN $industry ELSE c.industry END,
                    c.size        = CASE WHEN $size IS NOT NULL AND $size <> '' THEN $size ELSE c.size END,
                    c.description = CASE WHEN $description IS NOT NULL AND $description <> '' THEN $description ELSE c.description END
                """)
                .bindAll(params)
                .run();

        // Also persist the text embedding if present (kept separate because
        // Neo4jClient bindAll doesn't handle List<Double> inside the same map
        // reliably on all driver versions).
        if (company.getTextEmbedding() != null && !company.getTextEmbedding().isEmpty()) {
            neo4jClient.query("""
                    MATCH (c:Company {company_id: $companyId})
                    SET c.text_embedding = $text_embedding
                    """)
                    .bindAll(Map.of(
                            "companyId",      company.getCompany_id(),
                            "text_embedding", company.getTextEmbedding()
                    ))
                    .run();
        }

        // Return the now-persisted node via the repository so callers have the
        // managed entity (needed for relationship merges).
        return companyRepository.findByCompanyId(company.getCompany_id())
                .orElseThrow(() -> new IllegalStateException(
                        "Company not found after MERGE: " + company.getCompany_id()));
    }

    private Job mergeJob(Job job) {
        return jobRepository.findByJobId(job.getJob_id())
                .orElseGet(() -> jobRepository.save(job));
    }

    private void mergeOccupation(Occupation occupation) {
        if (occupation.getTextEmbedding() == null || occupation.getTextEmbedding().isEmpty()) {
            neo4jClient.query("""
                    MERGE (o:Occupation {occupation_id: $occupationId})
                    ON CREATE SET o.name   = $name,
                                  o.type   = $type,
                                  o.layer  = $layer,
                                  o.parent = $parent
                    """)
                    .bindAll(Map.of(
                            "occupationId", occupation.getOccupationId(),
                            "name",         occupation.getName(),
                            "type",         occupation.getType()   != null ? occupation.getType()   : "Specialized Role",
                            "layer",        occupation.getLayer()  != null ? occupation.getLayer()  : 3,
                            "parent",       occupation.getParent() != null ? occupation.getParent() : ""
                    ))
                    .run();
        } else {
            neo4jClient.query("""
                    MERGE (o:Occupation {occupation_id: $occupationId})
                    ON CREATE SET o.name           = $name,
                                  o.type           = $type,
                                  o.layer          = $layer,
                                  o.parent         = $parent,
                                  o.text_embedding = $text_embedding
                    """)
                    .bindAll(Map.of(
                            "occupationId",   occupation.getOccupationId(),
                            "name",           occupation.getName(),
                            "type",           occupation.getType()   != null ? occupation.getType()   : "Specialized Role",
                            "layer",          occupation.getLayer()  != null ? occupation.getLayer()  : 3,
                            "parent",         occupation.getParent() != null ? occupation.getParent() : "",
                            "text_embedding", occupation.getTextEmbedding()
                    ))
                    .run();
        }
    }

    private void mergeSkill(Skill skill) {
        if (skill.getTextEmbedding() == null || skill.getTextEmbedding().isEmpty()) {
            neo4jClient.query("""
                    MERGE (s:Skill {skill_id: $skillId})
                    ON CREATE SET s.name   = $name,
                                  s.layer  = $layer,
                                  s.type   = $type,
                                  s.parent = $parent
                    """)
                    .bindAll(Map.of(
                            "skillId", skill.getSkillId(),
                            "name",    skill.getName(),
                            "layer",   skill.getLayer()  != null ? skill.getLayer()  : 3,
                            "type",    skill.getType()   != null ? skill.getType()   : "Specific Skill",
                            "parent",  skill.getParent() != null ? skill.getParent() : ""
                    ))
                    .run();
        } else {
            neo4jClient.query("""
                    MERGE (s:Skill {skill_id: $skillId})
                    ON CREATE SET s.name           = $name,
                                  s.layer          = $layer,
                                  s.type           = $type,
                                  s.parent         = $parent,
                                  s.text_embedding = $text_embedding
                    """)
                    .bindAll(Map.of(
                            "skillId",        skill.getSkillId(),
                            "name",           skill.getName(),
                            "layer",          skill.getLayer()  != null ? skill.getLayer()  : 3,
                            "type",           skill.getType()   != null ? skill.getType()   : "Specific Skill",
                            "parent",         skill.getParent() != null ? skill.getParent() : "",
                            "text_embedding", skill.getTextEmbedding()
                    ))
                    .run();
        }
    }

    // ── Hierarchy wiring ─────────────────────────────────────────────────────

    private void mergeSkillHierarchy(Skill skill) {
        if (skill.getParent() == null || skill.getParent().isBlank()) return;

        neo4jClient.query("""
                MATCH (child:Skill  {skill_id: $skillId})
                MATCH (parent:Skill {name: $parentName, layer: 2})
                MERGE (child)-[:SUBCLASS_OF]->(parent)
                """)
                .bindAll(Map.of(
                        "skillId",    skill.getSkillId(),
                        "parentName", skill.getParent()
                ))
                .run();

        neo4jClient.query("""
                MATCH (group:Skill    {name: $groupName, layer: 2})
                MATCH (category:Skill {layer: 1})
                WHERE group.parent = category.name
                MERGE (group)-[:SUBCLASS_OF]->(category)
                """)
                .bindAll(Map.of("groupName", skill.getParent()))
                .run();
    }

    // ── Relationship merges ──────────────────────────────────────────────────

    private void mergePostedRelationship(String companyId, String jobId) {
        neo4jClient.query("""
                MATCH (c:Company {company_id: $companyId})
                MATCH (j:Job     {job_id:     $jobId})
                MERGE (c)-[r:POSTS]->(j)
                ON CREATE SET r.is_active = true
                """)
                .bindAll(Map.of("companyId", companyId, "jobId", jobId))
                .run();
    }

    private void mergeHasOccupationRelationship(String jobId, String occupationId) {
        neo4jClient.query("""
                MATCH (j:Job        {job_id:        $jobId})
                MATCH (o:Occupation {occupation_id: $occupationId})
                MERGE (j)-[:HAS_OCCUPATION]->(o)
                """)
                .bindAll(Map.of("jobId", jobId, "occupationId", occupationId))
                .run();
    }

    private void mergeRequiresRelationship(String jobId, String skillId) {
        neo4jClient.query("""
                MATCH (j:Job   {job_id:   $jobId})
                MATCH (s:Skill {skill_id: $skillId})
                MERGE (j)-[r:REQUIRES]->(s)
                ON CREATE SET r.importance      = 'required',
                              r.min_proficiency = 1
                """)
                .bindAll(Map.of("jobId", jobId, "skillId", skillId))
                .run();
    }
}