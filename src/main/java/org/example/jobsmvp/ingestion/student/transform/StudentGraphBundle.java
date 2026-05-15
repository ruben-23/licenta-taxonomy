package org.example.jobsmvp.ingestion.student.transform;

import org.example.jobsmvp.models.nodes.Course;
import org.example.jobsmvp.models.nodes.Diploma;
import org.example.jobsmvp.models.nodes.Project;
import org.example.jobsmvp.models.nodes.Student;

import java.util.List;

/**
 * Carries all graph nodes produced for a single student record from the
 * {@link StudentGraphTransformService}.
 *
 * Passed as a unit to
 * {@link org.example.jobsmvp.ingestion.student.graph.StudentGraphIngestionService}
 * for persistence. Skill nodes are embedded inside each entity's relationship
 * collections rather than listed separately, matching the domain model in
 * {@link Student}, {@link Project}, {@link Course}, and {@link Diploma}.
 *
 * Note: the {@code contentHash} is stored on the Student node in Neo4j so that
 * {@link org.example.jobsmvp.ingestion.student.deduplication.StudentDeduplicationService}
 * can perform fast duplicate checks on subsequent pipeline runs.
 */
public record StudentGraphBundle(

        /** The student node with all relationship collections populated. */
        Student student,

        /** Projects authored by the student (each carries its builtWith skills). */
        List<Project> projects,

        /** Courses completed by the student (each carries its covers skills). */
        List<Course> courses,

        /** Diplomas earned by the student (each carries its certifies skills). */
        List<Diploma> diplomas,

        /**
         * SHA-256 fingerprint of the raw record — stored on the Student node
         * as {@code content_hash} for future deduplication lookups.
         */
        String contentHash

) {}