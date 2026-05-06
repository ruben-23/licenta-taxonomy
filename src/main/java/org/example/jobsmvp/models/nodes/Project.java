package org.example.jobsmvp.models.nodes;
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
    private String project_id;

    private String title;
    private String description;

    @Property("github_link")
    private String githubLink;

    @Relationship(type = "BUILT_WITH", direction = Relationship.Direction.OUTGOING)
    private List<Skill> builtWith;
}