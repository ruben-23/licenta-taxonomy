package org.example.jobsmvp.ingestion.student.source;

import lombok.Data;

@Data
public class ExperienceDto {
    private String company_name;
    private String role;
    private String start_date;
    private String end_date;
    private String description;
}