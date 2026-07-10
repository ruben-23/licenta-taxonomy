package org.example.jobsmvp.ingestion.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.example.jobsmvp.models.nodes.Occupation;
import org.example.jobsmvp.repositories.OccupationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class OccupationNormalizationServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private ChatModel chatModel;

    @Mock
    private OccupationRepository occupationRepository;

    private final ObjectMapper realObjectMapper = new ObjectMapper();

    @InjectMocks
    private OccupationNormalizationService occupationNormalizationService;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(occupationNormalizationService, "objectMapper", realObjectMapper);
        ReflectionTestUtils.setField(occupationNormalizationService, "unmatchedOutputDir", "target/test-unmatched-occupations");
        occupationNormalizationService.init();
        List<Map<String, Object>> unmatchedBuffer = (List<Map<String, Object>>) ReflectionTestUtils.getField(occupationNormalizationService, "unmatchedBuffer");
        if (unmatchedBuffer != null) {
            unmatchedBuffer.clear();
        }
    }

    @Test
    void testResolveOccupation_exactMatch() {
        // Given
        String rawOccupation = "Software Engineer";
        Occupation expectedOccupation = new Occupation();
        expectedOccupation.setName(rawOccupation);

        when(occupationRepository.findByNameIgnoreCase(rawOccupation)).thenReturn(Optional.of(expectedOccupation));

        // When
        Optional<Occupation> result = occupationNormalizationService.resolveOccupation(rawOccupation);

        // Then
        assertTrue(result.isPresent());
        assertEquals(expectedOccupation, result.get());
    }

    @Test
    void testResolveOccupation_embeddingMatch() {
        // Given
        String rawOccupation = "Data Scientist";
        Occupation expectedOccupation = new Occupation();
        expectedOccupation.setName("Data Scientist");
        float[] embeddingVector = {1.0f, 2.0f, 3.0f};
        Embedding embedding = new Embedding(embeddingVector);

        when(occupationRepository.findByNameIgnoreCase(rawOccupation)).thenReturn(Optional.empty());
        when(embeddingModel.embed(rawOccupation)).thenReturn(Response.from(embedding));
        when(occupationRepository.findMostSimilarOccupation(any(double[].class), anyDouble())).thenReturn(Optional.of(expectedOccupation));

        // When
        Optional<Occupation> result = occupationNormalizationService.resolveOccupation(rawOccupation);

        // Then
        assertTrue(result.isPresent());
        assertEquals(expectedOccupation, result.get());
    }

    @Test
    void testResolveOccupation_llmMatched() throws Exception {
        // Given
        String rawOccupation = "Java Dev";
        String canonicalName = "Java Developer";
        Occupation expectedOccupation = new Occupation();
        expectedOccupation.setName(canonicalName);

        // Mock pre-LLM steps to fail
        when(occupationRepository.findByNameIgnoreCase(rawOccupation)).thenReturn(Optional.empty());
        when(embeddingModel.embed(rawOccupation)).thenReturn(Response.from(new Embedding(new float[]{0.1f})));
        when(occupationRepository.findMostSimilarOccupation(any(double[].class), anyDouble())).thenReturn(Optional.empty());

        // Mock LLM response
        String llmResponseJson = realObjectMapper.writeValueAsString(Map.of("matched", true, "name", canonicalName));
        when(chatModel.chat(anyString())).thenReturn(llmResponseJson);
        when(occupationRepository.findByNameIgnoreCase(canonicalName)).thenReturn(Optional.of(expectedOccupation));

        // When
        Optional<Occupation> result = occupationNormalizationService.resolveOccupation(rawOccupation);

        // Then
        assertTrue(result.isPresent());
        assertEquals(expectedOccupation, result.get());
    }

    @Test
    void testResolveOccupation_llmUnmatchedAndBuffered() throws Exception {
        // Given
        String rawOccupation = "Blockchain Wizard";
        String canonicalName = "Blockchain Developer";
        int layer = 3;
        String type = "Specialized Role";
        String parent = "Software Engineering & Architecture";

        // Mock pre-LLM steps to fail
        when(occupationRepository.findByNameIgnoreCase(rawOccupation)).thenReturn(Optional.empty());
        when(embeddingModel.embed(rawOccupation)).thenReturn(Response.from(new Embedding(new float[]{0.2f})));
        when(occupationRepository.findMostSimilarOccupation(any(double[].class), anyDouble())).thenReturn(Optional.empty());

        // Mock LLM response
        String llmResponseJson = realObjectMapper.writeValueAsString(Map.of(
                "matched", false, "name", canonicalName, "layer", layer, "type", type, "parent", parent
        ));
        when(chatModel.chat(anyString())).thenReturn(llmResponseJson);

        // Mock post-LLM steps
        when(embeddingModel.embed(canonicalName)).thenReturn(Response.from(new Embedding(new float[]{0.3f})));
        when(occupationRepository.save(any(Occupation.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Optional<Occupation> result = occupationNormalizationService.resolveOccupation(rawOccupation);

        // Then
        assertTrue(result.isPresent());
        Occupation newOccupation = result.get();
        assertEquals(canonicalName, newOccupation.getName());

        List<Map<String, Object>> unmatchedBuffer = (List<Map<String, Object>>) ReflectionTestUtils.getField(occupationNormalizationService, "unmatchedBuffer");
        assertNotNull(unmatchedBuffer);
        assertEquals(1, unmatchedBuffer.size());
        assertEquals(canonicalName, unmatchedBuffer.get(0).get("name"));
    }

    @Test
    void testIntegration_AllResolutionPaths() throws Exception {
        // 1. Exact Match setup
        String rawExact = "Backend Developer";
        Occupation exactOcc = new Occupation();
        exactOcc.setName(rawExact);
        when(occupationRepository.findByNameIgnoreCase(rawExact)).thenReturn(Optional.of(exactOcc));

        // 2. Embedding Match setup
        String rawEmbedding = "Data Analyst";
        Occupation embeddingOcc = new Occupation();
        embeddingOcc.setName("Data Analyst");
        when(occupationRepository.findByNameIgnoreCase(rawEmbedding)).thenReturn(Optional.empty());
        when(embeddingModel.embed(rawEmbedding)).thenReturn(Response.from(new Embedding(new float[]{1f, 1f, 1f})));

        // 3. LLM Matched setup
        String rawLlmMatched = "SRE";
        String canonicalLlmMatched = "Site Reliability Engineer";
        Occupation matchedOcc = new Occupation();
        matchedOcc.setName(canonicalLlmMatched);
        when(occupationRepository.findByNameIgnoreCase(rawLlmMatched)).thenReturn(Optional.empty());
        when(embeddingModel.embed(rawLlmMatched)).thenReturn(Response.from(new Embedding(new float[]{0.4f})));
        String llmMatchedResponseJson = realObjectMapper.writeValueAsString(Map.of("matched", true, "name", canonicalLlmMatched));
        when(chatModel.chat(org.mockito.ArgumentMatchers.contains(rawLlmMatched))).thenReturn(llmMatchedResponseJson);
        when(occupationRepository.findByNameIgnoreCase(canonicalLlmMatched)).thenReturn(Optional.of(matchedOcc));

        // 4. LLM New setup
        String rawLlmNew = "Prompt Engineer";
        String canonicalLlmNew = "Prompt Engineer";
        when(occupationRepository.findByNameIgnoreCase(rawLlmNew)).thenReturn(Optional.empty());
        when(embeddingModel.embed(rawLlmNew)).thenReturn(Response.from(new Embedding(new float[]{0.5f})));
        String llmNewResponseJson = realObjectMapper.writeValueAsString(Map.of("matched", false, "name", canonicalLlmNew, "layer", 3, "type", "Specialized Role", "parent", "AI & Machine Learning"));
        when(chatModel.chat(org.mockito.ArgumentMatchers.contains(rawLlmNew))).thenReturn(llmNewResponseJson);
        when(embeddingModel.embed(canonicalLlmNew)).thenReturn(Response.from(new Embedding(new float[]{0.6f})));
        when(occupationRepository.save(any(Occupation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Chained mock for findMostSimilarOccupation to return a value only for the first call
        when(occupationRepository.findMostSimilarOccupation(any(double[].class), anyDouble()))
                .thenReturn(Optional.of(embeddingOcc)) // For rawEmbedding
                .thenReturn(Optional.empty())          // For rawLlmMatched
                .thenReturn(Optional.empty());         // For rawLlmNew

        // --- WHEN ---
        Optional<Occupation> resultExact = occupationNormalizationService.resolveOccupation(rawExact);
        Optional<Occupation> resultEmbedding = occupationNormalizationService.resolveOccupation(rawEmbedding);
        Optional<Occupation> resultLlmMatched = occupationNormalizationService.resolveOccupation(rawLlmMatched);
        Optional<Occupation> resultLlmNew = occupationNormalizationService.resolveOccupation(rawLlmNew);

        // --- THEN ---
        assertTrue(resultExact.isPresent() && resultExact.get().getName().equals(rawExact));
        assertTrue(resultEmbedding.isPresent() && resultEmbedding.get().getName().equals("Data Analyst"));
        assertTrue(resultLlmMatched.isPresent() && resultLlmMatched.get().getName().equals(canonicalLlmMatched));
        assertTrue(resultLlmNew.isPresent() && resultLlmNew.get().getName().equals(canonicalLlmNew));

        List<Map<String, Object>> unmatchedBuffer = (List<Map<String, Object>>) ReflectionTestUtils.getField(occupationNormalizationService, "unmatchedBuffer");
        assertEquals(1, unmatchedBuffer.size());
        assertEquals(canonicalLlmNew, unmatchedBuffer.get(0).get("name"));
    }
}