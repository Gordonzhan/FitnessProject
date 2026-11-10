package com.fitness.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentUserContextServiceTest {
    private final AgentUserContextService service = new AgentUserContextService(
            "0123456789abcdef0123456789abcdef", 300);

    @Test
    void signedContextOnlyReturnsIssuedCurrentUser() {
        String context = service.issue(42L);
        assertEquals(42L, service.verify(context));
    }

    @Test
    void tamperedContextIsRejected() {
        String context = service.issue(42L);
        String tampered = (context.charAt(0) == 'A' ? "B" : "A") + context.substring(1);
        assertThrows(SecurityException.class, () -> service.verify(tampered));
    }
}
