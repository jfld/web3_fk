package com.web3.risk.controller;

import com.web3.risk.entity.RiskRule;
import com.web3.risk.entity.RiskTransaction;
import com.web3.risk.service.RuleEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 规则管理REST API控制器
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/rules")
@RequiredArgsConstructor
@Tag(name = "规则管理API", description = "提供风险规则的增删改查和管理功能")
public class RuleController {

    private final RuleEngineService ruleEngineService;

    /**
     * 创建新规则
     */
    @PostMapping
    @Operation(summary = "创建规则", description = "创建新的风险评估规则")
    public ResponseEntity<?> createRule(@Valid @RequestBody @Parameter(description = "规则信息") RiskRule rule) {
        log.info("收到创建规则请求: {}", rule.getRuleName());
        
        try {
            RiskRule createdRule = ruleEngineService.createRule(rule);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdRule);
        } catch (IllegalArgumentException e) {
            log.warn("创建规则失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("创建规则异常", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "服务器内部错误"));
        }
    }

    /**
     * 更新规则
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新规则", description = "更新指定ID的风险规则")
    public ResponseEntity<?> updateRule(
            @PathVariable @Parameter(description = "规则ID") Long id,
            @Valid @RequestBody @Parameter(description = "更新的规则信息") RiskRule rule) {
        
        log.info("收到更新规则请求: {}", id);
        
        try {
            RiskRule updatedRule = ruleEngineService.updateRule(id, rule);
            return ResponseEntity.ok(updatedRule);
        } catch (IllegalArgumentException e) {
            log.warn("更新规则失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("更新规则异常", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "服务器内部错误"));
        }
    }

    /**
     * 删除规则
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除规则", description = "删除指定ID的风险规则")
    public ResponseEntity<?> deleteRule(@PathVariable @Parameter(description = "规则ID") Long id) {
        log.info("收到删除规则请求: {}", id);
        
        try {
            ruleEngineService.deleteRule(id);
            return ResponseEntity.ok(Map.of("message", "规则删除成功"));
        } catch (IllegalArgumentException e) {
            log.warn("删除规则失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("删除规则异常", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "服务器内部错误"));
        }
    }

    /**
     * 获取规则列表
     */
    @GetMapping
    @Operation(summary = "获取规则列表", description = "分页获取风险规则列表")
    public ResponseEntity<Page<RiskRule>> getRules(
            @RequestParam(defaultValue = "0") @Parameter(description = "页码") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "页大小") int size,
            @RequestParam(defaultValue = "priority") @Parameter(description = "排序字段") String sortBy,
            @RequestParam(defaultValue = "asc") @Parameter(description = "排序方向") String sortDir) {
        
        try {
            Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? 
                Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            
            Page<RiskRule> rules = ruleEngineService.getRules(pageable);
            return ResponseEntity.ok(rules);
        } catch (Exception e) {
            log.error("获取规则列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据类型获取规则
     */
    @GetMapping("/type/{ruleType}")
    @Operation(summary = "按类型获取规则", description = "获取指定类型的所有启用规则")
    public ResponseEntity<List<RiskRule>> getRulesByType(
            @PathVariable @Parameter(description = "规则类型") RiskRule.RuleType ruleType) {
        
        try {
            List<RiskRule> rules = ruleEngineService.getRulesByType(ruleType);
            return ResponseEntity.ok(rules);
        } catch (Exception e) {
            log.error("按类型获取规则失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 启用/禁用规则
     */
    @PatchMapping("/{id}/toggle")
    @Operation(summary = "切换规则状态", description = "启用或禁用指定规则")
    public ResponseEntity<?> toggleRuleStatus(
            @PathVariable @Parameter(description = "规则ID") Long id,
            @RequestParam @Parameter(description = "是否启用") boolean enabled) {
        
        log.info("收到切换规则状态请求: {} -> {}", id, enabled);
        
        try {
            ruleEngineService.toggleRuleStatus(id, enabled);
            String action = enabled ? "启用" : "禁用";
            return ResponseEntity.ok(Map.of("message", "规则" + action + "成功"));
        } catch (IllegalArgumentException e) {
            log.warn("切换规则状态失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("切换规则状态异常", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "服务器内部错误"));
        }
    }

    /**
     * 测试规则
     */
    @PostMapping("/{id}/test")
    @Operation(summary = "测试规则", description = "使用测试交易数据测试规则")
    public ResponseEntity<?> testRule(
            @PathVariable @Parameter(description = "规则ID") Long id,
            @RequestBody @Parameter(description = "测试交易数据") RiskTransaction testTransaction) {
        
        log.info("收到测试规则请求: {}", id);
        
        try {
            // 首先获取规则
            // 这里需要添加获取规则的方法，暂时返回测试结果格式
            Map<String, Object> testResult = Map.of(
                "ruleId", id,
                "testTransaction", testTransaction.getTxHash(),
                "result", "测试功能待完善",
                "timestamp", java.time.LocalDateTime.now().toString()
            );
            
            return ResponseEntity.ok(testResult);
        } catch (Exception e) {
            log.error("测试规则异常", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "测试规则失败"));
        }
    }

    /**
     * 获取规则统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取规则统计", description = "获取规则执行和性能统计信息")
    public ResponseEntity<Map<String, Object>> getRuleStatistics() {
        try {
            Map<String, Object> statistics = ruleEngineService.getRuleStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("获取规则统计失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 规则健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "规则服务健康检查", description = "检查规则引擎服务状态")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            // 可以添加更详细的健康检查逻辑
            Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "Rule Engine Service",
                "timestamp", java.time.LocalDateTime.now().toString(),
                "activeRulesCount", 0 // 这里可以添加实际的活跃规则数量
            );
            
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("健康检查失败", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "DOWN", "error", e.getMessage()));
        }
    }
}