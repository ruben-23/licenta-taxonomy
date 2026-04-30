package org.example.jobsmvp.ingestion.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Collects every {@link JobSource} bean registered in the Spring context
 * and provides a single fan-out fetch method used by the orchestrator.
 *
 * The orchestrator depends only on this registry — it has zero knowledge of
 * individual source implementations. Adding or removing a source requires
 * no changes outside the source's own class.
 */
@Component
public class JobSourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(JobSourceRegistry.class);

    private final List<JobSource> sources;

    /**
     * Spring injects all beans that implement {@link JobSource} here automatically.
     */
    public JobSourceRegistry(List<JobSource> sources) {
        this.sources = sources;
        Set<String> names = sources.stream()
                .map(JobSource::sourceName)
                .collect(Collectors.toSet());
        log.info("JobSourceRegistry initialised with {} source(s): {}", sources.size(), names);
    }

    /**
     * Calls {@link JobSource#fetchJobs()} on every registered source and
     * returns the combined flat list of all results.
     *
     * A failure in one source is logged and skipped; other sources continue.
     *
     * @return combined jobs from all sources
     */
    public List<RawJobDto> fetchAll() {
        List<RawJobDto> all = new ArrayList<>();
        for (JobSource source : sources) {
            try {
                List<RawJobDto> results = source.fetchJobs();
                log.info("Source '{}' returned {} jobs", source.sourceName(), results.size());
                all.addAll(results);
            } catch (Exception e) {
                log.error("Source '{}' failed: {}", source.sourceName(), e.getMessage(), e);
            }
        }
        return all;
    }

    /**
     * Calls {@link JobSource#fetchJobs(String)} on every registered source
     * for the given query and returns the combined flat list.
     *
     * @param query free-text search term forwarded to each source
     * @return combined jobs from all sources
     */
    public List<RawJobDto> fetchAll(String query) {
        List<RawJobDto> all = new ArrayList<>();
        for (JobSource source : sources) {
            try {
                List<RawJobDto> results = source.fetchJobs(query);
                log.info("Source '{}' returned {} jobs for query '{}'",
                        source.sourceName(), results.size(), query);
                all.addAll(results);
            } catch (Exception e) {
                log.error("Source '{}' failed for query '{}': {}",
                        source.sourceName(), query, e.getMessage(), e);
            }
        }
        return all;
    }

    /**
     * Returns the names of all currently registered sources.
     * Useful for the /api/ingestion/status endpoint.
     */
    public List<String> registeredSourceNames() {
        return sources.stream().map(JobSource::sourceName).toList();
    }
}
