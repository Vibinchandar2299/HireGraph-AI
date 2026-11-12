package com.hiregraph;

import com.hiregraph.client.AiServiceClient;
import com.hiregraph.dto.request.PredictionRequest;
import com.hiregraph.dto.response.PredictionResponse;
import com.hiregraph.dto.response.SubgraphResponse;
import com.hiregraph.entity.Prediction;
import com.hiregraph.exception.AiServiceException;
import com.hiregraph.exception.ResourceNotFoundException;
import com.hiregraph.repository.PredictionRepository;
import com.hiregraph.service.DataIngestionService;
import com.hiregraph.service.GraphService;
import com.hiregraph.service.PredictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
public class PredictionServiceAndIntegrationTest {

    @Autowired
    private PredictionService predictionService;

    @Autowired
    private PredictionRepository predictionRepository;

    @Autowired
    private DataIngestionService ingestionService;

    @Autowired
    private GraphService graphService;

    @MockBean
    private AiServiceClient aiServiceClient;

    @BeforeEach
    void setup() {
        ingestionService.ingestData(false);

        // Clean up predictions for A00001 before test
        Optional<Prediction> existing = predictionRepository.findByApplicationIdAndModelNameAndModelVersion(
                "A00001", "AMRG-GraphSAGE", "research-v1"
        );
        existing.ifPresent(predictionRepository::delete);
    }

    @Test
    void testLivePredictionAndPersistence() {
        Map<String, Object> mockAiResult = new HashMap<>();
        mockAiResult.put("application_id", "A00001");
        mockAiResult.put("predicted_status", 1);
        mockAiResult.put("predicted_probability", 0.8063023686408997);
        mockAiResult.put("model_name", "AMRG-GraphSAGE");
        mockAiResult.put("model_version", "research-v1");

        when(aiServiceClient.predictApplication("A00001")).thenReturn(mockAiResult);

        PredictionRequest request = new PredictionRequest("AMRG-GraphSAGE", "research-v1", false);
        PredictionResponse response = predictionService.predictApplication("A00001", request);

        assertNotNull(response);
        assertEquals("A00001", response.getApplicationId());
        assertEquals("SELECTED", response.getPredictedStatus());
        assertEquals(0.8063023686408997, response.getEstimatedProbability(), 1e-6);

        // Verify persisted in PostgreSQL
        Optional<Prediction> persisted = predictionRepository.findByApplicationIdAndModelNameAndModelVersion(
                "A00001", "AMRG-GraphSAGE", "research-v1"
        );
        assertTrue(persisted.isPresent(), "Prediction must be persisted in PostgreSQL");
        assertEquals("SELECTED", persisted.get().getPredictedStatus());
        assertEquals(0.8063023686408997, persisted.get().getPredictedProbability(), 1e-6);

        // Verify AI service called once
        verify(aiServiceClient, times(1)).predictApplication("A00001");
    }

    @Test
    void testDuplicatePredictionPreventionAndCacheHit() {
        Map<String, Object> mockAiResult = new HashMap<>();
        mockAiResult.put("application_id", "A00001");
        mockAiResult.put("predicted_status", 1);
        mockAiResult.put("predicted_probability", 0.8063023686408997);

        when(aiServiceClient.predictApplication("A00001")).thenReturn(mockAiResult);

        PredictionRequest request = new PredictionRequest("AMRG-GraphSAGE", "research-v1", false);

        // First call: misses cache, calls AI service, persists
        PredictionResponse firstResponse = predictionService.predictApplication("A00001", request);
        assertNotNull(firstResponse);

        // Second call: hits PostgreSQL cache, does NOT call AI service
        PredictionResponse secondResponse = predictionService.predictApplication("A00001", request);
        assertNotNull(secondResponse);
        assertEquals(firstResponse.getEstimatedProbability(), secondResponse.getEstimatedProbability());
        assertEquals(firstResponse.getPredictedStatus(), secondResponse.getPredictedStatus());

        // AI service should only have been called ONCE
        verify(aiServiceClient, times(1)).predictApplication("A00001");

        // Verify exactly one prediction record exists in DB
        long count = predictionRepository.findAll().stream()
                .filter(p -> "A00001".equals(p.getApplicationId()) && "AMRG-GraphSAGE".equals(p.getModelName()))
                .count();
        assertEquals(1, count, "Must not create duplicate prediction rows in PostgreSQL");
    }

