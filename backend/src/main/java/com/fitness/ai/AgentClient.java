package com.fitness.ai;

import com.fitness.dto.AgentConnectionStatus;
import com.fitness.dto.FoodRecognitionRequest;
import com.fitness.dto.FoodRecognitionResponse;
import com.fitness.dto.WorkoutDraftAgentRequest;
import com.fitness.dto.WorkoutDraftResponse;
import com.fitness.dto.FitnessAgentDtos.PythonRequest;
import com.fitness.dto.FitnessAgentDtos.PythonResponse;

/** Java 业务服务访问 Python Agent 的唯一出口。 */
public interface AgentClient {
    AgentConnectionStatus status(String requestId);

    FoodRecognitionResponse recognizeFood(FoodRecognitionRequest request, String requestId);

    WorkoutDraftResponse createWorkoutDraft(WorkoutDraftAgentRequest request, String requestId);

    PythonResponse runFitnessAgent(PythonRequest request, String requestId);
}
