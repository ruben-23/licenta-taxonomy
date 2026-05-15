package org.example.jobsmvp.ingestion.student.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Raw student DTO mapped directly from the student_data.json file.
 *
 * IDs present in the JSON (student_id, project_id, etc.) are intentionally
 * ignored during ingestion — fresh UUIDs are generated for every entity by
 * {@link org.example.jobsmvp.ingestion.student.transform.StudentGraphTransformService}
 * to guarantee stable, collision-free graph node identifiers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawStudentDto(

        /** Original file ID — kept only for deduplication fingerprinting, never stored. */
        @JsonProperty("student_id")
        String studentId,

        @JsonProperty("name")
        String name,

        @JsonProperty("major")
        String major,

        @JsonProperty("graduation_year")
        Integer graduationYear,

        @JsonProperty("current_year_of_study")
        Integer currentYearOfStudy,

        @JsonProperty("degree_level")
        String degreeLevel,

        @JsonProperty("knownTechnologies")
        List<RawKnowsDto> knownTechnologies,

        @JsonProperty("projects")
        List<RawProjectDto> projects,

        @JsonProperty("courses")
        List<RawCourseDto> courses,

        @JsonProperty("diplomas")
        List<RawDiplomaDto> diplomas

) {

    // ── Nested raw DTOs ───────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawKnowsDto(
            @JsonProperty("proficiency_level")
            Integer proficiencyLevel,

            @JsonProperty("years_of_experience")
            Double yearsOfExperience,

            @JsonProperty("technology")
            RawSkillRefDto technology
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawSkillRefDto(
            /** Original taxonomy ID from the file — used only for deduplication. */
            @JsonProperty("skill_id")
            String skillId,

            @JsonProperty("name")
            String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawProjectDto(
            /** Original file ID — kept only for deduplication, never persisted. */
            @JsonProperty("project_id")
            String projectId,

            @JsonProperty("title")
            String title,

            @JsonProperty("description")
            String description,

            @JsonProperty("github_link")
            String githubLink,

            @JsonProperty("builtWith")
            List<RawSkillRefDto> builtWith
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawCourseDto(
            @JsonProperty("course_id")
            String courseId,

            @JsonProperty("title")
            String title,

            @JsonProperty("description")
            String description,

            @JsonProperty("provider")
            String provider,

            @JsonProperty("covers")
            List<RawSkillRefDto> covers
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawDiplomaDto(
            @JsonProperty("diploma_id")
            String diplomaId,

            @JsonProperty("title")
            String title,

            @JsonProperty("description")
            String description,

            @JsonProperty("issuer")
            String issuer,

            @JsonProperty("certifies")
            List<RawSkillRefDto> certifies
    ) {}
}