package com.web3.user.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户实体类
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username", unique = true),
    @Index(name = "idx_email", columnList = "email", unique = true),
    @Index(name = "idx_phone", columnList = "phone"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_last_login", columnList = "lastLoginAt")
})
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户名（唯一）
     */
    @Column(name = "username", unique = true, nullable = false, length = 50)
    private String username;

    /**
     * 邮箱（唯一）
     */
    @Column(name = "email", unique = true, nullable = false, length = 100)
    private String email;

    /**
     * 手机号
     */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * 密码哈希
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * 真实姓名
     */
    @Column(name = "full_name", length = 100)
    private String fullName;

    /**
     * 头像URL
     */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /**
     * 用户状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * 邮箱验证状态
     */
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    /**
     * 邮箱验证时间
     */
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    /**
     * 手机验证状态
     */
    @Column(name = "phone_verified", nullable = false)
    private Boolean phoneVerified = false;

    /**
     * 手机验证时间
     */
    @Column(name = "phone_verified_at")
    private LocalDateTime phoneVerifiedAt;

    /**
     * 两步验证启用状态
     */
    @Column(name = "two_factor_enabled", nullable = false)
    private Boolean twoFactorEnabled = false;

    /**
     * 两步验证密钥
     */
    @Column(name = "two_factor_secret", length = 32)
    private String twoFactorSecret;

    /**
     * 备用恢复码
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "user_recovery_codes",
        joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "recovery_code")
    private Set<String> recoveryCodes;

    /**
     * 用户角色
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles;

    /**
     * 上次登录时间
     */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * 上次登录IP
     */
    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    /**
     * 登录次数
     */
    @Column(name = "login_count")
    private Long loginCount = 0L;

    /**
     * 失败登录尝试次数
     */
    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;

    /**
     * 账户锁定时间
     */
    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    /**
     * 锁定到期时间
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /**
     * 密码最后更改时间
     */
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    /**
     * 密码到期时间
     */
    @Column(name = "password_expires_at")
    private LocalDateTime passwordExpiresAt;

    /**
     * 账户到期时间
     */
    @Column(name = "account_expires_at")
    private LocalDateTime accountExpiresAt;

    /**
     * 用户偏好设置（JSON格式）
     */
    @Column(name = "preferences", columnDefinition = "jsonb")
    private String preferences;

    /**
     * 用户元数据（JSON格式）
     */
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    /**
     * 时区
     */
    @Column(name = "timezone", length = 50)
    private String timezone = "UTC";

    /**
     * 语言
     */
    @Column(name = "language", length = 10)
    private String language = "en";

    /**
     * 组织ID（如果属于某个组织）
     */
    @Column(name = "organization_id")
    private Long organizationId;

    /**
     * 部门
     */
    @Column(name = "department", length = 100)
    private String department;

    /**
     * 职位
     */
    @Column(name = "job_title", length = 100)
    private String jobTitle;

    /**
     * 上级用户ID
     */
    @Column(name = "manager_id")
    private Long managerId;

    /**
     * 备注
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

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
     * 用户状态枚举
     */
    public enum UserStatus {
        ACTIVE,         // 活跃
        INACTIVE,       // 不活跃
        LOCKED,         // 锁定
        SUSPENDED,      // 暂停
        PENDING,        // 待激活
        DELETED         // 已删除
    }

    // ================== UserDetails接口实现 ==================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> new SimpleGrantedAuthority(permission.getName()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountExpiresAt == null || LocalDateTime.now().isBefore(accountExpiresAt);
    }

    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || LocalDateTime.now().isAfter(lockedUntil);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return passwordExpiresAt == null || LocalDateTime.now().isBefore(passwordExpiresAt);
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    // ================== 业务方法 ==================

    /**
     * 检查是否需要两步验证
     */
    public boolean requiresTwoFactorAuth() {
        return twoFactorEnabled && twoFactorSecret != null;
    }

    /**
     * 检查是否有指定权限
     */
    public boolean hasPermission(String permissionName) {
        return getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals(permissionName));
    }

    /**
     * 检查是否有指定角色
     */
    public boolean hasRole(String roleName) {
        return roles.stream()
                .anyMatch(role -> role.getName().equals(roleName));
    }

    /**
     * 增加登录计数
     */
    public void incrementLoginCount() {
        loginCount++;
        lastLoginAt = LocalDateTime.now();
        failedLoginAttempts = 0; // 重置失败次数
    }

    /**
     * 增加失败登录尝试次数
     */
    public void incrementFailedLoginAttempts() {
        failedLoginAttempts++;
    }

    /**
     * 锁定账户
     */
    public void lockAccount(int lockoutDurationSeconds) {
        lockedAt = LocalDateTime.now();
        lockedUntil = LocalDateTime.now().plusSeconds(lockoutDurationSeconds);
        status = UserStatus.LOCKED;
    }

    /**
     * 解锁账户
     */
    public void unlockAccount() {
        lockedAt = null;
        lockedUntil = null;
        failedLoginAttempts = 0;
        if (status == UserStatus.LOCKED) {
            status = UserStatus.ACTIVE;
        }
    }

    /**
     * 验证邮箱
     */
    public void verifyEmail() {
        emailVerified = true;
        emailVerifiedAt = LocalDateTime.now();
    }

    /**
     * 验证手机
     */
    public void verifyPhone() {
        phoneVerified = true;
        phoneVerifiedAt = LocalDateTime.now();
    }

    /**
     * 启用两步验证
     */
    public void enableTwoFactorAuth(String secret) {
        twoFactorEnabled = true;
        twoFactorSecret = secret;
    }

    /**
     * 禁用两步验证
     */
    public void disableTwoFactorAuth() {
        twoFactorEnabled = false;
        twoFactorSecret = null;
        recoveryCodes = null;
    }

    /**
     * 更新密码
     */
    public void updatePassword(String newPasswordHash) {
        passwordHash = newPasswordHash;
        passwordChangedAt = LocalDateTime.now();
        failedLoginAttempts = 0;
    }

    /**
     * 检查是否为系统管理员
     */
    public boolean isSystemAdmin() {
        return hasRole("SYSTEM_ADMIN");
    }

    /**
     * 检查是否为组织管理员
     */
    public boolean isOrgAdmin() {
        return hasRole("ORG_ADMIN");
    }

    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return fullName != null && !fullName.trim().isEmpty() ? fullName : username;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (passwordChangedAt == null) {
            passwordChangedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}