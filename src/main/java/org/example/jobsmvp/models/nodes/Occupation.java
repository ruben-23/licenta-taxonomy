package org.example.jobsmvp.models.nodes;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.util.List;

/**
 * Represents a canonical occupation node in the knowledge graph.
 *
 * Maps to entries in the occupations taxonomy, e.g.:
 *  Layer 1 — Job Family:       "Software Engineering & Architecture"
 *  Layer 2 — Core Occupation:  "Back-End Developer"
 *  Layer 3 — Specialized Role: "Java Developer"
 */
@Node("Occupation")
@Data
public class Occupation {

    @Id
    @Property("occupation_id")
    private String occupationId;

    /** Canonical occupation name, e.g. "Java Developer". */
    @Property("name")
    private String name;

    /**
     * Taxonomy node type:
     *  "Job Family" | "Core Occupation" | "Specialized Role"
     */
    @Property("type")
    private String type;

    /**
     * Taxonomy layer (0 = root, 1 = Job Family, 2 = Core Occupation,
     * 3 = Specialized Role).
     */
    @Property("layer")
    private Integer layer;

    /**
     * Name of the direct parent node in the taxonomy tree,
     * e.g. "Back-End Developer" for "Java Developer".
     */
    @Property("parent")
    private String parent;

    /** Text embedding vector for semantic similarity matching. */
    @Property("text_embedding")
    private List<Double> textEmbedding;


}