package com.web3.risk.repository;

import com.web3.risk.entity.RiskAddress;
import com.web3.risk.entity.RiskTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 风险地址数据访问接口
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Repository
public interface RiskAddressRepository extends JpaRepository<RiskAddress, Long> {

    /**
     * 根据地址查找
     */
    Optional<RiskAddress> findByAddress(String address);

    /**
     * 根据地址和网络查找
     */
    Optional<RiskAddress> findByAddressAndNetwork(String address, String network);

    /**
     * 检查地址是否存在
     */
    boolean existsByAddress(String address);

    /**
     * 检查地址是否在黑名单中
     */
    @Query("SELECT ra FROM RiskAddress ra WHERE ra.address = :address AND ra.isBlacklisted = true")
    Optional<RiskAddress> findBlacklistedAddress(@Param("address") String address);

    /**
     * 根据风险等级查找地址
     */
    Page<RiskAddress> findByRiskLevel(RiskTransaction.RiskLevel riskLevel, Pageable pageable);

    /**
     * 查找高风险地址
     */
    @Query("SELECT ra FROM RiskAddress ra WHERE ra.riskLevel IN ('HIGH', 'CRITICAL') ORDER BY ra.riskScore DESC")
    Page<RiskAddress> findHighRiskAddresses(Pageable pageable);

    /**
     * 根据地址类型查找
     */
    Page<RiskAddress> findByAddressType(String addressType, Pageable pageable);

    /**
     * 查找黑名单地址
     */
    Page<RiskAddress> findByIsBlacklistedTrue(Pageable pageable);

    /**
     * 根据网络查找地址
     */
    Page<RiskAddress> findByNetwork(String network, Pageable pageable);

    /**
     * 根据风险评分范围查找地址
     */
    @Query("SELECT ra FROM RiskAddress ra WHERE ra.riskScore BETWEEN :minScore AND :maxScore ORDER BY ra.riskScore DESC")
    Page<RiskAddress> findByRiskScoreRange(@Param("minScore") BigDecimal minScore, 
                                         @Param("maxScore") BigDecimal maxScore, 
                                         Pageable pageable);

