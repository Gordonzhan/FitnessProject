package com.fitness.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OSSUtilTest {
    private static final String UUID = "977b707b-4444-41e3-aef9-9c58d0fa7b28";
    private static final String BASE = "https://testbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/";
    private final OSS client = mock(OSS.class);
    private final OSSUtil util = new OSSUtil("https://oss-cn-beijing.aliyuncs.com", "test", "test", "testbucket", "fitness-diary/images/") {
        @Override protected OSS createClient() { return client; }
    };

    @Test void parsesLegacyAndUserKeysWithoutHostInObjectName() {
        assertEquals("fitness-diary/images/" + UUID + ".jpeg", util.objectKey(BASE + UUID + ".jpeg"));
        assertTrue(util.belongsToUser(BASE + "7/" + UUID + ".png", 7L));
        assertFalse(util.belongsToUser(BASE + "7/" + UUID + ".png", 8L));
        assertFalse(util.belongsToUser(BASE + UUID + ".jpeg", 7L));
        assertFalse(util.belongsToUser(BASE.replace("https:", "http:") + "7/" + UUID + ".png", 7L));
    }

    @Test void rejectsForeignHostsTraversalAndAmbiguousUrls() {
        for (String url : new String[]{null, "", "wxfile://tmp.jpg", BASE + "../" + UUID + ".jpeg",
                BASE + "%2e%2e/" + UUID + ".jpeg", BASE + UUID + ".svg", BASE + UUID + ".jpeg?x=1",
                BASE.replace(".com/", ".com.evil.test/") + UUID + ".jpeg",
                BASE.replace("https://", "https://user@") + UUID + ".jpeg"}) {
            assertThrows(IllegalArgumentException.class, () -> util.objectKey(url));
        }
    }

    @Test void deletesExactObjectKeyAndClosesClient() {
        util.deleteImage(BASE + UUID + ".jpeg");
        verify(client).deleteObject("testbucket", "fitness-diary/images/" + UUID + ".jpeg");
        verify(client).shutdown();
    }

    @Test void detectsFormatAndRejectsErrorDocuments() {
        assertEquals("jpeg", OSSUtil.detectFormat(new byte[]{-1, -40, -1}));
        assertEquals("png", OSSUtil.detectFormat(new byte[]{-119, 80, 78, 71, 13, 10, 26, 10}));
        assertEquals("webp", OSSUtil.detectFormat("RIFF1234WEBP".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("gif", OSSUtil.detectFormat("GIF89a".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () -> OSSUtil.detectFormat("<Error>oops".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test void uploadUsesUserDirectoryAndContentTypeFromFileHeader() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "wrong.html", "text/html", new byte[]{-1, -40, -1, 1});
        String url = util.uploadImage(file, 7L);
        assertTrue(url.startsWith(BASE + "7/"));
        assertTrue(url.endsWith(".jpeg"));
        verify(client).putObject(eq("testbucket"), startsWith("fitness-diary/images/7/"), any(InputStream.class),
                argThat((ObjectMetadata metadata) -> "image/jpeg".equals(metadata.getContentType()) && metadata.getContentLength() == 4));
        verify(client).shutdown();
    }

    @Test void badFileDoesNotCallOss() {
        assertThrows(IllegalArgumentException.class, () -> util.uploadImage(
                new MockMultipartFile("file", "fake.jpg", "image/jpeg", "<html>bad</html>".getBytes()), 7L));
        verifyNoInteractions(client);
    }

    @Test void confirmationReadsMetadataAndOnlyTheFirstTwelveBytes() throws Exception {
        String key = "fitness-diary/images/7/" + UUID + ".jpeg";
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(20);
        metadata.setContentType("image/jpeg");
        OSSObject object = mock(OSSObject.class);
        when(client.getObjectMetadata("testbucket", key)).thenReturn(metadata);
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(object);
        when(object.getObjectContent()).thenReturn(new ByteArrayInputStream(new byte[20]));

        OSSUtil.RemoteImage remote = util.inspectImageObject(key);
        assertEquals(20, remote.size());
        assertEquals(12, remote.header().length);
        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObject(request.capture());
        assertArrayEquals(new long[]{0, 11}, request.getValue().getRange());
        verify(object).close();
        verify(client).shutdown();
    }

    @Test void serverKeyOperationsRejectLegacyForeignAndMalformedKeys() {
        assertThrows(IllegalArgumentException.class, () -> util.deleteObjectKey("other/path/" + UUID + ".jpeg"));
        assertThrows(IllegalArgumentException.class, () -> util.deleteObjectKey("fitness-diary/images/" + UUID + ".jpeg"));
        assertThrows(IllegalArgumentException.class,
                () -> util.deleteObjectKey("fitness-diary/images/7/" + UUID + ".svg"));
    }
}
