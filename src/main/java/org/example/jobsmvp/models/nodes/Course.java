package org.example.jobsmvp.models.nodes;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

@Node("Course")
@Data
public class Course {
    @Id
//    @JsonProperty("course_id")
    private String courseId;

    private String title;
    private String description;
    private String provider;

    @Relationship(type = "COVERS", direction = Relationship.Direction.OUTGOING)
    private List<Skill> covers;
}