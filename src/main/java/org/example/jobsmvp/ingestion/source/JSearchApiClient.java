package org.example.jobsmvp.ingestion.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import org.example.jobsmvp.ingestion.source.RawJobDto;
import org.example.jobsmvp.ingestion.storage.RawJobStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Synchronous wrapper around the JSearch RapidAPI endpoint.
 *
 * Uses Spring 6's {@link RestClient} (blocking, no WebFlux dependency).
 * Paginates through the configured number of pages and returns a flat list.
 */
@Component
public class JSearchApiClient {

    private static final Logger log = LoggerFactory.getLogger(JSearchApiClient.class);

    private static final String BASE_URL     = "https://jsearch.p.rapidapi.com";
    private static final String RAPIDAPI_HOST = "jsearch.p.rapidapi.com";

    private final RestClient restClient;
    private final RawJobStorageService storageService;

    @Value("${jsearch.api-key}")
    private String apiKey;

    @Value("${jsearch.query:developer}")
    private String defaultQuery;

    @Value("${jsearch.pages:3}")
    private int pagesToFetch;

    public JSearchApiClient(RestClient restClient, RawJobStorageService rawJobStorageService) {
        this.restClient = restClient;
        this.storageService = rawJobStorageService;
    }

    /**
     * Fetches jobs for the default configured query across all configured pages.
     *
     * @return list of raw job DTOs (never null, may be empty)
     */
    public List<RawJobDto> fetchJobs() {
        return fetchJobs(defaultQuery, pagesToFetch);
    }

    /**
     * Fetches jobs for the given query across {@code numPages} pages.
     *
     * @param query    search term
     * @param numPages number of pages to retrieve (1-indexed)
     * @return flat list of raw job DTOs
     */
    public List<RawJobDto> fetchJobs(String query, int numPages) {
        List<RawJobDto> results = new ArrayList<>();

        for (int page = 1; page <= numPages; page++) {
            List<RawJobDto> pageResults = fetchPage(query, page);
            results.addAll(pageResults);
            log.info("Fetched page {}/{} for query '{}' — {} jobs (total so far: {})",
                    page, numPages, query, pageResults.size(), results.size());
        }

        return Collections.unmodifiableList(results);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private List<RawJobDto> fetchPage(String query, int page) {
        try {
            JSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("query", query)
                            .queryParam("page", page)
                            .queryParam("num_pages", 1)
                            .queryParam("date_posted", "all")
                            .build())
                    .header("x-rapidapi-host", RAPIDAPI_HOST)
                    .header("x-rapidapi-key", apiKey)
                    .retrieve()
                    .body(JSearchResponse.class);

            if (response == null || response.data() == null) {
                log.warn("Empty response for query='{}' page={}", query, page);
                return List.of();
            }

            return response.data();

        } catch (RestClientException e) {
            log.error("HTTP error fetching query='{}' page={}: {}", query, page, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("Unexpected error fetching query='{}' page={}: {}", query, page, e.getMessage());
            return List.of();
        }
    }

    /**
     * MOCK: Returns a list containing a single specific job from disk.
     * * @param jobId the filename (without .json) to load
     * @return List containing the job, or empty list if not found
     */
    public List<RawJobDto> fetchJobsFromFile(String jobId) {
        log.info("MOCK: Loading specific job file: {}.json", jobId);
        return storageService.load(jobId)
                .map(List::of)
                .orElseGet(() -> {
                    log.error("MOCK: File {}.json not found!", jobId);
                    return List.of();
                });
    }



    /**
     * Internal helper to pick N random files from the raw-data folder.
     */
    public List<RawJobDto> fetchJobsFromRandomFile(int count) {
        try {
            List<String> allIds = storageService.listStoredJobIds().collect(Collectors.toList());

            if (allIds.isEmpty()) {
                log.warn("MOCK: No files found in raw-data folder.");
                return List.of();
            }

            Collections.shuffle(allIds);

            return allIds.stream()
                    .limit(count)
                    .map(storageService::load)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();

        } catch (IOException e) {
            log.error("MOCK: Failed to list local files", e);
            return List.of();
        }
    }

    // ── Response wrapper ─────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record JSearchResponse(
            @JsonProperty("data")   List<RawJobDto> data,
            @JsonProperty("status") String status
    ) {}
}