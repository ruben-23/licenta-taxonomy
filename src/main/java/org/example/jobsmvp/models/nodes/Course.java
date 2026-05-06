package org.example.jobsmvp.models.nodes;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

@Node("Course")
@Data
public class Course {
    @Id
    private String course_id;

    private String title;
    private String description;
    private String provider;

    @Relationship(type = "COVERS", direction = Relationship.Direction.OUTGOING)
    private List<Skill> covers;
}