package org.example.jobsmvp.controllers;

import lombok.AllArgsConstructor;
import org.example.jobsmvp.ingestion.orchestrator.IngestionPipelineOrchestrator;
import org.example.jobsmvp.ingestion.preprocessing.JobDescriptionReformatter;
import org.example.jobsmvp.models.nodes.Company;
import org.example.jobsmvp.models.nodes.Job;
import org.example.jobsmvp.repositories.*;
import org.example.jobsmvp.services.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class AppController {

    private final RecommendationService recommendationService;
    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;
    private final StudentRepository studentRepository;
    private final IngestionPipelineOrchestrator ingestionOrchestrator;
    private final JobDescriptionReformatter jobDescriptionReformatter;

    @GetMapping("/companies")
    public ResponseEntity<?> getCompanies(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(companyRepository.findAllPaginated(page * size, size));
    }

    @GetMapping("/companies/{id}/jobs")
    public ResponseEntity<?> getJobsByCompany(@PathVariable String id) {
        System.out.println("Getting jobs for company with id: " + id);
        List<Job> jobs = jobRepository.findJobsByCompany(id);
//        System.out.println(jobs);
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/students")
    public ResponseEntity<?> getAllStudents() {
        return ResponseEntity.ok(studentRepository.findAll());
    }

    // Generic details endpoints for the UI modals
    @GetMapping("/companies/{id}")
    public ResponseEntity<?> getCompanyDetails(@PathVariable String id) { return ResponseEntity.of(companyRepository.findById(id)); }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getJobDetails(@PathVariable String id) { return ResponseEntity.of(jobRepository.findById(id)); }

    @GetMapping("/students/{id}")
    public ResponseEntity<?> getStudentDetails(@PathVariable String id) { return ResponseEntity.of(studentRepository.findById(id)); }

    // Recommendation Endpoints
    @GetMapping("/jobs/{jobId}/recommend-candidates")
    public ResponseEntity<?> recommendCandidates(@PathVariable String jobId) {
        try {
            return ResponseEntity.ok(recommendationService.getRecommendedStudentsForJob(jobId));
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown internal error";
            return ResponseEntity.internalServerError().body(Map.of("error", errorMsg));
        }
    }

    @GetMapping("/students/{studentId}/recommend-jobs")
    public ResponseEntity<?> recommendJobs(@PathVariable String studentId) {
        try {
            return ResponseEntity.ok(recommendationService.getRecommendedJobsForStudent(studentId));
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown internal error";
            return ResponseEntity.internalServerError().body(Map.of("error", errorMsg));
        }
    }

    // ========================================================================
    // Vector Embedding Recommendation Endpoints (Node2Vec Hybrid AI)
    // ========================================================================

    @GetMapping("/jobs/{jobId}/recommend-candidates/vector")
    public ResponseEntity<?> recommendCandidatesByEmbedding(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            return ResponseEntity.ok(recommendationService.getStudentMatchesByEmbedding(jobId, limit));
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown internal error during vector search";
            return ResponseEntity.internalServerError().body(Map.of("error", errorMsg));
        }
    }

    @GetMapping("/students/{studentId}/recommend-jobs/vector")
    public ResponseEntity<?> recommendJobsByEmbedding(
            @PathVariable String studentId,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            return ResponseEntity.ok(recommendationService.getJobMatchesByEmbedding(studentId, limit));
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown internal error during vector search";
            return ResponseEntity.internalServerError().body(Map.of("error", errorMsg));
        }
    }

    @GetMapping("/ingestion")
    public ResponseEntity<Void> startIngestion(){
        System.out.println("Starting ingestion...");

        IngestionPipelineOrchestrator.PipelineResult result = ingestionOrchestrator.run("junior developer jobs", -1);
        System.out.println(result.toString());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/jobs/reformat-missing")
    public ResponseEntity<?> reformatMissingJobDescriptions() {
        System.out.println("Starting to reformat missing job descriptions...");
        List<Job> jobs = jobRepository.findJobsWithoutCleanDescription();
        int successCount = 0;
        int failCount = 0;
        
        for (Job job : jobs) {
            try {
                // Avoid NullPointerException if raw description is missing
                String rawDescription = job.getDescription();
                if (rawDescription == null || rawDescription.isBlank()) {
                    continue;
                }

                // Find company to get employerName
                Optional<Company> companyOpt = jobRepository.findCompanyForJob(job.getJob_id());
                String employerName = companyOpt.map(Company::getName).orElse("");
                
                var result = jobDescriptionReformatter.reformat(employerName, job.getTitle(), rawDescription);
                
                if (result.cleanDescription() != null && !result.cleanDescription().equals(rawDescription)) {
                    job.setCleanDescription(result.cleanDescription());
                    jobRepository.save(job);
                    
                    if (result.companyDescription() != null && companyOpt.isPresent()) {
                        Company company = companyOpt.get();
                        company.setDescription(result.companyDescription());
                        companyRepository.save(company);
                    }
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                failCount++;
                System.err.println("Failed to reformat job " + job.getJob_id() + ": " + e.getMessage());
            }
        }
        
        return ResponseEntity.ok(Map.of(
            "processed", jobs.size(), 
            "success", successCount, 
            "failed", failCount
        ));
    }
}