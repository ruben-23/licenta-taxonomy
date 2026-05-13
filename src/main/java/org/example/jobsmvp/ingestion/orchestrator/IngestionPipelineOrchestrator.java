
package org.example.jobsmvp.ingestion.orchestrator;
//
//import lombok.AllArgsConstructor;
//import org.example.jobsmvp.ingestion.deduplication.DeduplicationService;
//import org.example.jobsmvp.ingestion.extraction.EntityExtractionService;
//import org.example.jobsmvp.ingestion.extraction.ExtractedEntities;
//import org.example.jobsmvp.ingestion.graph.GraphIngestionService;
//import org.example.jobsmvp.ingestion.normalization.EntityNormalizationService;
//import org.example.jobsmvp.ingestion.preprocessing.JobPreprocessor;
//import org.example.jobsmvp.ingestion.source.JSearchApiClient;
//import org.example.jobsmvp.ingestion.source.RawJobDto;
//import org.example.jobsmvp.ingestion.storage.RawJobStorageService;
//import org.example.jobsmvp.ingestion.transform.GraphTransformService;
//import org.example.jobsmvp.ingestion.transform.JobGraphBundle;
//import org.example.jobsmvp.repositories.GraphRepository;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
///**
// * Entry point for the job ingestion pipeline.
// *
// * Execution order per job:
// *  1. Fetch raw jobs from JSearch API (synchronous HTTP via RestClient)
// *  2. Deduplication check  → skip if already in graph
// *  3. Store raw JSON       → raw-data/{jobId}.json
// *  4. Preprocess text      → strip HTML, normalise
// *  5. Extract entities     → LLM → ExtractedEntities
// *  6. Transform            → graph nodes/edges (normalisation inside)
// *  7. Persist              → MERGE into Neo4j
// *
// * Triggers:
// *  - @Scheduled cron  (configurable via ingestion.cron)
// *  - POST /api/ingestion/run
// */
//@Service
//@AllArgsConstructor
//public class IngestionPipelineOrchestrator {
//
//    private static final Logger log = LoggerFactory.getLogger(IngestionPipelineOrchestrator.class);
//
//    private final JSearchApiClient           apiClient;
//    private final RawJobStorageService       storageService;
//    private final DeduplicationService       deduplicationService;
//    private final JobPreprocessor            preprocessor;
//    private final EntityExtractionService    extractionService;
//    private final GraphTransformService      transformService;
//    private final GraphIngestionService      ingestionService;
//    private final EntityNormalizationService normalizationService;
//    private final GraphRepository graphRepository;
//
//
//
//    // ── Scheduled run ─────────────────────────────────────────────────────────
//
//    @Scheduled(cron = "${ingestion.cron:0 0 2 * * ?}")
//    public PipelineResult runScheduled() {
//        log.info("=== Ingestion pipeline starting (scheduled) ===");
//        return run(null, -1);
//    }
//
//    // ── Manual run ────────────────────────────────────────────────────────────
//
//    /**
//     * Runs the pipeline synchronously and returns a summary when complete.
//     *
//     * @param query   search query; null → use default from config
//     * @param maxJobs stop after processing this many new jobs; -1 = no limit
//     */
//    public PipelineResult run(String query, int maxJobs) {
//        normalizationService.clearCache();
//
////        List<RawJobDto> jobs = query != null
////                ? apiClient.fetchJobs(query, 1)
////                : apiClient.fetchJobs();
//        List<RawJobDto> jobs = query != null
//                ? apiClient.fetchJobsFromRandomFile(3)
//                : apiClient.fetchJobsFromFile("jwLMxhYLldcDdFY7AAAAAA==");
//
//        int fetched  = jobs.size();
//        int stored   = 0;
//        int skipped  = 0;
//        int ingested = 0;
//        int failed   = 0;
//
//        for (RawJobDto job : jobs) {
//            if (maxJobs >= 0 && ingested >= maxJobs) {
//                log.info("Reached maxJobs={} limit, stopping.", maxJobs);
//                break;
//            }
//
//            if (deduplicationService.isDuplicate(job)) {
//                skipped++;
//                continue;
//            }
//
//            if (storageService.save(job)) {
//                stored++;
//            }
//
//            boolean success = processJob(job);
//            if (success) {
//                ingested++;
//            } else {
//                failed++;
//            }
//        }
//
//        generateNode2VecEmbeddings();
//        PipelineResult result = new PipelineResult(fetched, stored, skipped, ingested, failed);
//        log.info("=== Pipeline complete: {} ===", result);
//        return result;
//    }
//
//    // ── Per-job processing ────────────────────────────────────────────────────
//
//    /**
//     * Runs a single job through the full pipeline.
//     *
//     * @return true on success, false if an exception is thrown
//     */
//    private boolean processJob(RawJobDto job) {
//        try {
//            String cleanedDesc = preprocessor.cleanDescription(job);
//
//            ExtractedEntities entities = extractionService.extract(
//                    job.employerName(), job.jobTitle(), cleanedDesc);
//
//            JobGraphBundle bundle = transformService.transform(job, entities, cleanedDesc);
//
//            ingestionService.ingest(bundle);
//
//            log.debug("Processed jobId={} title='{}'", job.jobId(), bundle.job().getTitle());
//            return true;
//
//        } catch (Exception e) {
//            log.error("Failed to process jobId={}: {}", job.jobId(), e.getMessage(), e);
//            return false;
//        }
//    }
//
//    private void generateNode2VecEmbeddings(){
//        try {
//            System.out.println("Generating Node2Vec embeddings...");
//
//            // Step 0: Clean up any old graph projection that might be stuck in memory
//            graphRepository.dropGraphProjection();
//
//            // Step 1: Project the graph into GDS memory
//            Long projectedNodes = graphRepository.createGraphProjection();
////            System.out.println("Projected " + projectedNodes + " nodes into GDS memory.");
//
//            // Step 2: Run the algorithm and write properties back to the database
//            Long nodesProcessed = graphRepository.writeNode2VecEmbeddings();
//            System.out.println("Embeddings generated and written for " + nodesProcessed + " nodes.");
//
//            // Step 3: Drop the graph from memory
//            graphRepository.dropGraphProjection();
////            System.out.println("Cleaned up GDS memory.");
//
//        } catch (Exception e) {
//            System.err.println("Failed to generate embeddings.");
//            e.printStackTrace();
//        }
//    }
//
//    // ── Result DTO ────────────────────────────────────────────────────────────
//
//    public record PipelineResult(
//            int fetched,
//            int storedRaw,
//            int skippedDuplicates,
//            int ingested,
//            int failed
//    ) {
//        @Override
//        public String toString() {
//            return "fetched=%d stored=%d skipped=%d ingested=%d failed=%d"
//                    .formatted(fetched, storedRaw, skippedDuplicates, ingested, failed);
//        }
//    }
//}

