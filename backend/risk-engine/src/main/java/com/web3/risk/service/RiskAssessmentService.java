package com.web3.risk.service;

import com.web3.risk.entity.RiskAddress;
import com.web3.risk.entity.RiskRule;
import com.web3.risk.entity.RiskTransaction;
import com.web3.risk.repository.RiskAddressRepository;
import com.web3.risk.repository.RiskRuleRepository;
import com.web3.risk.repository.RiskTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 风险评估服务 - 智能风险识别与评估的核心业务逻辑
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAssessmentService {

    private final RiskTransactionRepository transactionRepository;
    private final RiskAddressRepository addressRepository;
    private final RiskRuleRepository ruleRepository;
    private final RuleEngineService ruleEngineService;

    // 风险评估阈值常量
    private static final BigInteger LARGE_TRANSACTION_THRESHOLD = new BigInteger("100000000000000000000"); // 100 ETH
    private static final BigInteger HIGH_GAS_THRESHOLD = new BigInteger("1000000000000000000"); // 1 ETH in gas
    private static final Long HIGH_TRANSACTION_COUNT_THRESHOLD = 1000L;
    private static final BigDecimal HIGH_RISK_SCORE_THRESHOLD = new BigDecimal("0.8");
    private static final BigDecimal CRITICAL_RISK_SCORE_THRESHOLD = new BigDecimal("0.95");

    /**
     * 评估交易风险
     */
    @Transactional
    public RiskTransaction assessTransactionRisk(RiskTransaction transaction) {
        log.info("开始评估交易风险: {}", transaction.getTxHash());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 1. 基础风险评分
            BigDecimal baseRiskScore = calculateBaseRiskScore(transaction);
            
            // 2. 地址风险评估
            BigDecimal addressRiskScore = assessAddressRisk(transaction);
            
            // 3. 交易行为风险评估
            BigDecimal behaviorRiskScore = assessTransactionBehavior(transaction);
            
            // 4. 规则引擎评估
            BigDecimal ruleRiskScore = ruleEngineService.evaluateTransaction(transaction);
            
            // 5. 综合风险评分计算
            BigDecimal finalRiskScore = calculateFinalRiskScore(
                baseRiskScore, addressRiskScore, behaviorRiskScore, ruleRiskScore
            );
            
            // 6. 设置风险等级和因子
            RiskTransaction.RiskLevel riskLevel = determineRiskLevel(finalRiskScore);
            List<String> riskFactors = identifyRiskFactors(transaction, finalRiskScore);
            
            // 7. 更新交易风险信息
            transaction.setRiskScore(finalRiskScore);
            transaction.setRiskLevel(riskLevel);
            transaction.setRiskFactors(riskFactors);
            transaction.setProcessed(true);
            transaction.setProcessingTime(System.currentTimeMillis() - startTime);
            
            // 8. 更新地址风险档案
            updateAddressRiskProfile(transaction);
            
            // 9. 保存交易
            RiskTransaction savedTransaction = transactionRepository.save(transaction);
            
            log.info("交易风险评估完成: {} -> 风险等级: {}, 评分: {}", 
                transaction.getTxHash(), riskLevel, finalRiskScore);
                
            return savedTransaction;
            
        } catch (Exception e) {
            log.error("交易风险评估失败: {}", transaction.getTxHash(), e);
            transaction.setErrorMessage(e.getMessage());
            transaction.setProcessed(true);
            return transactionRepository.save(transaction);
        }
    }

    /**
     * 批量评估交易风险
     */
    @Transactional
    public List<RiskTransaction> batchAssessTransactionRisk(List<RiskTransaction> transactions) {
        log.info("开始批量评估 {} 笔交易风险", transactions.size());
        
        List<RiskTransaction> results = new ArrayList<>();
        for (RiskTransaction transaction : transactions) {
            results.add(assessTransactionRisk(transaction));
        }
        
        log.info("批量风险评估完成，共处理 {} 笔交易", results.size());
        return results;
    }

    /**
     * 评估地址风险
     */
    @Transactional
    public RiskAddress assessAddressRisk(String address, String network) {
        log.info("开始评估地址风险: {} ({})", address, network);
        
        Optional<RiskAddress> existingAddress = addressRepository.findByAddressAndNetwork(address, network);
        RiskAddress riskAddress = existingAddress.orElse(new RiskAddress());
        
        if (existingAddress.isEmpty()) {
            riskAddress.setAddress(address);
            riskAddress.setNetwork(network);
        }
        
        try {
            // 1. 黑名单检查
            boolean isBlacklisted = checkBlacklistStatus(address);
            
            // 2. 地址类型识别
            String addressType = identifyAddressType(address, network);
            
            // 3. 交易行为分析
            analyzeAddressTransactionBehavior(riskAddress);
            
            // 4. 风险标签分析
            List<String> riskTags = analyzeRiskTags(address, network);
            
            // 5. 计算综合风险评分
            BigDecimal riskScore = calculateAddressRiskScore(riskAddress, isBlacklisted, riskTags);
            
            // 6. 确定风险等级
            RiskTransaction.RiskLevel riskLevel = determineAddressRiskLevel(riskScore, isBlacklisted);
            
            // 7. 更新地址信息
            riskAddress.setRiskScore(riskScore);
            riskAddress.setRiskLevel(riskLevel);
            riskAddress.setRiskTags(riskTags);
            riskAddress.setAddressType(addressType);
            riskAddress.setIsBlacklisted(isBlacklisted);
            
            if (isBlacklisted && riskAddress.getBlacklistedAt() == null) {
                riskAddress.setBlacklistedAt(LocalDateTime.now());
                riskAddress.setBlacklistSource("SYSTEM_DETECTION");
            }
            
            RiskAddress savedAddress = addressRepository.save(riskAddress);
            log.info("地址风险评估完成: {} -> 风险等级: {}, 评分: {}", address, riskLevel, riskScore);
            
            return savedAddress;
            
        } catch (Exception e) {
            log.error("地址风险评估失败: {}", address, e);
            throw new RuntimeException("地址风险评估失败", e);
        }
    }

    /**
     * 获取高风险交易
     */
    public Page<RiskTransaction> getHighRiskTransactions(Pageable pageable) {
        return transactionRepository.findHighRiskTransactions(pageable);
    }

    /**
     * 获取高风险地址
     */
    public Page<RiskAddress> getHighRiskAddresses(Pageable pageable) {
        return addressRepository.findHighRiskAddresses(pageable);
    }

    /**
     * 获取最近的高风险活动
     */
    public Map<String, Object> getRecentHighRiskActivity(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        List<RiskTransaction> recentHighRiskTx = transactionRepository.findRecentHighRiskTransactions(since);
        List<RiskAddress> recentHighRiskAddr = addressRepository.findRecentHighRiskAddresses(since);
        
        Map<String, Object> result = new HashMap<>();
        result.put("highRiskTransactions", recentHighRiskTx);
        result.put("highRiskAddresses", recentHighRiskAddr);
        result.put("summary", Map.of(
            "transactionCount", recentHighRiskTx.size(),
            "addressCount", recentHighRiskAddr.size(),
            "timeRange", hours + " hours"
        ));
        
        return result;
    }

    // ========================= 私有辅助方法 =========================

    /**
     * 计算基础风险评分
     */
    private BigDecimal calculateBaseRiskScore(RiskTransaction transaction) {
        BigDecimal score = BigDecimal.ZERO;
        
        // 大额交易风险
        if (transaction.getValue() != null && transaction.getValue().compareTo(LARGE_TRANSACTION_THRESHOLD) > 0) {
            score = score.add(new BigDecimal("0.3"));
        }
        
        // Gas费异常风险
        BigInteger totalGasFee = transaction.getTotalGasFee();
        if (totalGasFee.compareTo(HIGH_GAS_THRESHOLD) > 0) {
            score = score.add(new BigDecimal("0.2"));
        }
        
        // 零值交易（可能的垃圾交易或攻击）
        if (transaction.isZeroValue()) {
            score = score.add(new BigDecimal("0.1"));
        }
        
        // 合约调用风险
        if (Boolean.TRUE.equals(transaction.getIsContractCall())) {
            score = score.add(new BigDecimal("0.1"));
        }
        
        // EIP-1559交易类型检查
        if (!transaction.isEip1559Transaction() && transaction.getGasPrice() != null) {
            // 传统交易类型在某些情况下风险较高
            score = score.add(new BigDecimal("0.05"));
        }
        
        return score.min(BigDecimal.ONE);
    }

    /**
     * 评估交易中地址的风险
     */
    private BigDecimal assessAddressRisk(RiskTransaction transaction) {
        BigDecimal maxAddressRisk = BigDecimal.ZERO;
        
        // 评估发送方地址风险
        if (transaction.getFromAddress() != null) {
            Optional<RiskAddress> fromAddr = addressRepository.findByAddressAndNetwork(
                transaction.getFromAddress(), transaction.getNetwork());
            if (fromAddr.isPresent()) {
                maxAddressRisk = maxAddressRisk.max(fromAddr.get().getRiskScore());
            }
        }
        
        // 评估接收方地址风险  
        if (transaction.getToAddress() != null) {
            Optional<RiskAddress> toAddr = addressRepository.findByAddressAndNetwork(
                transaction.getToAddress(), transaction.getNetwork());
            if (toAddr.isPresent()) {
                maxAddressRisk = maxAddressRisk.max(toAddr.get().getRiskScore());
            }
        }
        
        return maxAddressRisk;
    }

    /**
     * 评估交易行为风险
     */
    private BigDecimal assessTransactionBehavior(RiskTransaction transaction) {
        BigDecimal behaviorScore = BigDecimal.ZERO;
        
        // 时间模式分析（深夜交易风险较高）
        int hour = transaction.getTimestamp().getHour();
        if (hour >= 23 || hour <= 5) {
            behaviorScore = behaviorScore.add(new BigDecimal("0.1"));
        }
        
        // 周末交易风险
        if (transaction.getTimestamp().getDayOfWeek().getValue() >= 6) {
            behaviorScore = behaviorScore.add(new BigDecimal("0.05"));
        }
        
        // 交易失败风险
        if (transaction.getStatus() != null && transaction.getStatus() == 0) {
            behaviorScore = behaviorScore.add(new BigDecimal("0.2"));
        }
        
        return behaviorScore;
    }

    /**
     * 计算最终风险评分
     */
    private BigDecimal calculateFinalRiskScore(BigDecimal baseScore, BigDecimal addressScore, 
                                             BigDecimal behaviorScore, BigDecimal ruleScore) {
        // 加权平均计算
        BigDecimal weightedScore = baseScore.multiply(new BigDecimal("0.3"))
                .add(addressScore.multiply(new BigDecimal("0.4")))
                .add(behaviorScore.multiply(new BigDecimal("0.1")))
                .add(ruleScore.multiply(new BigDecimal("0.2")));
        
        return weightedScore.min(BigDecimal.ONE);
    }

    /**
     * 确定风险等级
     */
    private RiskTransaction.RiskLevel determineRiskLevel(BigDecimal riskScore) {
        if (riskScore.compareTo(CRITICAL_RISK_SCORE_THRESHOLD) >= 0) {
            return RiskTransaction.RiskLevel.CRITICAL;
        } else if (riskScore.compareTo(HIGH_RISK_SCORE_THRESHOLD) >= 0) {
            return RiskTransaction.RiskLevel.HIGH;
        } else if (riskScore.compareTo(new BigDecimal("0.5")) >= 0) {
            return RiskTransaction.RiskLevel.MEDIUM;
        } else if (riskScore.compareTo(new BigDecimal("0.2")) >= 0) {
            return RiskTransaction.RiskLevel.LOW;
        } else {
            return RiskTransaction.RiskLevel.INFO;
        }
    }

    /**
     * 识别风险因子
     */
    private List<String> identifyRiskFactors(RiskTransaction transaction, BigDecimal riskScore) {
        List<String> factors = new ArrayList<>();
        
        if (transaction.getValue() != null && transaction.getValue().compareTo(LARGE_TRANSACTION_THRESHOLD) > 0) {
            factors.add("LARGE_VALUE_TRANSACTION");
        }
        
        if (transaction.getTotalGasFee().compareTo(HIGH_GAS_THRESHOLD) > 0) {
            factors.add("HIGH_GAS_FEE");
        }
        
        if (transaction.isZeroValue()) {
            factors.add("ZERO_VALUE_TRANSACTION");
        }
        
        if (Boolean.TRUE.equals(transaction.getIsContractCall())) {
            factors.add("CONTRACT_INTERACTION");
        }
        
        if (transaction.getStatus() != null && transaction.getStatus() == 0) {
            factors.add("FAILED_TRANSACTION");
        }
        
        if (riskScore.compareTo(HIGH_RISK_SCORE_THRESHOLD) >= 0) {
            factors.add("HIGH_RISK_SCORE");
        }
        
        return factors;
    }

    /**
     * 更新地址风险档案
     */
    private void updateAddressRiskProfile(RiskTransaction transaction) {
        // 更新发送方地址
        if (transaction.getFromAddress() != null) {
            updateSingleAddressProfile(transaction.getFromAddress(), transaction, true);
        }
        
        // 更新接收方地址
        if (transaction.getToAddress() != null) {
            updateSingleAddressProfile(transaction.getToAddress(), transaction, false);
        }
    }

    private void updateSingleAddressProfile(String address, RiskTransaction transaction, boolean isSender) {
        Optional<RiskAddress> existingAddr = addressRepository.findByAddressAndNetwork(address, transaction.getNetwork());
        RiskAddress riskAddress = existingAddr.orElse(new RiskAddress());
        
        if (existingAddr.isEmpty()) {
            riskAddress.setAddress(address);
            riskAddress.setNetwork(transaction.getNetwork());
        }
        
        // 更新统计信息
        riskAddress.updateStatistics(transaction.getValue(), isSender, transaction.getTimestamp());
        
        // 如果是高风险交易，增加可疑活动计数
        if (transaction.isHighRisk()) {
            riskAddress.setSuspiciousActivityCount(
                riskAddress.getSuspiciousActivityCount() + 1);
        }
        
        addressRepository.save(riskAddress);
    }

    /**
     * 检查黑名单状态
     */
    private boolean checkBlacklistStatus(String address) {
        // 这里应该集成外部黑名单服务
        return addressRepository.findBlacklistedAddress(address).isPresent();
    }

    /**
     * 识别地址类型
     */
    private String identifyAddressType(String address, String network) {
        // 基于地址模式和行为识别地址类型
        // 这里是简化实现，实际应该更复杂的分析逻辑
        
        Optional<RiskAddress> existingAddr = addressRepository.findByAddressAndNetwork(address, network);
        if (existingAddr.isPresent()) {
            return existingAddr.get().getAddressType();
        }
        
        // 默认类型判断逻辑
        return "EOA"; // 外部拥有账户
    }

    /**
     * 分析地址交易行为
     */
    private void analyzeAddressTransactionBehavior(RiskAddress riskAddress) {
        // 分析地址的交易行为模式
        // 这里可以添加更复杂的行为分析逻辑
        
        if (riskAddress.getTransactionCount() != null && riskAddress.getTransactionCount() > HIGH_TRANSACTION_COUNT_THRESHOLD) {
            // 高频交易地址
            riskAddress.addRiskTag("HIGH_FREQUENCY_TRADER");
        }
    }

    /**
     * 分析风险标签
     */
    private List<String> analyzeRiskTags(String address, String network) {
        List<String> tags = new ArrayList<>();
        
        // 基于各种风险指标添加标签
        // 这里是简化实现
        
        return tags;
    }

    /**
     * 计算地址风险评分
     */
    private BigDecimal calculateAddressRiskScore(RiskAddress riskAddress, boolean isBlacklisted, List<String> riskTags) {
        if (isBlacklisted) {
            return new BigDecimal("1.0"); // 黑名单地址直接最高风险
        }
        
        BigDecimal score = BigDecimal.ZERO;
        
        // 基于交易统计计算风险
        if (riskAddress.getTransactionCount() != null) {
            if (riskAddress.getTransactionCount() > HIGH_TRANSACTION_COUNT_THRESHOLD) {
                score = score.add(new BigDecimal("0.3"));
            }
        }
        
        // 基于风险标签计算
        score = score.add(new BigDecimal(riskTags.size()).multiply(new BigDecimal("0.1")));
        
        return score.min(BigDecimal.ONE);
    }

    /**
     * 确定地址风险等级
     */
    private RiskTransaction.RiskLevel determineAddressRiskLevel(BigDecimal riskScore, boolean isBlacklisted) {
        if (isBlacklisted) {
            return RiskTransaction.RiskLevel.CRITICAL;
        }
        
        return determineRiskLevel(riskScore);
    }
}