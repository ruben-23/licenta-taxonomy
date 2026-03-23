package org.example.jobsmvp.ingestion.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw job DTO mapped directly from the JSearch RapidAPI response.
 * Only fields relevant to graph ingestion are captured; everything else is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawJobDto(

        @JsonProperty("job_id")
        String jobId,

        @JsonProperty("employer_name")
        String employerName,

        @JsonProperty("employer_website")
        String employerWebsite,

        @JsonProperty("job_title")
        String jobTitle,

        @JsonProperty("job_description")
        String jobDescription,

        @JsonProperty("job_employment_type")
        String jobEmploymentType,

        @JsonProperty("job_city")
        String jobCity,

        @JsonProperty("job_country")
        String jobCountry,

        @JsonProperty("job_is_remote")
        Boolean jobIsRemote,

        @JsonProperty("job_posted_at_datetime_utc")
        String jobPostedAt,

        @JsonProperty("job_offer_expiration_datetime_utc")
        String jobExpiresAt,

        @JsonProperty("job_min_salary")
        Integer jobMinSalary,

        @JsonProperty("job_max_salary")
        Integer jobMaxSalary,

        @JsonProperty("job_salary_currency")
        String jobSalaryCurrency,

        @JsonProperty("job_required_experience")
        JobExperience jobRequiredExperience
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobExperience(
            @JsonProperty("required_experience_in_months")
            Integer requiredExperienceInMonths,

            @JsonProperty("experience_mentioned")
            Boolean experienceMentioned
    ) {}
}
