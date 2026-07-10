package org.example.jobsmvp.ingestion.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.example.jobsmvp.models.nodes.Skill;
import org.example.jobsmvp.repositories.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class EntityNormalizationServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private ChatModel chatModel;

    @Mock
    private SkillRepository skillRepository;

    @InjectMocks
    private EntityNormalizationService entityNormalizationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        entityNormalizationService.clearCache();
    }

    @Test
    void testNormaliseSkills_exactMatch() {
        // Given
        String rawSkill = "Java";
        Skill expectedSkill = new Skill();
        expectedSkill.setName(rawSkill);

        when(skillRepository.findByNameIgnoreCaseAndLayer(rawSkill, 3)).thenReturn(Optional.of(expectedSkill));

        // When
        List<Skill> result = entityNormalizationService.normaliseSkills(List.of(rawSkill));

        // Then
        assertEquals(1, result.size());
        assertEquals(expectedSkill, result.get(0));
    }

    @Test
    void testNormaliseSkills_aliasMatch() {
        // Given
        String rawSkill = "py";
        String canonicalSkillName = "Python";
        Skill expectedSkill = new Skill();
        expectedSkill.setName(canonicalSkillName);

        when(skillRepository.findByNameIgnoreCaseAndLayer(canonicalSkillName, 3)).thenReturn(Optional.of(expectedSkill));

        // When
        List<Skill> result = entityNormalizationService.normaliseSkills(List.of(rawSkill));

        // Then
        assertEquals(1, result.size());
        assertEquals(expectedSkill, result.get(0));
    }

    @Test
    void testNormaliseSkills_embeddingMatch() {
        // Given
        String rawSkill = "Machine Learning";
        Skill expectedSkill = new Skill();
        expectedSkill.setName(rawSkill);
        float[] embeddingVector = new float[]{1.0f, 2.0f, 3.0f};
        Embedding embedding = new Embedding(embeddingVector);

        when(skillRepository.findByNameIgnoreCaseAndLayer(rawSkill, 3)).thenReturn(Optional.empty());
        when(embeddingModel.embed(rawSkill)).thenReturn(Response.from(embedding));
        when(skillRepository.findMostSimilarSkill(any(double[].class), anyDouble())).thenReturn(Optional.of(expectedSkill));

        // When
        List<Skill> result = entityNormalizationService.normaliseSkills(List.of(rawSkill));

        // Then
        assertEquals(1, result.size());
        assertEquals(expectedSkill, result.get(0));
    }

    @Test
    void testNormaliseSkills_llmClassification() throws Exception {
        // Given
        String rawSkill = "New-fangled Skill";
        String canonicalName = "New-fangled Skill";
        String skillGroup = "Cognitive & Analytical";

        // Mock pre-LLM steps to fail
        when(skillRepository.findByNameIgnoreCaseAndLayer(rawSkill, 3)).thenReturn(Optional.empty());
        when(embeddingModel.embed(rawSkill)).thenReturn(Response.from(new Embedding(new float[]{0.1f})));
        when(skillRepository.findMostSimilarSkill(any(double[].class), anyDouble())).thenReturn(Optional.empty());

        // Mock LLM response
        String llmResponseJson = objectMapper.writeValueAsString(java.util.Map.of(
                "canonicalName", canonicalName,
                "skillGroup", skillGroup
        ));
        when(chatModel.chat(anyString())).thenReturn(llmResponseJson);

        // Mock post-LLM steps
        when(embeddingModel.embed(canonicalName)).thenReturn(Response.from(new Embedding(new float[]{0.2f})));
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> {
            Skill skillToSave = invocation.getArgument(0);
            skillToSave.setSkillId("new-id");
            return skillToSave;
        });

        // When
        List<Skill> result = entityNormalizationService.normaliseSkills(List.of(rawSkill));

        // Then
        assertEquals(1, result.size());
        Skill newSkill = result.get(0);
        assertEquals(canonicalName, newSkill.getName());
        assertEquals(skillGroup, newSkill.getParent());
        assertNotNull(newSkill.getSkillId());
    }

    @Test
    void testNormaliseSkills_integration() throws Exception {
        // Given
        String exactMatchSkill = "Java";
        String aliasSkill = "py";
        String embeddingSkill = "ML";
        String llmSkill = "Quantum Programming";

        List<String> rawSkills = List.of(exactMatchSkill, aliasSkill, embeddingSkill, llmSkill);

        // -- Mocks for exact match --
        Skill javaSkill = new Skill();
        javaSkill.setName(exactMatchSkill);
        when(skillRepository.findByNameIgnoreCaseAndLayer(exactMatchSkill, 3)).thenReturn(Optional.of(javaSkill));

        // -- Mocks for alias match --
        String pythonCanonical = "Python";
        Skill pythonSkill = new Skill();
        pythonSkill.setName(pythonCanonical);
        when(skillRepository.findByNameIgnoreCaseAndLayer(pythonCanonical, 3)).thenReturn(Optional.of(pythonSkill));

        // -- Mocks for embedding match --
        String mlCanonical = "Machine Learning";
        Skill mlSkill = new Skill();
        mlSkill.setName(mlCanonical);
        when(skillRepository.findByNameIgnoreCaseAndLayer(embeddingSkill, 3)).thenReturn(Optional.empty());
        when(embeddingModel.embed(embeddingSkill)).thenReturn(Response.from(new Embedding(new float[]{1.0f})));

        // -- Mocks for LLM fallback --
        String qpCanonical = "Quantum Programming";
        String qpGroup = "Programming & Scripting";
        when(skillRepository.findByNameIgnoreCaseAndLayer(llmSkill, 3)).thenReturn(Optional.empty());
        when(embeddingModel.embed(llmSkill)).thenReturn(Response.from(new Embedding(new float[]{2.0f})));
        String llmResponseJson = objectMapper.writeValueAsString(java.util.Map.of(
                "canonicalName", qpCanonical,
                "skillGroup", qpGroup
        ));
        when(chatModel.chat(anyString())).thenReturn(llmResponseJson);
        when(embeddingModel.embed(qpCanonical)).thenReturn(Response.from(new Embedding(new float[]{3.0f})));
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> {
            Skill skillToSave = invocation.getArgument(0);
            if (skillToSave.getName().equals(qpCanonical)) {
                skillToSave.setSkillId("qp-id");
            }
            return skillToSave;
        });

        // Chained mock to ensure embedding search succeeds for "ML" but fails for "Quantum Programming"
        when(skillRepository.findMostSimilarSkill(any(double[].class), anyDouble()))
                .thenReturn(Optional.of(mlSkill))
                .thenReturn(Optional.empty());

        // When
        List<Skill> result = entityNormalizationService.normaliseSkills(rawSkills);

        // Then
        assertEquals(4, result.size());
        assertEquals(exactMatchSkill, result.get(0).getName());
        assertEquals(pythonCanonical, result.get(1).getName());
        assertEquals(mlCanonical, result.get(2).getName());
        assertEquals(qpCanonical, result.get(3).getName());
        assertEquals(qpGroup, result.get(3).getParent());
    }
}