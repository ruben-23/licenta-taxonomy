package org.example.jobsmvp.repositories;

import org.example.jobsmvp.models.nodes.Job;
import org.example.jobsmvp.projections.JobRecommendationProjection;
import org.example.jobsmvp.projections.StudentRecommendationProjection;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecommendationRepository extends Neo4jRepository<Job, String> {

    /**
     * Executes a hybrid recommendation query to find the best students for a given job.
     *
     * @param jobId The unique ID of the job posting.
     * @param limit The maximum number of students to evaluate from the vector index.
     * @return A ranked, explainable list of student candidates.
     */
    @Query("""
        // --- STAGE 1: VECTOR RECALL ---
        MATCH (j:Job {job_id: $jobId})
        CALL db.index.vector.queryNodes('student_embeddings', $limit, j.embedding) YIELD node AS s, score AS similarity
        
        // --- STAGE 2: GRAPH FILTERING (RELAXED CONSTRAINTS) ---
        OPTIONAL MATCH (j)-[req:REQUIRES {importance: 'Mandatory'}]->(req_t:Technology)
        OPTIONAL MATCH (s)-[k:KNOWS]->(req_t) WHERE k.proficiency_level >= req.min_proficiency
        
        WITH s, j, similarity, 
             count(req_t) AS total_mandatory, 
             count(k) AS matched_mandatory
             
        WHERE total_mandatory = 0 OR matched_mandatory >= 1
        
        // --- STAGE 3: CONTEXT & EXPLAINABILITY ---
        CALL (s, j) {
            OPTIONAL MATCH (j)-[req_all:REQUIRES]->(t:Technology)
            OPTIONAL MATCH (s)-[k_all:KNOWS]->(t) WHERE k_all.proficiency_level >= req_all.min_proficiency
            
            RETURN 
                collect(CASE WHEN k_all IS NOT NULL THEN t.name END) AS matchedTechnologies,
                collect(CASE WHEN k_all IS NULL AND t IS NOT NULL THEN t.name END) AS missingTechnologies
        }
        
        // --- STAGE 4: PROJECTION MAPPING ---
        RETURN 
            s.student_id AS studentId, 
            s.name AS name, 
            s.major AS major, 
            s.degree_level AS degreeLevel, 
            s.graduation_year AS graduationYear, 
            similarity AS similarityScore, 
            matchedTechnologies, 
            missingTechnologies
        ORDER BY similarityScore DESC
    """)
    List<StudentRecommendationProjection> recommendStudentsForJob(@Param("jobId") String jobId, @Param("limit") int limit);

    @Query("""
        // --- STAGE 1: VECTOR RECALL ---
        MATCH (s:Student {student_id: $studentId})
        CALL db.index.vector.queryNodes('job_embeddings', $limit, s.embedding) YIELD node AS j, score AS similarity
        
        // --- STAGE 2: GRAPH FILTERING (RELAXED CONSTRAINTS) ---
        OPTIONAL MATCH (j)-[req:REQUIRES {importance: 'Mandatory'}]->(req_t:Technology)
        OPTIONAL MATCH (s)-[k:KNOWS]->(req_t) WHERE k.proficiency_level >= req.min_proficiency
        
        WITH s, j, similarity, 
             count(req_t) AS total_mandatory, 
             count(k) AS matched_mandatory
             
        WHERE total_mandatory = 0 OR matched_mandatory >= 1
        
        // --- STAGE 3: CONTEXT & EXPLAINABILITY ---
        OPTIONAL MATCH (c:Company)-[:POSTS]->(j)
        
        CALL(s, j) {
            OPTIONAL MATCH (j)-[req_all:REQUIRES]->(t:Technology)
            OPTIONAL MATCH (s)-[k_all:KNOWS]->(t) WHERE k_all.proficiency_level >= req_all.min_proficiency
            
            RETURN 
                collect(CASE WHEN k_all IS NOT NULL THEN t.name END) AS matchedTechnologies,
                collect(CASE WHEN k_all IS NULL AND t IS NOT NULL THEN t.name END) AS missingTechnologies
        }
        
        // --- STAGE 4: PROJECTION MAPPING ---
        RETURN 
            j.job_id AS jobId, 
            j.title AS title, 
            c.name AS companyName, 
            j.location AS location, 
            j.remote AS remote, 
            similarity AS similarityScore, 
            matchedTechnologies, 
            missingTechnologies
        ORDER BY similarityScore DESC
    """)
    List<JobRecommendationProjection> recommendJobsForStudent(@Param("studentId") String studentId, @Param("limit") int limit);
}