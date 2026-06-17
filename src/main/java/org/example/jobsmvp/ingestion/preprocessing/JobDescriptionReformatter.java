package org.example.jobsmvp.ingestion.preprocessing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * Uses the LLM to produce two clean text fields from a raw job description:
 *
 * <ul>
 *   <li><b>cleanDescription</b> – a signal-only, no-markdown summary of the role
 *       (responsibilities, required skills, tech stack, qualifications).
 *       Stored as {@code clean_description} on the Job node.</li>
 *   <li><b>companyDescription</b> – the "About us" / company overview paragraph
 *       extracted verbatim from the posting.
 *       Stored as {@code description} on the Company node.</li>
 * </ul>
 *
 * The model is asked to return a single JSON object containing both fields so
 * only one LLM call is needed per job.
 */
@Service
@AllArgsConstructor
public class JobDescriptionReformatter {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionReformatter.class);

    // ── Prompt ────────────────────────────────────────────────────────────────

    private static final PromptTemplate REFORMAT_TEMPLATE = PromptTemplate.from("""
            You are an expert technical recruiter and editor.

            Given the raw job posting below, return ONLY a valid JSON object with exactly two fields.
            Do NOT include markdown code fences, explanations, or any text other than the JSON.

            Required JSON schema:
            {
              "cleanDescription":   string,
              "companyDescription": string | null
            }

            Rules for "cleanDescription":
            - The very first words MUST be the job title followed by a period (e.g. "Java Developer.").
            - Extract ONLY: day-to-day responsibilities, required skills, preferred qualifications,
              tech stack, certifications, and required degrees.
            - COMPLETELY REMOVE: company history, "About Us" sections, generic benefits (PTO, 401k,
              healthcare), EEO / diversity legal statements, and salary information.
            - Do NOT use conversational filler ("The ideal candidate will...", "We are looking for...").
              Chain related concepts with semicolons or commas instead.
            - Output a single continuous string of plain text — no bullet points, no bold/italic
              markdown, no line breaks.

            Rules for "companyDescription":
            - A brief description of the company based on the text. If none is found, infer a brief one based on the context.
            - If no company description is present in the posting, return null.

            Job Posting:
            Company: {{employerName}}
            Title:   {{jobTitle}}
            ---
            {{rawDescription}}
            """);

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final ChatModel    chatModel;
    private final ObjectMapper objectMapper;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Reformats the raw description and extracts the company blurb in one LLM call.
     *
     * @param employerName   raw company name (context hint for the prompt)
     * @param jobTitle       raw job title   (used as the opening of cleanDescription)
     * @param rawDescription HTML-stripped but otherwise unmodified description text
     * @return result containing both fields; falls back to safe defaults on failure
     */
    public ReformatResult reformat(String employerName, String jobTitle, String rawDescription) {
        Prompt prompt = REFORMAT_TEMPLATE.apply(Map.of(
                "employerName",   nullSafe(employerName),
                "jobTitle",       nullSafe(jobTitle),
                "rawDescription", rawDescription
        ));

        String raw;
        try {
            raw = chatModel.chat(prompt.text());
        } catch (Exception e) {
            log.error("LLM call failed during description reformat for '{}': {}", jobTitle, e.getMessage());
            return ReformatResult.fallback(rawDescription);
        }

        return parseJson(raw, jobTitle, rawDescription);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ReformatResult parseJson(String raw, String context, String fallbackDesc) {
        String clean = raw.strip()
                .replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)```\\s*$", "")
                .strip();

        try {
            LlmResponse response = objectMapper.readValue(clean, LlmResponse.class);
            String cleanDesc = (response.cleanDescription() != null && !response.cleanDescription().isBlank())
                    ? response.cleanDescription()
                    : fallbackDesc;
            return new ReformatResult(cleanDesc, response.companyDescription());
        } catch (Exception e) {
            log.warn("Failed to parse reformat response for '{}': {}", context, e.getMessage());
            log.debug("Raw LLM output was: {}", raw);
            return ReformatResult.fallback(fallbackDesc);
        }
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /** Raw JSON shape returned by the LLM. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmResponse(
            String cleanDescription,
            String companyDescription
    ) {}

    /**
     * Holds the two LLM-produced strings for a single job posting.
     *
     * @param cleanDescription   signal-only job description (stored as {@code clean_description} on Job)
     * @param companyDescription company overview paragraph (stored as {@code description} on Company);
     *                           may be {@code null} if the posting contained no "About Us" section
     */
    public record ReformatResult(
            String cleanDescription,
            String companyDescription
    ) {
        /** Safe fallback when the LLM call or parse fails. */
        static ReformatResult fallback(String rawDescription) {
            return new ReformatResult(rawDescription, null);
        }
    }
}