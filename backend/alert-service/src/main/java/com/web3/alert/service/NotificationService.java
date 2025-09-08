package com.web3.alert.service;

import com.web3.alert.entity.Alert;
import com.web3.alert.entity.NotificationRecord;
import com.web3.alert.repository.NotificationRecordRepository;
import com.web3.alert.service.notification.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 通知服务 - 多渠道通知实现
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRecordRepository notificationRecordRepository;
    private final EmailNotificationProvider emailProvider;
    private final SmsNotificationProvider smsProvider;
    private final WechatNotificationProvider wechatProvider;
    private final WebhookNotificationProvider webhookProvider;
    private final TelegramNotificationProvider telegramProvider;

    /**
     * 发送通知 - 主要入口方法
     */
    @Async
    public CompletableFuture<NotificationRecord> sendNotification(Alert alert, 
                                                                 NotificationRecord.NotificationChannel channel) {
        log.info("开始发送 {} 通知: {}", channel, alert.getAlertId());
        
        long startTime = System.currentTimeMillis();
        
        // 创建通知记录
        NotificationRecord record = createNotificationRecord(alert, channel);
        
        try {
            // 标记为发送中
            record.markAsSending();
            notificationRecordRepository.save(record);
            
            // 根据渠道选择提供者
            NotificationProvider provider = getNotificationProvider(channel);
            if (provider == null) {
                throw new UnsupportedOperationException("不支持的通知渠道: " + channel);
            }
            
            // 发送通知
            NotificationResult result = provider.sendNotification(alert, record);
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (result.isSuccess()) {
                record.markAsSent(result.getExternalId(), result.getResponse(), duration);
                log.info("{} 通知发送成功: {} (耗时: {}ms)", channel, alert.getAlertId(), duration);
            } else {
                record.markAsFailed(result.getErrorMessage());
                log.error("{} 通知发送失败: {} - {}", channel, alert.getAlertId(), result.getErrorMessage());
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            record.markAsFailed("发送异常: " + e.getMessage());
            log.error("{} 通知发送异常: {} (耗时: {}ms)", channel, alert.getAlertId(), duration, e);
        }
        
        // 保存通知记录
        NotificationRecord savedRecord = notificationRecordRepository.save(record);
        
        return CompletableFuture.completedFuture(savedRecord);
    }

    /**
     * 批量发送通知
     */
    @Async
    public CompletableFuture<Void> sendBulkNotifications(Alert alert) {
        log.info("开始批量发送通知: {}", alert.getAlertId());
        
        // 根据告警严重程度确定通知渠道
        NotificationRecord.NotificationChannel[] channels = determineChannels(alert.getSeverity());
        
        // 并行发送到所有渠道
        CompletableFuture<NotificationRecord>[] futures = new CompletableFuture[channels.length];
        
        for (int i = 0; i < channels.length; i++) {
            futures[i] = sendNotification(alert, channels[i]);
        }
        
        // 等待所有通知发送完成
        return CompletableFuture.allOf(futures).thenRun(() -> {
            log.info("批量通知发送完成: {}", alert.getAlertId());
        });
    }

    /**
     * 重发失败的通知
     */
    @Async
    public CompletableFuture<NotificationRecord> retryFailedNotification(Long recordId) {
        log.info("重试失败通知: {}", recordId);
        
        NotificationRecord record = notificationRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("通知记录不存在: " + recordId));
        
        if (!record.canRetry()) {
            log.warn("通知记录不能重试: {} (状态: {}, 重试次数: {})", 
                    recordId, record.getStatus(), record.getRetryCount());
            return CompletableFuture.completedFuture(record);
        }
        
        // 重新构造告警对象（简化版）
        Alert alert = reconstructAlertFromRecord(record);
        
        return sendNotification(alert, record.getChannel());
    }

    /**
     * 获取通知统计
     */
    public Map<String, Object> getNotificationStatistics(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        
        Map<String, Object> stats = new HashMap<>();
        
        // 总通知数
        long totalNotifications = notificationRecordRepository.countByCreatedAtAfter(since);
        stats.put("totalNotifications", totalNotifications);
        
        // 按渠道统计
        stats.put("channelStats", notificationRecordRepository.getNotificationCountByChannel(since));
        
        // 按状态统计
        stats.put("statusStats", notificationRecordRepository.getNotificationCountByStatus(since));
        
        // 成功率
        long successfulNotifications = notificationRecordRepository.countByStatusAndCreatedAtAfter(
                NotificationRecord.NotificationStatus.SENT, since);
        double successRate = totalNotifications > 0 ? 
                (double) successfulNotifications / totalNotifications * 100 : 0;
        stats.put("successRate", successRate);
        
        // 平均发送时延
        Double avgSendDelay = notificationRecordRepository.getAverageSendDelay(since);
        stats.put("averageSendDelayMs", avgSendDelay);
        
        return stats;
    }

    /**
     * 标记通知为已读
     */
    public void markAsRead(String externalMessageId) {
        NotificationRecord record = notificationRecordRepository.findByExternalMessageId(externalMessageId)
                .orElse(null);
        
        if (record != null) {
            record.markAsRead();
            notificationRecordRepository.save(record);
            log.debug("通知标记为已读: {}", externalMessageId);
        }
    }

    /**
     * 标记通知为已送达
     */
    public void markAsDelivered(String externalMessageId) {
        NotificationRecord record = notificationRecordRepository.findByExternalMessageId(externalMessageId)
                .orElse(null);
        
        if (record != null) {
            record.markAsDelivered();
            notificationRecordRepository.save(record);
            log.debug("通知标记为已送达: {}", externalMessageId);
        }
    }

    // ================== 私有方法 ==================

    /**
     * 创建通知记录
     */
    private NotificationRecord createNotificationRecord(Alert alert, NotificationRecord.NotificationChannel channel) {
        NotificationRecord record = new NotificationRecord();
        
        record.setAlertId(alert.getAlertId());
        record.setChannel(channel);
        record.setTitle(formatNotificationTitle(alert));
        record.setContent(formatNotificationContent(alert));
        record.setRecipient(determineRecipient(channel, alert));
        record.setPriority(mapSeverityToPriority(alert.getSeverity()));
        
        // 设置过期时间
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(getExpirationHours(alert.getSeverity()));
        record.setExpiresAt(expiresAt);
        
        return record;
    }

    /**
     * 根据渠道获取通知提供者
     */
    private NotificationProvider getNotificationProvider(NotificationRecord.NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> emailProvider;
            case SMS -> smsProvider;
            case WECHAT -> wechatProvider;
            case WEBHOOK -> webhookProvider;
            case TELEGRAM -> telegramProvider;
            default -> null;
        };
    }

    /**
     * 根据严重程度确定通知渠道
     */
    private NotificationRecord.NotificationChannel[] determineChannels(Alert.Severity severity) {
        return switch (severity) {
            case CRITICAL -> new NotificationRecord.NotificationChannel[]{
                    NotificationRecord.NotificationChannel.EMAIL,
                    NotificationRecord.NotificationChannel.SMS,
                    NotificationRecord.NotificationChannel.WECHAT,
                    NotificationRecord.NotificationChannel.WEBHOOK,
                    NotificationRecord.NotificationChannel.TELEGRAM
            };
            case HIGH -> new NotificationRecord.NotificationChannel[]{
                    NotificationRecord.NotificationChannel.EMAIL,
                    NotificationRecord.NotificationChannel.WECHAT,
                    NotificationRecord.NotificationChannel.WEBHOOK
            };
            case MEDIUM -> new NotificationRecord.NotificationChannel[]{
                    NotificationRecord.NotificationChannel.EMAIL,
                    NotificationRecord.NotificationChannel.WEBHOOK
            };
            case LOW, INFO -> new NotificationRecord.NotificationChannel[]{
                    NotificationRecord.NotificationChannel.WEBHOOK
            };
        };
    }

    /**
     * 格式化通知标题
     */
    private String formatNotificationTitle(Alert alert) {
        return String.format("[%s] %s - %s", 
                alert.getSeverity().name(), 
                alert.getAlertType().name(), 
                alert.getTitle());
    }

    /**
     * 格式化通知内容
     */
    private String formatNotificationContent(Alert alert) {
        StringBuilder content = new StringBuilder();
        
        content.append("告警详情:\n");
        content.append("- 告警ID: ").append(alert.getAlertId()).append("\n");
        content.append("- 类型: ").append(alert.getAlertType()).append("\n");
        content.append("- 严重程度: ").append(alert.getSeverity()).append("\n");
        content.append("- 时间: ").append(alert.getCreatedAt()).append("\n");
        
        if (alert.getTargetAddress() != null) {
            content.append("- 目标地址: ").append(alert.getTargetAddress()).append("\n");
        }
        
        if (alert.getTransactionHash() != null) {
            content.append("- 交易哈希: ").append(alert.getTransactionHash()).append("\n");
        }
        
        if (alert.getNetwork() != null) {
            content.append("- 网络: ").append(alert.getNetwork()).append("\n");
        }
        
        if (alert.getRiskScore() != null) {
            content.append("- 风险评分: ").append(alert.getRiskScore()).append("\n");
        }
        
        content.append("- 描述: ").append(alert.getDescription()).append("\n");
        
        return content.toString();
    }

    /**
     * 确定接收人
     */
    private String determineRecipient(NotificationRecord.NotificationChannel channel, Alert alert) {
        // 这里应该根据配置或用户设置来确定接收人
        // 简化实现，返回默认值
        return switch (channel) {
            case EMAIL -> "admin@example.com";
            case SMS -> "+1234567890";
            case WECHAT -> "default-wechat-user";
            case WEBHOOK -> "http://localhost:3000/webhook/alerts";
            case TELEGRAM -> "@admin";
            default -> "default-recipient";
        };
    }

    /**
     * 将告警严重程度映射到通知优先级
     */
    private NotificationRecord.NotificationPriority mapSeverityToPriority(Alert.Severity severity) {
        return switch (severity) {
            case CRITICAL -> NotificationRecord.NotificationPriority.URGENT;
            case HIGH -> NotificationRecord.NotificationPriority.HIGH;
            case MEDIUM -> NotificationRecord.NotificationPriority.NORMAL;
            case LOW, INFO -> NotificationRecord.NotificationPriority.LOW;
        };
    }

    /**
     * 获取过期时间（小时）
     */
    private int getExpirationHours(Alert.Severity severity) {
        return switch (severity) {
            case CRITICAL -> 1;  // 1小时
            case HIGH -> 6;      // 6小时
            case MEDIUM -> 24;   // 24小时
            case LOW, INFO -> 72; // 72小时
        };
    }

    /**
     * 从通知记录重构告警对象
     */
    private Alert reconstructAlertFromRecord(NotificationRecord record) {
        Alert alert = new Alert();
        alert.setAlertId(record.getAlertId());
        alert.setTitle(record.getTitle());
        alert.setDescription(record.getContent());
        // 其他字段根据需要设置...
        
        return alert;
    }

    /**
     * 通知结果内部类
     */
    public static class NotificationResult {
        private final boolean success;
        private final String externalId;
        private final String response;
        private final String errorMessage;
        
        public NotificationResult(boolean success, String externalId, String response, String errorMessage) {
            this.success = success;
            this.externalId = externalId;
            this.response = response;
            this.errorMessage = errorMessage;
        }
        
        public boolean isSuccess() { return success; }
        public String getExternalId() { return externalId; }
        public String getResponse() { return response; }
        public String getErrorMessage() { return errorMessage; }
        
        public static NotificationResult success(String externalId, String response) {
            return new NotificationResult(true, externalId, response, null);
        }
        
        public static NotificationResult failure(String errorMessage) {
            return new NotificationResult(false, null, null, errorMessage);
        }
    }
}