//
//
//package org.example.jobsmvp.ingestion.transform;
//
//import dev.langchain4j.data.embedding.Embedding;
//import dev.langchain4j.model.embedding.EmbeddingModel;
//import org.example.jobsmvp.ingestion.extraction.ExtractedEntities;
//import org.example.jobsmvp.ingestion.normalization.EntityNormalizationService;
//import org.example.jobsmvp.ingestion.normalization.OccupationNormalizationService;
//import org.example.jobsmvp.ingestion.source.RawJobDto;
//import org.example.jobsmvp.models.nodes.Company;
//import org.example.jobsmvp.models.nodes.Job;
//import org.example.jobsmvp.models.nodes.Occupation;
//import org.example.jobsmvp.models.nodes.Skill;
//import org.example.jobsmvp.models.relationships.Posts;
//import org.example.jobsmvp.models.relationships.Requires;
//import org.springframework.stereotype.Service;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
///**
// * Converts raw job data + extracted entities into graph-ready node and
// * relationship objects.
// *
// * Does NOT write to the database — output is handed to
// * {@link org.example.jobsmvp.ingestion.graph.GraphIngestionService}.
// *
// * Changes from previous version:
// *  - Technology → Skill (node + relationships)
// *  - normaliseTechnologies() → normaliseSkills() with merged technical + soft skills
// *  - resolveOccupation() wired in; Occupation added to the bundle
// *  - ExtractedEntities accessors updated (technicalSkills, softSkills, occupation)
// */
//@Service
//public class GraphTransformService {
//
//    private final EntityNormalizationService normalizationService;
//    private final OccupationNormalizationService occupationNormalizationService;
//    private final EmbeddingModel embeddingModel;
//
//    public GraphTransformService(
//            EntityNormalizationService normalizationService,
//            OccupationNormalizationService occupationNormalizationService,
//            EmbeddingModel embeddingModel
//    ) {
//        this.normalizationService            = normalizationService;
//        this.occupationNormalizationService  = occupationNormalizationService;
//        this.embeddingModel                  = embeddingModel;
//    }
//
//    /**
//     * Builds a {@link JobGraphBundle} containing all nodes and edges for a
//     * single job posting.
//     *
//     * @param raw         original API DTO
//     * @param entities    LLM-extracted entities
//     * @param cleanedDesc preprocessed description (used for embedding)
//     * @return bundle ready for graph persistence
//     */
//    public JobGraphBundle transform(RawJobDto raw, ExtractedEntities entities, String cleanedDesc) {
//        Company    company    = buildCompany(raw, entities);
//        Job        job        = buildJob(raw, entities, cleanedDesc);
//        Posts      postsRel   = buildPostsRelationship();
//        Occupation occupation = resolveOccupation(entities);
//
//        // Merge technical skills and soft skills into one normalisation pass
//        List<String> allRawSkills = new ArrayList<>();
//        if (entities.technicalSkills() != null) allRawSkills.addAll(entities.technicalSkills());
//        if (entities.softSkills()      != null) allRawSkills.addAll(entities.softSkills());
//
//        List<Skill>   skills      = normalizationService.normaliseSkills(allRawSkills);
//        List<Requires> requiresRels = buildRequiresRelationships(skills);
//
//        return new JobGraphBundle(company, job, postsRel, occupation, skills, requiresRels);
//    }
//
//    // ── Company ──────────────────────────────────────────────────────────────
//
//    private Company buildCompany(RawJobDto raw, ExtractedEntities entities) {
//        String name = firstNonNull(
//                entities.companyName(),
//                raw.employerName(),
//                "Unknown Company"
//        );
//
//        Embedding textEmbedding = embeddingModel.embed(name).content();
//
//        Company company = new Company();
//        company.setCompany_id(UUID.nameUUIDFromBytes(name.toLowerCase().getBytes()).toString());
//        company.setName(name);
//        company.setIndustry(entities.industry());
//        company.setSize(entities.companySize());
//        company.setTextEmbedding(
//                textEmbedding.vectorAsList().stream().map(Float::doubleValue).toList()
//        );
//        return company;
//    }
//
//    // ── Job ──────────────────────────────────────────────────────────────────
//
//    private Job buildJob(RawJobDto raw, ExtractedEntities entities, String cleanedDesc) {
//        String title      = firstNonNull(entities.jobTitle(), raw.jobTitle(), "Unknown Role");
//        String embedInput = title + " " + cleanedDesc;
//        Embedding textEmbedding = embeddingModel.embed(
//                embedInput.length() > 2000 ? embedInput.substring(0, 2000) : embedInput
//        ).content();
//
//        Job job = new Job();
//        job.setJob_id(firstNonNull(raw.jobId(), UUID.randomUUID().toString()));
//        job.setTitle(title);
//        job.setDescription(cleanedDesc);
//        job.setExperienceLevel(entities.experienceLevel());
//        job.setJobType(firstNonNull(entities.jobType(), raw.jobEmploymentType()));
//        job.setContractDuration(entities.contractDuration());
//        job.setRemote(firstNonNullBool(entities.remote(), raw.jobIsRemote()));
//        job.setSalary(firstNonNullInt(entities.salary(), deriveAvgSalary(raw)));
//        job.setCurrency(firstNonNull(entities.currency(), raw.jobSalaryCurrency()));
//        job.setPostedDate(raw.jobPostedAt());
//        job.setExpiresAt(raw.jobExpiresAt());
//        return job;
//    }
//
//    // ── Occupation ───────────────────────────────────────────────────────────
//
//    /**
//     * Resolves the extracted occupation string against the taxonomy.
//     * Falls back to the raw job title if no occupation was extracted.
//     */
//    private Occupation resolveOccupation(ExtractedEntities entities) {
//        String rawOccupation = firstNonNull(entities.occupation(), entities.jobTitle());
//        Optional<Occupation> resolved = occupationNormalizationService.resolveOccupation(rawOccupation);
//        return resolved.orElse(null);
//    }
//
//    // ── Relationships ─────────────────────────────────────────────────────────
//
//    private Posts buildPostsRelationship() {
//        Posts posts = new Posts();
//        posts.setIsActive(true);
//        return posts;
//    }
//
//    private List<Requires> buildRequiresRelationships(List<Skill> skills) {
//        return skills.stream().map(skill -> {
//            Requires req = new Requires();
//            req.setImportance("required");
//            req.setMinProficiency(1);
//            return req;
//        }).toList();
//    }
//
//    // ── Utilities ─────────────────────────────────────────────────────────────
//
//    private static String firstNonNull(String... values) {
//        for (String v : values) {
//            if (v != null && !v.isBlank()) return v;
//        }
//        return null;
//    }
//
//    private static Boolean firstNonNullBool(Boolean... values) {
//        for (Boolean v : values) {
//            if (v != null) return v;
//        }
//        return false;
//    }
//
//    private static Integer firstNonNullInt(Integer... values) {
//        for (Integer v : values) {
//            if (v != null) return v;
//        }
//        return null;
//    }
//
//    private static Integer deriveAvgSalary(RawJobDto raw) {
//        if (raw.jobMinSalary() == null && raw.jobMaxSalary() == null) return null;
//        if (raw.jobMinSalary() == null) return raw.jobMaxSalary();
//        if (raw.jobMaxSalary() == null) return raw.jobMinSalary();
//        return (raw.jobMinSalary() + raw.jobMaxSalary()) / 2;
//    }
//}


