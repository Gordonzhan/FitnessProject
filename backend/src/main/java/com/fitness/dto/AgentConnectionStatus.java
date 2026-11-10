package com.fitness.dto;

/** Java 后端观察到的 Python Agent 连接状态。 */
public record AgentConnectionStatus(
        boolean enabled,
        boolean available,
        String service,
        String version,
        String provider,
        String message) {

    public static AgentConnectionStatus disabled() {
        return new AgentConnectionStatus(false, false, null, null, null, "Agent 功能未开启");
    }

    public static AgentConnectionStatus unavailable(String message) {
        return new AgentConnectionStatus(true, false, null, null, null, message);
    }

    public static AgentConnectionStatus available(String service, String version, String provider) {
        return new AgentConnectionStatus(true, true, service, version, provider, "Agent 服务可用");
    }
}
