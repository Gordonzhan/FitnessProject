package com.fitness.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fitness.ai.AgentClient;
import com.fitness.ai.AgentUserContextService;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.dto.FitnessAgentDtos.*;
import com.fitness.dto.WorkoutDraftResponse;
import com.fitness.entity.AiAgentMessage;
import com.fitness.entity.AiAgentSession;
import com.fitness.mapper.AiAgentConversationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A7 会话编排；Java 持久化历史并签发身份，Python 仅负责模型与只读 Tool Loop。 */
@Service
public class FitnessAgentConversationService {
    private static final Logger log = LoggerFactory.getLogger(FitnessAgentConversationService.class);
    private static final int MODEL_HISTORY_MESSAGE_LIMIT = 6;
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(?<!\\d)(\\d{4}-\\d{2}-\\d{2})(?!\\d)");
    private static final Pattern DRAFT_REQUEST_PATTERN = Pattern.compile(
            "(?:生成|制定|创建|安排|规划|做一份|出一份|帮我)[^，。；\\n]{0,16}(?:训练|计划|草稿)"
                    + "|(?:纳入|加入|转成|保存为)[^，。；\\n]{0,12}(?:训练草稿|训练计划)"
                    + "|(?:训练计划|训练草稿|健身计划)[^，。；\\n]{0,12}(?:生成|制定|创建|安排|规划)"
                    + "|(?:生成|创建|安排)(?:一下)?(?:吧|草稿|训练|计划)$");
    private static final Pattern DRAFT_REJECTION_PATTERN = Pattern.compile(
            "(?:不要|不用|无需|别|不想)(?:再)?(?:生成|创建|安排|制定|规划)"
                    + "|(?:为什么|怎么会|怎么又)[^，。；\\n]{0,12}(?:生成|创建|安排)");
    private static final Pattern EXPLICIT_TRAINING_FOCUS_PATTERN = Pattern.compile(
            "胸部|练胸|胸肌|胸背|胸和背|胸肩|胸和肩|背部|练背|背肌|后背|肩背|肩和背|"
                    + "肩膀|肩部|练肩|三角肌|腹部|练腹|核心|腹肌|臀部|练臀|臀肌|"
                    + "腿部|练腿|下肢|肱二头|二头|肱三头|三头|手臂");
    private final AiAgentConversationMapper mapper;
    private final AgentClient agentClient;
    private final AgentUserContextService contexts;
    private final WorkoutDraftService workoutDraftService;
    private final FitnessAgentConversationPersistenceService persistence;
    private final ObjectMapper objectMapper;
    private final String businessZone;

    public FitnessAgentConversationService(AiAgentConversationMapper mapper, AgentClient agentClient,
            AgentUserContextService contexts, WorkoutDraftService workoutDraftService,
            FitnessAgentConversationPersistenceService persistence, ObjectMapper objectMapper,
            @Value("${fitness.business-zone:Asia/Shanghai}") String businessZone) {
        this.mapper = mapper; this.agentClient = agentClient; this.contexts = contexts;
        this.workoutDraftService = workoutDraftService; this.persistence = persistence;
        this.objectMapper = objectMapper;
        this.businessZone = businessZone;
    }

    @Transactional(rollbackFor = Exception.class)
    public SessionResponse getOrCreate(Long userId) {
        AiAgentSession session = mapper.findLatestActive(userId);
        if (session == null) {
            session = new AiAgentSession();
            session.setSessionId(UUID.randomUUID().toString()); session.setUserId(userId);
            session.setStatus("ACTIVE"); session.setTitle("Fitness Agent"); mapper.insertSession(session);
        }
        return response(session, mapper.findMessages(session.getId()));
    }

    public SessionResponse get(Long userId, String sessionId) {
        AiAgentSession session = required(userId, sessionId);
        return response(session, mapper.findMessages(session.getId()));
    }

