package org.example.jobsmvp.models.nodes;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.nio.DoubleBuffer;
import java.util.List;

@Node("Technology")
@Data
public class Technology {
    @Id
    private String tech_id;

    private String name;
    private String category;

    // For LangChain LLM (Semantic Text Embeddings for Normalization)
    @Property("text_embedding")
    private List<Double> textEmbedding;

}