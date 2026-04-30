package org.example.jobsmvp.ingestion.source;

import java.util.List;

/**
 * Contract for any job data source that feeds the ingestion pipeline.
 *
 * To add a new source:
 *  1. Create a class that implements this interface.
 *  2. Annotate it with @Component (or @Service).
 *  3. That's it — the {@link JobSourceRegistry} discovers it automatically via Spring.
 *
 * To disable a source without deleting it, remove @Component or add
 * @ConditionalOnProperty(name = "ingestion.sources.my-source.enabled", havingValue = "true").
 */
public interface JobSource {

    /**
     * A short, human-readable identifier for this source used in logs and metrics.
     * Must be unique across all registered sources (e.g. "jsearch", "adzuna").
     */
    String sourceName();

    /**
     * Fetches jobs using the source's own default query and pagination settings.
     *
     * @return list of raw job DTOs; never null, may be empty
     */
    List<RawJobDto> fetchJobs();

    /**
     * Fetches jobs for an explicit search query.
     * Sources that do not support arbitrary queries may ignore the parameter
     * and fall back to {@link #fetchJobs()}.
     *
     * @param query free-text search term
     * @return list of raw job DTOs; never null, may be empty
     */
    List<RawJobDto> fetchJobs(String query);
}
