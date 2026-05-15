//package org.example.jobsmvp.models.nodes;
//
//import lombok.Data;
//import org.example.jobsmvp.models.relationships.Knows;
//import org.springframework.data.neo4j.core.schema.Id;
//import org.springframework.data.neo4j.core.schema.Node;
//import org.springframework.data.neo4j.core.schema.Property;
//import org.springframework.data.neo4j.core.schema.Relationship;
//import java.util.List;
//
//@Node("Student")
//@Data
//public class Student {
//    @Id
//    private String student_id;
//
//    private String name;
//    private String major;
//
//    @Property("graduation_year")
//    private Integer graduationYear;
//
//    @Property("current_year_of_study")
//    private Integer currentYearOfStudy;
//
//    @Property("degree_level")
//    private String degreeLevel;
//
//    // Relationships with properties
//    @Relationship(type = "KNOWS", direction = Relationship.Direction.OUTGOING)
//    private List<Knows> knownTechnologies;
//
//    // Relationships without properties
//    @Relationship(type = "CREATED", direction = Relationship.Direction.OUTGOING)
//    private List<Project> projects;
//
//    @Relationship(type = "COMPLETED", direction = Relationship.Direction.OUTGOING)
//    private List<Course> courses;
//
//    @Relationship(type = "EARNED", direction = Relationship.Direction.OUTGOING)
//    private List<Diploma> diplomas;
//}


package org.example.jobsmvp.models.nodes;

import lombok.Data;
import org.example.jobsmvp.models.relationships.Knows;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

/**
 * Represents a student node in the graph.
 *
 * Changes vs original:
 *  - {@code content_hash} added — SHA-256 fingerprint used by
 *    {@link org.example.jobsmvp.ingestion.student.deduplication.StudentDeduplicationService}
 *    for duplicate detection across pipeline runs.
 *  - {@code text_embedding} added — vector representation of name + major + degree
 *    level, used for semantic similarity queries (e.g. student–job matching).
 */
@Node("Student")
@Data
public class Student {

    @Id
    @Property("student_id")
    private String studentId;

    private String name;
    private String major;

    @Property("graduation_year")
    private Integer graduationYear;

    @Property("current_year_of_study")
    private Integer currentYearOfStudy;

    @Property("degree_level")
    private String degreeLevel;

    /**
     * SHA-256 fingerprint of the student record (name + major + degreeLevel + graduationYear).
     * Stored so that {@link org.example.jobsmvp.ingestion.student.deduplication.StudentDeduplicationService}
     * can detect already-ingested records without relying on the source file's IDs.
     */
    @Property("content_hash")
    private String contentHash;

    /**
     * Text embedding vector for the student, derived from name + major + degree level.
     * Used for semantic similarity queries between students and job postings.
     */
    @Property("text_embedding")
    private List<Double> textEmbedding;

    // ── Relationships ─────────────────────────────────────────────────────────

    /** Direct skill knowledge with proficiency metadata. */
    @Relationship(type = "KNOWS", direction = Relationship.Direction.OUTGOING)
    private List<Knows> knownTechnologies;

    @Relationship(type = "CREATED", direction = Relationship.Direction.OUTGOING)
    private List<Project> projects;

    @Relationship(type = "COMPLETED", direction = Relationship.Direction.OUTGOING)
    private List<Course> courses;

    @Relationship(type = "EARNED", direction = Relationship.Direction.OUTGOING)
    private List<Diploma> diplomas;
}