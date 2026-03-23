package org.example.jobsmvp.ingestion.graph;

import lombok.AllArgsConstructor;
import org.example.jobsmvp.ingestion.transform.JobGraphBundle;
import org.example.jobsmvp.models.nodes.Company;
import org.example.jobsmvp.models.nodes.Job;
import org.example.jobsmvp.models.nodes.Technology;
import org.example.jobsmvp.repositories.CompanyRepository;
import org.example.jobsmvp.repositories.JobRepository;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Persists a {@link JobGraphBundle} into Neo4j via SDN repositories
 * and direct Cypher for relationship MERGE operations.
 *
 * All operations in a single bundle are wrapped in one transaction.
 * MERGE semantics ensure idempotency — re-running the pipeline is safe.
 */
@Service
@AllArgsConstructor
public class GraphIngestionService {

    private static final Logger log = LoggerFactory.getLogger(GraphIngestionService.class);

    private final CompanyRepository companyRepository;
    private final JobRepository     jobRepository;
    private final Neo4jClient       neo4jClient;

    /**
     * Persists the entire bundle atomically.
     * On failure the transaction rolls back; the raw file is kept for retry.
     *
     * @param bundle graph objects for one job posting
     */
    @Transactional
    public void ingest(JobGraphBundle bundle) {
        Company savedCompany = mergeCompany(bundle.company());
        Job     savedJob     = mergeJob(bundle.job());

        mergePostedRelationship(savedCompany.getCompany_id(), savedJob.getJob_id());

        for (Technology tech : bundle.technologies()) {
            mergeTechnology(tech);
            mergeRequiresRelationship(savedJob.getJob_id(), tech.getTech_id());
        }

        log.info("Ingested job '{}' by '{}'", savedJob.getTitle(), savedCompany.getName());
    }

    // ── Node merges ──────────────────────────────────────────────────────────

    private Company mergeCompany(Company company) {
        return companyRepository.findByCompanyId(company.getCompany_id())
                .map(existing -> {
                    // Update mutable fields on re-encounter
                    if (company.getIndustry() != null)  existing.setIndustry(company.getIndustry());
                    if (company.getSize()     != null)  existing.setSize(company.getSize());
                    return companyRepository.save(existing);
                })
                .orElseGet(() -> companyRepository.save(company));
    }

    private Job mergeJob(Job job) {
        return jobRepository.findByJobId(job.getJob_id())
                .orElseGet(() -> jobRepository.save(job));
    }

    private void mergeTechnology(Technology tech) {
        if (tech.getTextEmbedding() == null || tech.getTextEmbedding().isEmpty()) {
            log.warn("Technology '{}' has no embedding. Skipping embedding update.", tech.getName());
            neo4jClient.query("""
                    MERGE (t:Technology {tech_id: $techId})
                    ON CREATE SET t.name      = $name,
                                  t.category  = $category
                    """)
                    .bindAll(Map.of(
                            "techId",    tech.getTech_id(),
                            "name",      tech.getName(),
                            "category",  tech.getCategory() != null ? tech.getCategory() : "Other"
                    ))
                    .run();
        } else {
            neo4jClient.query("""
                    MERGE (t:Technology {tech_id: $techId})
                    ON CREATE SET t.name      = $name,
                                  t.category  = $category,
                                  t.text_embedding = $text_embedding
                    """)
                    .bindAll(Map.of(
                            "techId",    tech.getTech_id(),
                            "name",      tech.getName(),
                            "category",  tech.getCategory() != null ? tech.getCategory() : "Other",
                            "text_embedding", tech.getTextEmbedding()
                    ))
                    .run();
        }
    }

    // ── Relationship merges ──────────────────────────────────────────────────

    private void mergePostedRelationship(String companyId, String jobId) {
        neo4jClient.query("""
                MATCH (c:Company {company_id: $companyId})
                MATCH (j:Job {job_id: $jobId})
                MERGE (c)-[r:POSTS]->(j)
                ON CREATE SET r.is_active = true
                """)
                .bindAll(Map.of("companyId", companyId, "jobId", jobId))
                .run();
    }

    private void mergeRequiresRelationship(String jobId, String techId) {
        neo4jClient.query("""
                MATCH (j:Job {job_id: $jobId})
                MATCH (t:Technology {tech_id: $techId})
                MERGE (j)-[r:REQUIRES]->(t)
                ON CREATE SET r.importance      = 'required',
                              r.min_proficiency = 1
                """)
                .bindAll(Map.of("jobId", jobId, "techId", techId))
                .run();
    }
}
