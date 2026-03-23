package org.example.jobsmvp.models.nodes;

import lombok.Data;
import org.example.jobsmvp.models.relationships.Posts;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;
import java.util.List;

@Node("Company")
@Data
public class Company {
    @Id
    private String company_id;

    private String name;
    private String industry;
    private String location;
    private String size;

    @Relationship(type = "POSTS", direction = Relationship.Direction.OUTGOING)
    private List<Posts> jobs;

    @Property("text_embedding")
    private List<Double> textEmbedding;

}
