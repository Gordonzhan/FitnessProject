package com.fitness.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aliyun.oss.OSSException;
import com.fitness.common.BusinessException;
import com.fitness.dto.ImageUploadTicketResponse;
import com.fitness.entity.ImageUploadTicket;
import com.fitness.mapper.ImageUploadTicketMapper;
import com.fitness.util.OSSUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OssDirectUploadServiceTest {
    private static final String KEY = "fitness-diary/images/7/977b707b-4444-41e3-aef9-9c58d0fa7b28.jpeg";
    private static final byte[] JPEG = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
    private final ImageUploadTicketMapper mapper = mock(ImageUploadTicketMapper.class);
    private final OssStsService sts = mock(OssStsService.class);
    private final OSSUtil oss = mock(OSSUtil.class);
    private final ObjectMapper json = new ObjectMapper();
    private final OssDirectUploadService service = new OssDirectUploadService(mapper, sts, oss, json, "cn-beijing", 300);

    @Test void ticketBindsExactUserKeySizeMimeAndNeverReturnsSecret() throws Exception {
        when(oss.imageKey(eq(7L), eq("jpeg"))).thenReturn(KEY);
        when(oss.bucketName()).thenReturn("testbucket");
        when(oss.uploadHost()).thenReturn("https://testbucket.oss-cn-beijing.aliyuncs.com");
        when(sts.assumeForObject(eq(KEY), anyString())).thenReturn(
                new OssStsService.TemporaryCredentials("STS.test-id", "never-return-this-secret", "security-token"));

        ImageUploadTicketResponse response = service.issue(7L, 1234, "JPG");
        assertEquals(KEY, response.key());
        assertEquals("image/jpeg", response.formFields().get("content-type"));
        assertEquals("true", response.formFields().get("x-oss-forbid-overwrite"));
        assertEquals("security-token", response.formFields().get("x-oss-security-token"));
        assertFalse(response.formFields().toString().contains("never-return-this-secret"));

        String policyJson = new String(Base64.getDecoder().decode(response.formFields().get("policy")), StandardCharsets.UTF_8);
        Map<String, Object> policy = json.readValue(policyJson, new TypeReference<>() {});
        List<?> conditions = (List<?>) policy.get("conditions");
        assertTrue(conditions.contains(List.of("eq", "$key", KEY)));
        assertTrue(conditions.contains(List.of("eq", "$content-type", "image/jpeg")));
        assertTrue(conditions.contains(List.of("content-length-range", 1234, 1234)));
        assertTrue(conditions.contains(List.of("eq", "$x-oss-forbid-overwrite", "true")));

        ArgumentCaptor<ImageUploadTicket> saved = ArgumentCaptor.forClass(ImageUploadTicket.class);
        verify(mapper).insert(saved.capture());
        assertEquals(7L, saved.getValue().getUserId());
        assertEquals(KEY, saved.getValue().getObjectKey());
        assertEquals("ISSUED", saved.getValue().getStatus());
    }

    @Test void rejectsEmptyOversizeAndUnsupportedTicketsBeforeSts() {
        assertThrows(IllegalArgumentException.class, () -> service.issue(7L, 0, "jpeg"));
        assertThrows(IllegalArgumentException.class,
                () -> service.issue(7L, OssDirectUploadService.MAX_IMAGE_BYTES + 1, "jpeg"));
        assertThrows(IllegalArgumentException.class, () -> service.issue(7L, 10, "svg"));
        verifyNoInteractions(sts);
    }

    @Test void confirmationChecksObjectSizeMimeAndMagicThenConsumesTicket() {
        ImageUploadTicket ticket = ticket("VERIFYING", LocalDateTime.now(ZoneOffset.UTC).plusMinutes(4));
        when(mapper.claim(eq("ticket"), eq(7L), any())).thenReturn(1);
        when(mapper.findById("ticket")).thenReturn(ticket);
        when(oss.inspectImageObject(KEY)).thenReturn(new OSSUtil.RemoteImage(4, "image/jpeg", JPEG));
        when(mapper.markConfirmed(eq("ticket"), any())).thenReturn(1);
        when(oss.imageUrlForKey(KEY)).thenReturn("https://image/verified.jpeg");

        assertEquals("https://image/verified.jpeg", service.confirm(7L, "ticket"));
        verify(mapper).markConfirmed(eq("ticket"), any());
        verify(oss, never()).deleteObjectKey(anyString());
    }

    @Test void forgedContentIsDeletedAndTicketCannotBeReused() {
        ImageUploadTicket ticket = ticket("VERIFYING", LocalDateTime.now(ZoneOffset.UTC).plusMinutes(4));
        when(mapper.claim(eq("ticket"), eq(7L), any())).thenReturn(1);
        when(mapper.findById("ticket")).thenReturn(ticket);
        when(oss.inspectImageObject(KEY)).thenReturn(
                new OSSUtil.RemoteImage(4, "image/jpeg", "<svg".getBytes(StandardCharsets.US_ASCII)));

        BusinessException error = assertThrows(BusinessException.class, () -> service.confirm(7L, "ticket"));
        assertEquals(400, error.getErrorCode().code());
        verify(oss).deleteObjectKey(KEY);
        verify(mapper).markFailed("ticket");
    }

    @Test void sizeAndMimeMismatchesAreRejectedAndDeleted() {
        ImageUploadTicket ticket = ticket("VERIFYING", LocalDateTime.now(ZoneOffset.UTC).plusMinutes(4));
        when(mapper.claim(eq("ticket"), eq(7L), any())).thenReturn(1);
        when(mapper.findById("ticket")).thenReturn(ticket);
        when(oss.inspectImageObject(KEY)).thenReturn(new OSSUtil.RemoteImage(5, "image/jpeg", JPEG));
        assertEquals(400, assertThrows(BusinessException.class, () -> service.confirm(7L, "ticket"))
                .getErrorCode().code());
        verify(oss).deleteObjectKey(KEY);

        reset(mapper, oss);
        ticket.setStatus("VERIFYING");
        when(mapper.claim(eq("ticket"), eq(7L), any())).thenReturn(1);
        when(mapper.findById("ticket")).thenReturn(ticket);
        when(oss.inspectImageObject(KEY)).thenReturn(new OSSUtil.RemoteImage(4, "image/png", JPEG));
        assertEquals(400, assertThrows(BusinessException.class, () -> service.confirm(7L, "ticket"))
                .getErrorCode().code());
        verify(oss).deleteObjectKey(KEY);
    }

    @Test void missingObjectReturnsNotFoundAndStillRunsIdempotentCleanup() {
        ImageUploadTicket ticket = ticket("VERIFYING", LocalDateTime.now(ZoneOffset.UTC).plusMinutes(4));
        OSSException missing = new OSSException("missing", "NoSuchKey", "request", "host",
                "Object", "", "GET");
        when(mapper.claim(eq("ticket"), eq(7L), any())).thenReturn(1);
        when(mapper.findById("ticket")).thenReturn(ticket);
        when(oss.inspectImageObject(KEY)).thenThrow(missing);

        BusinessException error = assertThrows(BusinessException.class, () -> service.confirm(7L, "ticket"));
        assertEquals(404, error.getErrorCode().code());
        verify(oss).deleteObjectKey(KEY);
        verify(mapper).markFailed("ticket");
    }

    @Test void otherUserCannotConfirmAndConfirmedTicketCannotRepeat() {
        ImageUploadTicket ticket = ticket("ISSUED", LocalDateTime.now(ZoneOffset.UTC).plusMinutes(4));
        when(mapper.findById("ticket")).thenReturn(ticket);
        assertThrows(SecurityException.class, () -> service.confirm(8L, "ticket"));

        ticket.setStatus("CONFIRMED");
        BusinessException repeated = assertThrows(BusinessException.class, () -> service.confirm(7L, "ticket"));
        assertEquals(409, repeated.getErrorCode().code());
        verifyNoInteractions(oss);
    }

    @Test void expiredTicketIsMarkedAndAnyLateObjectIsRemoved() {
        ImageUploadTicket ticket = ticket("ISSUED", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        when(mapper.findById("ticket")).thenReturn(ticket);
        when(mapper.markExpired("ticket", 7L)).thenReturn(1);
        BusinessException expired = assertThrows(BusinessException.class, () -> service.confirm(7L, "ticket"));
        assertEquals(409, expired.getErrorCode().code());
        verify(mapper).markExpired("ticket", 7L);
        verify(oss).deleteObjectKey(KEY);
    }

    @Test void cleanupExpiresOnlyAtomicallyClaimedAbandonedTickets() {
        ImageUploadTicket first = ticket("ISSUED", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        ImageUploadTicket raced = ticket("ISSUED", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        raced.setTicketId("raced");
        raced.setObjectKey(KEY.replace("977b707b", "877b707b"));
        when(mapper.findExpiredIssued(any(), eq(100))).thenReturn(List.of(first, raced));
        when(mapper.markExpired("ticket", 7L)).thenReturn(1);
        when(mapper.markExpired("raced", 7L)).thenReturn(0);

        assertEquals(1, service.cleanupExpiredTickets(100));
        verify(oss).deleteObjectKey(KEY);
        verify(oss, never()).deleteObjectKey(raced.getObjectKey());
    }

    private ImageUploadTicket ticket(String status, LocalDateTime expiresAt) {
        ImageUploadTicket ticket = new ImageUploadTicket();
        ticket.setTicketId("ticket");
        ticket.setUserId(7L);
        ticket.setObjectKey(KEY);
        ticket.setExpectedSize(4L);
        ticket.setExpectedFormat("jpeg");
        ticket.setExpectedMime("image/jpeg");
        ticket.setStatus(status);
        ticket.setExpiresAt(expiresAt);
        return ticket;
    }
}
