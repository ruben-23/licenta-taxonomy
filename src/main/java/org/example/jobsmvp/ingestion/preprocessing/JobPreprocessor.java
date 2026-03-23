package org.example.jobsmvp.ingestion.preprocessing;

import org.example.jobsmvp.ingestion.source.RawJobDto;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Cleans raw job text so the LLM receives noise-free input.
 *
 * Operations (in order):
 *  1. Strip HTML tags via Jsoup
 *  2. Remove common tracking / boilerplate strings
 *  3. Collapse duplicate whitespace and blank lines
 *  4. Lowercase
 *  5. Standardise punctuation
 */
@Component
public class JobPreprocessor {

    // Patterns compiled once at class load time
    private static final Pattern MULTI_WHITESPACE   = Pattern.compile("[ \\t]{2,}");
    private static final Pattern MULTI_NEWLINE      = Pattern.compile("\\n{3,}");
    private static final Pattern TRACKING_PATTERNS  = Pattern.compile(
            "(?i)(equal opportunity employer|eoe|apply now|click here|"
          + "this job was posted|powered by|©\\s*\\d{4}|all rights reserved)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Returns a cleaned, normalised version of the job description.
     * The original DTO is unchanged; only the returned string is processed.
     *
     * @param job raw job DTO
     * @return cleaned description text
     */
    public String cleanDescription(RawJobDto job) {
        if (job.jobDescription() == null) return "";

        String text = job.jobDescription();

        // 1. Strip HTML
        text = Jsoup.parse(text).text();

        // 2. Remove tracking / boilerplate
        text = TRACKING_PATTERNS.matcher(text).replaceAll(" ");

        // 3. Collapse whitespace
        text = MULTI_WHITESPACE.matcher(text).replaceAll(" ");
        text = MULTI_NEWLINE.matcher(text).replaceAll("\n\n");

        // 4. Lowercase
        text = text.toLowerCase();

        // 5. Standardise punctuation – collapse multiple punctuation marks
        text = text.replaceAll("[.]{2,}", ".").replaceAll("[,]{2,}", ",");

        return text.strip();
    }

    /**
     * Normalises a job title for consistent display and matching.
     */
    public String cleanTitle(RawJobDto job) {
        if (job.jobTitle() == null) return "";
        return job.jobTitle().strip().toLowerCase()
                .replaceAll("\\s+", " ");
    }

    /**
     * Builds a human-readable location string from city and country fields.
     */
    public String buildLocation(RawJobDto job) {
        String city    = nullSafe(job.jobCity());
        String country = nullSafe(job.jobCountry());
        if (city.isEmpty() && country.isEmpty()) return "remote";
        if (city.isEmpty())    return country;
        if (country.isEmpty()) return city;
        return city + ", " + country;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s.strip();
    }
}
