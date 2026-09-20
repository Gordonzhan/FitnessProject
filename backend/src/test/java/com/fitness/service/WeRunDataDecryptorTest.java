package com.fitness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeRunDataDecryptorTest {
    private static final byte[] KEY = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final byte[] IV = "abcdef0123456789".getBytes(StandardCharsets.UTF_8);

    @Test
    void decryptsOnlyDocumentedStepFieldsAndValidatesWatermark() throws Exception {
        WeRunDataDecryptor decryptor = new WeRunDataDecryptor(new ObjectMapper());
        ReflectionTestUtils.setField(decryptor, "appId", "wx-test");
        String json = "{\"stepInfoList\":[{\"timestamp\":1788710400,\"step\":8131}],"
                + "\"watermark\":{\"appid\":\"wx-test\",\"timestamp\":1788710500}}";

        var payload = decryptor.decrypt(encoded(KEY), encrypt(json), encoded(IV));

        assertEquals(1, payload.stepInfoList().size());
        assertEquals(8131, payload.stepInfoList().get(0).steps());
        assertEquals(1788710500L, payload.watermarkTimestamp());
    }

    @Test
    void rejectsPayloadIssuedForAnotherMiniProgram() throws Exception {
        WeRunDataDecryptor decryptor = new WeRunDataDecryptor(new ObjectMapper());
        ReflectionTestUtils.setField(decryptor, "appId", "wx-current");
        String json = "{\"stepInfoList\":[],\"watermark\":{\"appid\":\"wx-other\",\"timestamp\":1}}";
        assertThrows(SecurityException.class,
                () -> decryptor.decrypt(encoded(KEY), encrypt(json), encoded(IV)));
    }

    @Test
    void rejectsMalformedCiphertextWithoutLeakingCryptoDetails() {
        WeRunDataDecryptor decryptor = new WeRunDataDecryptor(new ObjectMapper());
        ReflectionTestUtils.setField(decryptor, "appId", "wx-test");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> decryptor.decrypt(encoded(KEY), "not-base64", encoded(IV)));
        assertEquals("微信运动数据解密失败，请重新授权后再试", error.getMessage());
    }

    private String encrypt(String text) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(IV));
        return encoded(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }

    private String encoded(byte[] value) { return Base64.getEncoder().encodeToString(value); }
}
