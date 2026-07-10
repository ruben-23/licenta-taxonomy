package org.example.jobsmvp.controller;

import lombok.AllArgsConstructor;
import org.example.jobsmvp.ingestion.orchestrator.IngestionPipelineOrchestrator;
import org.example.jobsmvp.ingestion.source.RawJobDto;
import org.example.jobsmvp.ingestion.student.orchestrator.StudentIngestionOrchestrator;
import org.example.jobsmvp.ingestion.student.source.RawStudentDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingest")
@AllArgsConstructor
public class IngestionController {

    private final StudentIngestionOrchestrator studentIngestionOrchestrator;
    private final IngestionPipelineOrchestrator ingestionPipelineOrchestrator;

    @PostMapping("/student")
    public ResponseEntity<String> ingestStudent(@RequestBody RawStudentDto student) {
        boolean success = studentIngestionOrchestrator.ingestStudent(student);
        if (success) {
            return ResponseEntity.ok("Student ingested successfully.");
        } else {
            return ResponseEntity.badRequest().body("Student ingestion failed or student is a duplicate.");
        }
    }

    @PostMapping("/job")
    public ResponseEntity<String> ingestJob(@RequestBody RawJobDto job) {
        boolean success = ingestionPipelineOrchestrator.ingestJob(job);
        if (success) {
            return ResponseEntity.ok("Job ingested successfully.");
        } else {
            return ResponseEntity.badRequest().body("Job ingestion failed or job is a duplicate.");
        }
    }
}