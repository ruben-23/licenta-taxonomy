package org.example.jobsmvp.ingestion.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LongSummaryStatistics;

@Service
public class PerformanceLogger {

    private static final Logger log = LoggerFactory.getLogger(PerformanceLogger.class);
    private static final String LOG_DIRECTORY = "performance-logs";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private BufferedWriter writer;

    public void startRun() {
        try {
            Path logDir = Paths.get(LOG_DIRECTORY);
            Files.createDirectories(logDir);
            String timestamp = LocalDateTime.now().format(formatter);
            Path logFile = logDir.resolve("job-ingestion-" + timestamp + ".txt");
            this.writer = Files.newBufferedWriter(logFile);
            writer.write("Job Name, Job ID, Processing Time (ms)\n");
        } catch (IOException e) {
            log.error("Failed to initialize performance logger.", e);
            this.writer = null;
        }
    }

    public void logJob(String jobName, String jobId, long processingTime) {
        if (writer == null) return;
        try {
            writer.write(String.format("\"%s\", %s, %d\n", jobName, jobId, processingTime));
        } catch (IOException e) {
            log.error("Failed to log performance for job ID: {}", jobId, e);
        }
    }

    public void endRun(List<Long> processingTimes) {
        if (writer == null) return;
        try {
            if (processingTimes.isEmpty()) {
                writer.write("\n--- RUN SUMMARY ---\n");
                writer.write("No jobs were successfully ingested in this run.\n");
                return;
            }

            LongSummaryStatistics stats = processingTimes.stream()
                    .mapToLong(Long::longValue)
                    .summaryStatistics();

            writer.write("\n--- RUN SUMMARY ---\n");
            writer.write(String.format("Total Jobs Ingested: %d\n", stats.getCount()));
            writer.write(String.format("Total Processing Time: %.2f s\n", stats.getSum() / 1000.0));
            writer.write(String.format("Minimum Processing Time: %d ms\n", stats.getMin()));
            writer.write(String.format("Maximum Processing Time: %d ms\n", stats.getMax()));
            writer.write(String.format("Average Processing Time: %.2f ms\n", stats.getAverage()));

        } catch (IOException e) {
            log.error("Failed to write performance summary.", e);
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
                log.error("Failed to close performance logger.", e);
            }
        }
    }
}