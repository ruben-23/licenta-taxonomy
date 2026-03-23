package org.example.jobsmvp.models.nodes;

import lombok.Data;
import org.example.jobsmvp.models.relationships.Requires;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

@Node("Job")
@Data
public class Job {
    @Id
    private String job_id;

    private String title;

    @Property("experience_level")
    private String experienceLevel;

    @Property("job_type")
    private String jobType;

    @Property("contract_duration")
    private String contractDuration;
    private String description;
    private Integer salary;
    private String currency;
    private Boolean remote;
    private String location;

    // Using LocalDate, but can be String if you prefer to parse it manually
    @Property("posted_date")
    private String postedDate;

    @Property("expires_at")
    private String expiresAt;

    @Relationship(type = "REQUIRES", direction = Relationship.Direction.OUTGOING)
    private List<Requires> requiredTechnologies;

    @Property("text_embedding")
    private List<Double> textEmbedding;
}