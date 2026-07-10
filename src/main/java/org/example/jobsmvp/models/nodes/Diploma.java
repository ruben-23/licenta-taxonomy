package org.example.jobsmvp.models.nodes;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

@Node("Diploma")
@Data
public class Diploma {
    @Id
//    @JsonProperty("diploma_id")
    private String diplomaId;

    private String title;
    private String description;
    private String issuer;


//    @JsonProperty("certifies")
    @Relationship(type = "CERTIFIES", direction = Relationship.Direction.OUTGOING)
    private List<Skill> certifies;
}