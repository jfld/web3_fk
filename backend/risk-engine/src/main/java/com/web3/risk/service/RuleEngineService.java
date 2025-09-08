package com.web3.risk.service;

import com.web3.risk.entity.RiskRule;
import com.web3.risk.entity.RiskTransaction;
import com.web3.risk.repository.RiskRuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 规则引擎服务 - 风险规则执行和管理
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineService {

    private final RiskRuleRepository ruleRepository;
    private final ObjectMapper objectMapper;

    /**
     * 评估交易风险（规则引擎）
     */
    public BigDecimal evaluateTransaction(RiskTransaction transaction) {
        log.debug("开始规则引擎评估交易: {}", transaction.getTxHash());
        
        try {
            // 获取适用的规则
            List<RiskRule> applicableRules = getApplicableRules(transaction.getNetwork());
            
            if (applicableRules.isEmpty()) {
                log.warn("未找到适用于网络 {} 的规则", transaction.getNetwork());
                return BigDecimal.ZERO;
            }
            
            BigDecimal totalRiskScore = BigDecimal.ZERO;
            List<String> matchedRules = new ArrayList<>();
            
            // 逐个执行规则
            for (RiskRule rule : applicableRules) {
                try {
                    long startTime = System.nanoTime();
                    
                    // 更新规则执行统计
                    rule.incrementExecutionCount();
                    
                    // 执行规则
                    RuleEvaluationResult result = executeRule(rule, transaction);
                    
                    long executionTime = (System.nanoTime() - startTime) / 1_000_000; // 转换为毫秒
                    
                    if (result.isMatched()) {
                        rule.incrementMatchCount();
                        matchedRules.add(rule.getRuleName());
                        
                        // 计算加权风险评分
                        BigDecimal weightedScore = result.getRiskScore().multiply(rule.getRiskWeight());
                        totalRiskScore = totalRiskScore.add(weightedScore);
                        
                        log.debug("规则 {} 匹配，风险评分: {}", rule.getRuleName(), weightedScore);
                    }
                    
                    // 更新规则执行时间统计
                    rule.updateExecutionTime(executionTime);
                    
                } catch (Exception e) {
                    log.error("执行规则 {} 时发生错误", rule.getRuleName(), e);
                }
            }
            
            // 保存规则统计更新
            ruleRepository.saveAll(applicableRules);
            
            // 标准化风险评分 (0-1)
            BigDecimal normalizedScore = totalRiskScore.min(BigDecimal.ONE);
            
            log.debug("规则引擎评估完成，匹配规则: {}, 最终评分: {}", 
                     matchedRules, normalizedScore);
            
            return normalizedScore;
            
        } catch (Exception e) {
            log.error("规则引擎评估失败: {}", transaction.getTxHash(), e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 异步批量评估交易
     */
    public CompletableFuture<Map<String, BigDecimal>> batchEvaluateTransactions(List<RiskTransaction> transactions) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, BigDecimal> results = new HashMap<>();
            
            for (RiskTransaction transaction : transactions) {
                try {
                    BigDecimal score = evaluateTransaction(transaction);
                    results.put(transaction.getTxHash(), score);
                } catch (Exception e) {
                    log.error("批量评估交易 {} 失败", transaction.getTxHash(), e);
                    results.put(transaction.getTxHash(), BigDecimal.ZERO);
                }
            }
            
            return results;
        });
    }

    /**
     * 创建新规则
     */
    @Transactional
    public RiskRule createRule(RiskRule rule) {
        log.info("创建新风险规则: {}", rule.getRuleName());
        
        // 验证规则唯一性
        if (ruleRepository.existsByRuleName(rule.getRuleName())) {
            throw new IllegalArgumentException("规则名称已存在: " + rule.getRuleName());
        }
        
        // 验证规则条件JSON格式
        validateRuleCondition(rule.getConditionJson());
        
        // 设置默认值
        if (rule.getPriority() == null) {
            rule.setPriority(100);
        }
        
        if (rule.getVersion() == null) {
            rule.setVersion("1.0.0");
        }
        
        return ruleRepository.save(rule);
    }

    /**
     * 更新规则
     */
    @Transactional
    public RiskRule updateRule(Long ruleId, RiskRule updatedRule) {
        log.info("更新风险规则: {}", ruleId);
        
        Optional<RiskRule> existingRule = ruleRepository.findById(ruleId);
        if (existingRule.isEmpty()) {
            throw new IllegalArgumentException("规则不存在: " + ruleId);
        }
        
        RiskRule rule = existingRule.get();
        
        // 验证新的规则条件
        if (updatedRule.getConditionJson() != null) {
            validateRuleCondition(updatedRule.getConditionJson());
            rule.setConditionJson(updatedRule.getConditionJson());
        }
        
        // 更新其他字段
        if (updatedRule.getRuleDescription() != null) {
            rule.setRuleDescription(updatedRule.getRuleDescription());
        }
        
        if (updatedRule.getRiskWeight() != null) {
            rule.setRiskWeight(updatedRule.getRiskWeight());
        }
        
        if (updatedRule.getEnabled() != null) {
            rule.setEnabled(updatedRule.getEnabled());
        }
        
        if (updatedRule.getPriority() != null) {
            rule.setPriority(updatedRule.getPriority());
        }
        
        return ruleRepository.save(rule);
    }

    /**
     * 删除规则
     */
    @Transactional
    public void deleteRule(Long ruleId) {
        log.info("删除风险规则: {}", ruleId);
        
        if (!ruleRepository.existsById(ruleId)) {
            throw new IllegalArgumentException("规则不存在: " + ruleId);
        }
        
        ruleRepository.deleteById(ruleId);
    }

    /**
     * 启用/禁用规则
     */
    @Transactional
    public void toggleRuleStatus(Long ruleId, boolean enabled) {
        log.info("{}规则: {}", enabled ? "启用" : "禁用", ruleId);
        
        Optional<RiskRule> rule = ruleRepository.findById(ruleId);
        if (rule.isEmpty()) {
            throw new IllegalArgumentException("规则不存在: " + ruleId);
        }
        
        RiskRule riskRule = rule.get();
        riskRule.setEnabled(enabled);
        ruleRepository.save(riskRule);
    }

    /**
     * 获取规则列表
     */
    public Page<RiskRule> getRules(Pageable pageable) {
        return ruleRepository.findAll(pageable);
    }

    /**
     * 根据类型获取规则
     */
    public List<RiskRule> getRulesByType(RiskRule.RuleType ruleType) {
        return ruleRepository.findByRuleTypeAndEnabledTrueOrderByPriorityAsc(ruleType);
    }

    /**
     * 获取规则执行统计
     */
    public Map<String, Object> getRuleStatistics() {
        List<Object[]> typeStats = ruleRepository.getRuleTypeStatistics();
        List<Object[]> categoryStats = ruleRepository.getRuleCategoryStatistics();
        List<Object[]> performanceStats = ruleRepository.getRulePerformanceStats();
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("typeDistribution", typeStats);
        statistics.put("categoryDistribution", categoryStats);
        statistics.put("performanceStats", performanceStats);
        
        return statistics;
    }

    /**
     * 测试规则
     */
    public RuleEvaluationResult testRule(RiskRule rule, RiskTransaction testTransaction) {
        log.info("测试规则: {} 对交易: {}", rule.getRuleName(), testTransaction.getTxHash());
        
        try {
            return executeRule(rule, testTransaction);
        } catch (Exception e) {
            log.error("规则测试失败", e);
            return new RuleEvaluationResult(false, BigDecimal.ZERO, "规则测试失败: " + e.getMessage());
        }
    }

    // ========================= 私有方法 =========================

    /**
     * 获取适用的规则（缓存）
     */
    @Cacheable(value = "applicable-rules", key = "#network")
    private List<RiskRule> getApplicableRules(String network) {
        LocalDateTime now = LocalDateTime.now();
        
        return ruleRepository.findValidRules(now)
                .stream()
                .filter(rule -> rule.appliesToNetwork(network))
                .sorted(Comparator.comparing(RiskRule::getPriority))
                .collect(Collectors.toList());
    }

    /**
     * 执行单个规则
     */
    private RuleEvaluationResult executeRule(RiskRule rule, RiskTransaction transaction) {
        try {
            JsonNode condition = objectMapper.readTree(rule.getConditionJson());
            
            // 根据规则类型执行不同的评估逻辑
            switch (rule.getRuleType()) {
                case TRANSACTION:
                    return evaluateTransactionRule(condition, transaction);
                case ADDRESS:
                    return evaluateAddressRule(condition, transaction);
                case THRESHOLD:
                    return evaluateThresholdRule(condition, transaction);
                case BLACKLIST:
                    return evaluateBlacklistRule(condition, transaction);
                case TIME_BASED:
                    return evaluateTimeBasedRule(condition, transaction);
                default:
                    return evaluateGenericRule(condition, transaction);
            }
            
        } catch (Exception e) {
            log.error("执行规则失败: {}", rule.getRuleName(), e);
            return new RuleEvaluationResult(false, BigDecimal.ZERO, "规则执行异常: " + e.getMessage());
        }
    }

    /**
     * 评估交易规则
     */
    private RuleEvaluationResult evaluateTransactionRule(JsonNode condition, RiskTransaction transaction) {
        boolean matched = false;
        BigDecimal riskScore = BigDecimal.ZERO;
        StringBuilder reason = new StringBuilder();
        
        // 检查交易金额阈值
        if (condition.has("minValue")) {
            BigInteger minValue = new BigInteger(condition.get("minValue").asText());
            if (transaction.getValue() != null && transaction.getValue().compareTo(minValue) >= 0) {
                matched = true;
                riskScore = riskScore.add(new BigDecimal("0.5"));
                reason.append("交易金额超过阈值; ");
            }
        }
        
        // 检查Gas费用
        if (condition.has("maxGasPrice")) {
            BigInteger maxGasPrice = new BigInteger(condition.get("maxGasPrice").asText());
            if (transaction.getGasPrice() != null && transaction.getGasPrice().compareTo(maxGasPrice) > 0) {
                matched = true;
                riskScore = riskScore.add(new BigDecimal("0.3"));
                reason.append("Gas价格异常; ");
            }
        }
        
        // 检查合约调用
        if (condition.has("contractCall") && condition.get("contractCall").asBoolean()) {
            if (Boolean.TRUE.equals(transaction.getIsContractCall())) {
                matched = true;
                riskScore = riskScore.add(new BigDecimal("0.2"));
                reason.append("合约调用; ");
            }
        }
        
        return new RuleEvaluationResult(matched, riskScore.min(BigDecimal.ONE), reason.toString());
    }

    /**
     * 评估地址规则
     */
    private RuleEvaluationResult evaluateAddressRule(JsonNode condition, RiskTransaction transaction) {
        boolean matched = false;
        BigDecimal riskScore = BigDecimal.ZERO;
        
        // 检查黑名单地址
        if (condition.has("blacklistedAddresses")) {
            JsonNode blacklistedAddresses = condition.get("blacklistedAddresses");
            for (JsonNode addr : blacklistedAddresses) {
                String blacklistedAddr = addr.asText();
                if (blacklistedAddr.equals(transaction.getFromAddress()) || 
                    blacklistedAddr.equals(transaction.getToAddress())) {
                    matched = true;
                    riskScore = BigDecimal.ONE; // 黑名单地址直接最高风险
                    break;
                }
            }
        }
        
        return new RuleEvaluationResult(matched, riskScore, matched ? "黑名单地址检测" : "");
    }

    /**
     * 评估阈值规则
     */
    private RuleEvaluationResult evaluateThresholdRule(JsonNode condition, RiskTransaction transaction) {
        boolean matched = false;
        BigDecimal riskScore = BigDecimal.ZERO;
        
        if (condition.has("thresholds")) {
            JsonNode thresholds = condition.get("thresholds");
            
            if (thresholds.has("value") && transaction.getValue() != null) {
                BigInteger threshold = new BigInteger(thresholds.get("value").asText());
                if (transaction.getValue().compareTo(threshold) > 0) {
                    matched = true;
                    riskScore = new BigDecimal("0.7");
                }
            }
        }
        
        return new RuleEvaluationResult(matched, riskScore, matched ? "超过阈值" : "");
    }

    /**
     * 评估黑名单规则
     */
    private RuleEvaluationResult evaluateBlacklistRule(JsonNode condition, RiskTransaction transaction) {
        // 实际应该查询外部黑名单服务
        return new RuleEvaluationResult(false, BigDecimal.ZERO, "");
    }

    /**
     * 评估时间基础规则
     */
    private RuleEvaluationResult evaluateTimeBasedRule(JsonNode condition, RiskTransaction transaction) {
        boolean matched = false;
        BigDecimal riskScore = BigDecimal.ZERO;
        
        if (condition.has("suspiciousHours")) {
            JsonNode suspiciousHours = condition.get("suspiciousHours");
            int txHour = transaction.getTimestamp().getHour();
            
            for (JsonNode hour : suspiciousHours) {
                if (hour.asInt() == txHour) {
                    matched = true;
                    riskScore = new BigDecimal("0.3");
                    break;
                }
            }
        }
        
        return new RuleEvaluationResult(matched, riskScore, matched ? "可疑时间段交易" : "");
    }

    /**
     * 评估通用规则
     */
    private RuleEvaluationResult evaluateGenericRule(JsonNode condition, RiskTransaction transaction) {
        // 通用规则评估逻辑
        return new RuleEvaluationResult(false, BigDecimal.ZERO, "");
    }

    /**
     * 验证规则条件JSON格式
     */
    private void validateRuleCondition(String conditionJson) {
        try {
            objectMapper.readTree(conditionJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("规则条件JSON格式无效: " + e.getMessage());
        }
    }

    /**
     * 规则评估结果内部类
     */
    public static class RuleEvaluationResult {
        private final boolean matched;
        private final BigDecimal riskScore;
        private final String reason;
        
        public RuleEvaluationResult(boolean matched, BigDecimal riskScore, String reason) {
            this.matched = matched;
            this.riskScore = riskScore;
            this.reason = reason;
        }
        
        public boolean isMatched() { return matched; }
        public BigDecimal getRiskScore() { return riskScore; }
        public String getReason() { return reason; }
    }
}