package com.fitness.service;

import com.fitness.entity.OperationLog;

import java.util.Date;

public interface OperationLogService {
    /** 保存一条经过脱敏处理的危险操作审计日志。 */
    void saveLog(OperationLog operationLog);

    /** 删除早于指定时间的过期审计日志。 */
    int deleteExpiredBefore(Date cutoff);
}
