package org.example.jobsmvp.ingestion.student.transform;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.AllArgsConstructor;
import org.example.jobsmvp.ingestion.normalization.EntityNormalizationService;
import org.example.jobsmvp.ingestion.student.deduplication.StudentDeduplicationService;
import org.example.jobsmvp.ingestion.student.source.RawStudentDto;
import org.example.jobsmvp.ingestion.student.source.RawStudentDto.*;
import org.example.jobsmvp.models.nodes.Course;
import org.example.jobsmvp.models.nodes.Diploma;
import org.example.jobsmvp.models.nodes.Project;
import org.example.jobsmvp.models.nodes.Skill;
import org.example.jobsmvp.models.nodes.Student;
import org.example.jobsmvp.models.relationships.Knows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converts a {@link RawStudentDto} into a graph-ready {@link StudentGraphBundle}.
 *
 * Key responsibilities:
 * <ul>
 *   <li>Generate fresh UUIDs for every entity (Student, Project, Course, Diploma).</li>
 *   <li>Delegate all skill name resolution to the shared
 *       {@link EntityNormalizationService} so students and jobs share the same
 *       canonical skill taxonomy.</li>
 *   <li>Build {@link Knows} relationships with proficiency and experience metadata.</li>
 *   <li>Compute text embeddings for the Student node.</li>
 * </ul>
 *
 * Does NOT write to the database — output is handed to
 * {@link org.example.jobsmvp.ingestion.student.graph.StudentGraphIngestionService}.
 */
@Service
@AllArgsConstructor
public class StudentGraphTransformService {

    private static final Logger log = LoggerFactory.getLogger(StudentGraphTransformService.class);

    private final EntityNormalizationService    normalizationService;
    private final StudentDeduplicationService   deduplicationService;
    private final EmbeddingModel                embeddingModel;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Builds a {@link StudentGraphBundle} for a single raw student record.
     *
     * @param raw the deserialized student record from JSON
     * @return bundle ready for graph persistence
     */
    public StudentGraphBundle transform(RawStudentDto raw) {
        System.out.println("Transform...");
        String contentHash = deduplicationService.contentHash(raw);

        Student        student  = buildStudent(raw, contentHash);
        List<Project>  projects = buildProjects(raw);
        List<Course>   courses  = buildCourses(raw);
        List<Diploma>  diplomas = buildDiplomas(raw);

        return new StudentGraphBundle(student, projects, courses, diplomas, contentHash);
    }

    // ── Student node ──────────────────────────────────────────────────────────

    private Student buildStudent(RawStudentDto raw, String contentHash) {
        Student student = new Student();
        student.setStudentId(UUID.randomUUID().toString());
        student.setName(raw.name());
        student.setMajor(raw.major());
        student.setGraduationYear(raw.graduationYear());
        student.setCurrentYearOfStudy(raw.currentYearOfStudy());
        student.setDegreeLevel(raw.degreeLevel());
        student.setContentHash(contentHash);

        // Text embedding: name + major + degree level give good semantic signal
        String embedInput = nullSafe(raw.name())
                + " " + nullSafe(raw.major())
                + " " + nullSafe(raw.degreeLevel());
        Embedding emb = embeddingModel.embed(embedInput).content();
        student.setTextEmbedding(
                emb.vectorAsList().stream().map(Float::doubleValue).toList()
        );

        // Build KNOWS relationships
        List<Knows> knows = buildKnowsRelationships(raw.knownTechnologies());
        student.setKnownTechnologies(knows);

        return student;
    }

    // ── KNOWS relationships ───────────────────────────────────────────────────

    /**
     * Converts each raw "knownTechnology" entry into a {@link Knows} relationship
     * with a normalised {@link Skill} as the target node.
     *
     * Skills are resolved through the shared taxonomy via
     * {@link EntityNormalizationService#normaliseSkills(List)}.
     */
    private List<Knows> buildKnowsRelationships(List<RawKnowsDto> rawList) {
        if (rawList == null || rawList.isEmpty()) return List.of();

        // Collect raw skill names for a single normalisation pass (more efficient)
        List<String> rawNames = rawList.stream()
                .filter(k -> k.technology() != null && k.technology().name() != null)
                .map(k -> k.technology().name())
                .toList();

        List<Skill> normalised = normalizationService.normaliseSkills(rawNames);

        // Pair each normalised skill back with its proficiency metadata
        List<Knows> result = new ArrayList<>();
        for (int i = 0; i < rawList.size(); i++) {
            RawKnowsDto raw = rawList.get(i);
            if (raw.technology() == null || raw.technology().name() == null) continue;

            Knows knows = new Knows();
            knows.setProficiencyLevel(raw.proficiencyLevel());
            knows.setYearsOfExperience(raw.yearsOfExperience());

            // Match by position — normaliseSkills preserves order and filters blanks
            if (i < normalised.size()) {
                knows.setTechnology(normalised.get(i));
            } else {
                log.warn("No normalised skill for KNOWS entry '{}', skipping.",
                        raw.technology().name());
                continue;
            }

            result.add(knows);
        }

        return result;
    }

    // ── Projects ──────────────────────────────────────────────────────────────

    private List<Project> buildProjects(RawStudentDto raw) {
        if (raw.projects() == null) return List.of();

        List<Project> result = new ArrayList<>();
        for (RawProjectDto rp : raw.projects()) {
            Project project = new Project();
            project.setProjectId(UUID.randomUUID().toString());
            project.setTitle(rp.title());
            project.setDescription(rp.description());
            project.setGithubLink(rp.githubLink());
            project.setBuiltWith(resolveSkillRefs(rp.builtWith()));
            result.add(project);
        }
        return result;
    }

    // ── Courses ───────────────────────────────────────────────────────────────

    private List<Course> buildCourses(RawStudentDto raw) {
        if (raw.courses() == null) return List.of();

        List<Course> result = new ArrayList<>();
        for (RawCourseDto rc : raw.courses()) {
            Course course = new Course();
            course.setCourseId(UUID.randomUUID().toString());
            course.setTitle(rc.title());
            course.setDescription(rc.description());
            course.setProvider(rc.provider());
            course.setCovers(resolveSkillRefs(rc.covers()));
            result.add(course);
        }
        return result;
    }

    // ── Diplomas ──────────────────────────────────────────────────────────────

    private List<Diploma> buildDiplomas(RawStudentDto raw) {
        if (raw.diplomas() == null) return List.of();

        List<Diploma> result = new ArrayList<>();
        for (RawDiplomaDto rd : raw.diplomas()) {
            Diploma diploma = new Diploma();
            diploma.setDiplomaId(UUID.randomUUID().toString());
            diploma.setTitle(rd.title());
            diploma.setDescription(rd.description());
            diploma.setIssuer(rd.issuer());
            diploma.setCertifies(resolveSkillRefs(rd.certifies()));
            result.add(diploma);
        }
        return result;
    }

    // ── Shared skill resolution ───────────────────────────────────────────────

    /**
     * Normalises a list of raw skill references (from builtWith / covers / certifies)
     * to canonical {@link Skill} nodes via the shared taxonomy resolver.
     */
    private List<Skill> resolveSkillRefs(List<RawSkillRefDto> refs) {
        if (refs == null || refs.isEmpty()) return List.of();
        List<String> names = refs.stream()
                .filter(r -> r != null && r.name() != null)
                .map(RawSkillRefDto::name)
                .toList();
        return normalizationService.normaliseSkills(names);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}