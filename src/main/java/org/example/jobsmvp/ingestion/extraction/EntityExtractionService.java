//package org.example.jobsmvp.ingestion.extraction;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import dev.langchain4j.model.chat.ChatModel;
//import dev.langchain4j.model.input.Prompt;
//import dev.langchain4j.model.input.PromptTemplate;
//import lombok.AllArgsConstructor;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//
//import java.util.Map;
//
///**
// * Calls the LLM to extract structured entities from a cleaned job description.
// *
// * The prompt instructs the model to return ONLY valid JSON matching
// * the {@link ExtractedEntities} schema — no markdown fences, no preamble.
// */
//@Service
//@AllArgsConstructor
//public class EntityExtractionService {
//
//    private static final Logger log = LoggerFactory.getLogger(EntityExtractionService.class);
//
//    private static final PromptTemplate EXTRACTION_TEMPLATE = PromptTemplate.from("""
//            You are an expert at extracting structured information from job postings.
//
//            Given the job posting below, extract ALL relevant entities and return ONLY a valid JSON object.
//            Do NOT include markdown code fences, explanations, or any text other than the JSON.
//
//            Required JSON schema:
//            {
//              "companyName":       string | null,
//              "jobTitle":          string | null,
//              "experienceLevel":   "junior" | "mid" | "senior" | "lead" | "staff" | "principal" | null,
//              "jobType":           "full-time" | "part-time" | "contract" | "internship" | "freelance" | null,
//              "contractDuration":  string | null,
//              "remote":            boolean | null,
//              "salary":            integer | null,
//              "currency":          string | null,
//              "technologies":      string[],
//              "industry":          string | null,
//              "companySize":       "startup" | "small" | "medium" | "large" | "enterprise" | null
//            }
//
//            Rules:
//            - "technologies" must list every tool, language, framework, library, platform, or cloud service mentioned.
//            - Use the exact names from the posting for technologies (do not normalise them here).
//            - If a field cannot be determined, use null.
//            - "technologies" must be a JSON array, never null — use [] if none are mentioned.
//
//            Job Posting:
//            Company: {{employerName}}
//            Title:   {{jobTitle}}
//            ---
//            {{description}}
//            """);
//
//    private final ChatModel chatModel;
//    private final ObjectMapper objectMapper;
//
//    /**
//     * Runs LLM extraction on the preprocessed job description.
//     *
//     * @param employerName  raw company name from the API (used as context hint)
//     * @param jobTitle      raw job title from the API
//     * @param cleanedDescription  preprocessed description text
//     * @return extracted entities, or {@link ExtractedEntities#empty()} on failure
//     */
//    public ExtractedEntities extract(String employerName, String jobTitle, String cleanedDescription) {
//        Prompt prompt = EXTRACTION_TEMPLATE.apply(Map.of(
//                "employerName", nullSafe(employerName),
//                "jobTitle",     nullSafe(jobTitle),
//                "description",  cleanedDescription
//        ));
//
//        String raw;
//        try {
//            raw = chatModel.chat(prompt.text());
//        } catch (Exception e) {
//            log.error("LLM call failed during extraction: {}", e.getMessage());
//            return ExtractedEntities.empty();
//        }
//
//        return parseJson(raw, employerName);
//    }
//
//    private ExtractedEntities parseJson(String raw, String context) {
//        // Strip any accidental markdown fences the model may have added
//        String clean = raw.strip()
//                .replaceAll("(?s)^```json\\s*", "")
//                .replaceAll("(?s)```\\s*$", "")
//                .strip();
//
//        try {
//            return objectMapper.readValue(clean, ExtractedEntities.class);
//        } catch (Exception e) {
//            log.warn("Failed to parse LLM extraction response for '{}': {}", context, e.getMessage());
//            log.debug("Raw LLM output was: {}", raw);
//            return ExtractedEntities.empty();
//        }
//    }
//
//    private static String nullSafe(String s) {
//        return s != null ? s : "";
//    }
//}


