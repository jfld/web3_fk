package com.web3.user.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 权限实体类
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "permissions", indexes = {
    @Index(name = "idx_permission_name", columnList = "name", unique = true),
    @Index(name = "idx_permission_code", columnList = "code", unique = true),
    @Index(name = "idx_permission_category", columnList = "category"),
    @Index(name = "idx_permission_resource", columnList = "resource")
})
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 权限名称
     */
    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    /**
     * 权限代码
     */
    @Column(name = "code", unique = true, nullable = false, length = 100)
    private String code;

    /**
     * 权限描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 权限类别
     */
    @Column(name = "category", length = 50)
    private String category;

    /**
     * 资源标识
     */
    @Column(name = "resource", length = 100)
    private String resource;

    /**
     * 操作类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private PermissionAction action;

    /**
     * 是否系统内置权限
     */
    @Column(name = "system_builtin", nullable = false)
    private Boolean systemBuiltin = false;

    /**
     * 权限级别（数字越小权限越高）
     */
    @Column(name = "permission_level")
    private Integer permissionLevel = 999;

    /**
     * 父权限ID（用于权限继承）
     */
    @Column(name = "parent_permission_id")
    private Long parentPermissionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 权限操作类型枚举
     */
    public enum PermissionAction {
        CREATE,     // 创建
        READ,       // 读取
        UPDATE,     // 更新
        DELETE,     // 删除
        EXECUTE,    // 执行
        MANAGE,     // 管理
        VIEW,       // 查看
        EXPORT,     // 导出
        IMPORT,     // 导入
        APPROVE,    // 审批
        REJECT      // 拒绝
    }

    /**
     * 系统预定义权限常量
     */
    public static class Permissions {
        // 系统管理权限
        public static final String SYSTEM_ADMIN = "system.admin";
        public static final String SYSTEM_CONFIG = "system.config";
        public static final String SYSTEM_MONITOR = "system.monitor";
        
        // 用户管理权限
        public static final String USER_CREATE = "user.create";
        public static final String USER_READ = "user.read";
        public static final String USER_UPDATE = "user.update";
        public static final String USER_DELETE = "user.delete";
        public static final String USER_MANAGE = "user.manage";
        
        // 角色权限管理
        public static final String ROLE_CREATE = "role.create";
        public static final String ROLE_READ = "role.read";
        public static final String ROLE_UPDATE = "role.update";
        public static final String ROLE_DELETE = "role.delete";
        public static final String ROLE_ASSIGN = "role.assign";
        
        // 风险管理权限
        public static final String RISK_VIEW = "risk.view";
        public static final String RISK_ANALYZE = "risk.analyze";
        public static final String RISK_MANAGE = "risk.manage";
        public static final String RISK_CONFIG = "risk.config";
        
        // 告警管理权限
        public static final String ALERT_VIEW = "alert.view";
        public static final String ALERT_MANAGE = "alert.manage";
        public static final String ALERT_RESOLVE = "alert.resolve";
        public static final String ALERT_CONFIG = "alert.config";
        
        // 报告权限
        public static final String REPORT_VIEW = "report.view";
        public static final String REPORT_CREATE = "report.create";
        public static final String REPORT_EXPORT = "report.export";
        public static final String REPORT_MANAGE = "report.manage";
        
        // 规则引擎权限
        public static final String RULE_VIEW = "rule.view";
        public static final String RULE_CREATE = "rule.create";
        public static final String RULE_UPDATE = "rule.update";
        public static final String RULE_DELETE = "rule.delete";
        public static final String RULE_EXECUTE = "rule.execute";
        
        // 数据权限
        public static final String DATA_VIEW = "data.view";
        public static final String DATA_EXPORT = "data.export";
        public static final String DATA_IMPORT = "data.import";
        public static final String DATA_DELETE = "data.delete";
        
        // 审计权限
        public static final String AUDIT_VIEW = "audit.view";
        public static final String AUDIT_EXPORT = "audit.export";
        
        // API权限
        public static final String API_ACCESS = "api.access";
        public static final String API_MANAGE = "api.manage";
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}