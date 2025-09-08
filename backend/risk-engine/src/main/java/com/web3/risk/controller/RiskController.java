package com.web3.risk.controller;

import com.web3.risk.entity.RiskAddress;
import com.web3.risk.entity.RiskTransaction;
import com.web3.risk.service.RiskAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 风险评估REST API控制器
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/risk")
@RequiredArgsConstructor
@Tag(name = "风险评估API", description = "提供交易和地址风险评估服务")
public class RiskController {

    private final RiskAssessmentService riskAssessmentService;

    /**
     * 评估单笔交易风险
     */
    @PostMapping("/transactions/assess")
    @Operation(summary = "评估交易风险", description = "对单笔交易进行风险评估")
    public ResponseEntity<RiskTransaction> assessTransaction(
            @RequestBody @Parameter(description = "交易信息") RiskTransaction transaction) {
        
        log.info("收到交易风险评估请求: {}", transaction.getTxHash());
        
        try {
            RiskTransaction assessedTransaction = riskAssessmentService.assessTransactionRisk(transaction);
            return ResponseEntity.ok(assessedTransaction);
        } catch (Exception e) {
            log.error("交易风险评估失败: {}", transaction.getTxHash(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 评估地址风险
     */
    @PostMapping("/addresses/assess")
    @Operation(summary = "评估地址风险", description = "对指定地址进行风险评估")
    public ResponseEntity<RiskAddress> assessAddress(
            @RequestParam @Parameter(description = "地址") String address,
            @RequestParam @Parameter(description = "网络") String network) {
        
        log.info("收到地址风险评估请求: {} ({})", address, network);
        
        try {
            RiskAddress assessedAddress = riskAssessmentService.assessAddressRisk(address, network);
            return ResponseEntity.ok(assessedAddress);
        } catch (Exception e) {
            log.error("地址风险评估失败: {}", address, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取高风险交易列表
     */
    @GetMapping("/transactions/high-risk")
    @Operation(summary = "获取高风险交易", description = "分页获取高风险交易列表")
    public ResponseEntity<Page<RiskTransaction>> getHighRiskTransactions(
            @RequestParam(defaultValue = "0") @Parameter(description = "页码") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "页大小") int size,
            @RequestParam(defaultValue = "timestamp") @Parameter(description = "排序字段") String sortBy,
            @RequestParam(defaultValue = "desc") @Parameter(description = "排序方向") String sortDir) {
        
        try {
            Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? 
                Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            
            Page<RiskTransaction> highRiskTransactions = riskAssessmentService.getHighRiskTransactions(pageable);
            return ResponseEntity.ok(highRiskTransactions);
        } catch (Exception e) {
            log.error("获取高风险交易失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取高风险地址列表
     */
    @GetMapping("/addresses/high-risk")
    @Operation(summary = "获取高风险地址", description = "分页获取高风险地址列表")
    public ResponseEntity<Page<RiskAddress>> getHighRiskAddresses(
            @RequestParam(defaultValue = "0") @Parameter(description = "页码") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "页大小") int size,
            @RequestParam(defaultValue = "riskScore") @Parameter(description = "排序字段") String sortBy,
            @RequestParam(defaultValue = "desc") @Parameter(description = "排序方向") String sortDir) {
        
        try {
            Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? 
                Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            
            Page<RiskAddress> highRiskAddresses = riskAssessmentService.getHighRiskAddresses(pageable);
            return ResponseEntity.ok(highRiskAddresses);
        } catch (Exception e) {
            log.error("获取高风险地址失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取最近的高风险活动
     */
    @GetMapping("/activity/recent")
    @Operation(summary = "获取最近高风险活动", description = "获取指定时间范围内的高风险活动统计")
    public ResponseEntity<Map<String, Object>> getRecentHighRiskActivity(
            @RequestParam(defaultValue = "24") @Parameter(description = "时间范围（小时）") int hours) {
        
        try {
            Map<String, Object> recentActivity = riskAssessmentService.getRecentHighRiskActivity(hours);
            return ResponseEntity.ok(recentActivity);
        } catch (Exception e) {
            log.error("获取最近高风险活动失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查风险评估服务状态")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "Risk Assessment Service",
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}