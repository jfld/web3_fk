package com.web3.risk.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 风险规则实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "risk_rules", indexes = {
    @Index(name = "idx_rule_name", columnList = "ruleName"),
    @Index(name = "idx_rule_type", columnList = "ruleType"),
    @Index(name = "idx_enabled", columnList = "enabled"),
    @Index(name = "idx_priority", columnList = "priority")
})
public class RiskRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, unique = true, length = 100)
    private String ruleName;

    @Column(name = "rule_description", columnDefinition = "TEXT")
    private String ruleDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 50)
    private RuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_category", length = 50)
    private RuleCategory ruleCategory;

    @Column(name = "condition_json", nullable = false, columnDefinition = "jsonb")
    private String conditionJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private RuleAction action;

    @Column(name = "risk_weight", precision = 5, scale = 4)
    private BigDecimal riskWeight = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private RiskTransaction.RiskLevel severity = RiskTransaction.RiskLevel.LOW;

    @Column(name = "priority")
    private Integer priority = 100;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "networks", length = 200)
    private String networks; // 逗号分隔的网络列表

    @Column(name = "execution_count")
    private Long executionCount = 0L;

    @Column(name = "match_count")
    private Long matchCount = 0L;

    @Column(name = "false_positive_count")
    private Long falsePositiveCount = 0L;

    @Column(name = "last_execution")
    private LocalDateTime lastExecution;

    @Column(name = "last_match")
    private LocalDateTime lastMatch;

    @Column(name = "execution_time_avg")
    private Long executionTimeAvg = 0L; // 平均执行时间(ms)

    @Column(name = "execution_time_max")
    private Long executionTimeMax = 0L; // 最大执行时间(ms)

    @Column(name = "author", length = 100)
    private String author;

    @Column(name = "version", length = 20)
    private String version = "1.0.0";

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "tags", length = 500)
    private String tags; // 逗号分隔的标签

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "test_cases", columnDefinition = "jsonb")
    private String testCases;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 规则类型枚举
     */
    public enum RuleType {
        TRANSACTION,      // 交易级规则
        ADDRESS,          // 地址级规则
        PATTERN,          // 模式识别规则
        BEHAVIORAL,       // 行为分析规则
        BLACKLIST,        // 黑名单规则
        WHITELIST,        // 白名单规则
        THRESHOLD,        // 阈值规则
        TIME_BASED,       // 时间基础规则
        NETWORK,          // 网络级规则
        COMPOSITE         // 复合规则
    }

    /**
     * 规则分类枚举
     */
    public enum RuleCategory {
        AML,              // 反洗钱
        SANCTIONS,        // 制裁检查
        FRAUD,            // 欺诈检测
        MARKET_MANIPULATION, // 市场操纵
        INSIDER_TRADING,  // 内幕交易
        MEV,              // MEV检测
        PHISHING,         // 钓鱼检测
        MIXER,            // 混币器检测
        DEFI_EXPLOIT,     // DeFi漏洞利用
        FLASH_LOAN,       // 闪电贷攻击
        GOVERNANCE,       // 治理攻击
        BRIDGE_EXPLOIT,   // 跨链桥攻击
        GENERAL           // 通用规则
    }

    /**
     * 规则动作枚举
     */
    public enum RuleAction {
        ALERT,            // 生成告警
        BLOCK,            // 阻断交易
        MONITOR,          // 监控
        LOG,              // 记录日志
        SCORE,            // 风险评分
        FLAG,             // 标记
        QUARANTINE,       // 隔离
        REPORT            // 报告
    }

    /**
     * 检查规则是否有效
     */
    public boolean isValid() {
        if (!enabled) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        
        if (validTo != null && now.isAfter(validTo)) {
            return false;
        }
        
        return true;
    }

    /**
     * 检查规则是否适用于指定网络
     */
    public boolean appliesToNetwork(String network) {
        if (networks == null || networks.trim().isEmpty()) {
            return true; // 适用于所有网络
        }
        
        String[] networkList = networks.split(",");
        for (String net : networkList) {
            if (net.trim().equalsIgnoreCase(network)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 增加执行次数
     */
    public void incrementExecutionCount() {
        executionCount++;
        lastExecution = LocalDateTime.now();
    }

    /**
     * 增加匹配次数
     */
    public void incrementMatchCount() {
        matchCount++;
        lastMatch = LocalDateTime.now();
    }

    /**
     * 增加误报次数
     */
    public void incrementFalsePositiveCount() {
        falsePositiveCount++;
    }

    /**
     * 更新执行时间统计
     */
    public void updateExecutionTime(long executionTime) {
        if (executionCount == 0) {
            executionTimeAvg = executionTime;
        } else {
            executionTimeAvg = (executionTimeAvg * (executionCount - 1) + executionTime) / executionCount;
        }
        
        if (executionTime > executionTimeMax) {
            executionTimeMax = executionTime;
        }
    }

    /**
     * 计算规则准确率
     */
    public BigDecimal calculateAccuracy() {
        if (matchCount == 0) {
            return BigDecimal.ZERO;
        }
        
        long truePositives = matchCount - falsePositiveCount;
        return new BigDecimal(truePositives)
                .divide(new BigDecimal(matchCount), 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 计算规则效率
     */
    public BigDecimal calculateEfficiency() {
        if (executionCount == 0) {
            return BigDecimal.ZERO;
        }
        
        return new BigDecimal(matchCount)
                .divide(new BigDecimal(executionCount), 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 获取规则标签列表
     */
    public String[] getTagList() {
        if (tags == null || tags.trim().isEmpty()) {
            return new String[0];
        }
        return tags.split(",");
    }

    /**
     * 添加标签
     */
    public void addTag(String tag) {
        if (tags == null || tags.trim().isEmpty()) {
            tags = tag;
        } else {
            tags += "," + tag;
        }
    }

    /**
     * 检查是否包含指定标签
     */
    public boolean hasTag(String tag) {
        if (tags == null) {
            return false;
        }
        String[] tagList = getTagList();
        for (String t : tagList) {
            if (t.trim().equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (version == null) {
            version = "1.0.0";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}