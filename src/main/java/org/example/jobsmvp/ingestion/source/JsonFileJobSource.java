//
//package org.example.jobsmvp.ingestion.source;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.stereotype.Component;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.util.Collections;
//import java.util.List;
//import java.util.stream.Collectors;
//import java.util.stream.StreamSupport;
//
//@Component
//@ConditionalOnProperty(
//        name    = "ingestion.sources.json-file.enabled",
//        havingValue = "true",
//        matchIfMissing = true   // on by default
//)
//public class JsonFileJobSource implements JobSource {
//
//    private static final String SOURCE_NAME = "json-file";
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    @Override
//    public String sourceName() {
//        return SOURCE_NAME;
//    }
//
//    @Override
//    public List<RawJobDto> fetchJobs() {
//        return loadJobsFromFile();
//    }
//
//    @Override
//    public List<RawJobDto> fetchJobs(String query) {
//        // For file-based source, we ignore the query and return all jobs.
//        return loadJobsFromFile();
//    }
//
//    private List<RawJobDto> loadJobsFromFile() {
//        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("exported-data/jobs.json")) {
//            if (inputStream == null) {
//                // Log this, but for now, return empty
//                return Collections.emptyList();
//            }
//            JsonNode rootNode = objectMapper.readTree(inputStream);
//            return StreamSupport.stream(rootNode.spliterator(), false)
//                    .map(this::toRawJobDto)
//                    .collect(Collectors.toList());
//        } catch (IOException e) {
//            // Log this properly in a real app
//            e.printStackTrace();
//            return Collections.emptyList();
//        }
//    }
//
//    private RawJobDto toRawJobDto(JsonNode node) {
//        JsonNode properties = node.get("j").get("properties");
//        String location = properties.has("location") ? properties.get("location").asText(null) : null;
//        String city = null;
//        if (location != null && !location.isEmpty()) {
//            city = location.split(",")[0];
//        }
//
//        return new RawJobDto(
//                properties.has("adzunaId") ? properties.get("adzunaId").asText(null) : null,
//                properties.has("companyName") ? properties.get("companyName").asText(null) : null,
//                properties.has("companyWebsite") ? properties.get("companyWebsite").asText(null) : null,
//                properties.has("title") ? properties.get("title").asText(null) : null,
//                properties.has("description") ? properties.get("description").asText(null) : null,
//                null, // employment type not present in the json
//                city,
//                properties.has("country") ? properties.get("country").asText(null) : null,
//                null, // isRemote not present
//                null, // postedAt not present
//                null, // expiresAt not present
//                properties.has("salaryMin") ? properties.get("salaryMin").asInt() : null, // minSalary not present
//                properties.has("salaryMax") ? properties.get("salaryMax").asInt() : null, // maxSalary not present
//                null, // salaryCurrency not present
//                null  // requiredExperience not present
//        );
//    }
//}




package org.example.jobsmvp.ingestion.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
@ConditionalOnProperty(
        name    = "ingestion.sources.json-file.enabled",
        havingValue = "true",
        matchIfMissing = false   // on by default
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
        List<RawJobDto> jobs = new ArrayList<>();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("exported-data/jobs.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            if (inputStream == null) {
                return Collections.emptyList();
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    JsonNode node = objectMapper.readTree(line);
                    RawJobDto job = toRawJobDto(node);
                    if (job != null) {
                        jobs.add(job);
                    }
                } catch (IOException e) {
                    // Log error for malformed JSON lines but continue processing others
                    System.err.println("Error parsing JSON line: " + line + " - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            // Log this properly in a real app
            e.printStackTrace();
            return Collections.emptyList();
        }
        return jobs;
    }

    private RawJobDto toRawJobDto(JsonNode node) {
        if (node == null || !node.has("j") || !node.get("j").has("properties")) {
            return null; // Or throw exception depending on desired behaviour
        }

        JsonNode properties = node.get("j").get("properties");

        String location = properties.hasNonNull("location") ? properties.get("location").asText() : null;
        String city = null;
        if (location != null && !location.isEmpty()) {
            city = location.split(",")[0].trim();
        }

        String postedAt = null;
        if (properties.hasNonNull("createdAt")) {
             JsonNode createdAt = properties.get("createdAt");
             if (createdAt.hasNonNull("year") && createdAt.hasNonNull("month") && createdAt.hasNonNull("day") &&
                 createdAt.hasNonNull("hour") && createdAt.hasNonNull("minute") && createdAt.hasNonNull("second")) {
                 try {
                     LocalDateTime dateTime = LocalDateTime.of(
                             createdAt.get("year").asInt(),
                             createdAt.get("month").asInt(),
                             createdAt.get("day").asInt(),
                             createdAt.get("hour").asInt(),
                             createdAt.get("minute").asInt(),
                             createdAt.get("second").asInt()
                     );
                     // RawJobDto expects String jobPostedAt, usually ISO 8601
                     postedAt = dateTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
                 } catch (Exception e) {
                     // Ignore parsing errors and leave as null
                 }
             }
        }


        return new RawJobDto(
                properties.hasNonNull("adzunaId") ? properties.get("adzunaId").asText() : null,
                properties.hasNonNull("companyName") ? properties.get("companyName").asText() : null,
                properties.hasNonNull("companyWebsite") ? properties.get("companyWebsite").asText() : null,
                properties.hasNonNull("title") ? properties.get("title").asText() : null,
                properties.hasNonNull("description") ? properties.get("description").asText() : null,
                null,
                city,
                properties.hasNonNull("country") ? properties.get("country").asText() : null,
                properties.hasNonNull("jobIsRemote") ? properties.get("jobIsRemote").asBoolean() : null,
                postedAt,
                null, // expiresAt not directly present in example, keeping null
                properties.hasNonNull("salaryMin") ? (int) properties.get("salaryMin").asDouble() : null,
                properties.hasNonNull("salaryMax") ? (int) properties.get("salaryMax").asDouble() : null,
                null, // salaryCurrency not present
                null  // requiredExperience not present
        );
    }
}