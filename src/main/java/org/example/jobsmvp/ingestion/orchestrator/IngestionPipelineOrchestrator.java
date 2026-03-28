
package org.example.jobsmvp.ingestion.orchestrator;

import lombok.AllArgsConstructor;
import org.example.jobsmvp.ingestion.deduplication.DeduplicationService;
import org.example.jobsmvp.ingestion.extraction.EntityExtractionService;
import org.example.jobsmvp.ingestion.extraction.ExtractedEntities;
import org.example.jobsmvp.ingestion.graph.GraphIngestionService;
import org.example.jobsmvp.ingestion.normalization.EntityNormalizationService;
import org.example.jobsmvp.ingestion.preprocessing.JobPreprocessor;
import org.example.jobsmvp.ingestion.source.JSearchApiClient;
import org.example.jobsmvp.ingestion.source.RawJobDto;
import org.example.jobsmvp.ingestion.storage.RawJobStorageService;
import org.example.jobsmvp.ingestion.transform.GraphTransformService;
import org.example.jobsmvp.ingestion.transform.JobGraphBundle;
import org.example.jobsmvp.repositories.GraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Entry point for the job ingestion pipeline.
 *
 * Execution order per job:
 *  1. Fetch raw jobs from JSearch API (synchronous HTTP via RestClient)
 *  2. Deduplication check  → skip if already in graph
 *  3. Store raw JSON       → raw-data/{jobId}.json
 *  4. Preprocess text      → strip HTML, normalise
 *  5. Extract entities     → LLM → ExtractedEntities
 *  6. Transform            → graph nodes/edges (normalisation inside)
 *  7. Persist              → MERGE into Neo4j
 *
 * Triggers:
 *  - @Scheduled cron  (configurable via ingestion.cron)
 *  - POST /api/ingestion/run
 */
@Service
@AllArgsConstructor
public class IngestionPipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipelineOrchestrator.class);

    private final JSearchApiClient           apiClient;
    private final RawJobStorageService       storageService;
    private final DeduplicationService       deduplicationService;
    private final JobPreprocessor            preprocessor;
    private final EntityExtractionService    extractionService;
    private final GraphTransformService      transformService;
    private final GraphIngestionService      ingestionService;
    private final EntityNormalizationService normalizationService;
    private final GraphRepository graphRepository;



    // ── Scheduled run ─────────────────────────────────────────────────────────

    @Scheduled(cron = "${ingestion.cron:0 0 2 * * ?}")
    public PipelineResult runScheduled() {
        log.info("=== Ingestion pipeline starting (scheduled) ===");
        return run(null, -1);
    }

    // ── Manual run ────────────────────────────────────────────────────────────

    /**
     * Runs the pipeline synchronously and returns a summary when complete.
     *
     * @param query   search query; null → use default from config
     * @param maxJobs stop after processing this many new jobs; -1 = no limit
     */
    public PipelineResult run(String query, int maxJobs) {
        normalizationService.clearCache();

//        List<RawJobDto> jobs = query != null
//                ? apiClient.fetchJobs(query, 1)
//                : apiClient.fetchJobs();
        List<RawJobDto> jobs = query != null
                ? apiClient.fetchJobsFromRandomFile(3)
                : apiClient.fetchJobsFromFile("jwLMxhYLldcDdFY7AAAAAA==");

        int fetched  = jobs.size();
        int stored   = 0;
        int skipped  = 0;
        int ingested = 0;
        int failed   = 0;

        for (RawJobDto job : jobs) {
            if (maxJobs >= 0 && ingested >= maxJobs) {
                log.info("Reached maxJobs={} limit, stopping.", maxJobs);
                break;
            }

            if (deduplicationService.isDuplicate(job)) {
                skipped++;
                continue;
            }

            if (storageService.save(job)) {
                stored++;
            }

            boolean success = processJob(job);
            if (success) {
                ingested++;
            } else {
                failed++;
            }
        }

        generateNode2VecEmbeddings();
        PipelineResult result = new PipelineResult(fetched, stored, skipped, ingested, failed);
        log.info("=== Pipeline complete: {} ===", result);
        return result;
    }

    // ── Per-job processing ────────────────────────────────────────────────────

    /**
     * Runs a single job through the full pipeline.
     *
     * @return true on success, false if an exception is thrown
     */
    private boolean processJob(RawJobDto job) {
        try {
            String cleanedDesc = preprocessor.cleanDescription(job);

            ExtractedEntities entities = extractionService.extract(
                    job.employerName(), job.jobTitle(), cleanedDesc);

            JobGraphBundle bundle = transformService.transform(job, entities, cleanedDesc);

            ingestionService.ingest(bundle);

            log.debug("Processed jobId={} title='{}'", job.jobId(), bundle.job().getTitle());
            return true;

        } catch (Exception e) {
            log.error("Failed to process jobId={}: {}", job.jobId(), e.getMessage(), e);
            return false;
        }
    }

    private void generateNode2VecEmbeddings(){
        try {
            System.out.println("Generating Node2Vec embeddings...");

            // Step 0: Clean up any old graph projection that might be stuck in memory
            graphRepository.dropGraphProjection();

            // Step 1: Project the graph into GDS memory
            Long projectedNodes = graphRepository.createGraphProjection();
//            System.out.println("Projected " + projectedNodes + " nodes into GDS memory.");

            // Step 2: Run the algorithm and write properties back to the database
            Long nodesProcessed = graphRepository.writeNode2VecEmbeddings();
            System.out.println("Embeddings generated and written for " + nodesProcessed + " nodes.");

            // Step 3: Drop the graph from memory
            graphRepository.dropGraphProjection();
//            System.out.println("Cleaned up GDS memory.");

        } catch (Exception e) {
            System.err.println("Failed to generate embeddings.");
            e.printStackTrace();
        }
    }

    // ── Result DTO ────────────────────────────────────────────────────────────

    public record PipelineResult(
            int fetched,
            int storedRaw,
            int skippedDuplicates,
            int ingested,
            int failed
    ) {
        @Override
        public String toString() {
            return "fetched=%d stored=%d skipped=%d ingested=%d failed=%d"
                    .formatted(fetched, storedRaw, skippedDuplicates, ingested, failed);
        }
    }
}