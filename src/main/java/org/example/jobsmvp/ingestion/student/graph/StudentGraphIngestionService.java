package org.example.jobsmvp.ingestion.student.graph;

import lombok.AllArgsConstructor;
import org.example.jobsmvp.ingestion.student.transform.StudentGraphBundle;
import org.example.jobsmvp.models.nodes.Course;
import org.example.jobsmvp.models.nodes.Diploma;
import org.example.jobsmvp.models.nodes.Project;
import org.example.jobsmvp.models.nodes.Skill;
import org.example.jobsmvp.models.nodes.Student;
import org.example.jobsmvp.models.relationships.Knows;
import org.example.jobsmvp.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Persists a {@link StudentGraphBundle} into Neo4j.
 *
 * All operations for a single bundle run inside one transaction.
 * MERGE semantics ensure idempotency — re-running the pipeline is safe.
 *
 * Graph shape produced per student:
 *
 * <pre>
 *   (Student)-[:KNOWS {proficiency_level, years_of_experience}]->(Skill)
 *   (Student)-[:CREATED]->(Project)-[:BUILT_WITH]->(Skill)
 *   (Student)-[:COMPLETED]->(Course)-[:COVERS]->(Skill)
 *   (Student)-[:EARNED]->(Diploma)-[:CERTIFIES]->(Skill)
 *
 *   Skill nodes are shared with the job side of the graph — MERGE by skill_id
 *   ensures students and jobs reference the same canonical taxonomy nodes.
 * </pre>
 *
 * The shared {@link org.example.jobsmvp.ingestion.graph.GraphIngestionService} is
 * deliberately NOT reused here because the student and job graph shapes are
 * structurally different enough to warrant their own merge logic, and coupling the
 * two would make both harder to evolve independently.
 */
@Service
@AllArgsConstructor
public class StudentGraphIngestionService {

    private static final Logger log = LoggerFactory.getLogger(StudentGraphIngestionService.class);

    private final Neo4jClient neo4jClient;
    private final EmbeddingService embeddingService;

    // ── Public entry point ────────────────────────────────────────────────────

    @Transactional
    public void ingest(StudentGraphBundle bundle) {
        mergeStudent(bundle.student(), bundle.contentHash());

        // KNOWS → Skill (direct from Student)
        for (Knows knows : nullSafe(bundle.student().getKnownTechnologies())) {
            Skill skill = knows.getTechnology();
            if (skill == null) continue;
            mergeSkill(skill);
            mergeSkillHierarchy(skill);
            mergeKnowsRelationship(
                    bundle.student().getStudentId(),
                    skill.getSkillId(),
                    knows.getProficiencyLevel(),
                    knows.getYearsOfExperience()
            );
        }

        // CREATED → Project → BUILT_WITH → Skill
        for (Project project : nullSafe(bundle.projects())) {
            mergeProject(project);
            mergeCreatedRelationship(bundle.student().getStudentId(), project.getProject_id());
            for (Skill skill : nullSafe(project.getBuiltWith())) {
                mergeSkill(skill);
                mergeSkillHierarchy(skill);
                mergeBuiltWithRelationship(project.getProject_id(), skill.getSkillId());
            }
        }

        // COMPLETED → Course → COVERS → Skill
        for (Course course : nullSafe(bundle.courses())) {
            mergeCourse(course);
            mergeCompletedRelationship(bundle.student().getStudentId(), course.getCourse_id());
            for (Skill skill : nullSafe(course.getCovers())) {
                mergeSkill(skill);
                mergeSkillHierarchy(skill);
                mergeCoverRelationship(course.getCourse_id(), skill.getSkillId());
            }
        }

        // EARNED → Diploma → CERTIFIES → Skill
        for (Diploma diploma : nullSafe(bundle.diplomas())) {
            mergeDiploma(diploma);
            mergeEarnedRelationship(bundle.student().getStudentId(), diploma.getDiploma_id());
            for (Skill skill : nullSafe(diploma.getCertifies())) {
                mergeSkill(skill);
                mergeSkillHierarchy(skill);
                mergeCertifiesRelationship(diploma.getDiploma_id(), skill.getSkillId());
            }
        }

        embeddingService.processStudent(bundle.student().getStudentId());
        log.info("Ingested student '{}'", bundle.student().getName());
    }

    // ── Node merges ───────────────────────────────────────────────────────────

