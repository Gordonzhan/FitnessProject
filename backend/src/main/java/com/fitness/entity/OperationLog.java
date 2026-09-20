package com.fitness.entity;

import lombok.Data;

import java.util.Date;

@Data
public class OperationLog {
    /** 审计日志数据库主键。 */
    private Long id;
    /** 触发操作的用户编号；无法识别用户时可为空。 */
    private Long userId;
    /** 用于串联一次请求全链路日志的追踪编号。 */
    private String requestId;
    /** 结构化危险操作事件类型。 */
    private String eventType;
    /** 经脱敏或哈希处理的操作对象引用。 */
    private String targetRef;
    /** 操作结果，如 SUCCESS、FAILED 或 DENIED。 */
    private String result;
    /** 发起请求的客户端 IP 地址。 */
    private String ip;
    /** 被调用的控制器或服务类名。 */
    private String interfaceName;
    /** 被调用的方法名。 */
    private String method;
    /** 兼容旧日志的请求参数摘要；新审计默认不保存敏感参数。 */
    private String requestParams;
    /** HTTP 响应状态码。 */
    private Integer statusCode;
    /** 失败时经过长度限制的错误摘要。 */
    private String errorMessage;
    /** 请求执行耗时，单位：毫秒。 */
    private Long executionTime;
    /** 审计事件发生时间。 */
    private Date operationTime;
}