    @Test
    void testAiServiceFailureHandling() {
        when(aiServiceClient.predictApplication(anyString()))
                .thenThrow(new AiServiceException("Failed to connect to Python AI Service"));

        PredictionRequest request = new PredictionRequest("AMRG-GraphSAGE", "research-v1", false);
        assertThrows(AiServiceException.class, () -> {
            predictionService.predictApplication("A00001", request);
        });
    }

    @Test
    void testInvalidApplicationIdNotFound() {
        PredictionRequest request = new PredictionRequest("AMRG-GraphSAGE", "research-v1", false);
        assertThrows(ResourceNotFoundException.class, () -> {
            predictionService.predictApplication("NON_EXISTENT_APP_999", request);
        });
    }

    @Test
    void testGraphSubgraphEnrichmentAndSkillNameResolution() {
        Map<String, Object> aiNeighborhood = new HashMap<>();
        aiNeighborhood.put("center_node", new HashMap<>(Map.of("id", "A00001", "type", "application")));
        aiNeighborhood.put("nodes_count", 4);
        aiNeighborhood.put("links_count", 6);
        aiNeighborhood.put("nodes", java.util.List.of(
                new HashMap<>(Map.of("id", "A00001", "type", "application", "is_center", true)),
                new HashMap<>(Map.of("id", "S1654", "type", "student", "is_center", false)),
                new HashMap<>(Map.of("id", "C014", "type", "company", "is_center", false)),
                new HashMap<>(Map.of("id", "J0263", "type", "job", "is_center", false))
        ));
        aiNeighborhood.put("links", java.util.List.of(
                Map.of("source", "A00001", "source_type", "application", "target", "S1654", "target_type", "student", "relation", "APPLICATION_STUDENT", "relation_id", 0),
                Map.of("source", "S1654", "source_type", "student", "target", "A00001", "target_type", "application", "relation", "STUDENT_APPLICATION", "relation_id", 1),
                Map.of("source", "A00001", "source_type", "application", "target", "J0263", "target_type", "job", "relation", "APPLICATION_JOB", "relation_id", 4)
        ));

        when(aiServiceClient.getNeighborhood("application", "A00001", 35)).thenReturn(aiNeighborhood);

        SubgraphResponse response = graphService.getSubgraph("application", "A00001", 35);
        assertNotNull(response);
        assertNotNull(response.getSummary());
        assertTrue(response.getNodes().stream().anyMatch(n -> "skill".equals(n.get("type"))),
                "Application subgraph should include 2-hop student/job skills");
        assertTrue(response.getLinks().stream().anyMatch(l -> "Applied By".equals(l.get("relation"))),
                "Bidirectional edges should collapse into plain-English labels");
        assertEquals(1, response.getLinks().stream().filter(l -> "Applied By".equals(l.get("relation"))).count(),
                "Inverse edge pair should be deduplicated to a single edge");

        // Verify querying by readable skill name ("Python") resolves to SK_16
        Map<String, Object> skillNeighborhood = new HashMap<>();
        skillNeighborhood.put("center_node", new HashMap<>(Map.of("id", "SK_16", "type", "skill")));
        skillNeighborhood.put("nodes_count", 1);
        skillNeighborhood.put("links_count", 0);
        skillNeighborhood.put("nodes", java.util.List.of(
                new HashMap<>(Map.of("id", "SK_16", "type", "skill", "is_center", true))
        ));
        skillNeighborhood.put("links", java.util.List.of());

        when(aiServiceClient.getNeighborhood("skill", "SK_16", 20)).thenReturn(skillNeighborhood);

        SubgraphResponse skillResp = graphService.getSubgraph("skill", "Python", 20);
        assertEquals("Python", skillResp.getCenterNode().get("label"));
        verify(aiServiceClient).getNeighborhood("skill", "SK_16", 20);
    }
}
