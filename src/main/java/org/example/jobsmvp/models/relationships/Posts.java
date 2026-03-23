package org.example.jobsmvp.models.relationships;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.example.jobsmvp.models.nodes.Job;
import org.springframework.data.neo4j.core.schema.*;
import lombok.Data;



@RelationshipProperties
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Posts {
    @Id @GeneratedValue
    private String id;

    @Property("is_active")
    private Boolean isActive;

    @TargetNode
    private Job job;

    // Custom constructor to easily wrap the Job node
    public Posts(Job job, Boolean isActive) {
        this.job = job;
        this.isActive = isActive;
    }
}
