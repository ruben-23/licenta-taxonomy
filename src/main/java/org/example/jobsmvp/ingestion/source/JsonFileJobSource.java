package org.example.jobsmvp.ingestion.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
@ConditionalOnProperty(
        name    = "ingestion.sources.json-file.enabled",
        havingValue = "true",
        matchIfMissing = true   // on by default
)
public class JsonFileJobSource implements JobSource {

    private static final String SOURCE_NAME = "json-file";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<RawJobDto> fetchJobs() {
        return loadJobsFromFile();
    }

    @Override
    public List<RawJobDto> fetchJobs(String query) {
        // For file-based source, we ignore the query and return all jobs.
        return loadJobsFromFile();
    }

    private List<RawJobDto> loadJobsFromFile() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("exported-data/jobs.json")) {
            if (inputStream == null) {
                // Log this, but for now, return empty
                return Collections.emptyList();
            }
            JsonNode rootNode = objectMapper.readTree(inputStream);
            return StreamSupport.stream(rootNode.spliterator(), false)
                    .map(this::toRawJobDto)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            // Log this properly in a real app
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private RawJobDto toRawJobDto(JsonNode node) {
        JsonNode properties = node.get("j").get("properties");
        String location = properties.has("location") ? properties.get("location").asText(null) : null;
        String city = null;
        if (location != null && !location.isEmpty()) {
            city = location.split(",")[0];
        }

        return new RawJobDto(
                properties.has("adzunaId") ? properties.get("adzunaId").asText(null) : null,
                properties.has("companyName") ? properties.get("companyName").asText(null) : null,
                properties.has("companyWebsite") ? properties.get("companyWebsite").asText(null) : null,
                properties.has("title") ? properties.get("title").asText(null) : null,
                properties.has("description") ? properties.get("description").asText(null) : null,
                null, // employment type not present in the json
                city,
                properties.has("country") ? properties.get("country").asText(null) : null,
                null, // isRemote not present
                null, // postedAt not present
                null, // expiresAt not present
                properties.has("salaryMin") ? properties.get("salaryMin").asInt() : null, // minSalary not present
                properties.has("salaryMax") ? properties.get("salaryMax").asInt() : null, // maxSalary not present
                null, // salaryCurrency not present
                null  // requiredExperience not present
        );
    }
}
