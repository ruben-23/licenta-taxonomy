package org.example.jobsmvp.repositories;

import org.example.jobsmvp.models.nodes.Job;
import org.example.jobsmvp.models.nodes.Company;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends Neo4jRepository<Job, String> {
    @Query("MATCH (c:Company {company_id: $companyId})-[:POSTS]->(j:Job) " +
            "OPTIONAL MATCH (j)-[r:REQUIRES]->(t:Technology) " +
            "RETURN j, collect(r), collect(t)")
    List<Job> findJobsByCompany(String companyId);

    @Query("MATCH (j:Job) WHERE j.clean_description IS NULL RETURN j")
    List<Job> findJobsWithoutCleanDescription();

    @Query("MATCH (c:Company)-[:POSTS]->(j:Job {job_id: $jobId}) RETURN c LIMIT 1")
    Optional<Company> findCompanyForJob(@Param("jobId") String jobId);

    @Query("MATCH (j:Job {job_id: $jobId}) RETURN j LIMIT 1")
    Optional<Job> findByJobId(@Param("jobId") String jobId);

    @Query("MATCH (j:Job {job_id: $jobId}) RETURN COUNT(j) > 0")
    boolean existsByJobId(@Param("jobId") String jobId);

    /**
     * Checks for a previously-computed content hash stored on the Job node.
     * Requires adding a `content_hash` property to the Job node class and populating
     * it in GraphIngestionService.
     */
    @Query("MATCH (j:Job {content_hash: $hash}) RETURN count(j) > 0")
    boolean existsByContentHash(@Param("hash") String hash);

    @Query("MATCH (j:Job) WHERE j.text_embedding IS NOT NULL " +
           "WITH j, gds.similarity.cosine($embedding, j.text_embedding) AS score " +
           "WHERE score >= $threshold " +
           "RETURN count(j) > 0")
    boolean existsSimilarJobByEmbedding(@Param("embedding") List<Double> embedding, @Param("threshold") double threshold);
}