package org.example.jobsmvp.ingestion.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Calls the LLM to extract structured entities from a cleaned job description.
 *
 * The prompt instructs the model to return ONLY valid JSON matching
 * the {@link ExtractedEntities} schema — no markdown fences, no preamble.
 *
 * Changes from previous version:
 *  - {@code technologies} split into {@code technicalSkills} and {@code softSkills}
 *  - {@code occupation} field added (best-guess canonical role name)
 */
@Service
@AllArgsConstructor
public class EntityExtractionService {

    private static final Logger log = LoggerFactory.getLogger(EntityExtractionService.class);

    private static final PromptTemplate EXTRACTION_TEMPLATE = PromptTemplate.from("""
            You are an expert at extracting structured information from job postings.

            Given the job posting below, extract ALL relevant entities and return ONLY a valid JSON object.
            Do NOT include markdown code fences, explanations, or any text other than the JSON.

            Required JSON schema:
            {
              "companyName":      string | null,
              "jobTitle":         string | null,
              "experienceLevel":  "junior" | "mid" | "senior" | "lead" | "staff" | "principal" | null,
              "jobType":          "full-time" | "part-time" | "contract" | "internship" | "freelance" | null,
              "contractDuration": string | null,
              "remote":           boolean | null,
              "salary":           integer | null,
              "currency":         string | null,
              "technicalSkills":  string[],
              "softSkills":       string[],
              "occupation":       string | null,
              "industry":         string | null,
              "companySize":      "startup" | "small" | "medium" | "large" | "enterprise" | null
            }

            Rules:
            - "technicalSkills" must list every programming language, framework, library, tool, platform,
              cloud service, or technical methodology mentioned (e.g. Java, Spring Boot, Docker, AWS,
              Microservices architecture, CI/CD pipelines).
            - Exclude broad job domains and vague concepts from "technicalSkills". Strictly ignore general
              categories like "Backend", "Frontend", "Full Stack Development", or "Software Engineering".
              Focus ONLY on concrete, named technologies and tools.
            - "softSkills" must list every interpersonal or cognitive skill mentioned or clearly implied
              (e.g. Problem solving, Communication skills, Teamwork, Analytical thinking, Adaptability).
            - "occupation" should be the single best-fit canonical job role for this posting
              (e.g. "Java Developer", "Data Scientist", "DevOps Engineer"). Use the job title as a hint
              but choose the most precise standard occupational title.
            - Use the exact names from the posting for skills (do not normalise them here).
            - If a field cannot be determined, use null.
            - Both skill arrays must be valid JSON arrays — use [] if none are found, never null.
            

            Job Posting:
            Company: {{employerName}}
            Title:   {{jobTitle}}
            ---
            {{description}}
            """);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    /**
     * Runs LLM extraction on the preprocessed job description.
     *
     * @param employerName       raw company name from the API (context hint)
     * @param jobTitle           raw job title from the API
     * @param cleanedDescription preprocessed description text
     * @return extracted entities, or {@link ExtractedEntities#empty()} on failure
     */
    public ExtractedEntities extract(String employerName, String jobTitle, String cleanedDescription) {
        Prompt prompt = EXTRACTION_TEMPLATE.apply(Map.of(
                "employerName", nullSafe(employerName),
                "jobTitle",     nullSafe(jobTitle),
                "description",  cleanedDescription
        ));

        String raw;
        try {
            raw = chatModel.chat(prompt.text());
        } catch (Exception e) {
            log.error("LLM call failed during extraction:", e);
            return ExtractedEntities.empty();
        }

        return parseJson(raw, employerName);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private ExtractedEntities parseJson(String raw, String context) {
        String clean = raw.strip()
                .replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)```\\s*$", "")
                .strip();

        try {
            return objectMapper.readValue(clean, ExtractedEntities.class);
        } catch (Exception e) {
            log.warn("Failed to parse LLM extraction response for '{}': {}", context, e.getMessage());
            log.debug("Raw LLM output was: {}", raw);
            return ExtractedEntities.empty();
        }
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}