    public SessionResponse send(Long userId, String sessionId, String rawContent, String requestId) {
        AiAgentSession session = required(userId, sessionId);
        String content = sanitize(rawContent);
        if (isSelfContainedWorkoutDraftRequest(content)) {
            return sendDirectWorkoutDraft(userId, session, content, requestId);
        }
        List<PythonMessage> history = new ArrayList<>(mapper.findRecentMessages(
                session.getId(), MODEL_HISTORY_MESSAGE_LIMIT).stream()
                .map(item -> new PythonMessage(item.getRole().toLowerCase(), item.getContent())).toList());
        history.add(new PythonMessage("user", content));
        PythonRequest request = new PythonRequest(requestId, sessionId, contexts.issue(userId), history);
        long chatStarted = System.nanoTime();
        PythonResponse agent = agentClient.runFitnessAgent(request, requestId);
        log.info("FITNESS_AGENT_PHASE_COMPLETED requestId={} phase=chat durationMs={}",
                requestId, elapsedMillis(chatStarted));

        String payloadJson = null;
        String reply = agent.reply();
        String action = "WORKOUT_DRAFT".equals(agent.action()) ? "WORKOUT_DRAFT" : "NONE";
        boolean draftRequested = explicitlyRequestsWorkoutDraft(content);
        if ("WORKOUT_DRAFT".equals(action) && !draftRequested) {
            log.info("FITNESS_AGENT_DRAFT_ACTION_REJECTED requestId={} reason=latest_turn_not_explicit", requestId);
            action = "NONE";
            reply = adviceOnlyReply(reply);
        }
        if ("WORKOUT_DRAFT".equals(action) && agent.workoutRequest() != null) {
            long draftStarted = System.nanoTime();
            try {
                String workoutRequest = mergeWorkoutRequest(agent.workoutRequest(), content);
                WorkoutDraftResponse draft = workoutDraftService.createForConversation(userId,
                        resolveDraftDate(workoutRequest), workoutRequest, content, requestId + "-draft");
                payloadJson = writePayload(draft, agent.citations());
                // 对话文案以 Java 已校验的最终草稿为准，避免模型预告的动作或餐单与卡片不一致。
                reply = groundedDraftReply(draft);
                log.info("FITNESS_AGENT_PHASE_COMPLETED requestId={} phase=workout_draft durationMs={} status={}",
                        requestId, elapsedMillis(draftStarted), draft.status());
            } catch (RuntimeException error) {
                log.warn("FITNESS_AGENT_DRAFT_FALLBACK requestId={} reason={}", requestId,
                        error.getClass().getSimpleName());
                log.info("FITNESS_AGENT_PHASE_COMPLETED requestId={} phase=workout_draft durationMs={} status=FAILED",
                        requestId, elapsedMillis(draftStarted));
                action = "NONE";
                reply = draftFailureReply(error);
            }
        }
        if (payloadJson == null && agent.citations() != null && !agent.citations().isEmpty()) {
            payloadJson = writePayload(null, agent.citations());
        }
        // 上游成功后才持久化本轮，失败重试不会留下重复的悬挂用户消息。
        persistence.persistTurn(session.getId(), content, reply, agent.provider(),
                String.join(",", agent.toolsUsed() == null ? List.of() : agent.toolsUsed()), action, payloadJson);
        return response(required(userId, sessionId), mapper.findMessages(session.getId()));
    }

