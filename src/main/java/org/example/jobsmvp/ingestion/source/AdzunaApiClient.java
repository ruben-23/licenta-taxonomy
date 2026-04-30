package org.example.jobsmvp.ingestion.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

/**
 * {@link JobSource} implementation for the Adzuna Jobs API.
 * https://developer.adzuna.com/
 *
 * Disabled by default — enable by setting:
 *   ingestion.sources.adzuna.enabled=true
 *
 * This class exists to demonstrate the pattern for adding a new source.
 * The orchestrator and registry require zero changes to pick this up.
 */
@Component
@ConditionalOnProperty(
        name        = "ingestion.sources.adzuna.enabled",
        havingValue = "true",
        matchIfMissing = false  // off by default
)
public class AdzunaApiClient implements JobSource {

    private static final Logger log = LoggerFactory.getLogger(AdzunaApiClient.class);

    private static final String BASE_URL = "https://api.adzuna.com/v1/api/jobs";

    private final RestClient restClient;

    @Value("${ingestion.sources.adzuna.app-id}")
    private String appId;

    @Value("${ingestion.sources.adzuna.api-key}")
    private String apiKey;

    @Value("${ingestion.sources.adzuna.country:gb}")
    private String country;

    @Value("${ingestion.sources.adzuna.default-query:software engineer}")
    private String defaultQuery;

    @Value("${ingestion.sources.adzuna.pages:2}")
    private int pagesToFetch;

    public AdzunaApiClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String sourceName() {
        return "adzuna";
    }

    @Override
    public List<RawJobDto> fetchJobs() {
        return fetchJobs(defaultQuery);
    }

    @Override
    public List<RawJobDto> fetchJobs(String query) {
        java.util.List<RawJobDto> results = new java.util.ArrayList<>();
        for (int page = 1; page <= pagesToFetch; page++) {
            results.addAll(fetchPage(query, page));
        }
        return results;
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private List<RawJobDto> fetchPage(String query, int page) {
        try {
            AdzunaResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{country}/search/{page}")
                            .queryParam("app_id",  appId)
                            .queryParam("app_key", apiKey)
                            .queryParam("what",    query)
                            .queryParam("results_per_page", 20)
                            .build(country, page))
                    .retrieve()
                    .body(AdzunaResponse.class);

            if (response == null || response.results() == null) return List.of();

            // Map Adzuna's response shape to the shared RawJobDto
            return response.results().stream()
                    .map(this::toRawJobDto)
                    .toList();

        } catch (RestClientException e) {
            log.error("[adzuna] HTTP error page={}: {}", page, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("[adzuna] unexpected error page={}: {}", page, e.getMessage());
            return List.of();
        }
    }

    private RawJobDto toRawJobDto(AdzunaJob job) {
        return new RawJobDto(
                job.id() != null ? job.id() : UUID.randomUUID().toString(),
                job.company() != null ? job.company().displayName() : null,
                null,                               // no employer website in Adzuna
                job.title(),
                job.description(),
                "FULLTIME",                         // Adzuna does not expose contract type
                job.location() != null ? job.location().displayName() : null,
                country.toUpperCase(),
                null,                               // remote flag not available
                job.created(),
                null,                               // no expiry field
                job.salaryMin() != null ? job.salaryMin().intValue() : null,
                job.salaryMax() != null ? job.salaryMax().intValue() : null,
                "GBP",                              // default; adjust per country config
                null
        );
    }

    // ── Adzuna response DTOs ──────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AdzunaResponse(
            @JsonProperty("results") List<AdzunaJob> results,
            @JsonProperty("count")   Integer count
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AdzunaJob(
            @JsonProperty("id")          String id,
            @JsonProperty("title")       String title,
            @JsonProperty("description") String description,
            @JsonProperty("created")     String created,
            @JsonProperty("salary_min")  Double salaryMin,
            @JsonProperty("salary_max")  Double salaryMax,
            @JsonProperty("company")     AdzunaCompany company,
            @JsonProperty("location")    AdzunaLocation location
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AdzunaCompany(
            @JsonProperty("display_name") String displayName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AdzunaLocation(
            @JsonProperty("display_name") String displayName
    ) {}
}
