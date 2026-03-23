package org.example.jobsmvp.ingestion.storage;

import org.example.jobsmvp.ingestion.source.RawJobDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Persists raw job DTOs to disk under raw-data/ so the original API payload
 * is preserved for re-processing without hitting the API again.
 *
 * File layout: raw-data/{jobId}.json
 */
@Service
public class RawJobStorageService {

    private static final Logger log = LoggerFactory.getLogger(RawJobStorageService.class);

    private final Path storageRoot;
    private final ObjectMapper objectMapper;

    public RawJobStorageService(
            @Value("${ingestion.raw-data-path:raw-data}") String rawDataPath,
            ObjectMapper objectMapper
    ) throws IOException {
        this.storageRoot = Paths.get(rawDataPath);
        this.objectMapper = objectMapper;
        Files.createDirectories(storageRoot);
        log.info("Raw job storage initialised at {}", storageRoot.toAbsolutePath());
    }

    /**
     * Saves the raw job to disk.
     * Skips if a file for this jobId already exists (idempotent).
     *
     * @param job raw job DTO
     * @return true if written, false if already existed
     */
    public boolean save(RawJobDto job) {
        if (job.jobId() == null || job.jobId().isBlank()) {
            log.warn("Skipping job with null/empty jobId");
            return false;
        }

        Path target = storageRoot.resolve(job.jobId() + ".json");

        if (Files.exists(target)) {
            log.debug("Raw file already exists for jobId={}", job.jobId());
            return false;
        }

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), job);
            log.debug("Saved raw job jobId={}", job.jobId());
            return true;
        } catch (IOException e) {
            log.error("Failed to write raw job jobId={}: {}", job.jobId(), e.getMessage());
            return false;
        }
    }

    /**
     * Reads a previously stored raw job from disk.
     *
     * @param jobId the job identifier
     * @return the deserialized DTO, or empty if not found
     */
    public Optional<RawJobDto> load(String jobId) {
        Path target = storageRoot.resolve(jobId + ".json");
        if (!Files.exists(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(target.toFile(), RawJobDto.class));
        } catch (IOException e) {
            log.error("Failed to read raw job jobId={}: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lists all stored job IDs (file names without extension).
     */
    public java.util.stream.Stream<String> listStoredJobIds() throws IOException {
        return Files.list(storageRoot)
                .filter(p -> p.toString().endsWith(".json"))
                .map(p -> p.getFileName().toString().replace(".json", ""));
    }
}
