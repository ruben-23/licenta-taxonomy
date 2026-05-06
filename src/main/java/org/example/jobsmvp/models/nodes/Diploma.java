package org.example.jobsmvp.models.nodes;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

@Node("Diploma")
@Data
public class Diploma {
    @Id
    private String diploma_id;

    private String title;
    private String description;
    private String issuer;


    @Relationship(type = "CERTIFIES", direction = Relationship.Direction.OUTGOING)
    private List<Skill> certifies;
}