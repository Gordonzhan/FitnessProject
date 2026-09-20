package com.fitness.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 只记录超阈值的 Mapper 标识和耗时，不输出参数，避免日志泄露用户数据。
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class SlowQueryInterceptor implements Interceptor {
    private static final Logger logger = LoggerFactory.getLogger(SlowQueryInterceptor.class);

    @Value("${fitness.performance.slow-query-ms:300}")
    private long slowQueryMs = 300;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startedAt = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            if (elapsedMs >= Math.max(slowQueryMs, 0)) {
                MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
                logger.warn("SLOW_SQL mapper={} elapsedMs={}", statement.getId(), elapsedMs);
            }
        }
    }
}