    /**
     * 查找活跃地址（最近30天有活动）
     */
    @Query("SELECT ra FROM RiskAddress ra WHERE ra.lastActivity >= :since ORDER BY ra.lastActivity DESC")
    Page<RiskAddress> findActiveAddresses(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * 查找交易所地址
     */
    Page<RiskAddress> findByIsExchangeWalletTrue(Pageable pageable);

    /**
     * 查找合约地址
     */
    @Query("SELECT ra FROM RiskAddress ra WHERE ra.addressType = 'CONTRACT' ORDER BY ra.creationTimestamp DESC")
    Page<RiskAddress> findContractAddresses(Pageable pageable);

    /**
     * 根据交易量范围查找地址
     */
    @Query("SELECT ra FROM RiskAddress ra WHERE ra.transactionCount BETWEEN :minTxCount AND :maxTxCount ORDER BY ra.transactionCount DESC")
    Page<RiskAddress> findByTransactionCountRange(@Param("minTxCount") Long minTxCount, 
                                                @Param("maxTxCount") Long maxTxCount, 
                                                Pageable pageable);

    /**
     * 查找可疑活动地址
     */
    @Query("SELECT ra FROM RiskAddress ra WHERE ra.suspiciousActivityCount > 0 ORDER BY ra.suspiciousActivityCount DESC")
    Page<RiskAddress> findSuspiciousAddresses(Pageable pageable);

    /**
     * 查找混币器交互地址
     */
    @Query("SELECT ra FROM RiskAddress ra WHERE ra.mixerInteractions > 0 ORDER BY ra.mixerInteractions DESC")
    Page<RiskAddress> findMixerInteractionAddresses(Pageable pageable);

    /**
     * 根据标签查找地址
     */
    @Query("SELECT ra FROM RiskAddress ra JOIN ra.riskTags rt WHERE rt = :tag")
    Page<RiskAddress> findByRiskTag(@Param("tag") String tag, Pageable pageable);

    /**
     * 查找需要分析师审核的地址
     */
    Page<RiskAddress> findByAnalystReviewTrue(Pageable pageable);

    /**
     * 根据创建时间范围查找地址
     */
    @Query("SELECT ra FROM RiskAddress ra WHERE ra.firstSeen BETWEEN :startTime AND :endTime ORDER BY ra.firstSeen DESC")
    Page<RiskAddress> findByFirstSeenRange(@Param("startTime") LocalDateTime startTime, 
                                         @Param("endTime") LocalDateTime endTime, 
                                         Pageable pageable);

    /**
     * 查找DeFi协议相关地址
     */
    @Query("SELECT ra FROM RiskAddress ra WHERE ra.isLiquidityProvider = true OR ra.isYieldFarmer = true")
    Page<RiskAddress> findDefiRelatedAddresses(Pageable pageable);

    /**
     * 统计各风险等级地址数量
     */
    @Query("SELECT ra.riskLevel, COUNT(ra) FROM RiskAddress ra GROUP BY ra.riskLevel")
    List<Object[]> getRiskLevelDistribution();

    /**
     * 统计各地址类型数量
     */
    @Query("SELECT ra.addressType, COUNT(ra) FROM RiskAddress ra GROUP BY ra.addressType")
    List<Object[]> getAddressTypeDistribution();

    /**
     * 统计各网络地址数量
     */
    @Query("SELECT ra.network, COUNT(ra) FROM RiskAddress ra GROUP BY ra.network")
    List<Object[]> getNetworkDistribution();

    /**
     * 获取风险评分统计信息
     */
    @Query("SELECT AVG(ra.riskScore), MIN(ra.riskScore), MAX(ra.riskScore), COUNT(ra) FROM RiskAddress ra WHERE ra.network = :network")
    Object[] getRiskScoreStatsByNetwork(@Param("network") String network);

    /**
     * 查找最近新增的高风险地址
     */
    @Query("SELECT ra FROM RiskAddress ra WHERE ra.riskLevel IN ('HIGH', 'CRITICAL') AND ra.createdAt >= :since ORDER BY ra.createdAt DESC")
    List<RiskAddress> findRecentHighRiskAddresses(@Param("since") LocalDateTime since);

    /**
     * 查找交易量异常的地址
     */
    @Query("SELECT ra FROM RiskAddress ra WHERE ra.transactionCount > :threshold ORDER BY ra.transactionCount DESC")
    Page<RiskAddress> findHighVolumeAddresses(@Param("threshold") Long threshold, Pageable pageable);

    /**
     * 根据黑名单来源查找地址
     */
    Page<RiskAddress> findByBlacklistSource(String blacklistSource, Pageable pageable);

    /**
     * 查找验证过的合约地址
     */
    @Query("SELECT ra FROM RiskAddress ra WHERE ra.addressType = 'CONTRACT' AND ra.isVerified = true")
    Page<RiskAddress> findVerifiedContracts(Pageable pageable);

    /**
     * 查找代理合约地址
     */
    @Query("SELECT ra FROM RiskAddress ra WHERE ra.proxyType IS NOT NULL")
    Page<RiskAddress> findProxyContracts(Pageable pageable);

    /**
     * 根据国家/地区查找地址
     */
    Page<RiskAddress> findByEstimatedCountry(String country, Pageable pageable);

    /**
     * 查找检测到VPN使用的地址
     */
    Page<RiskAddress> findByVpnUsageDetectedTrue(Pageable pageable);

    /**
     * 更新地址风险评分
     */
    @Query("UPDATE RiskAddress ra SET ra.riskScore = :riskScore, ra.riskLevel = :riskLevel WHERE ra.id = :id")
    int updateRiskScore(@Param("id") Long id, 
                       @Param("riskScore") BigDecimal riskScore, 
                       @Param("riskLevel") RiskTransaction.RiskLevel riskLevel);

    /**
     * 批量标记为黑名单
     */
    @Query("UPDATE RiskAddress ra SET ra.isBlacklisted = true, ra.blacklistSource = :source, ra.blacklistedAt = :timestamp WHERE ra.address IN :addresses")
    int batchBlacklist(@Param("addresses") List<String> addresses, 
                      @Param("source") String source, 
                      @Param("timestamp") LocalDateTime timestamp);

    /**
     * 获取地址活跃度统计
     */
    @Query("SELECT DATE(ra.lastActivity), COUNT(ra) FROM RiskAddress ra WHERE ra.lastActivity >= :since GROUP BY DATE(ra.lastActivity) ORDER BY DATE(ra.lastActivity)")
    List<Object[]> getAddressActivityStats(@Param("since") LocalDateTime since);
}