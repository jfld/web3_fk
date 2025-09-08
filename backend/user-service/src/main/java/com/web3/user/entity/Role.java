package com.web3.user.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 角色实体类
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "roles", indexes = {
    @Index(name = "idx_role_name", columnList = "name", unique = true),
    @Index(name = "idx_role_code", columnList = "code", unique = true),
    @Index(name = "idx_role_type", columnList = "roleType"),
    @Index(name = "idx_enabled", columnList = "enabled")
})
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 角色名称
     */
    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    /**
     * 角色代码
     */
    @Column(name = "code", unique = true, nullable = false, length = 50)
    private String code;

    /**
     * 角色描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 角色类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 20)
    private RoleType roleType;

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    /**
     * 是否系统内置角色
     */
    @Column(name = "system_builtin", nullable = false)
    private Boolean systemBuiltin = false;

    /**
     * 角色权限
     */
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions;

    /**
     * 父角色ID（用于角色继承）
     */
    @Column(name = "parent_role_id")
    private Long parentRoleId;

    /**
     * 角色级别（数字越小权限越高）
     */
    @Column(name = "role_level")
    private Integer roleLevel = 999;

    /**
     * 组织ID（如果是组织特定角色）
     */
    @Column(name = "organization_id")
    private Long organizationId;

    /**
     * 角色元数据（JSON格式）
     */
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 创建人ID
     */
    @Column(name = "created_by")
    private Long createdBy;

    /**
     * 更新人ID
     */
    @Column(name = "updated_by")
    private Long updatedBy;

    /**
     * 角色类型枚举
     */
    public enum RoleType {
        SYSTEM,         // 系统角色
        ORGANIZATION,   // 组织角色
        FUNCTIONAL,     // 功能角色
        CUSTOM          // 自定义角色
    }

    /**
     * 检查是否有指定权限
     */
    public boolean hasPermission(String permissionName) {
        return permissions != null && permissions.stream()
                .anyMatch(permission -> permission.getName().equals(permissionName));
    }

    /**
     * 检查是否为系统内置角色
     */
    public boolean isSystemRole() {
        return systemBuiltin;
    }

    /**
     * 检查角色是否启用
     */
    public boolean isActive() {
        return enabled;
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