    /** 明确包含目标部位的草稿命令直接进入 A5，避免先让对话模型重复整理同一句话。 */
    private SessionResponse sendDirectWorkoutDraft(Long userId, AiAgentSession session,
                                                   String content, String requestId) {
        long started = System.nanoTime();
        String action = "NONE";
        String payloadJson = null;
        String reply;
        String provider = "java";
        try {
            WorkoutDraftResponse draft = workoutDraftService.createForConversation(userId,
                    resolveDraftDate(content), content, content, requestId + "-draft");
            action = "WORKOUT_DRAFT";
            payloadJson = writeJson(draft);
            reply = groundedDraftReply(draft);
            provider = draft.provider();
            log.info("FITNESS_AGENT_PHASE_COMPLETED requestId={} phase=direct_workout_draft durationMs={} status={}",
                    requestId, elapsedMillis(started), draft.status());
        } catch (RuntimeException error) {
            reply = draftFailureReply(error);
            log.warn("FITNESS_AGENT_DIRECT_DRAFT_FAILED requestId={} reason={} durationMs={}",
                    requestId, error.getClass().getSimpleName(), elapsedMillis(started));
        }
        persistence.persistTurn(session.getId(), content, reply, provider, "", action, payloadJson);
        return response(required(userId, session.getSessionId()), mapper.findMessages(session.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public SessionResponse clear(Long userId, String sessionId) {
        AiAgentSession session = required(userId, sessionId);
        mapper.deleteMessages(session.getId());
        mapper.touchSession(session.getId());
        return response(session, List.of());
    }

    private String mergeWorkoutRequest(String agentRequest, String latestUserContent) {
        String agent = agentRequest == null ? "" : agentRequest.strip();
        String latest = latestUserContent == null ? "" : latestUserContent.strip();
        if (agent.equals(latest)) return latest.substring(0, Math.min(500, latest.length()));
        String marker = "\n用户本轮明确要求：";
        if (latest.length() + marker.length() >= 500) return latest.substring(0, 500);
        int agentLimit = 500 - marker.length() - latest.length();
        return agent.substring(0, Math.min(agentLimit, agent.length())) + marker + latest;
    }

    private boolean explicitlyRequestsWorkoutDraft(String content) {
        if (content == null || DRAFT_REJECTION_PATTERN.matcher(content).find()) return false;
        return content.contains("帮我练") || DRAFT_REQUEST_PATTERN.matcher(content).find();
    }

    private boolean isSelfContainedWorkoutDraftRequest(String content) {
        return explicitlyRequestsWorkoutDraft(content)
                && EXPLICIT_TRAINING_FOCUS_PATTERN.matcher(content).find();
    }

    private long elapsedMillis(long started) {
        return Math.round((System.nanoTime() - started) / 1_000_000.0);
    }

    private String adviceOnlyReply(String modelReply) {
        if (modelReply != null && !Pattern.compile("已生成|训练草稿|查看并编辑草稿|确认前不会保存")
                .matcher(modelReply).find()) return modelReply;
        return "我已读取本轮相关数据。本轮是建议请求，因此没有创建或保存训练草稿。"
                + "如果希望我安排训练，请明确告诉我训练日期、部位和时长。";
    }

    private String draftFailureReply(RuntimeException error) {
        String message = error.getMessage();
        if (message != null && (message.contains("训练已完成") || message.contains("已有训练计划"))) {
            return message;
        }
        return "我已读取本轮相关数据，但训练草稿暂时无法生成。"
                + "请补充训练部位、时长和可用器械后再试。";
    }

    private String groundedDraftReply(WorkoutDraftResponse draft) {
        if (draft.exercises() == null || draft.exercises().isEmpty()) return draft.assistantReply();
        String exercises = draft.exercises().stream()
                .map(WorkoutDraftResponse.ExerciseDraft::name)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining("、"));
        StringBuilder reply = new StringBuilder("已生成“")
                .append(draft.title()).append("”，草稿动作是：").append(exercises).append("。");
        if (draft.nutritionPlan() != null && draft.nutritionPlan().meals() != null
                && !draft.nutritionPlan().meals().isEmpty()) {
            String meals = draft.nutritionPlan().meals().stream()
                    .map(WorkoutDraftResponse.MealDraft::name)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .collect(java.util.stream.Collectors.joining("、"));
            if (!meals.isBlank()) reply.append("饮食草稿是：").append(meals).append("。");
        }
        return reply.append("请在草稿页检查和编辑，确认前不会保存。").toString();
    }

    private LocalDate resolveDraftDate(String content) {
        LocalDate today = LocalDate.now(ZoneId.of(businessZone));
        Matcher matcher = ISO_DATE_PATTERN.matcher(content);
        if (matcher.find()) {
            try {
                return LocalDate.parse(matcher.group(1));
            } catch (DateTimeParseException ignored) {
                // 非法日期交给相对日期和默认当天逻辑处理，避免把模型文本当作可信日期。
            }
        }
        if (content.contains("后天")) return today.plusDays(2);
        if (content.contains("明天") || content.contains("明日")) return today.plusDays(1);
        return today;
    }

    private AiAgentSession required(Long userId, String sessionId) {
        if (sessionId == null || !sessionId.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("会话编号格式无效");
        }
        AiAgentSession session = mapper.findOwnedSession(userId, sessionId);
        if (session == null) throw new BusinessException(ApiErrorCode.NOT_FOUND, "对话不存在或无权访问");
        return session;
    }

    private SessionResponse response(AiAgentSession session, List<AiAgentMessage> messages) {
        List<MessageResponse> values = messages.stream().map(item -> new MessageResponse(
                item.getMessageId(), item.getRole().toLowerCase(), item.getContent(), item.getProvider(),
                parseTools(item.getToolSummary()), item.getActionType(), parseJson(item.getActionPayload()),
                item.getCreatedAt(), parseCitations(item.getActionPayload()))).toList();
        return new SessionResponse(session.getSessionId(), session.getTitle(), values,
                session.getCreatedAt(), session.getUpdatedAt());
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("请输入问题");
        String normalized = value.trim().replaceAll("(?i)Bearer\\s+[A-Za-z0-9._-]+", "[已隐藏令牌]");
        return normalized.length() > 1000 ? normalized.substring(0, 1000) : normalized;
    }
    private List<String> parseTools(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(",")).toList();
    }
    private Object parseJson(String value) {
        if (value == null || value.isBlank()) return null;
        try { return objectMapper.readValue(value, Object.class); }
        catch (JsonProcessingException error) { return null; }
    }
    private List<Citation> parseCitations(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            JsonNode node = objectMapper.readTree(value).path("citations");
            if (!node.isArray()) return List.of();
            return Arrays.asList(objectMapper.treeToValue(node, Citation[].class));
        } catch (JsonProcessingException error) {
            return List.of();
        }
    }
    private String writePayload(Object value, List<Citation> citations) {
        try {
            ObjectNode payload = value == null
                    ? objectMapper.createObjectNode()
                    : (ObjectNode) objectMapper.valueToTree(value);
            payload.set("citations", objectMapper.valueToTree(citations == null ? List.of() : citations));
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException | ClassCastException error) {
            throw new IllegalStateException("无法保存Agent引用", error);
        }
    }
    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("无法保存Agent草稿", error); }
    }
}
