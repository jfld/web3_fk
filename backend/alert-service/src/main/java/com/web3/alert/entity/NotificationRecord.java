package com.web3.alert.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 通知记录实体类
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "notification_records", indexes = {
    @Index(name = "idx_alert_id", columnList = "alertId"),
    @Index(name = "idx_channel", columnList = "channel"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_recipient", columnList = "recipient")
})
public class NotificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的告警ID
     */
    @Column(name = "alert_id", nullable = false, length = 64)
    private String alertId;

    /**
     * 通知渠道
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    /**
     * 接收人
     */
    @Column(name = "recipient", nullable = false, length = 200)
    private String recipient;

    /**
     * 通知标题
     */
    @Column(name = "title", length = 500)
    private String title;

    /**
     * 通知内容
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * 通知状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    /**
     * 发送时间
     */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /**
     * 送达时间
     */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /**
     * 阅读时间
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * 重试次数
     */
    @Column(name = "retry_count")
    private Integer retryCount = 0;

    /**
     * 最大重试次数
     */
    @Column(name = "max_retry_count")
    private Integer maxRetryCount = 3;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 响应数据
     */
    @Column(name = "response_data", columnDefinition = "TEXT")
    private String responseData;

    /**
     * 外部消息ID（如邮件ID、短信ID等）
     */
    @Column(name = "external_message_id", length = 200)
    private String externalMessageId;

    /**
     * 发送耗时（毫秒）
     */
    @Column(name = "send_duration_ms")
    private Long sendDurationMs;

    /**
     * 模板ID
     */
    @Column(name = "template_id", length = 100)
    private String templateId;

    /**
     * 模板参数
     */
    @Column(name = "template_params", columnDefinition = "jsonb")
    private String templateParams;

    /**
     * 优先级
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 10)
    private NotificationPriority priority = NotificationPriority.NORMAL;

    /**
     * 预定发送时间
     */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /**
     * 过期时间
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * 批次ID（用于批量发送）
     */
    @Column(name = "batch_id", length = 64)
    private String batchId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 通知渠道枚举
     */
    public enum NotificationChannel {
        EMAIL,      // 邮件
        SMS,        // 短信
        WECHAT,     // 微信
        WEBHOOK,    // Webhook
        TELEGRAM,   // Telegram
        SLACK,      // Slack
        DINGTALK,   // 钉钉
        FEISHU,     // 飞书
        PUSH        // 推送通知
    }

    /**
     * 通知状态枚举
     */
    public enum NotificationStatus {
        PENDING,        // 等待发送
        SENDING,        // 发送中
        SENT,           // 已发送
        DELIVERED,      // 已送达
        READ,           // 已阅读
        FAILED,         // 发送失败
        EXPIRED,        // 已过期
        CANCELLED       // 已取消
    }

    /**
     * 通知优先级枚举
     */
    public enum NotificationPriority {
        LOW,        // 低优先级
        NORMAL,     // 普通优先级
        HIGH,       // 高优先级
        URGENT      // 紧急优先级
    }

    /**
     * 标记为发送中
     */
    public void markAsSending() {
        status = NotificationStatus.SENDING;
        sentAt = LocalDateTime.now();
    }

    /**
     * 标记为发送成功
     */
    public void markAsSent(String externalId, String response, long duration) {
        status = NotificationStatus.SENT;
        externalMessageId = externalId;
        responseData = response;
        sendDurationMs = duration;
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }

    /**
     * 标记为送达
     */
    public void markAsDelivered() {
        status = NotificationStatus.DELIVERED;
        deliveredAt = LocalDateTime.now();
    }

    /**
     * 标记为已阅读
     */
    public void markAsRead() {
        status = NotificationStatus.READ;
        readAt = LocalDateTime.now();
    }

    /**
     * 标记为失败
     */
    public void markAsFailed(String error) {
        status = NotificationStatus.FAILED;
        errorMessage = error;
        retryCount++;
    }

    /**
     * 检查是否可以重试
     */
    public boolean canRetry() {
        return status == NotificationStatus.FAILED && 
               retryCount < maxRetryCount &&
               (expiresAt == null || LocalDateTime.now().isBefore(expiresAt));
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 获取发送延迟（毫秒）
     */
    public Long getSendDelay() {
        if (createdAt != null && sentAt != null) {
            return java.time.Duration.between(createdAt, sentAt).toMillis();
        }
        return null;
    }

    /**
     * 获取送达延迟（毫秒）
     */
    public Long getDeliveryDelay() {
        if (sentAt != null && deliveredAt != null) {
            return java.time.Duration.between(sentAt, deliveredAt).toMillis();
        }
        return null;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        
        // 如果没有设置过期时间，默认设置为24小时后过期
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusHours(24);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        
        // 检查是否过期
        if (isExpired() && status != NotificationStatus.EXPIRED) {
            status = NotificationStatus.EXPIRED;
        }
    }
}