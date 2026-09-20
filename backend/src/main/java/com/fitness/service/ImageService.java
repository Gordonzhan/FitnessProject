package com.fitness.service;

import com.fitness.mapper.RecipeImageMapper;
import com.fitness.mapper.SystemRecipeTemplateMapper;
import com.fitness.util.OSSUtil;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

@Service
public class ImageService {
    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);
    private final OSSUtil ossUtil;
    private final RecipeImageMapper imageMapper;
    private final SystemRecipeTemplateMapper systemRecipeMapper;
    private final ExecutorService cleanupExecutor = new ThreadPoolExecutor(2, 2, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(100), runnable -> {
                Thread thread = new Thread(runnable, "oss-image-cleanup");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    /** 注入 OSS 工具和两类图片引用查询组件。 */
    public ImageService(OSSUtil ossUtil, RecipeImageMapper imageMapper,
                        SystemRecipeTemplateMapper systemRecipeMapper) {
        this.ossUtil = ossUtil;
        this.imageMapper = imageMapper;
        this.systemRecipeMapper = systemRecipeMapper;
    }

    /** 删除属于当前用户且尚未被任何菜谱引用的 OSS 上传图片。 */
    public void deleteUnusedUpload(Long userId, String url) {
        if (!ossUtil.belongsToUser(url, userId)) throw new SecurityException("不能删除其他用户或历史菜谱的图片");
        if (isReferenced(url)) throw new ImageInUseException();
        ossUtil.deleteImage(url);
    }

    /** 校验菜谱图片数量、地址以及图片所有权，允许复用系统菜谱封面。 */
    public void validateImages(Long userId, Collection<String> urls, Collection<String> previousUrls) {
        if (urls.size() > 9) throw new IllegalArgumentException("每个菜谱最多保存9张图片");
        for (String url : urls) {
            if (url == null || url.isBlank()) throw new IllegalArgumentException("图片地址不能为空");
            // 历史链接允许保留，加载失败由用户选择重试或移除；不因欠费自动删库。
            boolean reusableSystemCover = systemRecipeMapper.countEnabledByCoverImageUrl(url) > 0;
            if (!previousUrls.contains(url) && !ossUtil.belongsToUser(url, userId) && !reusableSystemCover) {
                throw new IllegalArgumentException("只能使用当前用户上传的图片");
            }
        }
    }

    /** 在菜谱事务提交成功后异步清理已经失去引用的旧图片。 */
    public void cleanupAfterCommit(Collection<String> urls) {
        List<String> candidates = urls.stream().distinct().toList();
        if (candidates.isEmpty()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("图片清理必须在菜谱事务内注册");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String url : candidates) {
                    try {
                        cleanupExecutor.execute(() -> cleanupUnusedImage(url));
                    } catch (RejectedExecutionException e) {
                        logger.warn("OSS_CLEANUP_PENDING reason=queue_full");
                    }
                }
            }
        });
    }

    /** 再次确认引用关系后删除单张图片；OSS 故障只记录待清理日志。 */
    private void cleanupUnusedImage(String url) {
        try {
            // 在提交后的独立线程重查引用；共享图片仍被其他菜谱使用时不删除。
            ossUtil.objectKey(url);
            if (!isReferenced(url)) ossUtil.deleteImage(url);
        } catch (Exception e) {
            // OSS不可用不应把已经成功的数据库提交报成保存失败。
            logger.warn("OSS_CLEANUP_PENDING reason={}", e.getClass().getSimpleName());
        }
    }

    /** 判断图片是否仍被用户菜谱或系统菜谱引用。 */
    private boolean isReferenced(String url) {
        return imageMapper.countByImageUrl(url) > 0
                || systemRecipeMapper.countEnabledByCoverImageUrl(url) > 0;
    }

    /** Spring 容器关闭时停止图片清理线程池。 */
    @PreDestroy
    public void shutdown() { cleanupExecutor.shutdown(); }

    public static class ImageInUseException extends RuntimeException {
        /** 创建“图片仍被引用”的业务异常。 */
        public ImageInUseException() { super("图片仍被菜谱使用，请先保存菜谱中的移除操作"); }
    }
}