package org.example.jobsmvp.ingestion.transform;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.example.jobsmvp.ingestion.extraction.ExtractedEntities;
import org.example.jobsmvp.ingestion.normalization.EntityNormalizationService;
import org.example.jobsmvp.ingestion.normalization.OccupationNormalizationService;
import org.example.jobsmvp.ingestion.source.RawJobDto;
import org.example.jobsmvp.models.nodes.Company;
import org.example.jobsmvp.models.nodes.Job;
import org.example.jobsmvp.models.nodes.Occupation;
import org.example.jobsmvp.models.nodes.Skill;
import org.example.jobsmvp.models.relationships.Posts;
import org.example.jobsmvp.models.relationships.Requires;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Converts raw job data + extracted entities into graph-ready node and
 * relationship objects.
 *
 * Does NOT write to the database — output is handed to
 * {@link org.example.jobsmvp.ingestion.graph.GraphIngestionService}.
 *
 * Changes from previous version:
 *  - {@code transform()} now accepts {@code cleanDescription} (LLM-reformatted,
 *    stored as {@code clean_description} on Job) and {@code companyDescription}
 *    (company overview blurb, stored as {@code description} on Company).
 *  - Job embedding uses {@code cleanDescription} instead of the raw preprocessed text.
 */
@Service
public class GraphTransformService {

    private final EntityNormalizationService     normalizationService;
    private final OccupationNormalizationService occupationNormalizationService;
    private final EmbeddingModel                 embeddingModel;

    public GraphTransformService(
            EntityNormalizationService     normalizationService,
            OccupationNormalizationService occupationNormalizationService,
            EmbeddingModel                 embeddingModel
    ) {
        this.normalizationService           = normalizationService;
        this.occupationNormalizationService = occupationNormalizationService;
        this.embeddingModel                 = embeddingModel;
    }

