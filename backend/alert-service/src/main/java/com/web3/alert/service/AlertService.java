package com.web3.alert.service;

import com.web3.alert.entity.Alert;
import com.web3.alert.entity.NotificationRecord;
import com.web3.alert.repository.AlertRepository;
import com.web3.alert.repository.NotificationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 告警管理服务 - 5秒内实时告警处理
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final NotificationRecordRepository notificationRecordRepository;
    private final NotificationService notificationService;
    private final AlertSuppressionService suppressionService;
    private final AlertEscalationService escalationService;

    /**
     * 创建新告警 - 核心方法，必须在5秒内完成处理
     */
    @Transactional
    public Alert createAlert(Alert alert) {
        long startTime = System.currentTimeMillis();
        log.info("创建告警开始: {} (类型: {}, 严重程度: {})", 
                alert.getAlertId(), alert.getAlertType(), alert.getSeverity());

        try {
            // 1. 验证告警数据 (目标: <100ms)
            validateAlert(alert);
            
            // 2. 检查告警抑制 (目标: <200ms)
            if (suppressionService.shouldSuppress(alert)) {
                log.info("告警被抑制: {}", alert.getAlertId());
                alert.setStatus(Alert.AlertStatus.SUPPRESSED);
                return alertRepository.save(alert);
            }
            
            // 3. 检查重复告警并去重 (目标: <300ms)
            Optional<Alert> existingAlert = findDuplicateAlert(alert);
            if (existingAlert.isPresent()) {
                log.info("发现重复告警，更新现有告警: {}", existingAlert.get().getAlertId());
                return updateExistingAlert(existingAlert.get(), alert);
            }
            
            // 4. 保存告警到数据库 (目标: <500ms)
            Alert savedAlert = alertRepository.save(alert);
            log.debug("告警保存完成: {}", savedAlert.getAlertId());
            
            // 5. 异步发送通知 - 不阻塞主流程 (目标: 立即返回)
            CompletableFuture.runAsync(() -> {
                try {
                    processNotifications(savedAlert);
                } catch (Exception e) {
                    log.error("异步发送通知失败: {}", savedAlert.getAlertId(), e);
                }
            });
            
            // 6. 异步检查是否需要升级
            CompletableFuture.runAsync(() -> {
                try {
                    escalationService.scheduleEscalation(savedAlert);
                } catch (Exception e) {
                    log.error("异步升级检查失败: {}", savedAlert.getAlertId(), e);
                }
            });
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("告警创建完成: {} (耗时: {}ms)", savedAlert.getAlertId(), processingTime);
            
            // 确保在5秒内完成
            if (processingTime > 5000) {
                log.warn("告警处理超时: {} (耗时: {}ms)", savedAlert.getAlertId(), processingTime);
            }
            
            return savedAlert;
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("创建告警失败: {} (耗时: {}ms)", alert.getAlertId(), processingTime, e);
            throw new RuntimeException("告警创建失败", e);
        }
    }

    /**
     * 批量创建告警 - 优化性能
     */
    @Transactional
    public List<Alert> createAlerts(List<Alert> alerts) {
        log.info("开始批量创建告警: {} 个", alerts.size());
        
        List<Alert> results = new ArrayList<>();
        List<CompletableFuture<Alert>> futures = new ArrayList<>();
        
        // 并行处理多个告警
        for (Alert alert : alerts) {
            CompletableFuture<Alert> future = CompletableFuture.supplyAsync(() -> createAlert(alert));
            futures.add(future);
        }
        
        // 等待所有告警处理完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    for (CompletableFuture<Alert> future : futures) {
                        try {
                            results.add(future.get());
                        } catch (Exception e) {
                            log.error("批量创建告警中的某个告警失败", e);
                        }
                    }
                });
        
        return results;
    }

    /**
     * 获取活跃告警
     */
    public Page<Alert> getActiveAlerts(Pageable pageable) {
        return alertRepository.findByStatus(Alert.AlertStatus.ACTIVE, pageable);
    }

    /**
     * 获取高严重等级告警
     */
    public Page<Alert> getHighSeverityAlerts(Pageable pageable) {
        return alertRepository.findBySeverityIn(
                Arrays.asList(Alert.Severity.CRITICAL, Alert.Severity.HIGH), 
                pageable);
    }

    /**
     * 获取最近的告警
     */
    public List<Alert> getRecentAlerts(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return alertRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since);
    }

    /**
     * 确认告警
     */
    @Transactional
    public Alert acknowledgeAlert(String alertId, String acknowledgedBy) {
        log.info("确认告警: {} (操作人: {})", alertId, acknowledgedBy);
        
        Optional<Alert> alertOpt = alertRepository.findByAlertId(alertId);
        if (alertOpt.isEmpty()) {
            throw new IllegalArgumentException("告警不存在: " + alertId);
        }
        
        Alert alert = alertOpt.get();
        alert.setStatus(Alert.AlertStatus.ACKNOWLEDGED);
        alert.setAssignedTo(acknowledgedBy);
        
        return alertRepository.save(alert);
    }

    /**
     * 解决告警
     */
    @Transactional
    public Alert resolveAlert(String alertId, String resolutionReason, String resolvedBy) {
        log.info("解决告警: {} (操作人: {}, 原因: {})", alertId, resolvedBy, resolutionReason);
        
        Optional<Alert> alertOpt = alertRepository.findByAlertId(alertId);
        if (alertOpt.isEmpty()) {
            throw new IllegalArgumentException("告警不存在: " + alertId);
        }
        
        Alert alert = alertOpt.get();
        alert.resolve(resolutionReason);
        alert.setAssignedTo(resolvedBy);
        
        return alertRepository.save(alert);
    }

    /**
     * 标记为误报
     */
    @Transactional
    public Alert markAsFalsePositive(String alertId, String reason, String operatedBy) {
        log.info("标记告警为误报: {} (操作人: {}, 原因: {})", alertId, operatedBy, reason);
        
        Optional<Alert> alertOpt = alertRepository.findByAlertId(alertId);
        if (alertOpt.isEmpty()) {
            throw new IllegalArgumentException("告警不存在: " + alertId);
        }
        
        Alert alert = alertOpt.get();
        alert.markAsFalsePositive(reason);
        alert.setAssignedTo(operatedBy);
        
        return alertRepository.save(alert);
    }

    /**
     * 获取告警统计信息
     */
    public Map<String, Object> getAlertStatistics(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        
        Map<String, Object> statistics = new HashMap<>();
        
        // 总告警数
        long totalAlerts = alertRepository.countByCreatedAtAfter(since);
        statistics.put("totalAlerts", totalAlerts);
        
        // 按严重程度分组
        List<Object[]> severityStats = alertRepository.getAlertCountBySeverity(since);
        Map<String, Long> severityMap = new HashMap<>();
        for (Object[] stat : severityStats) {
            severityMap.put(stat[0].toString(), (Long) stat[1]);
        }
        statistics.put("severityDistribution", severityMap);
        
        // 按状态分组
        List<Object[]> statusStats = alertRepository.getAlertCountByStatus(since);
        Map<String, Long> statusMap = new HashMap<>();
        for (Object[] stat : statusStats) {
            statusMap.put(stat[0].toString(), (Long) stat[1]);
        }
        statistics.put("statusDistribution", statusMap);
        
        // 按类型分组
        List<Object[]> typeStats = alertRepository.getAlertCountByType(since);
        Map<String, Long> typeMap = new HashMap<>();
        for (Object[] stat : typeStats) {
            typeMap.put(stat[0].toString(), (Long) stat[1]);
        }
        statistics.put("typeDistribution", typeMap);
        
        // 平均解决时间
        Double avgResolutionTime = alertRepository.getAverageResolutionTime(since);
        statistics.put("averageResolutionTimeMinutes", avgResolutionTime);
        
        // 误报率
        long falsePositiveCount = alertRepository.countByFalsePositiveTrueAndCreatedAtAfter(since);
        double falsePositiveRate = totalAlerts > 0 ? (double) falsePositiveCount / totalAlerts * 100 : 0;
        statistics.put("falsePositiveRate", falsePositiveRate);
        
        return statistics;
    }

    /**
     * 搜索告警
     */
    public Page<Alert> searchAlerts(String keyword, Alert.AlertType type, Alert.Severity severity, 
                                   Alert.AlertStatus status, LocalDateTime startTime, 
                                   LocalDateTime endTime, Pageable pageable) {
        
        return alertRepository.searchAlerts(keyword, type, severity, status, 
                                          startTime, endTime, pageable);
    }

    // ================== 私有辅助方法 ==================

    /**
     * 验证告警数据
     */
    private void validateAlert(Alert alert) {
        if (alert.getAlertType() == null) {
            throw new IllegalArgumentException("告警类型不能为空");
        }
        
        if (alert.getSeverity() == null) {
            throw new IllegalArgumentException("严重程度不能为空");
        }
        
        if (alert.getTitle() == null || alert.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("告警标题不能为空");
        }
        
        if (alert.getSource() == null || alert.getSource().trim().isEmpty()) {
            throw new IllegalArgumentException("告警来源不能为空");
        }
    }

    /**
     * 查找重复告警
     */
    private Optional<Alert> findDuplicateAlert(Alert alert) {
        // 在最近5分钟内查找相同类型和目标的告警
        LocalDateTime since = LocalDateTime.now().minusMinutes(5);
        
        if (alert.getTransactionHash() != null) {
            // 基于交易哈希查重
            return alertRepository.findDuplicateByTransactionHash(
                    alert.getTransactionHash(), 
                    alert.getAlertType(), 
                    since);
        } else if (alert.getTargetAddress() != null) {
            // 基于地址查重
            return alertRepository.findDuplicateByAddress(
                    alert.getTargetAddress(), 
                    alert.getAlertType(), 
                    since);
        } else {
            // 基于类型和描述查重
            return alertRepository.findDuplicateByTypeAndDescription(
                    alert.getAlertType(), 
                    alert.getTitle(), 
                    since);
        }
    }

    /**
     * 更新现有告警
     */
    private Alert updateExistingAlert(Alert existingAlert, Alert newAlert) {
        // 更新告警次数或相关信息
        existingAlert.setNotificationCount(existingAlert.getNotificationCount() + 1);
        
        // 如果新告警的严重程度更高，则更新
        if (newAlert.getSeverity().ordinal() > existingAlert.getSeverity().ordinal()) {
            existingAlert.setSeverity(newAlert.getSeverity());
        }
        
        // 合并风险评分（取最高值）
        if (newAlert.getRiskScore() != null) {
            if (existingAlert.getRiskScore() == null || 
                newAlert.getRiskScore().compareTo(existingAlert.getRiskScore()) > 0) {
                existingAlert.setRiskScore(newAlert.getRiskScore());
            }
        }
        
        return alertRepository.save(existingAlert);
    }

    /**
     * 处理通知 - 异步执行
     */
    private void processNotifications(Alert alert) {
        try {
            log.debug("开始处理告警通知: {}", alert.getAlertId());
            
            // 根据严重程度确定通知渠道
            List<NotificationRecord.NotificationChannel> channels = determineNotificationChannels(alert);
            
            // 发送通知
            for (NotificationRecord.NotificationChannel channel : channels) {
                try {
                    notificationService.sendNotification(alert, channel);
                } catch (Exception e) {
                    log.error("发送 {} 通知失败: {}", channel, alert.getAlertId(), e);
                }
            }
            
            // 更新通知状态
            alert.setNotified(true);
            alert.setLastNotificationAt(LocalDateTime.now());
            alert.setNotificationChannels(channels.stream().map(Enum::name).toList());
            alertRepository.save(alert);
            
        } catch (Exception e) {
            log.error("处理告警通知失败: {}", alert.getAlertId(), e);
        }
    }

    /**
     * 根据严重程度确定通知渠道
     */
    private List<NotificationRecord.NotificationChannel> determineNotificationChannels(Alert alert) {
        List<NotificationRecord.NotificationChannel> channels = new ArrayList<>();
        
        switch (alert.getSeverity()) {
            case CRITICAL:
                // 严重告警：所有渠道
                channels.add(NotificationRecord.NotificationChannel.EMAIL);
                channels.add(NotificationRecord.NotificationChannel.SMS);
                channels.add(NotificationRecord.NotificationChannel.WECHAT);
                channels.add(NotificationRecord.NotificationChannel.WEBHOOK);
                break;
                
            case HIGH:
                // 高级告警：邮件、微信、Webhook
                channels.add(NotificationRecord.NotificationChannel.EMAIL);
                channels.add(NotificationRecord.NotificationChannel.WECHAT);
                channels.add(NotificationRecord.NotificationChannel.WEBHOOK);
                break;
                
            case MEDIUM:
                // 中级告警：邮件、Webhook
                channels.add(NotificationRecord.NotificationChannel.EMAIL);
                channels.add(NotificationRecord.NotificationChannel.WEBHOOK);
                break;
                
            case LOW:
            case INFO:
                // 低级告警：仅Webhook
                channels.add(NotificationRecord.NotificationChannel.WEBHOOK);
                break;
        }
        
        return channels;
    }
}