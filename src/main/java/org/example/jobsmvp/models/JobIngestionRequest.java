package org.example.jobsmvp.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class JobIngestionRequest {
    @JsonProperty("job_id")
    private String job_id;
    @JsonProperty("employer_name")
    private String employer_name;
    @JsonProperty("job_title")
    private String job_title;
    @JsonProperty("job_description")
    private String job_description;
    @JsonProperty("job_city")
    private String job_city;
    @JsonProperty("job_is_remote")
    private boolean job_is_remote;
    @JsonProperty("job_posted_at_datetime_utc")
    private String job_posted_at_datetime_utc;
    @JsonProperty("job_offer_expiration_datetime_utc")
    private String job_offer_expiration_datetime_utc;
    @JsonProperty("job_min_salary")
    private Double job_min_salary;
    @JsonProperty("job_salary_currency")
    private String job_salary_currency;
}