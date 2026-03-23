package org.example.jobsmvp.ingestion.deduplication;

import org.example.jobsmvp.ingestion.source.RawJobDto;
import org.example.jobsmvp.repositories.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Guards against re-ingesting job postings already present in the graph.
 *
 * Hash strategy: SHA-256 over (jobId + title.lowercase + company.lowercase)
 *
 * Two checks are performed:
 *  1. Primary: the job_id returned by the API (JSearch uses stable IDs).
 *  2. Fallback: content hash for jobs whose IDs may have changed (re-posted roles).
 */
@Service
public class DeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(DeduplicationService.class);

    private final JobRepository jobRepository;

    public DeduplicationService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
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

        return false;
    }

    /**
     * Computes a stable fingerprint for a raw job based on identifying fields.
     */
    public String contentHash(RawJobDto job) {
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
