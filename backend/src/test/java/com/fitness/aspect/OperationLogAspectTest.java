package com.fitness.aspect;

import com.fitness.audit.AuditEvent;
import com.fitness.audit.AuditedOperation;
import com.fitness.auth.AuthContext;
import com.fitness.controller.RequestTraceFilter;
import com.fitness.dto.ImageDeleteRequest;
import com.fitness.service.OperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationLogAspectTest {
    private final OperationLogService logs = mock(OperationLogService.class);
    private final OperationLogAspect aspect = new OperationLogAspect();
    private final ProceedingJoinPoint invocation = mock(ProceedingJoinPoint.class);
    private final MethodSignature signature = mock(MethodSignature.class);

    @BeforeEach void setup() throws Exception {
        ReflectionTestUtils.setField(aspect, "operationLogService", logs);
        ReflectionTestUtils.setField(aspect, "slowRequestMs", Long.MAX_VALUE);
        request("POST", "/api/recipe/delete/recipe-1");
        when(signature.toShortString()).thenReturn("RecipeController.deleteRecipe(..)");
        when(invocation.getSignature()).thenReturn(signature);
        selectMethod("deleteRecipe", String.class);
        when(invocation.getArgs()).thenReturn(new Object[]{"recipe-1"});
    }

    @AfterEach void teardown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test void ordinaryReadAndSearchRequestsDoNotWriteDatabaseAuditLogs() throws Throwable {
        request("GET", "/api/recipe/catalog").addParameter("keyword", "private search text");
        selectMethod("readCatalog");
        when(invocation.getArgs()).thenReturn(new Object[0]);
        Object success = new Object();
        when(invocation.proceed()).thenReturn(success);

        assertSame(success, aspect.around(invocation));
        verifyNoInteractions(logs);
    }

    @Test void auditFailureDoesNotReplaceSuccessfulBusinessResponse() throws Throwable {
        Object success = new Object();
        when(invocation.proceed()).thenReturn(success);
        doThrow(new IllegalStateException("log database unavailable")).when(logs).saveLog(any());

        assertSame(success, aspect.around(invocation));
        verify(invocation, times(1)).proceed();
    }

    @Test void auditFailureDoesNotHideOriginalBusinessFailure() throws Throwable {
        Exception original = new Exception("original business failure");
        when(invocation.proceed()).thenThrow(original);
        doThrow(new IllegalStateException("log database unavailable")).when(logs).saveLog(any());

        assertSame(original, assertThrows(Exception.class, () -> aspect.around(invocation)));
    }

    @Test void dangerousOperationRecordsEventTargetResultAndNoRequestContent() throws Throwable {
        RuntimeException original = new RuntimeException("write failed with private details");
        when(invocation.proceed()).thenThrow(original);

        assertSame(original, assertThrows(RuntimeException.class, () -> aspect.around(invocation)));
        verify(logs).saveLog(argThat(log -> "RECIPE_DELETE".equals(log.getEventType())
                && "recipe-1".equals(log.getTargetRef())
                && "FAILURE".equals(log.getResult())
                && "RuntimeException".equals(log.getErrorMessage())
                && Integer.valueOf(500).equals(log.getStatusCode())
                && Long.valueOf(1L).equals(log.getUserId())
                && "trace_12345678".equals(log.getRequestId())
                && log.getRequestParams() == null
                && "/api/recipe/delete/recipe-1".equals(log.getInterfaceName())));
    }

    @Test void permissionDeniedIsAuditedEvenOnAnUnannotatedEndpoint() throws Throwable {
        selectMethod("readCatalog");
        when(invocation.getArgs()).thenReturn(new Object[0]);
        SecurityException original = new SecurityException("not allowed");
        when(invocation.proceed()).thenThrow(original);

        assertSame(original, assertThrows(SecurityException.class, () -> aspect.around(invocation)));
        verify(logs).saveLog(argThat(log -> "PERMISSION_DENIED".equals(log.getEventType())
                && "FAILURE".equals(log.getResult())
                && log.getTargetRef() == null));
    }

    @Test void imageUrlIsStoredOnlyAsAnIrreversibleDigest() throws Throwable {
        request("POST", "/api/image/delete");
        selectMethod("deleteImage", ImageDeleteRequest.class);
        String url = "https://bucket.example/private/object.jpeg?signature=secret";
        when(invocation.getArgs()).thenReturn(new Object[]{new ImageDeleteRequest(url)});
        when(invocation.proceed()).thenReturn(new Object());

        aspect.around(invocation);
        verify(logs).saveLog(argThat(log -> "OSS_IMAGE_DELETE".equals(log.getEventType())
                && "SUCCESS".equals(log.getResult())
                && log.getTargetRef() != null
                && log.getTargetRef().matches("sha256:[0-9a-f]{64}")
                && !log.getTargetRef().contains("secret")
                && log.getRequestParams() == null));
    }

    @Test void ordinaryServerFailureStaysOutOfDatabaseAuditLog() throws Throwable {
        selectMethod("readCatalog");
        when(invocation.getArgs()).thenReturn(new Object[0]);
        RuntimeException original = new RuntimeException("database down");
        when(invocation.proceed()).thenThrow(original);

        assertSame(original, assertThrows(RuntimeException.class, () -> aspect.around(invocation)));
        verifyNoInteractions(logs);
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setAttribute(AuthContext.USER_ID_ATTRIBUTE, 1L);
        request.setAttribute(RequestTraceFilter.REQUEST_ID_ATTRIBUTE, "trace_12345678");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    private void selectMethod(String name, Class<?>... parameterTypes) throws Exception {
        Method method = AuditFixtures.class.getDeclaredMethod(name, parameterTypes);
        when(signature.getMethod()).thenReturn(method);
    }

    private static class AuditFixtures {
        @AuditedOperation(value = AuditEvent.RECIPE_DELETE, targetArgument = 0)
        void deleteRecipe(String recipeId) {}

        @AuditedOperation(value = AuditEvent.OSS_IMAGE_DELETE,
                targetArgument = 0, targetProperty = "imageUrl")
        void deleteImage(ImageDeleteRequest request) {}

        void readCatalog() {}
    }
}
