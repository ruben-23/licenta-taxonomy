package org.example.jobsmvp.models.relationships;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.example.jobsmvp.models.nodes.Skill;
import org.springframework.data.neo4j.core.schema.*;
import lombok.Data;

@RelationshipProperties
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Requires {
    @Id @GeneratedValue
    private String id;

    private String importance;

    @Property("min_proficiency")
    private Integer minProficiency;

    @TargetNode
    private Skill technology;


    public Requires(Skill technology, String importance, Integer minProficiency) {
        this.technology = technology;
        this.importance = importance;
        this.minProficiency = minProficiency;
    }
}