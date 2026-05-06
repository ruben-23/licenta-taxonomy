//package org.example.jobsmvp.ingestion.extraction;
//
//import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
//
//import java.util.List;
//
///**
// * Structured output from the LLM entity extraction step.
// *
// * The LLM is prompted to return JSON matching this schema exactly.
// * Unknown fields are ignored to guard against hallucinated extras.
// */
//@JsonIgnoreProperties(ignoreUnknown = true)
//public record ExtractedEntities(
//
//        /**
//         * Canonical company name as mentioned in the posting.
//         */
//        String companyName,
//
//        /**
//         * Normalised job title (e.g. "Backend Engineer").
//         */
//        String jobTitle,
//
//        /**
//         * Experience level label: "junior", "mid", "senior", "lead", "staff", "principal".
//         */
//        String experienceLevel,
//
//        /**
//         * Employment type: "full-time", "part-time", "contract", "internship", "freelance".
//         */
//        String jobType,
//
//        /**
//         * Contract duration when the job is fixed-term, e.g. "6 months".
//         * Null for permanent roles.
//         */
//        String contractDuration,
//
//        /**
//         * Whether the role is fully remote.
//         */
//        Boolean remote,
//
//        /**
//         * Approximate annual salary in the posting's currency, if stated.
//         */
//        Integer salary,
//
//        /**
//         * ISO 4217 currency code of the salary, e.g. "USD", "EUR".
//         */
//        String currency,
//
//        /**
//         * Technologies, frameworks, languages, tools and platforms
//         * extracted from the description (raw names, not yet normalised).
//         */
//        List<String> technologies,
//
//        /**
//         * Industry the company operates in, inferred from context.
//         */
//        String industry,
//
//        /**
//         * Company size bucket: "startup", "small", "medium", "large", "enterprise".
//         */
//        String companySize
//) {
//    /** Returns an empty/null-safe instance for error fallback. */
//    public static ExtractedEntities empty() {
//        return new ExtractedEntities(null, null, null, null, null, null, null, null, List.of(), null, null);
//    }
//}


package org.example.jobsmvp.ingestion.extraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Structured output from the LLM entity extraction step.
 *
 * The LLM is prompted to return JSON matching this schema exactly.
 * Unknown fields are ignored to guard against hallucinated extras.
 *
 * Changes from previous version:
 *  - {@code technologies} renamed to {@code technicalSkills} (hard skills only)
 *  - {@code softSkills} added for transversal / interpersonal skills
 *  - {@code occupation} added for the extracted job role
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedEntities(

        /** Canonical company name as mentioned in the posting. */
        String companyName,

        /** Normalised job title (e.g. "Backend Engineer"). */
        String jobTitle,

        /** Experience level label: "junior", "mid", "senior", "lead", "staff", "principal". */
        String experienceLevel,

        /** Employment type: "full-time", "part-time", "contract", "internship", "freelance". */
        String jobType,

        /**
         * Contract duration when the job is fixed-term, e.g. "6 months".
         * Null for permanent roles.
         */
        String contractDuration,

        /** Whether the role is fully remote. */
        Boolean remote,

        /** Approximate annual salary in the posting's currency, if stated. */
        Integer salary,

        /** ISO 4217 currency code of the salary, e.g. "USD", "EUR". */
        String currency,

        /**
         * Technical (hard) skills: programming languages, frameworks, tools,
         * platforms, cloud services, methodologies.
         * Raw names from the posting — normalised downstream.
         */
        List<String> technicalSkills,

        /**
         * Transversal (soft) skills explicitly or implicitly required by the role,
         * e.g. "Communication skills", "Problem solving", "Teamwork".
         * Raw names — normalised downstream.
         */
        List<String> softSkills,

        /**
         * The occupation / role the posting is for, as a best-guess canonical name,
         * e.g. "Java Developer", "Data Scientist".
         * Will be matched against the occupations taxonomy downstream.
         */
        String occupation,

        /** Industry the company operates in, inferred from context. */
        String industry,

        /** Company size bucket: "startup", "small", "medium", "large", "enterprise". */
        String companySize

) {
    /** Returns an empty / null-safe instance for error fallback. */
    public static ExtractedEntities empty() {
        return new ExtractedEntities(
                null, null, null, null, null,
                null, null, null,
                List.of(), List.of(), null,
                null, null
        );
    }
}