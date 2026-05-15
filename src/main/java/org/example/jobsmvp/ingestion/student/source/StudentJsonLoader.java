package org.example.jobsmvp.ingestion.student.source;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Loads raw student records from a JSON file on disk.
 *
 * The path is configurable via {@code ingestion.students.file-path} (application.yml).
 * If the property is absent, the loader falls back to {@code student_data.json} on the
 * classpath so it works out-of-the-box in test environments.
 *
 * The file must be a JSON array of objects matching the {@link RawStudentDto} schema.
 */
@Component
public class StudentJsonLoader {

    private static final Logger log = LoggerFactory.getLogger(StudentJsonLoader.class);

    private final ObjectMapper objectMapper;
    private final String       filePath;

    public StudentJsonLoader(
            ObjectMapper objectMapper,
            @Value("${ingestion.students.file-path:student_data.json}") String filePath
    ) {
        this.objectMapper = objectMapper;
        this.filePath     = filePath;
    }

    /**
     * Reads the configured JSON file and returns all student records.
     *
     * Tries the path as an absolute/relative filesystem path first.
     * Falls back to classpath resource lookup when the file is not found on disk.
     *
     * @return list of raw student DTOs; never null, may be empty on error
     */
    public List<RawStudentDto> loadAll() {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                log.info("Loading students from filesystem: {}", path.toAbsolutePath());
                return objectMapper.readValue(
                        path.toFile(),
                        new TypeReference<>() {}
                );
            }

            // Classpath fallback
            log.info("File not found on disk, trying classpath: {}", filePath);
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(filePath)) {
                if (is == null) {
                    log.error("Student data file not found on classpath either: {}", filePath);
                    return List.of();
                }
                return objectMapper.readValue(is, new TypeReference<>() {});
            }

        } catch (IOException e) {
            log.error("Failed to load student data from '{}': {}", filePath, e.getMessage(), e);
            return List.of();
        }
    }
}