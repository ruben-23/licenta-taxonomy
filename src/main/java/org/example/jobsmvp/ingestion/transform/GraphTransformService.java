package org.example.jobsmvp.ingestion.transform;

import dev.langchain4j.data.embedding.Embedding;
import org.example.jobsmvp.ingestion.deduplication.DeduplicationService;
import org.example.jobsmvp.ingestion.extraction.ExtractedEntities;
import org.example.jobsmvp.ingestion.normalization.EntityNormalizationService;
import org.example.jobsmvp.ingestion.source.RawJobDto;
import org.example.jobsmvp.models.nodes.Company;
import org.example.jobsmvp.models.nodes.Job;
import org.example.jobsmvp.models.nodes.Technology;
import org.example.jobsmvp.models.relationships.Posts;
import org.example.jobsmvp.models.relationships.Requires;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;


/**
 * Converts raw job data + extracted entities into graph-ready node and relationship objects.
 *
 * Does NOT write to the database — output is handed to {@link org.example.jobsmvp.ingestion.graph.GraphIngestionService}.
 */
@Service
public class GraphTransformService {

    private final EntityNormalizationService normalizationService;
    private final DeduplicationService deduplicationService;
    private final EmbeddingModel embeddingModel;

    public GraphTransformService(
            EntityNormalizationService normalizationService,
            DeduplicationService deduplicationService,
            EmbeddingModel embeddingModel
    ) {
        this.normalizationService = normalizationService;
        this.deduplicationService = deduplicationService;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Builds a {@link JobGraphBundle} containing all nodes and edges for a single job posting.
     *
     * @param raw        original API DTO
     * @param entities   LLM-extracted entities
     * @param cleanedDesc preprocessed description (used for embedding)
     * @return bundle ready for graph persistence
     */
    public JobGraphBundle transform(RawJobDto raw, ExtractedEntities entities, String cleanedDesc) {
        Company company  = buildCompany(raw, entities);
        Job     job      = buildJob(raw, entities, cleanedDesc);
        Posts   postsRel = buildPostsRelationship();

        List<Technology> technologies = normalizationService.normaliseTechnologies(
                entities.technologies()
        );

        List<Requires> requiresRels = buildRequiresRelationships(entities, technologies);

        return new JobGraphBundle(company, job, postsRel, technologies, requiresRels);
    }

    // ── Company ─────────────────────────────────────────────────────────────

    private Company buildCompany(RawJobDto raw, ExtractedEntities entities) {
        String name = firstNonNull(
                entities.companyName(),
                raw.employerName(),
                "Unknown Company"
        );

        Embedding textEmbedding = embeddingModel.embed(name).content();

        Company company = new Company();
        company.setCompany_id(UUID.nameUUIDFromBytes(name.toLowerCase().getBytes()).toString());
        company.setName(name);
        company.setIndustry(entities.industry());
        company.setSize(entities.companySize());

        // Convert List<Float> to List<Double>
        List<Double> doubleEmbedding = textEmbedding.vectorAsList()
                .stream()
                .map(Float::doubleValue)
                .toList();

        company.setTextEmbedding(doubleEmbedding);
        return company;
    }

    // ── Job ─────────────────────────────────────────────────────────────────

    private Job buildJob(RawJobDto raw, ExtractedEntities entities, String cleanedDesc) {
        String title = firstNonNull(entities.jobTitle(), raw.jobTitle(), "Unknown Role");
        String embedInput = title + " " + cleanedDesc;
        Embedding textEmbedding = embeddingModel.embed(
                embedInput.length() > 2000 ? embedInput.substring(0, 2000) : embedInput
        ).content();

        Job job = new Job();
        job.setJob_id(firstNonNull(raw.jobId(), UUID.randomUUID().toString()));
        job.setTitle(title);
        job.setDescription(cleanedDesc);
        job.setExperienceLevel(entities.experienceLevel());
        job.setJobType(firstNonNull(entities.jobType(), raw.jobEmploymentType()));
        job.setContractDuration(entities.contractDuration());
        job.setRemote(firstNonNullBool(entities.remote(), raw.jobIsRemote()));
        job.setSalary(firstNonNullInt(entities.salary(), deriveAvgSalary(raw)));
        job.setCurrency(firstNonNull(entities.currency(), raw.jobSalaryCurrency()));
        job.setPostedDate(raw.jobPostedAt());
        job.setExpiresAt(raw.jobExpiresAt());
//        job.setTextEmbedding(textEmbedding.vectorAsList());
        return job;
    }

    // ── Relationships ────────────────────────────────────────────────────────

    private Posts buildPostsRelationship() {
        Posts posts = new Posts();
        posts.setIsActive(true);
        return posts;
    }

    private List<Requires> buildRequiresRelationships(ExtractedEntities entities, List<Technology> techs) {
        return techs.stream().map(tech -> {
            Requires req = new Requires();
            req.setImportance("required");
            req.setMinProficiency(1);
            return req;
        }).toList();
    }

    // ── Utilities ────────────────────────────────────────────────────────────

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

    private static double[] toDoubleArray(float[] f) {
        double[] d = new double[f.length];
        for (int i = 0; i < f.length; i++) d[i] = f[i];
        return d;
    }
}
