package org.example.jobsmvp.models.relationships;

import org.example.jobsmvp.models.nodes.Skill;
import org.springframework.data.neo4j.core.schema.*;
import lombok.Data;

@RelationshipProperties
@Data
public class Knows {
    @Id @GeneratedValue
    private String id;

    @Property("proficiency_level")
    private Integer proficiencyLevel;

    @Property("years_of_experience")
    private Double yearsOfExperience;

    @TargetNode
    private Skill technology;
}