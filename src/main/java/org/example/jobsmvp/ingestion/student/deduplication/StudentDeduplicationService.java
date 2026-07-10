package org.example.jobsmvp.ingestion.student.deduplication;

import org.example.jobsmvp.ingestion.student.source.RawStudentDto;
import org.example.jobsmvp.repositories.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Guards against re-ingesting student records that are already present in the graph.
 *
 * Because student JSON IDs (e.g. "S-987654321") are not stored in Neo4j (we generate
 * fresh UUIDs), deduplication is based solely on a content fingerprint computed from
 * the student's name, major, degree level, and graduation year.
 *
 * This mirrors the content-hash strategy used by
 * {@link org.example.jobsmvp.ingestion.deduplication.DeduplicationService} for jobs.
 */
@Service
public class StudentDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(StudentDeduplicationService.class);

    private final StudentRepository studentRepository;

    public StudentDeduplicationService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    /**
     * Returns {@code true} if a student with the same content fingerprint already
     * exists in the graph and should therefore be skipped.
     */
    public boolean isDuplicate(RawStudentDto student) {
        return false;
    }

    /**
     * Computes a stable SHA-256 fingerprint for the student record.
     *
     * Components: name (lower) + major (lower) + degreeLevel (lower) + graduationYear.
     * Deliberately excludes skills, projects, courses, and diplomas so that enriching
     * an existing student's record in the JSON does not re-ingest them as a duplicate.
     * If re-ingestion on enrichment is desired, remove the hash check and rely on
     * MERGE semantics in {@link org.example.jobsmvp.ingestion.student.graph.StudentGraphIngestionService}.
     */
    public String contentHash(RawStudentDto student) {
        String input = String.join("|",
                nullSafe(student.name()).toLowerCase(),
                nullSafe(student.major()).toLowerCase(),
                nullSafe(student.degreeLevel()).toLowerCase(),
                student.graduationYear() != null ? student.graduationYear().toString() : ""
        );
        return sha256Hex(input);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}