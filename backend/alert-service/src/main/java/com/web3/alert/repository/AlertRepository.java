package com.web3.alert.repository;

import com.web3.alert.entity.Alert;
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
 * 告警数据访问接口
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    /**
     * 根据告警ID查找
     */
    Optional<Alert> findByAlertId(String alertId);

    /**
     * 检查告警ID是否存在
     */
    boolean existsByAlertId(String alertId);

    /**
     * 根据状态查找告警
     */
    Page<Alert> findByStatus(Alert.AlertStatus status, Pageable pageable);

    /**
     * 根据严重程度查找告警
     */
    Page<Alert> findBySeverity(Alert.Severity severity, Pageable pageable);

    /**
     * 根据多个严重程度查找告警
     */
    Page<Alert> findBySeverityIn(List<Alert.Severity> severities, Pageable pageable);

    /**
     * 根据告警类型查找
     */
    Page<Alert> findByAlertType(Alert.AlertType alertType, Pageable pageable);

    /**
     * 根据创建时间后查找告警
     */
    List<Alert> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    /**
     * 根据时间范围查找告警
     */
    @Query("SELECT a FROM Alert a WHERE a.createdAt BETWEEN :startTime AND :endTime ORDER BY a.createdAt DESC")
    Page<Alert> findByTimeRange(@Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime,
                               Pageable pageable);

    /**
     * 查找活跃的高严重程度告警
     */
    @Query("SELECT a FROM Alert a WHERE a.status = 'ACTIVE' AND a.severity IN ('CRITICAL', 'HIGH') ORDER BY a.createdAt DESC")
    List<Alert> findActiveHighSeverityAlerts();

    /**
     * 根据目标地址查找告警
     */
    Page<Alert> findByTargetAddress(String targetAddress, Pageable pageable);

    /**
     * 根据交易哈希查找告警
     */
    Page<Alert> findByTransactionHash(String transactionHash, Pageable pageable);

    /**
     * 根据网络查找告警
     */
    Page<Alert> findByNetwork(String network, Pageable pageable);

    /**
     * 查找需要升级的告警
     */
    @Query("SELECT a FROM Alert a WHERE a.escalated = false AND a.status = 'ACTIVE' AND a.createdAt <= :threshold ORDER BY a.createdAt ASC")
    List<Alert> findAlertsNeedingEscalation(@Param("threshold") LocalDateTime threshold);

    /**
     * 查找未通知的告警
     */
    @Query("SELECT a FROM Alert a WHERE a.notified = false AND a.status = 'ACTIVE' ORDER BY a.createdAt ASC")
    List<Alert> findUnnotifiedAlerts();

    /**
     * 查找误报告警
     */
    Page<Alert> findByFalsePositiveTrue(Pageable pageable);

    /**
     * 根据分配人查找告警
     */
    Page<Alert> findByAssignedTo(String assignedTo, Pageable pageable);

    /**
     * 复合搜索告警
     */
    @Query("SELECT a FROM Alert a WHERE " +
           "(:keyword IS NULL OR a.title LIKE %:keyword% OR a.description LIKE %:keyword% OR a.alertId LIKE %:keyword%) AND " +
           "(:alertType IS NULL OR a.alertType = :alertType) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:startTime IS NULL OR a.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR a.createdAt <= :endTime) " +
           "ORDER BY a.createdAt DESC")
    Page<Alert> searchAlerts(@Param("keyword") String keyword,
                           @Param("alertType") Alert.AlertType alertType,
                           @Param("severity") Alert.Severity severity,
                           @Param("status") Alert.AlertStatus status,
                           @Param("startTime") LocalDateTime startTime,
                           @Param("endTime") LocalDateTime endTime,
                           Pageable pageable);

    // =================== 重复告警检测查询 ===================

    /**
     * 基于交易哈希查找重复告警
     */
    @Query("SELECT a FROM Alert a WHERE a.transactionHash = :txHash AND a.alertType = :alertType AND a.createdAt >= :since AND a.status != 'RESOLVED' ORDER BY a.createdAt DESC")
    Optional<Alert> findDuplicateByTransactionHash(@Param("txHash") String transactionHash,
                                                  @Param("alertType") Alert.AlertType alertType,
                                                  @Param("since") LocalDateTime since);

    /**
     * 基于地址查找重复告警
     */
    @Query("SELECT a FROM Alert a WHERE a.targetAddress = :address AND a.alertType = :alertType AND a.createdAt >= :since AND a.status != 'RESOLVED' ORDER BY a.createdAt DESC")
    Optional<Alert> findDuplicateByAddress(@Param("address") String targetAddress,
                                          @Param("alertType") Alert.AlertType alertType,
                                          @Param("since") LocalDateTime since);

    /**
     * 基于类型和标题查找重复告警
     */
    @Query("SELECT a FROM Alert a WHERE a.alertType = :alertType AND a.title = :title AND a.createdAt >= :since AND a.status != 'RESOLVED' ORDER BY a.createdAt DESC")
    Optional<Alert> findDuplicateByTypeAndDescription(@Param("alertType") Alert.AlertType alertType,
                                                     @Param("title") String title,
                                                     @Param("since") LocalDateTime since);

    // =================== 统计查询 ===================

    /**
     * 统计指定时间后的告警数量
     */
    long countByCreatedAtAfter(LocalDateTime since);

    /**
     * 统计误报告警数量
     */
    long countByFalsePositiveTrueAndCreatedAtAfter(LocalDateTime since);

    /**
     * 按严重程度统计告警数量
     */
    @Query("SELECT a.severity, COUNT(a) FROM Alert a WHERE a.createdAt >= :since GROUP BY a.severity")
    List<Object[]> getAlertCountBySeverity(@Param("since") LocalDateTime since);

    /**
     * 按状态统计告警数量
     */
    @Query("SELECT a.status, COUNT(a) FROM Alert a WHERE a.createdAt >= :since GROUP BY a.status")
    List<Object[]> getAlertCountByStatus(@Param("since") LocalDateTime since);

    /**
     * 按类型统计告警数量
     */
    @Query("SELECT a.alertType, COUNT(a) FROM Alert a WHERE a.createdAt >= :since GROUP BY a.alertType")
    List<Object[]> getAlertCountByType(@Param("since") LocalDateTime since);

    /**
     * 按网络统计告警数量
     */
    @Query("SELECT a.network, COUNT(a) FROM Alert a WHERE a.createdAt >= :since AND a.network IS NOT NULL GROUP BY a.network")
    List<Object[]> getAlertCountByNetwork(@Param("since") LocalDateTime since);

    /**
     * 获取平均解决时间（分钟）
     */
    @Query("SELECT AVG(a.durationSeconds / 60.0) FROM Alert a WHERE a.resolvedAt IS NOT NULL AND a.createdAt >= :since")
    Double getAverageResolutionTime(@Param("since") LocalDateTime since);

    /**
     * 获取每日告警统计
     */
    @Query("SELECT DATE(a.createdAt), COUNT(a) FROM Alert a WHERE a.createdAt >= :since GROUP BY DATE(a.createdAt) ORDER BY DATE(a.createdAt)")
    List<Object[]> getDailyAlertStats(@Param("since") LocalDateTime since);

    /**
     * 获取每小时告警统计
     */
    @Query("SELECT HOUR(a.createdAt), COUNT(a) FROM Alert a WHERE a.createdAt >= :since GROUP BY HOUR(a.createdAt) ORDER BY HOUR(a.createdAt)")
    List<Object[]> getHourlyAlertStats(@Param("since") LocalDateTime since);

    /**
     * 获取响应时间统计
     */
    @Query("SELECT a.severity, AVG(TIMESTAMPDIFF(MINUTE, a.createdAt, a.resolvedAt)) as avgResponseTime FROM Alert a WHERE a.resolvedAt IS NOT NULL AND a.createdAt >= :since GROUP BY a.severity")
    List<Object[]> getResponseTimeStatsBySeverity(@Param("since") LocalDateTime since);

    /**
     * 查找长时间未解决的告警
     */
    @Query("SELECT a FROM Alert a WHERE a.status IN ('ACTIVE', 'ACKNOWLEDGED') AND a.createdAt <= :threshold ORDER BY a.createdAt ASC")
    List<Alert> findLongRunningAlerts(@Param("threshold") LocalDateTime threshold);

    /**
     * 查找特定时间段内的突发告警
     */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.createdAt BETWEEN :startTime AND :endTime AND a.alertType = :alertType")
    long countAlertsBurstByType(@Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime,
                               @Param("alertType") Alert.AlertType alertType);

    /**
     * 获取最活跃的地址（产生最多告警）
     */
    @Query("SELECT a.targetAddress, COUNT(a) as alertCount FROM Alert a WHERE a.targetAddress IS NOT NULL AND a.createdAt >= :since GROUP BY a.targetAddress ORDER BY alertCount DESC")
    List<Object[]> getMostActiveAddresses(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * 获取最常见的告警类型
     */
    @Query("SELECT a.alertType, COUNT(a) as alertCount FROM Alert a WHERE a.createdAt >= :since GROUP BY a.alertType ORDER BY alertCount DESC")
    List<Object[]> getMostCommonAlertTypes(@Param("since") LocalDateTime since, Pageable pageable);

    // =================== 批量操作 ===================

    /**
     * 批量更新告警状态
     */
    @Query("UPDATE Alert a SET a.status = :status WHERE a.id IN :ids")
    int batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") Alert.AlertStatus status);

    /**
     * 批量分配告警
     */
    @Query("UPDATE Alert a SET a.assignedTo = :assignee WHERE a.id IN :ids")
    int batchAssignAlerts(@Param("ids") List<Long> ids, @Param("assignee") String assignee);

    /**
     * 批量标记为误报
     */
    @Query("UPDATE Alert a SET a.falsePositive = true, a.status = 'RESOLVED', a.resolvedAt = :resolvedAt, a.resolutionReason = :reason WHERE a.id IN :ids")
    int batchMarkAsFalsePositive(@Param("ids") List<Long> ids,
                                @Param("reason") String reason,
                                @Param("resolvedAt") LocalDateTime resolvedAt);
}