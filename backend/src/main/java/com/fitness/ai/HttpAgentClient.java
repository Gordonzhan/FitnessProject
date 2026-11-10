package com.fitness.ai;

import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.dto.AgentConnectionStatus;
import com.fitness.dto.FoodRecognitionRequest;
import com.fitness.dto.FoodRecognitionResponse;
import com.fitness.dto.WorkoutDraftAgentRequest;
import com.fitness.dto.WorkoutDraftResponse;
import com.fitness.dto.FitnessAgentDtos.PythonRequest;
import com.fitness.dto.FitnessAgentDtos.PythonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

/** 通过受控 HTTP 接口调用独立 Python Agent；不向 Agent 暴露数据库或用户身份凭据。 */
@Component
public class HttpAgentClient implements AgentClient {
    private static final Logger log = LoggerFactory.getLogger(HttpAgentClient.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final RestClient restClient;
    private final boolean enabled;
    private final String serviceToken;

    public HttpAgentClient(RestClient.Builder restClientBuilder,
                           @Value("${fitness.agent.enabled:false}") boolean enabled,
                           @Value("${fitness.agent.base-url:http://127.0.0.1:8000}") String baseUrl,
                           @Value("${fitness.agent.service-token:}") String serviceToken,
                           @Value("${fitness.agent.connect-timeout-ms:2000}") int connectTimeoutMs,
                           @Value("${fitness.agent.read-timeout-ms:180000}") int readTimeoutMs) {
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalArgumentException("Agent 超时时间必须大于0");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = restClientBuilder
                .baseUrl(normalizeBaseUrl(baseUrl))
                .requestFactory(requestFactory)
                .build();
        this.enabled = enabled;
        this.serviceToken = serviceToken;
    }

    @Override
    public AgentConnectionStatus status(String requestId) {
        if (!enabled) {
            return AgentConnectionStatus.disabled();
        }
        try {
            AgentHealthPayload payload = restClient.get().uri("/health")
                    .header(REQUEST_ID_HEADER, requestId)
                    .retrieve().body(AgentHealthPayload.class);
            if (payload == null || !"UP".equals(payload.status())) {
                return AgentConnectionStatus.unavailable("Agent 健康检查返回异常");
            }
            return AgentConnectionStatus.available(payload.service(), payload.version(), payload.provider());
        } catch (Exception error) {
            log.warn("AGENT_HEALTH_UNAVAILABLE requestId={} reason={} rootCause={}",
                    requestId, error.getClass().getSimpleName(), rootCauseType(error));
            return AgentConnectionStatus.unavailable("Agent 服务暂时不可用");
        }
    }

    @Override
    public FoodRecognitionResponse recognizeFood(FoodRecognitionRequest request, String requestId) {
        if (!enabled) {
            throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE, "AI识别功能尚未开启");
        }
        AgentFoodRecognitionRequest payload = new AgentFoodRecognitionRequest(
                requestId,
                request.imageUrls(),
                request.userContext(),
                request.dishHint(),
                "DRAFT_ONLY"
        );
        try {
            RestClient.RequestBodySpec call = restClient.post().uri("/v1/vision/food-recognition")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(REQUEST_ID_HEADER, requestId);
            if (StringUtils.hasText(serviceToken)) {
                call.header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken);
            }
            FoodRecognitionResponse response = call.body(payload)
                    .retrieve().body(FoodRecognitionResponse.class);
            if (response == null || !requestId.equals(response.requestId())) {
                log.warn("AGENT_RESPONSE_REJECTED requestId={} reason=missing_or_mismatched_request_id", requestId);
                throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE, "AI识别结果校验失败，请稍后重试");
            }
            return response;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            log.warn("AGENT_RECOGNITION_UNAVAILABLE requestId={} reason={} rootCause={}",
                    requestId, error.getClass().getSimpleName(), rootCauseType(error));
            throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE, "AI识别服务暂时不可用，请稍后重试");
        }
    }

    @Override
    public WorkoutDraftResponse createWorkoutDraft(WorkoutDraftAgentRequest request, String requestId) {
        if (!enabled) {
            throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE, "AI训练生成功能尚未开启");
        }
        try {
            RestClient.RequestBodySpec call = restClient.post().uri("/v1/workouts/draft")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(REQUEST_ID_HEADER, requestId);
            if (StringUtils.hasText(serviceToken)) {
                call.header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken);
            }
            WorkoutDraftResponse response = call.body(request)
                    .retrieve().body(WorkoutDraftResponse.class);
            if (response == null || !requestId.equals(response.requestId())) {
                log.warn("AGENT_WORKOUT_RESPONSE_REJECTED requestId={} reason=missing_or_mismatched_request_id",
                        requestId);
                throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE,
                        "AI训练草稿校验失败，请稍后重试");
            }
            return response;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            log.warn("AGENT_WORKOUT_UNAVAILABLE requestId={} reason={} rootCause={}",
                    requestId, error.getClass().getSimpleName(), rootCauseType(error));
            throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE,
                    "AI训练生成服务暂时不可用，请稍后重试");
        }
    }

    @Override
    public PythonResponse runFitnessAgent(PythonRequest request, String requestId) {
        if (!enabled) throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE, "Fitness Agent尚未开启");
        try {
            RestClient.RequestBodySpec call = restClient.post().uri("/v1/agents/fitness/run")
                    .contentType(MediaType.APPLICATION_JSON).header(REQUEST_ID_HEADER, requestId);
            if (StringUtils.hasText(serviceToken)) call.header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken);
            PythonResponse response = call.body(request).retrieve().body(PythonResponse.class);
            if (response == null || !requestId.equals(response.requestId())) {
                throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE, "Fitness Agent响应校验失败");
            }
            return response;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            log.warn("FITNESS_AGENT_UNAVAILABLE requestId={} reason={} rootCause={}", requestId,
                    error.getClass().getSimpleName(), rootCauseType(error));
            throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE,
                    "Fitness Agent暂时不可用，请稍后重试");
        }
    }

    private String normalizeBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Agent Base URL 不能为空");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String rootCauseType(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    private record AgentHealthPayload(String status, String service, String version, String provider) {}

    private record AgentFoodRecognitionRequest(
            String requestId,
            List<String> imageUrls,
            FoodRecognitionRequest.UserContext userContext,
            String dishHint,
            String mode) {}
}
