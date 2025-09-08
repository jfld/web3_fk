package com.web3.alert.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警实体类
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_alert_type", columnList = "alertType"),
    @Index(name = "idx_severity", columnList = "severity"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_source", columnList = "source"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_resolved_at", columnList = "resolvedAt"),
    @Index(name = "idx_target_address", columnList = "targetAddress"),
    @Index(name = "idx_tx_hash", columnList = "transactionHash")
})
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 告警唯一标识
     */
    @Column(name = "alert_id", unique = true, nullable = false, length = 64)
    private String alertId;

    /**
     * 告警类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 50)
    private AlertType alertType;

    /**
     * 严重等级
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

    /**
     * 告警状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlertStatus status = AlertStatus.ACTIVE;

    /**
     * 告警来源
     */
    @Column(name = "source", nullable = false, length = 50)
    private String source;

    /**
     * 告警标题
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * 告警描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 目标地址（如果相关）
     */
    @Column(name = "target_address", length = 42)
    private String targetAddress;

    /**
     * 交易哈希（如果相关）
     */
    @Column(name = "transaction_hash", length = 66)
    private String transactionHash;

    /**
     * 区块号（如果相关）
     */
    @Column(name = "block_number")
    private Long blockNumber;

    /**
     * 网络
     */
    @Column(name = "network", length = 20)
    private String network;

    /**
     * 风险评分
     */
    @Column(name = "risk_score", precision = 5, scale = 4)
    private java.math.BigDecimal riskScore;

    /**
     * 告警标签
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "alert_tags",
        joinColumns = @JoinColumn(name = "alert_id")
    )
    @Column(name = "tag")
    private List<String> tags;

    /**
     * 告警元数据（JSON格式）
     */
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    /**
     * 触发规则
     */
    @Column(name = "trigger_rule", length = 100)
    private String triggerRule;

    /**
     * 是否已通知
     */
    @Column(name = "notified", nullable = false)
    private Boolean notified = false;

    /**
     * 通知渠道
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "alert_notification_channels",
        joinColumns = @JoinColumn(name = "alert_id")
    )
    @Column(name = "channel")
    private List<String> notificationChannels;

    /**
     * 通知次数
     */
    @Column(name = "notification_count")
    private Integer notificationCount = 0;

    /**
     * 最后通知时间
     */
    @Column(name = "last_notification_at")
    private LocalDateTime lastNotificationAt;

    /**
     * 是否已升级
     */
    @Column(name = "escalated", nullable = false)
    private Boolean escalated = false;

    /**
     * 升级时间
     */
    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    /**
     * 升级原因
     */
    @Column(name = "escalation_reason", length = 500)
    private String escalationReason;

    /**
     * 处理人员
     */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    /**
     * 解决时间
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * 解决原因
     */
    @Column(name = "resolution_reason", columnDefinition = "TEXT")
    private String resolutionReason;

    /**
     * 是否为误报
     */
    @Column(name = "false_positive", nullable = false)
    private Boolean falsePositive = false;

    /**
     * 反馈评分 (1-5)
     */
    @Column(name = "feedback_score")
    private Integer feedbackScore;

    /**
     * 反馈备注
     */
    @Column(name = "feedback_notes", columnDefinition = "TEXT")
    private String feedbackNotes;

    /**
     * 相关告警ID列表
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "alert_relations",
        joinColumns = @JoinColumn(name = "alert_id")
    )
    @Column(name = "related_alert_id")
    private List<String> relatedAlertIds;

    /**
     * 父告警ID（用于告警聚合）
     */
    @Column(name = "parent_alert_id", length = 64)
    private String parentAlertId;

    /**
     * 子告警数量
     */
    @Column(name = "child_alert_count")
    private Integer childAlertCount = 0;

    /**
     * 告警持续时间（秒）
     */
    @Column(name = "duration_seconds")
    private Long durationSeconds;

    /**
     * 自动解决时间（如果设置了自动解决）
     */
    @Column(name = "auto_resolve_at")
    private LocalDateTime autoResolveAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 告警类型枚举
     */
    public enum AlertType {
        HIGH_RISK_TRANSACTION,      // 高风险交易
        BLACKLIST_ADDRESS,          // 黑名单地址
        LARGE_TRANSFER,             // 大额转账
        SUSPICIOUS_BEHAVIOR,        // 可疑行为
        CONTRACT_EXPLOIT,           // 合约漏洞利用
        PHISHING_ATTACK,            // 钓鱼攻击
        MONEY_LAUNDERING,           // 洗钱活动
        FLASH_LOAN_ATTACK,          // 闪电贷攻击
        MEV_ATTACK,                 // MEV攻击
        GOVERNANCE_ATTACK,          // 治理攻击
        BRIDGE_EXPLOIT,             // 跨链桥攻击
        UNUSUAL_GAS_USAGE,          // 异常Gas使用
        FAILED_TRANSACTION_BURST,   // 失败交易爆发
        NEW_TOKEN_SCAM,             // 新代币诈骗
        SANDWICH_ATTACK,            // 三明治攻击
        SYSTEM_ERROR,               // 系统错误
        PERFORMANCE_DEGRADATION,    // 性能下降
        DATA_QUALITY_ISSUE          // 数据质量问题
    }

    /**
     * 严重等级枚举
     */
    public enum Severity {
        CRITICAL,   // 严重
        HIGH,       // 高
        MEDIUM,     // 中
        LOW,        // 低
        INFO        // 信息
    }

    /**
     * 告警状态枚举
     */
    public enum AlertStatus {
        ACTIVE,         // 活跃
        ACKNOWLEDGED,   // 已确认
        INVESTIGATING,  // 调查中
        RESOLVED,       // 已解决
        CLOSED,         // 已关闭
        SUPPRESSED      // 已抑制
    }

    /**
     * 检查是否为高严重等级
     */
    public boolean isHighSeverity() {
        return severity == Severity.CRITICAL || severity == Severity.HIGH;
    }

    /**
     * 检查是否需要立即处理
     */
    public boolean requiresImmediateAttention() {
        return severity == Severity.CRITICAL && status == AlertStatus.ACTIVE;
    }

    /**
     * 计算告警持续时间
     */
    public void calculateDuration() {
        if (createdAt != null) {
            LocalDateTime endTime = resolvedAt != null ? resolvedAt : LocalDateTime.now();
            durationSeconds = java.time.Duration.between(createdAt, endTime).getSeconds();
        }
    }

    /**
     * 增加通知计数
     */
    public void incrementNotificationCount() {
        notificationCount++;
        lastNotificationAt = LocalDateTime.now();
        notified = true;
    }

    /**
     * 标记为已升级
     */
    public void markAsEscalated(String reason) {
        escalated = true;
        escalatedAt = LocalDateTime.now();
        escalationReason = reason;
    }

    /**
     * 解决告警
     */
    public void resolve(String reason) {
        status = AlertStatus.RESOLVED;
        resolvedAt = LocalDateTime.now();
        resolutionReason = reason;
        calculateDuration();
    }

    /**
     * 标记为误报
     */
    public void markAsFalsePositive(String reason) {
        falsePositive = true;
        resolve("False Positive: " + reason);
    }

    @PrePersist
    protected void onCreate() {
        if (alertId == null) {
            alertId = generateAlertId();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 生成告警ID
     */
    private String generateAlertId() {
        return "ALERT_" + System.currentTimeMillis() + "_" + 
               Integer.toHexString((int)(Math.random() * 65536)).toUpperCase();
    }
}