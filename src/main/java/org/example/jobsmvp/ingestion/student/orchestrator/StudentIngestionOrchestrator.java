package org.example.jobsmvp.ingestion.student.orchestrator;

import lombok.AllArgsConstructor;
import org.example.jobsmvp.ingestion.normalization.EntityNormalizationService;
import org.example.jobsmvp.ingestion.student.deduplication.StudentDeduplicationService;
import org.example.jobsmvp.ingestion.student.graph.StudentGraphIngestionService;
import org.example.jobsmvp.ingestion.student.source.RawStudentDto;
import org.example.jobsmvp.ingestion.student.source.StudentJsonLoader;
import org.example.jobsmvp.ingestion.student.transform.StudentGraphBundle;
import org.example.jobsmvp.ingestion.student.transform.StudentGraphTransformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Entry point for the student ingestion pipeline.
 *
 * This pipeline is completely independent of
 * {@link org.example.jobsmvp.ingestion.orchestrator.IngestionPipelineOrchestrator}
 * and can be triggered separately — either manually via a REST endpoint or on a
 * dedicated schedule.
 *
 * Execution order per student record:
 * <ol>
 *   <li>Load all student records from JSON via {@link StudentJsonLoader}.</li>
 *   <li>Deduplication check — skip records already present in the graph.</li>
 *   <li>Transform — build a graph-ready {@link StudentGraphBundle}:
 *     <ul>
 *       <li>Generate fresh UUIDs for all entities.</li>
 *       <li>Normalise skill names through the <em>shared</em>
 *           {@link EntityNormalizationService} (same taxonomy as the job pipeline).</li>
 *       <li>Compute text embeddings for the Student node.</li>
 *     </ul>
 *   </li>
 *   <li>Persist — MERGE all nodes and relationships into Neo4j via
 *       {@link StudentGraphIngestionService}.</li>
 * </ol>
 *
 * Shared components with the job pipeline:
 * <ul>
 *   <li>{@link EntityNormalizationService} — skill taxonomy resolution.</li>
 * </ul>
 *
 * Student-specific components:
 * <ul>
 *   <li>{@link StudentJsonLoader} — JSON source.</li>
 *   <li>{@link StudentDeduplicationService} — content-hash deduplication.</li>
 *   <li>{@link StudentGraphTransformService} — graph bundle assembly.</li>
 *   <li>{@link StudentGraphIngestionService} — Neo4j persistence.</li>
 * </ul>
 */
@Service
@AllArgsConstructor
public class StudentIngestionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(StudentIngestionOrchestrator.class);

    private final StudentJsonLoader              jsonLoader;
    private final StudentDeduplicationService    deduplicationService;
    private final StudentGraphTransformService   transformService;
    private final StudentGraphIngestionService   ingestionService;
    private final EntityNormalizationService     normalizationService;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs the full student ingestion pipeline and returns a summary.
     *
     * Safe to call repeatedly — MERGE semantics and deduplication ensure that
     * already-ingested students are skipped without producing duplicate nodes.
     *
     * @return pipeline result with counts for fetched, skipped, ingested, failed
     */
    public PipelineResult run() {
        log.info("=== Student ingestion pipeline starting ===");

        // Reset the skill normalisation cache so this run starts clean.
        // If the student pipeline runs immediately after the job pipeline the cache
        // may still be warm, which is fine — clearing it avoids potential stale
        // cross-run entries when the taxonomy has been updated between runs.
        normalizationService.clearCache();

        List<RawStudentDto> students = jsonLoader.loadAll();

        int fetched  = students.size();
        int skipped  = 0;
        int ingested = 0;
        int failed   = 0;

        for (RawStudentDto student : students) {
            if (deduplicationService.isDuplicate(student)) {
                log.debug("Skipping duplicate student: '{}'", student.name());
                skipped++;
                continue;
            }

            if (processStudent(student)) {
                ingested++;
            } else {
                failed++;
            }
        }

        PipelineResult result = new PipelineResult(fetched, skipped, ingested, failed);
        log.info("=== Student pipeline complete: {} ===", result);
        return result;
    }

    // ── Per-student processing ────────────────────────────────────────────────

    /**
     * Runs a single student record through the transform → persist steps.
     *
     * @return {@code true} on success, {@code false} if an unrecoverable error occurs
     */
    private boolean processStudent(RawStudentDto student) {
        try {
            StudentGraphBundle bundle = transformService.transform(student);
            ingestionService.ingest(bundle);
            log.debug("Processed student '{}'", student.name());
            return true;
        } catch (Exception e) {
            log.error("Failed to process student '{}': {}", student.name(), e.getMessage(), e);
            return false;
        }
    }

    // ── Result DTO ────────────────────────────────────────────────────────────

    /**
     * Summary returned after a pipeline run completes.
     *
     * @param fetched  total records read from the JSON file
     * @param skipped  records skipped because they were already in the graph
     * @param ingested records successfully ingested
     * @param failed   records that caused an unrecoverable error
     */
    public record PipelineResult(
            int fetched,
            int skipped,
            int ingested,
            int failed
    ) {
        @Override
        public String toString() {
            return "fetched=%d skipped=%d ingested=%d failed=%d"
                    .formatted(fetched, skipped, ingested, failed);
        }
    }
}