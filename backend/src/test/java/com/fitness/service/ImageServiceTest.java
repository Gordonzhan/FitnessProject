package com.fitness.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fitness.mapper.RecipeImageMapper;
import com.fitness.mapper.SystemRecipeTemplateMapper;
import com.fitness.util.OSSUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageServiceTest {
    private final OSSUtil oss = mock(OSSUtil.class);
    private final RecipeImageMapper mapper = mock(RecipeImageMapper.class);
    private final SystemRecipeTemplateMapper systemMapper = mock(SystemRecipeTemplateMapper.class);
    private final ImageService service = new ImageService(oss, mapper, systemMapper);
    private final String url = "https://test/image.jpeg";

    @AfterEach void close() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        service.shutdown();
    }

    @Test void rejectsOtherUsersAndReferencedImages() {
        assertThrows(SecurityException.class, () -> service.deleteUnusedUpload(1L, url));
        when(oss.belongsToUser(url, 1L)).thenReturn(true);
        when(mapper.countByImageUrl(url)).thenReturn(1);
        assertThrows(ImageService.ImageInUseException.class, () -> service.deleteUnusedUpload(1L, url));
        verify(oss, never()).deleteImage(anyString());
    }

    @Test void unusedOwnUploadCanBeDeletedRepeatedly() {
        when(oss.belongsToUser(url, 1L)).thenReturn(true);
        service.deleteUnusedUpload(1L, url);
        service.deleteUnusedUpload(1L, url);
        verify(oss, times(2)).deleteImage(url);
    }

    @Test void legacyUrlsCanBePreservedButNotIntroducedByAnotherRecipe() {
        service.validateImages(1L, List.of(url), List.of(url));
        assertThrows(IllegalArgumentException.class, () -> service.validateImages(1L, List.of(url), List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.validateImages(1L, Collections.nCopies(10, url), List.of(url)));
    }

    @Test void systemCoverCanBeCopiedButCannotBeDeleted() {
        when(systemMapper.countEnabledByCoverImageUrl(url)).thenReturn(1L);
        service.validateImages(8L, List.of(url), List.of());
        when(oss.belongsToUser(url, 8L)).thenReturn(true);
        assertThrows(ImageService.ImageInUseException.class, () -> service.deleteUnusedUpload(8L, url));
        verify(oss, never()).deleteImage(anyString());
    }

    @Test void rollbackNeverDeletesRemoteImages() {
        TransactionSynchronizationManager.initSynchronization();
        service.cleanupAfterCommit(List.of(url));
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(oss, mapper);
    }

    @Test void commitCleansOnlyUnreferencedImagesAndDeduplicates() {
        TransactionSynchronizationManager.initSynchronization();
        service.cleanupAfterCommit(List.of(url, url));
        verifyNoInteractions(oss);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(oss, timeout(1500).times(1)).deleteImage(url);
    }

    @Test void sharedImageSurvivesCommitCleanup() {
        when(mapper.countByImageUrl(url)).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();
        service.cleanupAfterCommit(List.of(url));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(mapper, timeout(1500)).countByImageUrl(url);
        verify(oss, never()).deleteImage(anyString());
    }

    @Test void cleanupFailureLogNeverContainsImageUrl() {
        Logger logger = (Logger) LoggerFactory.getLogger(ImageService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            doThrow(new IllegalArgumentException("invalid image URL")).when(oss).objectKey(url);
            TransactionSynchronizationManager.initSynchronization();
            service.cleanupAfterCommit(List.of(url));
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            long deadline = System.currentTimeMillis() + 1500;
            while (appender.list.isEmpty() && System.currentTimeMillis() < deadline) Thread.onSpinWait();

            assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                    && event.getFormattedMessage().startsWith("OSS_CLEANUP_PENDING reason=")));
            assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().contains(url)));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
