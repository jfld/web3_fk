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
 * 交易风险评估实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "risk_transactions", indexes = {
    @Index(name = "idx_tx_hash", columnList = "txHash"),
    @Index(name = "idx_from_address", columnList = "fromAddress"),
    @Index(name = "idx_to_address", columnList = "toAddress"),
    @Index(name = "idx_risk_level", columnList = "riskLevel"),
    @Index(name = "idx_network", columnList = "network"),
    @Index(name = "idx_block_number", columnList = "blockNumber"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
public class RiskTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tx_hash", unique = true, nullable = false, length = 66)
    private String txHash;

    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    @Column(name = "block_hash", length = 66)
    private String blockHash;

    @Column(name = "transaction_index")
    private Integer transactionIndex;

    @Column(name = "from_address", nullable = false, length = 42)
    private String fromAddress;

    @Column(name = "to_address", length = 42)
    private String toAddress;

    @Column(name = "value", precision = 36, scale = 0)
    private BigInteger value;

    @Column(name = "gas_limit")
    private Long gasLimit;

    @Column(name = "gas_price", precision = 36, scale = 0)
    private BigInteger gasPrice;

    @Column(name = "gas_used")
    private Long gasUsed;

    @Column(name = "nonce")
    private Long nonce;

    @Column(name = "input_data", columnDefinition = "TEXT")
    private String inputData;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "network", nullable = false, length = 20)
    private String network;

    @Column(name = "status")
    private Integer status;

    @Column(name = "contract_address", length = 42)
    private String contractAddress;

    @Column(name = "is_contract_call")
    private Boolean isContractCall = false;

    @Column(name = "is_token_transfer")
    private Boolean isTokenTransfer = false;

    @Column(name = "token_symbol", length = 20)
    private String tokenSymbol;

    @Column(name = "token_amount", precision = 36, scale = 0)
    private BigInteger tokenAmount;

    @Column(name = "token_decimals")
    private Integer tokenDecimals;

    @Column(name = "max_fee_per_gas", precision = 36, scale = 0)
    private BigInteger maxFeePerGas;

    @Column(name = "max_priority_fee_per_gas", precision = 36, scale = 0)
    private BigInteger maxPriorityFeePerGas;

    @Column(name = "transaction_type")
    private Integer transactionType;

    // 风险评估相关字段
    @Column(name = "risk_score", precision = 5, scale = 4)
    private BigDecimal riskScore = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 10)
    private RiskLevel riskLevel = RiskLevel.LOW;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "transaction_risk_factors",
        joinColumns = @JoinColumn(name = "transaction_id")
    )
    @Column(name = "risk_factor")
    private List<String> riskFactors;

    @Column(name = "risk_metadata", columnDefinition = "jsonb")
    private String riskMetadata;

    @Column(name = "processed", nullable = false)
    private Boolean processed = false;

    @Column(name = "processing_time")
    private Long processingTime;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 风险等级枚举
     */
    public enum RiskLevel {
        INFO,     // 信息级别
        LOW,      // 低风险
        MEDIUM,   // 中风险
        HIGH,     // 高风险
        CRITICAL  // 严重风险
    }

    /**
     * 检查是否为高风险交易
     */
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }

    /**
     * 检查是否为零值交易
     */
    public boolean isZeroValue() {
        return value == null || value.equals(BigInteger.ZERO);
    }

    /**
     * 获取以太币金额（从wei转换）
     */
    public BigDecimal getEthAmount() {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value).divide(new BigDecimal("1000000000000000000"));
    }

    /**
     * 获取Gas费用总额
     */
    public BigInteger getTotalGasFee() {
        if (gasPrice == null || gasUsed == null) {
            return BigInteger.ZERO;
        }
        return gasPrice.multiply(BigInteger.valueOf(gasUsed));
    }

    /**
     * 检查是否为EIP-1559交易
     */
    public boolean isEip1559Transaction() {
        return transactionType != null && transactionType == 2;
    }

    /**
     * 预持久化操作
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (processed == null) {
            processed = false;
        }
        if (riskScore == null) {
            riskScore = BigDecimal.ZERO;
        }
        if (riskLevel == null) {
            riskLevel = RiskLevel.LOW;
        }
    }

    /**
     * 预更新操作
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}