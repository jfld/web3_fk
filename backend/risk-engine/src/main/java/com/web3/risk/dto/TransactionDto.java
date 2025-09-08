package com.web3.risk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 交易数据传输对象
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Data
public class TransactionDto {

    /**
     * 交易哈希
     */
    @JsonProperty("hash")
    private String hash;

    /**
     * 区块号
     */
    @JsonProperty("block_number")
    private Long blockNumber;

    /**
     * 区块哈希
     */
    @JsonProperty("block_hash")
    private String blockHash;

    /**
     * 交易索引
     */
    @JsonProperty("transaction_index")
    private Integer transactionIndex;

    /**
     * 发送方地址
     */
    @JsonProperty("from")
    private String fromAddress;

    /**
     * 接收方地址
     */
    @JsonProperty("to")
    private String toAddress;

    /**
     * 交易金额（Wei）
     */
    @JsonProperty("value")
    private String value;

    /**
     * Gas限额
     */
    @JsonProperty("gas_limit")
    private Long gasLimit;

    /**
     * Gas价格
     */
    @JsonProperty("gas_price")
    private String gasPrice;

    /**
     * 实际使用的Gas
     */
    @JsonProperty("gas_used")
    private Long gasUsed;

    /**
     * 随机数
     */
    @JsonProperty("nonce")
    private Long nonce;

    /**
     * 交易输入数据
     */
    @JsonProperty("input")
    private String inputData;

    /**
     * 时间戳
     */
    @JsonProperty("timestamp")
    private Long timestamp;

    /**
     * 网络标识
     */
    @JsonProperty("network")
    private String network;

    /**
     * 交易状态 (0=失败, 1=成功)
     */
    @JsonProperty("status")
    private Integer status;

    /**
     * 合约地址（如果是合约创建交易）
     */
    @JsonProperty("contract_address")
    private String contractAddress;

    /**
     * EIP-1559最大费用
     */
    @JsonProperty("max_fee_per_gas")
    private String maxFeePerGas;

    /**
     * EIP-1559最大优先费用
     */
    @JsonProperty("max_priority_fee_per_gas")
    private String maxPriorityFeePerGas;

    /**
     * 交易类型 (0=传统, 1=EIP-2930, 2=EIP-1559)
     */
    @JsonProperty("transaction_type")
    private Integer transactionType;

    /**
     * 代币转账信息
     */
    @JsonProperty("token_transfer")
    private TokenTransferDto tokenTransfer;

    /**
     * 日志事件
     */
    @JsonProperty("logs")
    private LogEventDto[] logs;

    /**
     * 代币转账DTO
     */
    @Data
    public static class TokenTransferDto {
        @JsonProperty("contract_address")
        private String contractAddress;
        
        @JsonProperty("symbol")
        private String symbol;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("decimals")
        private Integer decimals;
        
        @JsonProperty("amount")
        private String amount;
        
        @JsonProperty("from")
        private String fromAddress;
        
        @JsonProperty("to")
        private String toAddress;
    }

    /**
     * 日志事件DTO
     */
    @Data
    public static class LogEventDto {
        @JsonProperty("address")
        private String address;
        
        @JsonProperty("topics")
        private String[] topics;
        
        @JsonProperty("data")
        private String data;
        
        @JsonProperty("log_index")
        private Integer logIndex;
        
        @JsonProperty("removed")
        private Boolean removed;
    }
}