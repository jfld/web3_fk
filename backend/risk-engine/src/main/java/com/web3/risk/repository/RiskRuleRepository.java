package com.web3.risk.repository;

import com.web3.risk.entity.RiskRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 风险规则数据访问接口
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Repository
public interface RiskRuleRepository extends JpaRepository<RiskRule, Long> {

    /**
     * 根据规则名称查找
     */
    Optional<RiskRule> findByRuleName(String ruleName);

    /**
     * 检查规则名称是否存在
     */
    boolean existsByRuleName(String ruleName);

    /**
     * 查找启用的规则
     */
    List<RiskRule> findByEnabledTrueOrderByPriorityAsc();

    /**
     * 根据规则类型查找启用的规则
     */
    List<RiskRule> findByRuleTypeAndEnabledTrueOrderByPriorityAsc(RiskRule.RuleType ruleType);

    /**
     * 根据规则分类查找启用的规则
     */
    List<RiskRule> findByRuleCategoryAndEnabledTrueOrderByPriorityAsc(RiskRule.RuleCategory ruleCategory);

    /**
     * 根据严重等级查找规则
     */
    Page<RiskRule> findBySeverity(RiskRule.RiskLevel severity, Pageable pageable);

    /**
     * 根据作者查找规则
     */
    Page<RiskRule> findByAuthor(String author, Pageable pageable);

    /**
     * 查找适用于指定网络的规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.enabled = true AND (rr.networks IS NULL OR rr.networks = '' OR rr.networks LIKE %:network%) ORDER BY rr.priority ASC")
    List<RiskRule> findApplicableRulesForNetwork(@Param("network") String network);

    /**
     * 查找有效期内的规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.enabled = true AND (rr.validFrom IS NULL OR rr.validFrom <= :now) AND (rr.validTo IS NULL OR rr.validTo >= :now) ORDER BY rr.priority ASC")
    List<RiskRule> findValidRules(@Param("now") LocalDateTime now);

    /**
     * 根据优先级范围查找规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.priority BETWEEN :minPriority AND :maxPriority ORDER BY rr.priority ASC")
    List<RiskRule> findByPriorityRange(@Param("minPriority") Integer minPriority, @Param("maxPriority") Integer maxPriority);

    /**
     * 查找最近执行的规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.lastExecution >= :since ORDER BY rr.lastExecution DESC")
    Page<RiskRule> findRecentlyExecutedRules(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * 查找最近匹配的规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.lastMatch >= :since ORDER BY rr.lastMatch DESC")
    Page<RiskRule> findRecentlyMatchedRules(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * 根据执行次数查找规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.executionCount >= :minCount ORDER BY rr.executionCount DESC")
    Page<RiskRule> findByMinExecutionCount(@Param("minCount") Long minCount, Pageable pageable);

    /**
     * 根据匹配次数查找规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.matchCount >= :minCount ORDER BY rr.matchCount DESC")
    Page<RiskRule> findByMinMatchCount(@Param("minCount") Long minCount, Pageable pageable);

    /**
     * 查找高误报率的规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.matchCount > 0 AND (rr.falsePositiveCount * 1.0 / rr.matchCount) > :threshold ORDER BY (rr.falsePositiveCount * 1.0 / rr.matchCount) DESC")
    Page<RiskRule> findHighFalsePositiveRules(@Param("threshold") Double threshold, Pageable pageable);

    /**
     * 查找低效率的规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.executionCount > 0 AND (rr.matchCount * 1.0 / rr.executionCount) < :threshold ORDER BY (rr.matchCount * 1.0 / rr.executionCount) ASC")
    Page<RiskRule> findLowEfficiencyRules(@Param("threshold") Double threshold, Pageable pageable);

    /**
     * 根据标签查找规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.tags LIKE %:tag%")
    Page<RiskRule> findByTag(@Param("tag") String tag, Pageable pageable);

    /**
     * 查找即将过期的规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.validTo IS NOT NULL AND rr.validTo BETWEEN :now AND :threshold ORDER BY rr.validTo ASC")
    List<RiskRule> findExpiringRules(@Param("now") LocalDateTime now, @Param("threshold") LocalDateTime threshold);

    /**
     * 查找从未执行的规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.executionCount = 0 OR rr.lastExecution IS NULL")
    Page<RiskRule> findNeverExecutedRules(Pageable pageable);

    /**
     * 查找从未匹配的规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.matchCount = 0 OR rr.lastMatch IS NULL")
    Page<RiskRule> findNeverMatchedRules(Pageable pageable);

    /**
     * 根据动作类型查找规则
     */
    List<RiskRule> findByActionAndEnabledTrueOrderByPriorityAsc(RiskRule.RuleAction action);

    /**
     * 获取规则类型统计
     */
    @Query("SELECT rr.ruleType, COUNT(rr) FROM RiskRule rr GROUP BY rr.ruleType")
    List<Object[]> getRuleTypeStatistics();

    /**
     * 获取规则分类统计
     */
    @Query("SELECT rr.ruleCategory, COUNT(rr) FROM RiskRule rr GROUP BY rr.ruleCategory")
    List<Object[]> getRuleCategoryStatistics();

    /**
     * 获取规则执行性能统计
     */
    @Query("SELECT rr.ruleName, rr.executionCount, rr.matchCount, rr.executionTimeAvg, rr.executionTimeMax FROM RiskRule rr WHERE rr.executionCount > 0 ORDER BY rr.executionTimeAvg DESC")
    List<Object[]> getRulePerformanceStats();

    /**
     * 获取最活跃的规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.executionCount > 0 ORDER BY rr.executionCount DESC")
    Page<RiskRule> getMostActiveRules(Pageable pageable);

    /**
     * 获取最有效的规则
     */
    @Query("SELECT rr FROM RiskRule rr WHERE rr.matchCount > 0 ORDER BY rr.matchCount DESC")
    Page<RiskRule> getMostEffectiveRules(Pageable pageable);

    /**
     * 更新规则执行统计
     */
    @Query("UPDATE RiskRule rr SET rr.executionCount = rr.executionCount + 1, rr.lastExecution = :executionTime WHERE rr.id = :id")
    int incrementExecutionCount(@Param("id") Long id, @Param("executionTime") LocalDateTime executionTime);

    /**
     * 更新规则匹配统计
     */
    @Query("UPDATE RiskRule rr SET rr.matchCount = rr.matchCount + 1, rr.lastMatch = :matchTime WHERE rr.id = :id")
    int incrementMatchCount(@Param("id") Long id, @Param("matchTime") LocalDateTime matchTime);

    /**
     * 更新规则误报统计
     */
    @Query("UPDATE RiskRule rr SET rr.falsePositiveCount = rr.falsePositiveCount + 1 WHERE rr.id = :id")
    int incrementFalsePositiveCount(@Param("id") Long id);

    /**
     * 更新规则执行时间
     */
    @Query("UPDATE RiskRule rr SET rr.executionTimeAvg = :avgTime, rr.executionTimeMax = :maxTime WHERE rr.id = :id")
    int updateExecutionTime(@Param("id") Long id, @Param("avgTime") Long avgTime, @Param("maxTime") Long maxTime);

    /**
     * 批量启用规则
     */
    @Query("UPDATE RiskRule rr SET rr.enabled = true WHERE rr.id IN :ids")
    int batchEnableRules(@Param("ids") List<Long> ids);

    /**
     * 批量禁用规则
     */
    @Query("UPDATE RiskRule rr SET rr.enabled = false WHERE rr.id IN :ids")
    int batchDisableRules(@Param("ids") List<Long> ids);
}