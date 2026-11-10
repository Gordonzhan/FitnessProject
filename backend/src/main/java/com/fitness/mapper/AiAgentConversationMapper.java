package com.fitness.mapper;

import com.fitness.entity.AiAgentMessage;
import com.fitness.entity.AiAgentSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface AiAgentConversationMapper {
    AiAgentSession findLatestActive(@Param("userId") Long userId);
    AiAgentSession findOwnedSession(@Param("userId") Long userId, @Param("sessionId") String sessionId);
    void insertSession(AiAgentSession session);
    void touchSession(@Param("id") Long id);
    void insertMessage(AiAgentMessage message);
    int deleteMessages(@Param("sessionDbId") Long sessionDbId);
    List<AiAgentMessage> findMessages(@Param("sessionDbId") Long sessionDbId);
    List<AiAgentMessage> findRecentMessages(@Param("sessionDbId") Long sessionDbId,
                                            @Param("limit") int limit);
}
