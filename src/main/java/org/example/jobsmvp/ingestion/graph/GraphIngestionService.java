
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        log.info("Ingested job '{}' by '{}'", savedJob.getTitle(), savedCompany.getName());
    }

    // ── Node merges ──────────────────────────────────────────────────────────

    private Company mergeCompany(Company company) {
        return companyRepository.findByCompanyId(company.getCompany_id())
                .map(existing -> {
                    if (company.getIndustry() != null) existing.setIndustry(company.getIndustry());
                    if (company.getSize()     != null) existing.setSize(company.getSize());
                    return companyRepository.save(existing);
                })
                .orElseGet(() -> companyRepository.save(company));
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
                    MERGE (s:Skill {SkillId: $skillId})
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
                    MERGE (s:Skill {SkillId: $skillId})
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

    /**
     * Ensures the full {@code SUBCLASS_OF} chain exists for a layer-3 Skill:
     *
     *   (Specific Skill) -[:SUBCLASS_OF]-> (Skill Group) -[:SUBCLASS_OF]-> (Skill Category)
     *
     * Both the Skill Group and Skill Category nodes are guaranteed to exist
     *
     *
     * The {@code skill.getParent()} field holds the Skill Group name, so we
     * can resolve the group node by name and then follow its own {@code parent}
     * field up to the category.
     */
    private void mergeSkillHierarchy(Skill skill) {
        if (skill.getParent() == null || skill.getParent().isBlank()) return;

        // Link Specific Skill → Skill Group (layer 2) by name
        neo4jClient.query("""
                MATCH (child:Skill  {SkillId: $skillId})
                MATCH (parent:Skill {name: $parentName, layer: 2})
                MERGE (child)-[:SUBCLASS_OF]->(parent)
                """)
                .bindAll(Map.of(
                        "skillId",    skill.getSkillId(),
                        "parentName", skill.getParent()
                ))
                .run();

        // Link Skill Group → Skill Category (layer 1) — seeder already did this,
        // but MERGE keeps it idempotent in case a new group was created at runtime.
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
                MATCH (s:Skill {SkillId: $skillId})
                MERGE (j)-[r:REQUIRES]->(s)
                ON CREATE SET r.importance      = 'required',
                              r.min_proficiency = 1
                """)
                .bindAll(Map.of("jobId", jobId, "skillId", skillId))
                .run();
    }
}