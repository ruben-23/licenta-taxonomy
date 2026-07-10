package org.example.jobsmvp.models.nodes;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;
import java.util.List;


@Node("Project")
@Data
public class Project {
    @Id
//    @JsonProperty("project_id")
    private String projectId;

    private String title;
    private String description;

//    @JsonProperty("github_link")
    private String githubLink;

//    @JsonProperty("builtWith")
    @Relationship(type = "BUILT_WITH", direction = Relationship.Direction.OUTGOING)
    private List<Skill> builtWith;
}