    /**
     * Builds a {@link JobGraphBundle} containing all nodes and edges for a
     * single job posting.
     *
     * @param raw                original API DTO
     * @param entities           LLM-extracted entities
     * @param cleanDescription   LLM-reformatted, signal-only description
     *                           (stored as {@code clean_description} on Job)
     * @param companyDescription company "About Us" blurb extracted by the LLM
     *                           (stored as {@code description} on Company; may be null)
     * @return bundle ready for graph persistence
     */
    public JobGraphBundle transform(
            RawJobDto         raw,
            ExtractedEntities entities,
            String            cleanDescription,
            String            companyDescription
    ) {
        Company    company    = buildCompany(raw, entities, companyDescription);
        Job        job        = buildJob(raw, entities, cleanDescription);
        Posts      postsRel   = buildPostsRelationship();
        Occupation occupation = resolveOccupation(entities);

        List<String> allRawSkills = new ArrayList<>();
        if (entities.technicalSkills() != null) allRawSkills.addAll(entities.technicalSkills());
        if (entities.softSkills()      != null) allRawSkills.addAll(entities.softSkills());

        List<Skill>    skills       = normalizationService.normaliseSkills(allRawSkills);
        List<Requires> requiresRels = buildRequiresRelationships(skills);

        return new JobGraphBundle(company, job, postsRel, occupation, skills, requiresRels);
    }

    // ── Company ───────────────────────────────────────────────────────────────

    private Company buildCompany(RawJobDto raw, ExtractedEntities entities, String companyDescription) {
        String name = firstNonNull(
                entities.companyName(),
                raw.employerName(),
                "Unknown Company"
        );

        Embedding textEmbedding = embeddingModel.embed(name).content();

        Company company = new Company();
        company.setCompany_id(UUID.nameUUIDFromBytes(name.toLowerCase().getBytes()).toString());
        company.setName(name);
        company.setDescription(companyDescription);          // ← new field
        company.setIndustry(entities.industry());
        company.setSize(entities.companySize());
        company.setTextEmbedding(
                textEmbedding.vectorAsList().stream().map(Float::doubleValue).toList()
        );
        return company;
    }

    // ── Job ───────────────────────────────────────────────────────────────────

    private Job buildJob(RawJobDto raw, ExtractedEntities entities, String cleanDescription) {
        String title      = firstNonNull(entities.jobTitle(), raw.jobTitle(), "Unknown Role");
        String embedInput = title + " " + cleanDescription;
        Embedding textEmbedding = embeddingModel.embed(
//                embedInput.length() > 2000 ? embedInput.substring(0, 2000) : embedInput
                embedInput
        ).content();

        Job job = new Job();
        job.setJob_id(firstNonNull(raw.jobId(), UUID.randomUUID().toString()));
        job.setTitle(title);
        job.setDescription(cleanDescription);                // preprocessed text (kept for compatibility)
        job.setCleanDescription(cleanDescription);           // ← new field: LLM-reformatted signal-only text
        job.setExperienceLevel(entities.experienceLevel());
        job.setJobType(firstNonNull(entities.jobType(), raw.jobEmploymentType()));
        job.setContractDuration(entities.contractDuration());
        job.setRemote(firstNonNullBool(entities.remote(), raw.jobIsRemote()));
        job.setSalary(firstNonNullInt(entities.salary(), deriveAvgSalary(raw)));
        job.setCurrency(firstNonNull(entities.currency(), raw.jobSalaryCurrency()));
        job.setPostedDate(raw.jobPostedAt());
        job.setExpiresAt(raw.jobExpiresAt());
        job.setTextEmbedding(
                textEmbedding.vectorAsList().stream().map(Float::doubleValue).toList()
        );
        return job;
    }

    // ── Occupation ────────────────────────────────────────────────────────────

    private Occupation resolveOccupation(ExtractedEntities entities) {
        String rawOccupation = firstNonNull(entities.occupation(), entities.jobTitle());
        Optional<Occupation> resolved = occupationNormalizationService.resolveOccupation(rawOccupation);
        return resolved.orElse(null);
    }

    // ── Relationships ─────────────────────────────────────────────────────────

    private Posts buildPostsRelationship() {
        Posts posts = new Posts();
        posts.setIsActive(true);
        return posts;
    }

    private List<Requires> buildRequiresRelationships(List<Skill> skills) {
        return skills.stream().map(skill -> {
            Requires req = new Requires();
            req.setImportance("required");
            req.setMinProficiency(1);
            return req;
        }).toList();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static Boolean firstNonNullBool(Boolean... values) {
        for (Boolean v : values) {
            if (v != null) return v;
        }
        return false;
    }

    private static Integer firstNonNullInt(Integer... values) {
        for (Integer v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private static Integer deriveAvgSalary(RawJobDto raw) {
        if (raw.jobMinSalary() == null && raw.jobMaxSalary() == null) return null;
        if (raw.jobMinSalary() == null) return raw.jobMaxSalary();
        if (raw.jobMaxSalary() == null) return raw.jobMinSalary();
        return (raw.jobMinSalary() + raw.jobMaxSalary()) / 2;
    }
}