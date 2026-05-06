package org.example.jobsmvp.models.nodes;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.util.List;

/**
 * Represents any node in the skills taxonomy tree:
 *
 *   Layer 1 — Skill Category : "Technical Competencies (Hard Skills)"
 *                               "Transversal Competencies (Soft Skills)"
 *   Layer 2 — Skill Group    : "Programming & Scripting", "Frameworks & Libraries", …
 *   Layer 3 — Specific Skill : "Java", "Python", "Docker", "Problem solving", …
 *
 * All three persisted layers share this node label. The {@code layer} and
 * {@code type} fields distinguish them. Hierarchy is expressed as
 * {@code BELONGS_TO} edges: Specific Skill → Skill Group → Skill Category.
 *
 * The {@code parent} field stores the direct parent's name and is used when
 * building those edges during ingestion.
 */
@Node("Skill")
@Data
public class Skill {

    @Id
    @Property("skill_id")
    private String skillId;

    /** Display name, e.g. "Java", "Programming & Scripting". */
    @Property("name")
    private String name;

    /**
     * Taxonomy layer:
     *   1 = Skill Category, 2 = Skill Group, 3 = Specific Skill.
     */
    @Property("layer")
    private Integer layer;

    /**
     * Taxonomy node type, mirrors skills.json:
     *   "Skill Category" | "Skill Group" | "Specific Skill"
     */
    @Property("type")
    private String type;

    /**
     * Name of the direct parent node in the taxonomy.
     *   Specific Skill  → Skill Group name   (e.g. "Java" → "Programming & Scripting")
     *   Skill Group     → Skill Category name (e.g. "Programming & Scripting" → "Technical Competencies (Hard Skills)")
     *   Skill Category  → null
     */
    @Property("parent")
    private String parent;

    /** Text embedding vector for semantic similarity matching. */
    @Property("text_embedding")
    private List<Double> textEmbedding;

}