    private void mergeStudent(Student student, String contentHash) {
        // Store content_hash so the deduplication service can find existing records
        // on future runs without relying on the file's original student_id.
        neo4jClient.query("""
                MERGE (s:Student {student_id: $studentId})
                ON CREATE SET
                    s.name                  = $name,
                    s.major                 = $major,
                    s.graduation_year       = $graduationYear,
                    s.current_year_of_study = $currentYearOfStudy,
                    s.degree_level          = $degreeLevel,
                    s.content_hash          = $contentHash
                ON MATCH SET
                    s.major                 = CASE WHEN $major         IS NOT NULL THEN $major         ELSE s.major                 END,
                    s.graduation_year       = CASE WHEN $graduationYear IS NOT NULL THEN $graduationYear ELSE s.graduation_year       END,
                    s.current_year_of_study = CASE WHEN $currentYearOfStudy IS NOT NULL THEN $currentYearOfStudy ELSE s.current_year_of_study END,
                    s.degree_level          = CASE WHEN $degreeLevel   IS NOT NULL THEN $degreeLevel   ELSE s.degree_level          END
                """)
                .bindAll(Map.of(
                        "studentId",          student.getStudentId(),
                        "name",               nullSafe(student.getName()),
                        "major",              nullSafe(student.getMajor()),
                        "graduationYear",     student.getGraduationYear()       != null ? student.getGraduationYear()       : 0,
                        "currentYearOfStudy", student.getCurrentYearOfStudy()   != null ? student.getCurrentYearOfStudy()   : 0,
                        "degreeLevel",        nullSafe(student.getDegreeLevel()),
                        "contentHash",        contentHash
                ))
                .run();

        // Embedding written separately (same driver-compatibility reason as GraphIngestionService)
        if (student.getTextEmbedding() != null && !student.getTextEmbedding().isEmpty()) {
            neo4jClient.query("""
                    MATCH (s:Student {student_id: $studentId})
                    SET s.text_embedding = $textEmbedding
                    """)
                    .bindAll(Map.of(
                            "studentId",    student.getStudentId(),
                            "textEmbedding", student.getTextEmbedding()
                    ))
                    .run();
        }
    }

    private void mergeProject(Project project) {
        neo4jClient.query("""
                MERGE (p:Project {project_id: $projectId})
                ON CREATE SET
                    p.title       = $title,
                    p.description = $description,
                    p.github_link = $githubLink
                """)
                .bindAll(Map.of(
                        "projectId",   project.getProject_id(),
                        "title",       nullSafe(project.getTitle()),
                        "description", nullSafe(project.getDescription()),
                        "githubLink",  nullSafe(project.getGithubLink())
                ))
                .run();
    }

    private void mergeCourse(Course course) {
        neo4jClient.query("""
                MERGE (c:Course {course_id: $courseId})
                ON CREATE SET
                    c.title       = $title,
                    c.description = $description,
                    c.provider    = $provider
                """)
                .bindAll(Map.of(
                        "courseId",    course.getCourse_id(),
                        "title",       nullSafe(course.getTitle()),
                        "description", nullSafe(course.getDescription()),
                        "provider",    nullSafe(course.getProvider())
                ))
                .run();
    }

    private void mergeDiploma(Diploma diploma) {
        neo4jClient.query("""
                MERGE (d:Diploma {diploma_id: $diplomaId})
                ON CREATE SET
                    d.title       = $title,
                    d.description = $description,
                    d.issuer      = $issuer
                """)
                .bindAll(Map.of(
                        "diplomaId",   diploma.getDiploma_id(),
                        "title",       nullSafe(diploma.getTitle()),
                        "description", nullSafe(diploma.getDescription()),
                        "issuer",      nullSafe(diploma.getIssuer())
                ))
                .run();
    }

    /**
     * MERGEs a layer-3 Skill node into the shared taxonomy.
     *
     * Uses the same MERGE key ({@code skill_id}) and property shape as
     * {@link org.example.jobsmvp.ingestion.graph.GraphIngestionService#mergeSkill}
     * so that skill nodes created by the job pipeline are reused here and vice versa.
     */
    private void mergeSkill(Skill skill) {
        if (skill.getTextEmbedding() == null || skill.getTextEmbedding().isEmpty()) {
            neo4jClient.query("""
                    MERGE (s:Skill {skill_id: $skillId})
                    ON CREATE SET
                        s.name   = $name,
                        s.layer  = $layer,
                        s.type   = $type,
                        s.parent = $parent
                    """)
                    .bindAll(Map.of(
                            "skillId", skill.getSkillId(),
                            "name",    skill.getName(),
                            "layer",   skill.getLayer()  != null ? skill.getLayer()  : 3,
                            "type",    skill.getType()   != null ? skill.getType()   : "Specific Skill",
                            "parent",  nullSafe(skill.getParent())
                    ))
                    .run();
        } else {
            neo4jClient.query("""
                    MERGE (s:Skill {skill_id: $skillId})
                    ON CREATE SET
                        s.name           = $name,
                        s.layer          = $layer,
                        s.type           = $type,
                        s.parent         = $parent,
                        s.text_embedding = $textEmbedding
                    """)
                    .bindAll(Map.of(
                            "skillId",       skill.getSkillId(),
                            "name",          skill.getName(),
                            "layer",         skill.getLayer()  != null ? skill.getLayer()  : 3,
                            "type",          skill.getType()   != null ? skill.getType()   : "Specific Skill",
                            "parent",        nullSafe(skill.getParent()),
                            "textEmbedding", skill.getTextEmbedding()
                    ))
                    .run();
        }
    }

