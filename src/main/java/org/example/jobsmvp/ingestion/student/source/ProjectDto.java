package org.example.jobsmvp.ingestion.student.source;

import lombok.Data;
import java.util.List;

@Data
public class ProjectDto {
    private String title;
    private String description;
    private String url;
    
    // This allows the LLM or your pipeline to extract extra skills from their projects!
    private List<String> technologies_used; 
}