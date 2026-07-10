package org.example.jobsmvp.repositories;

import org.example.jobsmvp.models.nodes.Student;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data Neo4j repository for {@link Student} nodes.
 *
 * Used by:
 *  - {@link org.example.jobsmvp.ingestion.student.deduplication.StudentDeduplicationService}
 *    for content-hash duplicate checks.
 *  - {@link org.example.jobsmvp.ingestion.student.graph.StudentGraphIngestionService}
 *    indirectly, through the raw {@code Neo4jClient} for MERGE operations.
 */
public interface StudentRepository extends Neo4jRepository<Student, String> {

    @Query("MATCH (s:Student) RETURN s SKIP $skip LIMIT $limit")
    List<Student> findAllPaginated(int skip, int limit);

    /**
     * Returns {@code true} if a Student node with the given content hash already
     * exists in the graph.
     *
     * The content hash is computed by
     * {@link org.example.jobsmvp.ingestion.student.deduplication.StudentDeduplicationService#contentHash}
     * from name + major + degreeLevel + graduationYear so it is stable across runs.
     */
    @Query("MATCH (s:Student {content_hash: $hash}) RETURN count(s) > 0")
    boolean existsByContentHash(@Param("hash") String hash);

    /**
     * Finds a student by their generated UUID (the primary key stored in Neo4j).
     * The source file's original student_id is never stored.
     */
    Optional<Student> findByStudentId(String studentId);
}