//
//import org.example.jobsmvp.ingestion.deduplication.DeduplicationService;
//import org.example.jobsmvp.ingestion.extraction.EntityExtractionService;
//import org.example.jobsmvp.ingestion.extraction.ExtractedEntities;
//import org.example.jobsmvp.ingestion.graph.GraphIngestionService;
//import org.example.jobsmvp.ingestion.normalization.EntityNormalizationService;
//import org.example.jobsmvp.ingestion.preprocessing.JobPreprocessor;
//import org.example.jobsmvp.ingestion.source.JSearchApiClient;
//import org.example.jobsmvp.ingestion.source.JobSourceRegistry;
//import org.example.jobsmvp.ingestion.source.RawJobDto;
//import org.example.jobsmvp.ingestion.storage.RawJobStorageService;
//import org.example.jobsmvp.ingestion.transform.GraphTransformService;
//import org.example.jobsmvp.ingestion.transform.JobGraphBundle;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
///**
// * Entry point for the job ingestion pipeline.
// *
// * Fully synchronous — no WebFlux, no reactive types.
// *
// * The orchestrator depends only on {@link JobSourceRegistry} and has no
// * knowledge of individual sources. Adding or removing a source requires
// * no changes here.
// *
// * Execution order per job:
// *  1. Collect jobs from all registered sources via JobSourceRegistry
// *  2. Deduplication check  → skip if already in graph
// *  3. Store raw JSON       → raw-data/{jobId}.json
// *  4. Preprocess text      → strip HTML, normalise
// *  5. Extract entities     → LLM → ExtractedEntities
// *  6. Transform            → graph nodes/edges (normalisation inside)
// *  7. Persist              → MERGE into Neo4j
// *
// * Triggers:
// *  - @Scheduled cron  (configurable via ingestion.cron)
// *  - POST /api/ingestion/run
// */
//@Service
//public class IngestionPipelineOrchestrator {
//
//    private static final Logger log = LoggerFactory.getLogger(IngestionPipelineOrchestrator.class);
//
//    private final JobSourceRegistry sourceRegistry;
//    private final RawJobStorageService storageService;
//    private final DeduplicationService deduplicationService;
//    private final JobPreprocessor preprocessor;
//    private final EntityExtractionService extractionService;
//    private final GraphTransformService transformService;
//    private final GraphIngestionService ingestionService;
//    private final EntityNormalizationService normalizationService;
//    private final JSearchApiClient apiClient;
//
//    public IngestionPipelineOrchestrator(
//            JobSourceRegistry sourceRegistry,
//            RawJobStorageService storageService,
//            DeduplicationService deduplicationService,
//            JobPreprocessor preprocessor,
//            EntityExtractionService extractionService,
//            GraphTransformService transformService,
//            GraphIngestionService ingestionService,
//            EntityNormalizationService normalizationService,
//            JSearchApiClient apiClient
//    ) {
//        this.sourceRegistry       = sourceRegistry;
//        this.storageService       = storageService;
//        this.deduplicationService = deduplicationService;
//        this.preprocessor         = preprocessor;
//        this.extractionService    = extractionService;
//        this.transformService     = transformService;
//        this.ingestionService     = ingestionService;
//        this.normalizationService = normalizationService;
//        this.apiClient = apiClient;
//    }
//
//    // ── Scheduled run ─────────────────────────────────────────────────────────
//
//    @Scheduled(cron = "${ingestion.cron:0 0 2 * * ?}")
//    public PipelineResult runScheduled() {
//        log.info("=== Ingestion pipeline starting (scheduled) ===");
//        return run(null, -1);
//    }
//
//    // ── Manual run ────────────────────────────────────────────────────────────
//
//    /**
//     * Runs the pipeline synchronously across all registered sources.
//     *
//     * @param query   search query forwarded to every source; null → each source uses its own default
//     * @param maxJobs stop after processing this many new jobs total; -1 = no limit
//     */
//    public PipelineResult run(String query, int maxJobs) {
//        normalizationService.clearCache();
//
////        List<RawJobDto> jobs = query != null
////                ? sourceRegistry.fetchAll(query)
////                : sourceRegistry.fetchAll();
//
//        List<RawJobDto> jobs = query != null
//                ? apiClient.fetchJobsFromRandomFile(3)
//                : apiClient.fetchJobsFromFile("jwLMxhYLldcDdFY7AAAAAA==");
//
//        int fetched  = jobs.size();
//        int stored   = 0;
//        int skipped  = 0;
//        int ingested = 0;
//        int failed   = 0;
//
//        for (RawJobDto job : jobs) {
//            if (maxJobs >= 0 && ingested >= maxJobs) {
//                log.info("Reached maxJobs={} limit, stopping.", maxJobs);
//                break;
//            }
//
//            if (deduplicationService.isDuplicate(job)) {
//                skipped++;
//                continue;
//            }
//
//            if (storageService.save(job)) {
//                stored++;
//            }
//
//            if (processJob(job)) {
//                ingested++;
//            } else {
//                failed++;
//            }
//        }
//
//        PipelineResult result = new PipelineResult(fetched, stored, skipped, ingested, failed);
//        log.info("=== Pipeline complete: {} ===", result);
//        return result;
//    }
//
//    // ── Per-job processing ────────────────────────────────────────────────────
//
//    private boolean processJob(RawJobDto job) {
//        try {
//            String cleanedDesc = preprocessor.cleanDescription(job);
//
//            ExtractedEntities entities = extractionService.extract(
//                    job.employerName(), job.jobTitle(), cleanedDesc);
//
//            JobGraphBundle bundle = transformService.transform(job, entities, cleanedDesc);
//
//            ingestionService.ingest(bundle);
//
//            log.debug("Processed jobId={} title='{}'", job.jobId(), bundle.job().getTitle());
//            return true;
//
//        } catch (Exception e) {
//            log.error("Failed to process jobId={}: {}", job.jobId(), e.getMessage(), e);
//            return false;
//        }
//    }
//
//    // ── Result DTO ────────────────────────────────────────────────────────────
//
//    public record PipelineResult(
//            int fetched,
//            int storedRaw,
//            int skippedDuplicates,
//            int ingested,
//            int failed
//    ) {
//        @Override
//        public String toString() {
//            return "fetched=%d stored=%d skipped=%d ingested=%d failed=%d"
//                    .formatted(fetched, storedRaw, skippedDuplicates, ingested, failed);
//        }
//    }
//}


