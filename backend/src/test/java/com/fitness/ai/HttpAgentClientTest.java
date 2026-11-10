package com.fitness.ai;

import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.dto.FoodRecognitionRequest;
import com.fitness.dto.WorkoutDraftAgentRequest;
import com.fitness.dto.FitnessAgentDtos.PythonMessage;
import com.fitness.dto.FitnessAgentDtos.PythonRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpAgentClientTest {
    private static final String REQUEST_ID = "java-agent-test-001";
    private final AtomicReference<RecordedRequest> lastRequest = new AtomicReference<>();
    private HttpServer server;
    private int responseStatus;
    private String responseBody;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        respond(200, "{}");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void disabledClientDoesNotCallAgent() {
        HttpAgentClient client = client(false, "");

        var status = client.status(REQUEST_ID);
        BusinessException error = assertThrows(BusinessException.class,
                () -> client.recognizeFood(request(), REQUEST_ID));

        assertFalse(status.enabled());
        assertFalse(status.available());
        assertEquals(ApiErrorCode.UPSTREAM_UNAVAILABLE, error.getErrorCode());
        assertNull(lastRequest.get());
    }

    @Test
    void healthCheckReturnsAgentMetadata() {
        respond(200, """
                {"status":"UP","service":"fitness-ai-agent","version":"0.1.0","provider":"fake"}
                """);
        HttpAgentClient client = client(true, "agent-secret");

        var status = client.status(REQUEST_ID);

        assertTrue(status.enabled());
        assertTrue(status.available());
        assertEquals("fake", status.provider());
        assertEquals("GET", lastRequest.get().method());
        assertEquals("/health", lastRequest.get().path());
        assertEquals(REQUEST_ID, lastRequest.get().requestId());
    }

    @Test
    void recognitionForwardsTypedPayloadAndServiceIdentity() {
        respond(200, """
                {
                  "requestId":"java-agent-test-001",
                  "status":"SUCCEEDED",
                  "provider":"fake",
                  "foods":[{
                    "name":"鸡胸肉",
                    "estimatedWeightGrams":150,
                    "weightRangeGrams":[120,180],
                    "cookingMethod":"煎制",
                    "nutrition":{
                      "caloriesKcal":248,
                      "proteinGrams":46.5,
                      "carbohydrateGrams":0,
                      "fatGrams":5.4
                    },
                    "confidence":0.82
                  }],
                  "overallConfidence":0.82,
                  "needsUserConfirmation":true,
                  "warnings":[],
                  "recipeSuggestion":{
                    "name":"香煎鸡胸肉",
                    "steps":["鸡胸肉调味","煎至熟透"],
                    "confidence":0.76,
                    "provider":"ollama",
                    "disclaimer":"AI 推测，请按实际情况修改"
                  },
                  "recipeGenerationStatus":"SUCCEEDED"
                }
                """);
        HttpAgentClient client = client(true, "agent-secret");

        var response = client.recognizeFood(request(), REQUEST_ID);

        assertEquals("SUCCEEDED", response.status());
        assertEquals("鸡胸肉", response.foods().get(0).name());
        assertEquals(new BigDecimal("46.5"), response.foods().get(0).nutrition().proteinGrams());
        assertEquals("香煎鸡胸肉", response.recipeSuggestion().name());
        assertEquals("SUCCEEDED", response.recipeGenerationStatus());
        assertEquals("POST", lastRequest.get().method());
        assertEquals("/v1/vision/food-recognition", lastRequest.get().path());
        assertEquals("Bearer agent-secret", lastRequest.get().authorization());
        assertTrue(lastRequest.get().body().contains("\"requestId\":\"java-agent-test-001\""));
        assertTrue(lastRequest.get().body().contains("\"mode\":\"DRAFT_ONLY\""));
        assertFalse(lastRequest.get().body().contains("userId"));
    }

    @Test
    void recognitionFailureUsesSafeUpstreamError() {
        respond(500, "{\"message\":\"internal detail that Java must not expose\"}");
        HttpAgentClient client = client(true, "agent-secret");

        BusinessException error = assertThrows(BusinessException.class,
                () -> client.recognizeFood(request(), REQUEST_ID));

        assertEquals(ApiErrorCode.UPSTREAM_UNAVAILABLE, error.getErrorCode());
        assertEquals("AI识别服务暂时不可用，请稍后重试", error.getMessage());
    }

    @Test
    void workoutDraftForwardsCandidateContextWithoutUserIdentity() {
        respond(200, """
                {
                  "requestId":"java-agent-test-001",
                  "status":"READY",
                  "provider":"fake",
                  "title":"今日训练",
                  "estimatedDurationMinutes":20,
                  "targetRpe":6,
                  "exercises":[{
                    "exerciseCode":"EX_ONE","sets":3,"reps":10,
                    "durationSeconds":40,"restSeconds":60,"rpe":6,"rationale":"合规动作"
                  }],
                  "warnings":[],
                  "disclaimer":"仅供健身参考"
                }
                """);
        HttpAgentClient client = client(true, "agent-secret");

        var response = client.createWorkoutDraft(workoutRequest(), REQUEST_ID);

        assertEquals("READY", response.status());
        assertEquals("EX_ONE", response.exercises().get(0).exerciseCode());
        assertEquals("/v1/workouts/draft", lastRequest.get().path());
        assertTrue(lastRequest.get().body().contains("\"exerciseCode\":\"EX_ONE\""));
        assertTrue(lastRequest.get().body().contains("\"userRequest\":\"今天练胸\""));
        assertTrue(lastRequest.get().body().contains("\"primaryRegion\":\"CHEST\""));
        assertTrue(lastRequest.get().body().contains("\"maximumDurationMinutes\":30"));
        assertFalse(lastRequest.get().body().contains("userId"));
    }

    @Test
    void fitnessAgentContractRestoresKnowledgeCitationsWithoutUserId() {
        respond(200, """
                {
                  "requestId":"java-agent-test-001",
                  "reply":"通用知识库依据：力量训练应循序渐进。",
                  "provider":"fake",
                  "toolRounds":1,
                  "toolsUsed":["search_fitness_knowledge"],
                  "action":"NONE",
                  "workoutRequest":null,
                  "safetyNotice":false,
                  "citations":[{
                    "documentId":"KB_ACSM_RESISTANCE_2026",
                    "title":"ACSM 抗阻训练摘要",
                    "sourceName":"American College of Sports Medicine",
                    "sourceUrl":"https://pubmed.ncbi.nlm.nih.gov/41843416/",
                    "version":"2026-position-stand-project-summary-v1"
                  }]
                }
                """);
        HttpAgentClient client = client(true, "agent-secret");
        PythonRequest request = new PythonRequest(REQUEST_ID,
                "00000000-0000-0000-0000-000000000001", "signed-context",
                List.of(new PythonMessage("user", "力量训练为什么要循序渐进？")));

        var response = client.runFitnessAgent(request, REQUEST_ID);

        assertEquals("KB_ACSM_RESISTANCE_2026", response.citations().get(0).documentId());
        assertEquals("/v1/agents/fitness/run", lastRequest.get().path());
        assertTrue(lastRequest.get().body().contains("signed-context"));
        assertFalse(lastRequest.get().body().contains("userId"));
    }

    private HttpAgentClient client(boolean enabled, String token) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new HttpAgentClient(RestClient.builder(), enabled, baseUrl, token, 1000, 3000);
    }

    private FoodRecognitionRequest request() {
        return new FoodRecognitionRequest(
                List.of("https://example.com/food.jpg"),
                new FoodRecognitionRequest.UserContext(
                        "FAT_LOSS", new BigDecimal("1800"), new BigDecimal("920"), List.of()),
                "鸡胸肉"
        );
    }

    private WorkoutDraftAgentRequest workoutRequest() {
        var preferences = new WorkoutDraftAgentRequest.Preferences(
                "BEGINNER", 30, "HOME", "GENERAL_FITNESS", List.of("无"), List.of(),
                List.of(), List.of(), 5, 7);
        var candidate = new WorkoutDraftAgentRequest.Candidate(
                "EX_ONE", "动作一", "力量", "胸部", "CHEST", "无", "中", 3, 10, 40, 60, 6,
                "BODYWEIGHT", null);
        var intent = new WorkoutDraftAgentRequest.TrainingIntent(null, 30,
                List.of(new WorkoutDraftAgentRequest.FocusRequirement("CHEST", "胸部", 1, null)), List.of());
        return new WorkoutDraftAgentRequest(REQUEST_ID, LocalDate.of(2026, 10, 8), "今天练胸", intent, preferences,
                List.of(candidate), List.of(), null, List.of(), "DRAFT_ONLY");
    }

    private void respond(int status, String body) {
        responseStatus = status;
        responseBody = body;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastRequest.set(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("X-Request-Id"),
                exchange.getRequestHeaders().getFirst("Authorization"),
                body
        ));
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private record RecordedRequest(
            String method,
            String path,
            String requestId,
            String authorization,
            String body) {}
}
