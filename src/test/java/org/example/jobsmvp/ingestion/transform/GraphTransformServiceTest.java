package org.example.jobsmvp.ingestion.transform;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.example.jobsmvp.ingestion.extraction.ExtractedEntities;
import org.example.jobsmvp.ingestion.normalization.EntityNormalizationService;
import org.example.jobsmvp.ingestion.normalization.OccupationNormalizationService;
import org.example.jobsmvp.ingestion.source.RawJobDto;
import org.example.jobsmvp.models.nodes.Company;
import org.example.jobsmvp.models.nodes.Occupation;
import org.example.jobsmvp.models.nodes.Skill;
import org.example.jobsmvp.repositories.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class GraphTransformServiceTest {

    @Mock
    private EntityNormalizationService normalizationService;

    @Mock
    private OccupationNormalizationService occupationNormalizationService;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private GraphTransformService graphTransformService;

    private RawJobDto rawJob;
    private ExtractedEntities entities;
    private String cleanDescription;
    private String companyDescription;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        rawJob = new RawJobDto(null, "Tech Corp", null, "Software Engineer", null, null, null, null, null, null, null, null, null, null, null);

        entities = new ExtractedEntities(
                "Tech Corp", false, "Software Engineer", "Mid-Senior level", "Full-time", null, true, null, 120000, "USD", List.of("Java"), List.of("Communication"), "Software Engineer", "IT", "100-500"
        );

        cleanDescription = "Develop and maintain web applications.";
        companyDescription = "A leading provider of innovative technology solutions.";

        when(embeddingModel.embed(anyString())).thenReturn(Response.from(new Embedding(new float[]{1.0f, 2.0f, 3.0f})));
        when(normalizationService.normaliseSkills(anyList())).thenReturn(List.of(new Skill()));
        when(occupationNormalizationService.resolveOccupation(anyString())).thenReturn(Optional.of(new Occupation()));
    }

    @Test
    void testTransform_createNewCompany() {
        // Given: No existing company
        when(companyRepository.findByNameIgnoreCase("Tech Corp")).thenReturn(Optional.empty());
        when(companyRepository.findMostSimilarCompany(any(double[].class), anyDouble())).thenReturn(Optional.empty());

        // When
        JobGraphBundle bundle = graphTransformService.transform(rawJob, entities, cleanDescription, companyDescription);

        // Then
        assertNotNull(bundle.company());
        assertEquals("Tech Corp", bundle.company().getName());
        assertEquals("IT", bundle.company().getIndustry());
        assertNotNull(bundle.company().getCompany_id());
        assertFalse(bundle.company().getTextEmbedding().isEmpty());
    }

    @Test
    void testTransform_findsExactCompany() {
        // Given: An existing company with an exact name match
        Company existingCompany = new Company();
        existingCompany.setCompany_id(UUID.randomUUID().toString());
        existingCompany.setName("Tech Corp");
        when(companyRepository.findByNameIgnoreCase("Tech Corp")).thenReturn(Optional.of(existingCompany));

        // When
        JobGraphBundle bundle = graphTransformService.transform(rawJob, entities, cleanDescription, companyDescription);

        // Then
        assertNotNull(bundle.company());
        assertEquals(existingCompany.getCompany_id(), bundle.company().getCompany_id());
        assertEquals("Tech Corp", bundle.company().getName());
        // Check that new info is added for enrichment
        assertEquals("IT", bundle.company().getIndustry());
    }

    @Test
    void testTransform_findsSimilarCompany() {
        // Given: An existing company with a similar name
        Company existingCompany = new Company();
        existingCompany.setCompany_id(UUID.randomUUID().toString());
        existingCompany.setName("Tech Corporation");
        when(companyRepository.findByNameIgnoreCase("Tech Corp")).thenReturn(Optional.empty());
        when(companyRepository.findMostSimilarCompany(any(double[].class), anyDouble())).thenReturn(Optional.of(existingCompany));

        // When
        JobGraphBundle bundle = graphTransformService.transform(rawJob, entities, cleanDescription, companyDescription);

        // Then
        assertNotNull(bundle.company());
        assertEquals(existingCompany.getCompany_id(), bundle.company().getCompany_id());
        assertEquals("Tech Corporation", bundle.company().getName());
        assertEquals("IT", bundle.company().getIndustry());
    }

    @Test
    void testTransform_enrichesExistingCompany() {
        // Given
        Company existingCompany = new Company();
        existingCompany.setCompany_id(UUID.randomUUID().toString());
        existingCompany.setName("Tech Corp");
        // Existing company is missing industry and description
        existingCompany.setIndustry(null);
        existingCompany.setDescription(null);

        when(companyRepository.findByNameIgnoreCase("Tech Corp")).thenReturn(Optional.of(existingCompany));

        // When
        JobGraphBundle bundle = graphTransformService.transform(rawJob, entities, cleanDescription, companyDescription);

        // Then
        Company companyPayload = bundle.company();
        assertEquals(existingCompany.getCompany_id(), companyPayload.getCompany_id());
        assertEquals("IT", companyPayload.getIndustry());
        assertEquals(companyDescription, companyPayload.getDescription());
        // Embedding should be null in the payload to avoid overwriting
        assertNull(companyPayload.getTextEmbedding());
    }
}