// last good version
//
//import org.example.jobsmvp.ingestion.deduplication.DeduplicationService;
//import org.example.jobsmvp.ingestion.extraction.EntityExtractionService;
//import org.example.jobsmvp.ingestion.extraction.ExtractedEntities;
//import org.example.jobsmvp.ingestion.graph.GraphIngestionService;
//import org.example.jobsmvp.ingestion.normalization.EntityNormalizationService;
//import org.example.jobsmvp.ingestion.normalization.OccupationNormalizationService;
//import org.example.jobsmvp.ingestion.preprocessing.JobPreprocessor;
//import org.example.jobsmvp.ingestion.source.JSearchApiClient;
//import org.example.jobsmvp.ingestion.source.JobSourceRegistry;
//import org.example.jobsmvp.ingestion.source.RawJobDto;
//import org.example.jobsmvp.ingestion.storage.RawJobStorageService;
//import org.example.jobsmvp.ingestion.transform.GraphTransformService;
//import org.example.jobsmvp.ingestion.transform.JobGraphBundle;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
///**
// * Entry point for the job ingestion pipeline.
// *
// * Fully synchronous — no WebFlux, no reactive types.
// *
// * Execution order per job:
// *  1. Collect jobs from all registered sources via JobSourceRegistry
// *  2. Deduplication check  → skip if already in graph
// *  3. Store raw JSON       → raw-data/{jobId}.json
// *  4. Preprocess text      → strip HTML, normalise
// *  5. Extract entities     → LLM → ExtractedEntities
// *  6. Transform            → graph nodes/edges (skill + occupation normalisation inside)
// *  7. Persist              → MERGE into Neo4j
// *
// * Post-run:
// *  - {@link OccupationNormalizationService#flushAndReset()} writes any unmatched
// *    occupations to a dated JSON file and resets per-run state.
// *  - {@link EntityNormalizationService#clearCache()} resets the skill cache.
// */
//@Service
//public class IngestionPipelineOrchestrator {
//
//    private static final Logger log = LoggerFactory.getLogger(IngestionPipelineOrchestrator.class);
//
//    private final JobSourceRegistry              sourceRegistry;
//    private final RawJobStorageService           storageService;
//    private final DeduplicationService           deduplicationService;
//    private final JobPreprocessor                preprocessor;
//    private final EntityExtractionService        extractionService;
//    private final GraphTransformService          transformService;
//    private final GraphIngestionService          ingestionService;
//    private final EntityNormalizationService     normalizationService;
//    private final OccupationNormalizationService occupationNormalizationService;
//    private final JSearchApiClient apiClient;
//
//    public IngestionPipelineOrchestrator(
//            JobSourceRegistry              sourceRegistry,
//            RawJobStorageService           storageService,
//            DeduplicationService           deduplicationService,
//            JobPreprocessor                preprocessor,
//            EntityExtractionService        extractionService,
//            GraphTransformService          transformService,
//            GraphIngestionService          ingestionService,
//            EntityNormalizationService     normalizationService,
//            OccupationNormalizationService occupationNormalizationService,
//            JSearchApiClient apiClient
//    ) {
//        this.sourceRegistry                = sourceRegistry;
//        this.storageService                = storageService;
//        this.deduplicationService          = deduplicationService;
//        this.preprocessor                  = preprocessor;
//        this.extractionService             = extractionService;
//        this.transformService              = transformService;
//        this.ingestionService              = ingestionService;
//        this.normalizationService          = normalizationService;
//        this.occupationNormalizationService = occupationNormalizationService;
//        this.apiClient = apiClient;
//    }
//
//    // ── Scheduled run ─────────────────────────────────────────────────────────
//
//    @Scheduled(cron = "${ingestion.cron:0 0 2 * * ?}")
//    public PipelineResult runScheduled() {
//        log.info("=== Ingestion pipeline starting (scheduled) ===");
//        return run(null, -1);
//    }
//
//    // ── Manual run ────────────────────────────────────────────────────────────
//
//    /**
//     * Runs the pipeline synchronously across all registered sources.
//     *
//     * @param query   search query forwarded to every source; null → each source uses its own default
//     * @param maxJobs stop after processing this many new jobs total; -1 = no limit
//     */
//    public PipelineResult run(String query, int maxJobs) {
//        // Reset per-run caches before starting
//        normalizationService.clearCache();
//
////        List<RawJobDto> jobs = query != null
////                ? sourceRegistry.fetchAll(query)
////                : sourceRegistry.fetchAll();
//        List<RawJobDto> jobs = query != null
//                ? apiClient.fetchJobsFromRandomFile(10)
//                : apiClient.fetchJobsFromFile("jwLMxhYLldcDdFY7AAAAAA==");
//
//        int fetched  = jobs.size();
//        int stored   = 0;
//        int skipped  = 0;
//        int ingested = 0;
//        int failed   = 0;
//
//        for (RawJobDto job : jobs) {
//            if (maxJobs >= 0 && ingested >= maxJobs) {
//                log.info("Reached maxJobs={} limit, stopping.", maxJobs);
//                break;
//            }
//
//            if (deduplicationService.isDuplicate(job)) {
//                skipped++;
//                continue;
//            }
//
//            if (storageService.save(job)) stored++;
//
//            if (processJob(job)) ingested++;
//            else                 failed++;
//        }
//
//        // Flush unmatched occupations to JSON and reset occupation service state
//        occupationNormalizationService.flushAndReset();
//
//        PipelineResult result = new PipelineResult(fetched, stored, skipped, ingested, failed);
//        log.info("=== Pipeline complete: {} ===", result);
//        return result;
//    }
//
//    // ── Per-job processing ────────────────────────────────────────────────────
//
//    private boolean processJob(RawJobDto job) {
//        try {
//            String cleanedDesc = preprocessor.cleanDescription(job);
//
//            ExtractedEntities entities = extractionService.extract(
//                    job.employerName(), job.jobTitle(), cleanedDesc);
//
//            if (entities == null || (entities.technicalSkills().isEmpty() && entities.softSkills().isEmpty())) {
//                log.warn("LLM failed to extract entities or extracted 0 skills for jobId={}, skipping DB ingestion.", job.jobId());
//                return false;
//            }
//
//            JobGraphBundle bundle = transformService.transform(job, entities, cleanedDesc);
//
//            ingestionService.ingest(bundle);
//
//            log.debug("Processed jobId={} title='{}'", job.jobId(), bundle.job().getTitle());
//            return true;
//
//        } catch (Exception e) {
//            log.error("Failed to process jobId={}: {}", job.jobId(), e.getMessage(), e);
//            return false;
//        }
//    }
//
//    // ── Result DTO ────────────────────────────────────────────────────────────
//
//    public record PipelineResult(
//            int fetched,
//            int storedRaw,
//            int skippedDuplicates,
//            int ingested,
//            int failed
//    ) {
//        @Override
//        public String toString() {
//            return "fetched=%d stored=%d skipped=%d ingested=%d failed=%d"
//                    .formatted(fetched, storedRaw, skippedDuplicates, ingested, failed);
//        }
//    }
//}