    // ── Skill hierarchy wiring ────────────────────────────────────────────────

    /**
     * Creates SUBCLASS_OF edges up the skill taxonomy for any newly created skill node.
     * Mirrors the hierarchy logic in GraphIngestionService to keep the taxonomy consistent.
     */
    private void mergeSkillHierarchy(Skill skill) {
        if (skill.getParent() == null || skill.getParent().isBlank()) return;

        neo4jClient.query("""
                MATCH (child:Skill  {skill_id: $skillId})
                MATCH (parent:Skill {name: $parentName, layer: 2})
                MERGE (child)-[:SUBCLASS_OF]->(parent)
                """)
                .bindAll(Map.of(
                        "skillId",    skill.getSkillId(),
                        "parentName", skill.getParent()
                ))
                .run();

        neo4jClient.query("""
                MATCH (group:Skill    {name: $groupName, layer: 2})
                MATCH (category:Skill {layer: 1})
                WHERE group.parent = category.name
                MERGE (group)-[:SUBCLASS_OF]->(category)
                """)
                .bindAll(Map.of("groupName", skill.getParent()))
                .run();
    }

    // ── Relationship merges ───────────────────────────────────────────────────

    private void mergeKnowsRelationship(
            String studentId, String skillId,
            Integer proficiencyLevel, Double yearsOfExperience
    ) {
        neo4jClient.query("""
                MATCH (s:Student {student_id: $studentId})
                MATCH (k:Skill   {skill_id:   $skillId})
                MERGE (s)-[r:KNOWS]->(k)
                ON CREATE SET
                    r.proficiency_level   = $proficiencyLevel,
                    r.years_of_experience = $yearsOfExperience
                ON MATCH SET
                    r.proficiency_level   = CASE WHEN $proficiencyLevel   IS NOT NULL THEN $proficiencyLevel   ELSE r.proficiency_level   END,
                    r.years_of_experience = CASE WHEN $yearsOfExperience  IS NOT NULL THEN $yearsOfExperience  ELSE r.years_of_experience END
                """)
                .bindAll(Map.of(
                        "studentId",         studentId,
                        "skillId",           skillId,
                        "proficiencyLevel",  proficiencyLevel  != null ? proficiencyLevel  : 1,
                        "yearsOfExperience", yearsOfExperience != null ? yearsOfExperience : 0.0
                ))
                .run();
    }

    private void mergeCreatedRelationship(String studentId, String projectId) {
        neo4jClient.query("""
                MATCH (s:Student {student_id: $studentId})
                MATCH (p:Project {project_id: $projectId})
                MERGE (s)-[:CREATED]->(p)
                """)
                .bindAll(Map.of("studentId", studentId, "projectId", projectId))
                .run();
    }

    private void mergeBuiltWithRelationship(String projectId, String skillId) {
        neo4jClient.query("""
                MATCH (p:Project {project_id: $projectId})
                MATCH (s:Skill   {skill_id:   $skillId})
                MERGE (p)-[:BUILT_WITH]->(s)
                """)
                .bindAll(Map.of("projectId", projectId, "skillId", skillId))
                .run();
    }

    private void mergeCompletedRelationship(String studentId, String courseId) {
        neo4jClient.query("""
                MATCH (s:Student {student_id: $studentId})
                MATCH (c:Course  {course_id:  $courseId})
                MERGE (s)-[:COMPLETED]->(c)
                """)
                .bindAll(Map.of("studentId", studentId, "courseId", courseId))
                .run();
    }

    private void mergeCoverRelationship(String courseId, String skillId) {
        neo4jClient.query("""
                MATCH (c:Course {course_id: $courseId})
                MATCH (s:Skill  {skill_id:  $skillId})
                MERGE (c)-[:COVERS]->(s)
                """)
                .bindAll(Map.of("courseId", courseId, "skillId", skillId))
                .run();
    }

    private void mergeEarnedRelationship(String studentId, String diplomaId) {
        neo4jClient.query("""
                MATCH (s:Student {student_id: $studentId})
                MATCH (d:Diploma {diploma_id: $diplomaId})
                MERGE (s)-[:EARNED]->(d)
                """)
                .bindAll(Map.of("studentId", studentId, "diplomaId", diplomaId))
                .run();
    }

    private void mergeCertifiesRelationship(String diplomaId, String skillId) {
        neo4jClient.query("""
                MATCH (d:Diploma {diploma_id: $diplomaId})
                MATCH (s:Skill   {skill_id:   $skillId})
                MERGE (d)-[:CERTIFIES]->(s)
                """)
                .bindAll(Map.of("diplomaId", diplomaId, "skillId", skillId))
                .run();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

    @SuppressWarnings("unchecked")
    private static <T> java.util.List<T> nullSafe(java.util.List<T> list) {
        return list != null ? list : java.util.List.of();
    }
}