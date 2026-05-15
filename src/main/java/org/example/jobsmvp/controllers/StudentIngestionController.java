package org.example.jobsmvp.controllers;

import lombok.AllArgsConstructor;
import org.example.jobsmvp.ingestion.student.orchestrator.StudentIngestionOrchestrator;
import org.example.jobsmvp.ingestion.student.orchestrator.StudentIngestionOrchestrator.PipelineResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes a manual trigger for the student ingestion pipeline.
 *
 * Endpoint:  POST /api/ingestion/students/run
 *
 * Returns a JSON summary of the run (fetched / skipped / ingested / failed counts).
 * The pipeline runs synchronously, so the response is only sent once all records
 * have been processed.
 *
 * For long-running imports consider wrapping the orchestrator call in a
 * {@code @Async} method and returning a 202 Accepted with a status-polling endpoint.
 */
@RestController
@RequestMapping("/api/ingestion/students")
@AllArgsConstructor
public class StudentIngestionController {

    private static final Logger log = LoggerFactory.getLogger(StudentIngestionController.class);

    private final StudentIngestionOrchestrator orchestrator;

    /**
     * Triggers the full student ingestion pipeline.
     *
     * @return 200 OK with a {@link PipelineResult} body summarising the run
     */
    @PostMapping("/run")
    public ResponseEntity<PipelineResult> run() {
        log.info("Manual student ingestion run triggered via REST.");
        PipelineResult result = orchestrator.run();
        return ResponseEntity.ok(result);
    }
}