import org.example.jobsmvp.ingestion.deduplication.DeduplicationService;
import org.example.jobsmvp.ingestion.extraction.EntityExtractionService;
import org.example.jobsmvp.ingestion.extraction.ExtractedEntities;
import org.example.jobsmvp.ingestion.graph.GraphIngestionService;
import org.example.jobsmvp.ingestion.normalization.EntityNormalizationService;
import org.example.jobsmvp.ingestion.normalization.OccupationNormalizationService;
import org.example.jobsmvp.ingestion.preprocessing.JobDescriptionReformatter;
import org.example.jobsmvp.ingestion.preprocessing.JobDescriptionReformatter.ReformatResult;
import org.example.jobsmvp.ingestion.preprocessing.JobPreprocessor;
//import org.example.jobsmvp.ingestion.source.JSearchApiClient;
import org.example.jobsmvp.ingestion.source.JSearchApiClient;
import org.example.jobsmvp.ingestion.source.JobSourceRegistry;
import org.example.jobsmvp.ingestion.source.RawJobDto;
import org.example.jobsmvp.ingestion.storage.RawJobStorageService;
import org.example.jobsmvp.ingestion.transform.GraphTransformService;
import org.example.jobsmvp.ingestion.transform.JobGraphBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Entry point for the job ingestion pipeline.
 *
 * Fully synchronous — no WebFlux, no reactive types.
 *
 * Execution order per job:
 *  1. Collect jobs from all registered sources via JobSourceRegistry
 *  2. Deduplication check       → skip if already in graph
 *  3. Store raw JSON            → raw-data/{jobId}.json
 *  4. Preprocess text           → strip HTML, normalise
 *  5. Reformat + extract blurb → LLM → clean_description + company description
 *  6. Extract entities          → LLM → ExtractedEntities
 *  7. Transform                 → graph nodes/edges (skill + occupation normalisation inside)
 *  8. Persist                   → MERGE into Neo4j
 *
 * Post-run:
 *  - {@link OccupationNormalizationService#flushAndReset()} writes any unmatched
 *    occupations to a dated JSON file and resets per-run state.
 *  - {@link EntityNormalizationService#clearCache()} resets the skill cache.
 */
@Service
public class IngestionPipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipelineOrchestrator.class);

    private final JobSourceRegistry              sourceRegistry;
    private final RawJobStorageService           storageService;
    private final DeduplicationService           deduplicationService;
    private final JobPreprocessor                preprocessor;
    private final JobDescriptionReformatter      descriptionReformatter;
    private final EntityExtractionService        extractionService;
    private final GraphTransformService          transformService;
    private final GraphIngestionService          ingestionService;
    private final EntityNormalizationService     normalizationService;
    private final OccupationNormalizationService occupationNormalizationService;
    private final JSearchApiClient apiClient;

    public IngestionPipelineOrchestrator(
            JobSourceRegistry              sourceRegistry,
            RawJobStorageService           storageService,
            DeduplicationService           deduplicationService,
            JobPreprocessor                preprocessor,
            JobDescriptionReformatter      descriptionReformatter,
            EntityExtractionService        extractionService,
            GraphTransformService          transformService,
            GraphIngestionService          ingestionService,
            EntityNormalizationService     normalizationService,
            OccupationNormalizationService occupationNormalizationService,
            JSearchApiClient               apiClient
    ) {
        this.sourceRegistry                = sourceRegistry;
        this.storageService                = storageService;
        this.deduplicationService          = deduplicationService;
        this.preprocessor                  = preprocessor;
        this.descriptionReformatter        = descriptionReformatter;
        this.extractionService             = extractionService;
        this.transformService              = transformService;
        this.ingestionService              = ingestionService;
        this.normalizationService          = normalizationService;
        this.occupationNormalizationService = occupationNormalizationService;
        this.apiClient                     = apiClient;
    }

    // ── Scheduled run ─────────────────────────────────────────────────────────

    @Scheduled(cron = "${ingestion.cron:0 0 2 * * ?}")
    public PipelineResult runScheduled() {
        log.info("=== Ingestion pipeline starting (scheduled) ===");
        return run(null, -1);
    }

    // ── Manual run ────────────────────────────────────────────────────────────

    /**
     * Runs the pipeline synchronously across all registered sources.
     *
     * @param query   search query forwarded to every source; null → each source uses its own default
     * @param maxJobs stop after processing this many new jobs total; -1 = no limit
     */
    public PipelineResult run(String query, int maxJobs) {
        normalizationService.clearCache();

        List<RawJobDto> jobs = query != null
                ? sourceRegistry.fetchAll(query)
                : sourceRegistry.fetchAll();

//        List<RawJobDto> jobs = query != null
//                ? apiClient.fetchJobsFromRandomFile(30)
//                : apiClient.fetchJobsFromFile("jwLMxhYLldcDdFY7AAAAAA==");

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

            if (storageService.save(job)) stored++;

            if (processJob(job)) ingested++;
            else                 failed++;
        }

        occupationNormalizationService.flushAndReset();

        PipelineResult result = new PipelineResult(fetched, stored, skipped, ingested, failed);
        log.info("=== Pipeline complete: {} ===", result);
        return result;
    }

    // ── Per-job processing ────────────────────────────────────────────────────

    /**
     * Runs a single job through the full pipeline.
     *
     * Step 4 produces a noise-free {@code preprocessedDesc} (HTML stripped, collapsed
     * whitespace) that is fed into the LLM steps.
     *
     * Step 5 uses the LLM to:
     *   - produce {@code cleanDescription} (signal-only, stored as {@code clean_description} on Job)
     *   - extract {@code companyDescription} (stored as {@code description} on Company)
     *
     * Step 6 runs entity extraction using the same {@code preprocessedDesc} as context
     * (entity extraction does not benefit from the reformatted text and runs in parallel
     * conceptually — keeping them on the raw preprocessed input avoids chaining LLM errors).
     *
     * @return true on success, false if an unrecoverable exception is thrown
     */
    private boolean processJob(RawJobDto job) {
        try {
            // Step 4 – lightweight text preprocessing (HTML strip, whitespace collapse)
            String preprocessedDesc = preprocessor.cleanDescription(job);

            // Step 5 – LLM reformat: clean_description + company description (one call)
            ReformatResult reformat = descriptionReformatter.reformat(
                    job.employerName(), job.jobTitle(), preprocessedDesc);

            // Step 6 – LLM entity extraction (skills, occupation, salary, …)
            ExtractedEntities entities = extractionService.extract(
                    job.employerName(), job.jobTitle(), preprocessedDesc);

            if (entities == null || (entities.technicalSkills().isEmpty() && entities.softSkills().isEmpty())) {
                log.warn("LLM extracted 0 skills for jobId={}, skipping DB ingestion.", job.jobId());
                return false;
            }

            // Step 7 – build graph bundle
            //   reformat.cleanDescription() → stored as clean_description on Job
            //   reformat.companyDescription() → stored as description on Company
            JobGraphBundle bundle = transformService.transform(
                    job, entities, reformat.cleanDescription(), reformat.companyDescription());

            // Step 8 – persist
            ingestionService.ingest(bundle);

            log.debug("Processed jobId={} title='{}'", job.jobId(), bundle.job().getTitle());
            return true;

        } catch (Exception e) {
            log.error("Failed to process jobId={}: {}", job.jobId(), e.getMessage(), e);
            return false;
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