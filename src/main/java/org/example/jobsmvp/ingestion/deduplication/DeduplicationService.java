package org.example.jobsmvp.ingestion.deduplication;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.example.jobsmvp.ingestion.source.RawJobDto;
import org.example.jobsmvp.repositories.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Guards against re-ingesting job postings already present in the graph.
 *
 * Hash strategy: SHA-256 over (jobId + title.lowercase + company.lowercase)
 *
 * Two checks are performed:
 *  1. Primary: the job_id returned by the API (JSearch uses stable IDs).
 *  2. Fallback: content hash for jobs whose IDs may have changed (re-posted roles).
 *  3. Fallback: vector similarity search on job title + description embedding.
 */
@Service
public class DeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(DeduplicationService.class);
    private static final double SIMILARITY_THRESHOLD = 0.98;

    private final JobRepository jobRepository;
    private final EmbeddingModel embeddingModel;

    public DeduplicationService(JobRepository jobRepository, EmbeddingModel embeddingModel) {
        this.jobRepository = jobRepository;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Returns true if this job should be skipped (already in the graph).
     */
    public boolean isDuplicate(RawJobDto job) {
        // 1. Check by stable job_id
        if (job.jobId() != null && jobRepository.existsByJobId(job.jobId())) {
            log.debug("Duplicate by job_id={}", job.jobId());
            return true;
        }

        // 2. Check by content hash
        String hash = contentHash(job);
        if (jobRepository.existsByContentHash(hash)) {
            log.debug("Duplicate by content hash, job_id={}", job.jobId());
            return true;
        }

        // 3. Check by embedding similarity
        String embedInput = job.jobTitle() + " " + job.jobDescription();
        Embedding embedding = embeddingModel.embed(embedInput).content();
        List<Double> embeddingVector = embedding.vectorAsList().stream().map(Float::doubleValue).toList();
        if (jobRepository.existsSimilarJobByEmbedding(embeddingVector, SIMILARITY_THRESHOLD)) {
            log.debug("Duplicate by embedding similarity, job_id={}", job.jobId());
            return true;
        }

        return false;
    }

    /**
     * Computes a stable fingerprint for a raw job based on identifying fields.
     */
    public static String contentHash(RawJobDto job) {
        String input = String.join("|",
                nullSafe(job.jobId()),
                nullSafe(job.jobTitle()).toLowerCase(),
                nullSafe(job.employerName()).toLowerCase()
        );
        return sha256Hex(input);
    }

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