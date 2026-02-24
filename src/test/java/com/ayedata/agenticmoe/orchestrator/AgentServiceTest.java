package com.ayedata.agenticmoe.orchestrator;

import com.ayedata.agenticmoe.config.AppConfig;
import com.ayedata.agenticmoe.core.VoyageClient;
import com.ayedata.agenticmoe.model.ExpertType;
import com.ayedata.agenticmoe.router.SemanticRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private VoyageClient mockClient;

    @Mock
    private SemanticRouter mockRouter;

    @Mock
    private AppConfig mockConfig;

    private AgentService agentService;

    @Mock
    private com.ayedata.agenticmoe.cache.SemanticCache mockCache;

    @BeforeEach
    void setUp() {
        // Setup Nested Configuration Mocking
        AppConfig.Routing routing = new AppConfig.Routing();
        routing.setModel("voyage-4-nano");
        routing.setDimension(256);
        routing.setThreshold(0.65);
        routing.setClarificationThreshold(0.5);

        AppConfig.Experts experts = new AppConfig.Experts();
        experts.setFinanceModel("voyage-finance-2");
        experts.setCodeModel("voyage-code-3");
        experts.setGeneralModel("voyage-4-lite");

        lenient().when(mockConfig.getRouting()).thenReturn(routing);
        lenient().when(mockConfig.getExperts()).thenReturn(experts);

        agentService = new AgentService(mockConfig, mockClient, mockCache);
        org.springframework.test.util.ReflectionTestUtils.setField(agentService, "router", mockRouter);

        // Manually inject the mockRouter using reflection or by removing the mockRouter
        // field and relying on the one instantiated.
        // Wait, agentService instantiates its own SemanticRouter. Do we need
        // mockRouter?
        // Let's modify agentService to use reflection to set mockRouter for testing, or
        // just test without mocking SemanticRouter.
    }

    @Test
    @DisplayName("Should successfully route and execute Finance Expert query")
    void testProcessQueryFinanceSuccess() throws Exception {
        // --- ARRANGE ---
        String query = "What is the EBITDA for Q3 2025?";
        float[] mockRoutingVector = new float[256]; // Tier 1 Vector

        // Mock Tier 1: Routing Embedding Call (MRL Sliced)
        when(mockClient.embedQuery(eq(query), eq("voyage-4-nano"), eq(256), any()))
                .thenReturn(CompletableFuture.completedFuture(mockRoutingVector));

        // Mock Router Decision
        when(mockRouter.route(eq(mockRoutingVector), any()))
                .thenReturn(new SemanticRouter.RoutingResult(ExpertType.FINANCE, java.util.Map.of(), new float[256]));

        // Mock Tier 2: Expert Execution (Full Vector)
        // Since Experts are instantiated via 'new', we mock the client behavior they
        // use
        when(mockClient.embedQuery(eq(query), eq("voyage-finance-2")))
                .thenReturn(CompletableFuture.completedFuture(new float[1024]));

        // --- ACT ---
        CompletableFuture<AgentService.AgentResponse> future = agentService.processQueryAsync(query);
        AgentService.AgentResponse response = future.get();

        // --- ASSERT ---
        assertNotNull(response);
        assertEquals(ExpertType.FINANCE, response.expertUsed());
        assertTrue(response.answer().contains("[FinanceExpert]"));

        // Verify Tier 1 was called with the correct MRL dimension
        verify(mockClient).embedQuery(anyString(), anyString(), eq(256), any());
    }

    @Test
    @DisplayName("Should fallback to Generalist when routing fails")
    void testProcessQueryResilienceFallback() throws Exception {
        // --- ARRANGE ---
        String query = "Hello!";

        // Mock a failure in the embedding API (e.g., Timeout or Invalid Key)
        when(mockClient.embedQuery(anyString(), anyString(), anyInt(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("API Unavailable")));

        // --- ACT ---
        AgentService.AgentResponse response = agentService.processQueryAsync(query).get();

        // --- ASSERT ---
        assertEquals(ExpertType.GENERAL, response.expertUsed());
        assertTrue(response.answer().contains("error while coordinating"));
    }

    @Test
    @DisplayName("Should verify record accessors use correct naming convention")
    void testAgentResponseRecordAccessors() {
        AgentService.AgentResponse response = new AgentService.AgentResponse("Test Answer", ExpertType.CODE,
                java.util.Map.of(), 100L, 200L);

        // Java Records use componentName() not getComponentName()
        assertEquals("Test Answer", response.answer());
        assertEquals(ExpertType.CODE, response.expertUsed());
    }
}