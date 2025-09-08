package com.web3.risk.kafka;

import com.web3.risk.dto.TransactionDto;
import com.web3.risk.entity.RiskTransaction;
import com.web3.risk.service.RiskAssessmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Kafka交易消息消费者
 * 
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionConsumer {

    private final RiskAssessmentService riskAssessmentService;
    private final ObjectMapper objectMapper;

    /**
     * 消费区块链交易消息
     */
    @KafkaListener(
        topics = "${app.kafka.topics.transactions:blockchain-transactions}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTransaction(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            log.debug("收到交易消息 - Topic: {}, Partition: {}, Key: {}", topic, partition, key);
            
            // 解析交易数据
            TransactionDto transactionDto = objectMapper.readValue(record.value(), TransactionDto.class);
            log.info("处理交易: {} (网络: {})", transactionDto.getHash(), transactionDto.getNetwork());
            
            // 转换为风险交易实体
            RiskTransaction riskTransaction = convertToRiskTransaction(transactionDto);
            
            // 进行风险评估
            RiskTransaction assessedTransaction = riskAssessmentService.assessTransactionRisk(riskTransaction);
            
            // 记录评估结果
            log.info("交易风险评估完成: {} -> 风险等级: {}, 评分: {}", 
                assessedTransaction.getTxHash(),
                assessedTransaction.getRiskLevel(),
                assessedTransaction.getRiskScore());
            
            // 如果是高风险交易，发送告警
            if (assessedTransaction.isHighRisk()) {
                log.warn("发现高风险交易: {} - {}", 
                    assessedTransaction.getTxHash(),
                    assessedTransaction.getRiskFactors());
                
                // TODO: 发送告警消息到告警队列
                sendHighRiskAlert(assessedTransaction);
            }
            
            // 手动确认消息
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("处理交易消息失败: {}", record.value(), e);
            
            // 根据错误类型决定是否重试
            if (shouldRetry(e)) {
                log.info("消息将重新处理");
                // 不确认消息，让Kafka重新投递
            } else {
                log.error("消息处理失败，跳过: {}", record.key());
                acknowledgment.acknowledge();
            }
        }
    }

    /**
     * 消费区块消息
     */
    @KafkaListener(
        topics = "${app.kafka.topics.blocks:blockchain-blocks}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeBlock(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        try {
            log.debug("收到区块消息: {}", record.key());
            
            // TODO: 处理区块级别的风险分析
            // 比如：MEV攻击检测、区块重组检测等
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("处理区块消息失败", e);
            acknowledgment.acknowledge();
        }
    }

    /**
     * 消费地址标签更新消息
     */
    @KafkaListener(
        topics = "${app.kafka.topics.address-labels:address-labels}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeAddressLabel(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        try {
            log.debug("收到地址标签更新消息: {}", record.key());
            
            // TODO: 处理地址标签更新
            // 更新地址风险档案
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("处理地址标签消息失败", e);
            acknowledgment.acknowledge();
        }
    }

    /**
     * 将TransactionDto转换为RiskTransaction实体
     */
    private RiskTransaction convertToRiskTransaction(TransactionDto dto) {
        RiskTransaction transaction = new RiskTransaction();
        
        // 基础交易信息
        transaction.setTxHash(dto.getHash());
        transaction.setBlockNumber(dto.getBlockNumber());
        transaction.setBlockHash(dto.getBlockHash());
        transaction.setTransactionIndex(dto.getTransactionIndex());
        transaction.setFromAddress(dto.getFromAddress());
        transaction.setToAddress(dto.getToAddress());
        
        // 金额和Gas信息
        if (dto.getValue() != null) {
            transaction.setValue(new BigInteger(dto.getValue()));
        }
        transaction.setGasLimit(dto.getGasLimit());
        if (dto.getGasPrice() != null) {
            transaction.setGasPrice(new BigInteger(dto.getGasPrice()));
        }
        transaction.setGasUsed(dto.getGasUsed());
        transaction.setNonce(dto.getNonce());
        
        // 交易数据
        transaction.setInputData(dto.getInputData());
        transaction.setNetwork(dto.getNetwork());
        transaction.setStatus(dto.getStatus());
        
        // 时间戳转换
        if (dto.getTimestamp() != null) {
            transaction.setTimestamp(
                LocalDateTime.ofInstant(Instant.ofEpochSecond(dto.getTimestamp()), ZoneId.systemDefault())
            );
        }
        
        // 合约相关信息
        transaction.setContractAddress(dto.getContractAddress());
        transaction.setIsContractCall(dto.getToAddress() != null && isContractAddress(dto.getToAddress()));
        
        // 代币转账信息
        if (dto.getTokenTransfer() != null) {
            transaction.setIsTokenTransfer(true);
            transaction.setTokenSymbol(dto.getTokenTransfer().getSymbol());
            if (dto.getTokenTransfer().getAmount() != null) {
                transaction.setTokenAmount(new BigInteger(dto.getTokenTransfer().getAmount()));
            }
            transaction.setTokenDecimals(dto.getTokenTransfer().getDecimals());
        }
        
        // EIP-1559相关
        if (dto.getMaxFeePerGas() != null) {
            transaction.setMaxFeePerGas(new BigInteger(dto.getMaxFeePerGas()));
        }
        if (dto.getMaxPriorityFeePerGas() != null) {
            transaction.setMaxPriorityFeePerGas(new BigInteger(dto.getMaxPriorityFeePerGas()));
        }
        transaction.setTransactionType(dto.getTransactionType());
        
        return transaction;
    }

    /**
     * 判断地址是否为合约地址
     */
    private boolean isContractAddress(String address) {
        // 这里应该调用区块链节点或缓存来检查
        // 简化实现：根据地址特征判断
        return address != null && address.length() == 42;
    }

    /**
     * 发送高风险交易告警
     */
    private void sendHighRiskAlert(RiskTransaction transaction) {
        try {
            // TODO: 发送到告警队列
            log.info("发送高风险交易告警: {} (风险等级: {})", 
                transaction.getTxHash(), transaction.getRiskLevel());
            
            // 这里应该发送到专门的告警Kafka主题
            // 或直接调用告警服务API
            
        } catch (Exception e) {
            log.error("发送告警失败", e);
        }
    }

    /**
     * 判断是否应该重试
     */
    private boolean shouldRetry(Exception e) {
        // 根据异常类型判断是否重试
        if (e instanceof com.fasterxml.jackson.core.JsonProcessingException) {
            return false; // JSON解析错误不重试
        }
        
        if (e instanceof IllegalArgumentException) {
            return false; // 参数错误不重试
        }
        
        // 其他异常重试
        return true;
    }
}