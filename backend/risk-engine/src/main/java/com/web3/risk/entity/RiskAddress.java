package com.web3.risk.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 地址风险档案实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "risk_addresses", indexes = {
    @Index(name = "idx_address", columnList = "address"),
    @Index(name = "idx_risk_level", columnList = "riskLevel"),
    @Index(name = "idx_network", columnList = "network"),
    @Index(name = "idx_last_activity", columnList = "lastActivity"),
    @Index(name = "idx_risk_score", columnList = "riskScore")
})
public class RiskAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "address", nullable = false, length = 42)
    private String address;

    @Column(name = "network", nullable = false, length = 20)
    private String network;

    @Column(name = "risk_score", precision = 5, scale = 4)
    private BigDecimal riskScore = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 10)
    private RiskTransaction.RiskLevel riskLevel = RiskTransaction.RiskLevel.LOW;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "address_risk_tags",
        joinColumns = @JoinColumn(name = "address_id")
    )
    @Column(name = "risk_tag")
    private List<String> riskTags;

    @Column(name = "address_type", length = 20)
    private String addressType; // EOA, CONTRACT, EXCHANGE, MIXER, etc.

    @Column(name = "first_seen")
    private LocalDateTime firstSeen;

    @Column(name = "last_activity")
    private LocalDateTime lastActivity;

    @Column(name = "transaction_count")
    private Long transactionCount = 0L;

    @Column(name = "sent_count")
    private Long sentCount = 0L;

    @Column(name = "received_count")
    private Long receivedCount = 0L;

    @Column(name = "total_volume_sent", precision = 36, scale = 0)
    private BigInteger totalVolumeSent = BigInteger.ZERO;

    @Column(name = "total_volume_received", precision = 36, scale = 0)
    private BigInteger totalVolumeReceived = BigInteger.ZERO;

    @Column(name = "unique_counterparties")
    private Integer uniqueCounterparties = 0;

    @Column(name = "max_single_transaction", precision = 36, scale = 0)
    private BigInteger maxSingleTransaction = BigInteger.ZERO;

    @Column(name = "avg_transaction_value", precision = 36, scale = 0)
    private BigInteger avgTransactionValue = BigInteger.ZERO;

    // 黑名单相关
    @Column(name = "is_blacklisted")
    private Boolean isBlacklisted = false;

    @Column(name = "blacklist_source", length = 100)
    private String blacklistSource;

    @Column(name = "blacklist_reason", columnDefinition = "TEXT")
    private String blacklistReason;

    @Column(name = "blacklisted_at")
    private LocalDateTime blacklistedAt;

    // 标签相关
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "address_labels",
        joinColumns = @JoinColumn(name = "address_id")
    )
    @Column(name = "label")
    private List<String> labels;

    @Column(name = "label_source", length = 100)
    private String labelSource;

    @Column(name = "label_confidence", precision = 3, scale = 2)
    private BigDecimal labelConfidence;

    // 合约相关（如果是合约地址）
    @Column(name = "contract_name", length = 100)
    private String contractName;

    @Column(name = "contract_creator", length = 42)
    private String contractCreator;

    @Column(name = "creation_block")
    private Long creationBlock;

    @Column(name = "creation_timestamp")
    private LocalDateTime creationTimestamp;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @Column(name = "proxy_type", length = 50)
    private String proxyType;

    // 交易所相关
    @Column(name = "exchange_name", length = 100)
    private String exchangeName;

    @Column(name = "is_exchange_wallet")
    private Boolean isExchangeWallet = false;

    @Column(name = "is_hot_wallet")
    private Boolean isHotWallet = false;

    @Column(name = "is_cold_wallet")
    private Boolean isColdWallet = false;

    // DeFi相关
    @Column(name = "defi_protocols", columnDefinition = "TEXT")
    private String defiProtocols; // JSON格式存储参与的DeFi协议

    @Column(name = "liquidity_provider")
    private Boolean isLiquidityProvider = false;

    @Column(name = "yield_farmer")
    private Boolean isYieldFarmer = false;

    // 风险行为统计
    @Column(name = "suspicious_activity_count")
    private Integer suspiciousActivityCount = 0;

    @Column(name = "high_risk_interactions")
    private Integer highRiskInteractions = 0;

    @Column(name = "mixer_interactions")
    private Integer mixerInteractions = 0;

    @Column(name = "sanction_interactions")
    private Integer sanctionInteractions = 0;

    // 时间模式分析
    @Column(name = "most_active_hour")
    private Integer mostActiveHour;

    @Column(name = "weekend_activity_ratio", precision = 3, scale = 2)
    private BigDecimal weekendActivityRatio;

    @Column(name = "night_activity_ratio", precision = 3, scale = 2)
    private BigDecimal nightActivityRatio;

    // 地理位置相关
    @Column(name = "estimated_country", length = 50)
    private String estimatedCountry;

    @Column(name = "estimated_region", length = 100)
    private String estimatedRegion;

    @Column(name = "vpn_usage_detected")
    private Boolean vpnUsageDetected = false;

    // 元数据
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "analyst_review")
    private Boolean analystReview = false;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 地址类型枚举
     */
    public enum AddressType {
        EOA,          // 外部拥有账户
        CONTRACT,     // 智能合约
        EXCHANGE,     // 交易所
        MIXER,        // 混币器
        DEFI,         // DeFi协议
        BRIDGE,       // 跨链桥
        MULTISIG,     // 多签钱包
        UNKNOWN       // 未知类型
    }

    /**
     * 检查是否为高风险地址
     */
    public boolean isHighRisk() {
        return riskLevel == RiskTransaction.RiskLevel.HIGH || 
               riskLevel == RiskTransaction.RiskLevel.CRITICAL ||
               isBlacklisted;
    }

    /**
     * 检查是否为活跃地址
     */
    public boolean isActive() {
        if (lastActivity == null) {
            return false;
        }
        return lastActivity.isAfter(LocalDateTime.now().minusDays(30));
    }

    /**
     * 计算地址活跃度评分
     */
    public BigDecimal calculateActivityScore() {
        if (transactionCount == null || transactionCount == 0) {
            return BigDecimal.ZERO;
        }
        
        // 基于交易频率、金额、时间等因素计算活跃度
        BigDecimal txCountScore = new BigDecimal(Math.min(transactionCount, 1000)).divide(new BigDecimal(1000));
        BigDecimal volumeScore = calculateVolumeScore();
        BigDecimal timeScore = calculateTimeScore();
        
        return txCountScore.multiply(new BigDecimal("0.4"))
               .add(volumeScore.multiply(new BigDecimal("0.4")))
               .add(timeScore.multiply(new BigDecimal("0.2")));
    }

    private BigDecimal calculateVolumeScore() {
        BigInteger totalVolume = totalVolumeSent.add(totalVolumeReceived);
        if (totalVolume.equals(BigInteger.ZERO)) {
            return BigDecimal.ZERO;
        }
        
        // 将总交易量转换为ETH并计算评分
        BigDecimal volumeEth = new BigDecimal(totalVolume).divide(new BigDecimal("1000000000000000000"));
        return volumeEth.divide(new BigDecimal("10000"), 4, BigDecimal.ROUND_HALF_UP).min(BigDecimal.ONE);
    }

    private BigDecimal calculateTimeScore() {
        if (lastActivity == null) {
            return BigDecimal.ZERO;
        }
        
        long daysSinceLastActivity = java.time.temporal.ChronoUnit.DAYS.between(lastActivity, LocalDateTime.now());
        if (daysSinceLastActivity <= 1) {
            return BigDecimal.ONE;
        } else if (daysSinceLastActivity <= 7) {
            return new BigDecimal("0.8");
        } else if (daysSinceLastActivity <= 30) {
            return new BigDecimal("0.6");
        } else if (daysSinceLastActivity <= 90) {
            return new BigDecimal("0.4");
        } else {
            return new BigDecimal("0.2");
        }
    }

    /**
     * 更新地址统计信息
     */
    public void updateStatistics(BigInteger txValue, boolean isSent, LocalDateTime txTimestamp) {
        if (firstSeen == null || txTimestamp.isBefore(firstSeen)) {
            firstSeen = txTimestamp;
        }
        
        if (lastActivity == null || txTimestamp.isAfter(lastActivity)) {
            lastActivity = txTimestamp;
        }
        
        transactionCount++;
        
        if (isSent) {
            sentCount++;
            totalVolumeSent = totalVolumeSent.add(txValue);
        } else {
            receivedCount++;
            totalVolumeReceived = totalVolumeReceived.add(txValue);
        }
        
        if (txValue.compareTo(maxSingleTransaction) > 0) {
            maxSingleTransaction = txValue;
        }
        
        // 重新计算平均交易金额
        BigInteger totalVolume = totalVolumeSent.add(totalVolumeReceived);
        if (transactionCount > 0) {
            avgTransactionValue = totalVolume.divide(BigInteger.valueOf(transactionCount));
        }
    }

    @PrePersist
    protected void onCreate() {
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
}