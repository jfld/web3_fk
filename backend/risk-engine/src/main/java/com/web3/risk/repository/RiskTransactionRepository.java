package com.web3.risk.repository;

import com.web3.risk.entity.RiskTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 风险交易数据访问接口
 *
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@Repository
public interface RiskTransactionRepository extends JpaRepository<RiskTransaction, Long> {

    /**
     * 根据交易哈希查找交易
     */
    Optional<RiskTransaction> findByTxHash(String txHash);

    /**
     * 检查交易是否存在
     */
    boolean existsByTxHash(String txHash);

    /**
     * 根据地址查找相关交易
     */
    @Query("SELECT rt FROM RiskTransaction rt WHERE rt.fromAddress = :address OR rt.toAddress = :address ORDER BY rt.timestamp DESC")
    Page<RiskTransaction> findByAddress(@Param("address") String address, Pageable pageable);

    /**
     * 根据风险等级查找交易
     */
    Page<RiskTransaction> findByRiskLevel(RiskTransaction.RiskLevel riskLevel, Pageable pageable);

    /**
     * 查找高风险交易 (HIGH, CRITICAL)
     */
    @Query("SELECT rt FROM RiskTransaction rt WHERE rt.riskLevel IN ('HIGH', 'CRITICAL') ORDER BY rt.timestamp DESC")
    Page<RiskTransaction> findHighRiskTransactions(Pageable pageable);

    /**
     * 根据网络查找交易
     */
    Page<RiskTransaction> findByNetwork(String network, Pageable pageable);

    /**
     * 根据时间范围查找交易
     */
    @Query("SELECT rt FROM RiskTransaction rt WHERE rt.timestamp BETWEEN :startTime AND :endTime ORDER BY rt.timestamp DESC")
    Page<RiskTransaction> findByTimeRange(@Param("startTime") LocalDateTime startTime, 
                                        @Param("endTime") LocalDateTime endTime, 
                                        Pageable pageable);

    /**
     * 查找大额交易
     */
    @Query("SELECT rt FROM RiskTransaction rt WHERE rt.value >= :minValue ORDER BY rt.value DESC")
    Page<RiskTransaction> findLargeValueTransactions(@Param("minValue") BigInteger minValue, Pageable pageable);

    /**
     * 查找未处理的交易
     */
    Page<RiskTransaction> findByProcessedFalseOrderByCreatedAtAsc(Pageable pageable);

    /**
     * 根据风险评分范围查找交易
     */
    @Query("SELECT rt FROM RiskTransaction rt WHERE rt.riskScore BETWEEN :minScore AND :maxScore ORDER BY rt.riskScore DESC")
    Page<RiskTransaction> findByRiskScoreRange(@Param("minScore") BigDecimal minScore, 
                                             @Param("maxScore") BigDecimal maxScore, 
                                             Pageable pageable);

    /**
     * 查找合约调用交易
     */
    Page<RiskTransaction> findByIsContractCallTrue(Pageable pageable);

    /**
     * 查找代币转账交易
     */
    Page<RiskTransaction> findByIsTokenTransferTrue(Pageable pageable);

    /**
     * 根据地址和时间范围统计交易数量
     */
    @Query("SELECT COUNT(rt) FROM RiskTransaction rt WHERE (rt.fromAddress = :address OR rt.toAddress = :address) AND rt.timestamp BETWEEN :startTime AND :endTime")
    long countByAddressAndTimeRange(@Param("address") String address,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 根据地址统计总交易金额
     */
    @Query("SELECT COALESCE(SUM(rt.value), 0) FROM RiskTransaction rt WHERE rt.fromAddress = :address AND rt.timestamp BETWEEN :startTime AND :endTime")
    BigInteger sumSentValueByAddressAndTimeRange(@Param("address") String address,
                                               @Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    /**
     * 根据地址统计接收金额
     */
    @Query("SELECT COALESCE(SUM(rt.value), 0) FROM RiskTransaction rt WHERE rt.toAddress = :address AND rt.timestamp BETWEEN :startTime AND :endTime")
    BigInteger sumReceivedValueByAddressAndTimeRange(@Param("address") String address,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 查找地址的唯一交易对手方数量
     */
    @Query("SELECT COUNT(DISTINCT CASE WHEN rt.fromAddress = :address THEN rt.toAddress ELSE rt.fromAddress END) FROM RiskTransaction rt WHERE rt.fromAddress = :address OR rt.toAddress = :address")
    long countUniqueCounterpartiesByAddress(@Param("address") String address);

    /**
     * 查找最近的高风险交易
     */
    @Query("SELECT rt FROM RiskTransaction rt WHERE rt.riskLevel IN ('HIGH', 'CRITICAL') AND rt.timestamp >= :since ORDER BY rt.timestamp DESC")
    List<RiskTransaction> findRecentHighRiskTransactions(@Param("since") LocalDateTime since);

    /**
     * 根据网络和时间统计风险分布
     */
    @Query("SELECT rt.riskLevel, COUNT(rt) FROM RiskTransaction rt WHERE rt.network = :network AND rt.timestamp BETWEEN :startTime AND :endTime GROUP BY rt.riskLevel")
    List<Object[]> getRiskDistributionByNetworkAndTime(@Param("network") String network,
                                                      @Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime);

    /**
     * 查找异常Gas费用交易
     */
    @Query("SELECT rt FROM RiskTransaction rt WHERE rt.gasPrice > :maxGasPrice OR rt.gasUsed > :maxGasUsed ORDER BY rt.timestamp DESC")
    Page<RiskTransaction> findAbnormalGasTransactions(@Param("maxGasPrice") BigInteger maxGasPrice,
                                                    @Param("maxGasUsed") Long maxGasUsed,
                                                    Pageable pageable);

    /**
     * 查找失败的交易
     */
    @Query("SELECT rt FROM RiskTransaction rt WHERE rt.status = 0 ORDER BY rt.timestamp DESC")
    Page<RiskTransaction> findFailedTransactions(Pageable pageable);

    /**
     * 根据区块范围查找交易
     */
    @Query("SELECT rt FROM RiskTransaction rt WHERE rt.blockNumber BETWEEN :startBlock AND :endBlock ORDER BY rt.blockNumber ASC")
    List<RiskTransaction> findByBlockRange(@Param("startBlock") Long startBlock, @Param("endBlock") Long endBlock);

    /**
     * 查找包含特定风险因子的交易
     */
    @Query("SELECT rt FROM RiskTransaction rt JOIN rt.riskFactors rf WHERE rf = :riskFactor ORDER BY rt.timestamp DESC")
    Page<RiskTransaction> findByRiskFactor(@Param("riskFactor") String riskFactor, Pageable pageable);

    /**
     * 更新交易处理状态
     */
    @Query("UPDATE RiskTransaction rt SET rt.processed = true, rt.processingTime = :processingTime WHERE rt.id = :id")
    int markAsProcessed(@Param("id") Long id, @Param("processingTime") Long processingTime);

    /**
     * 批量更新风险评分
     */
    @Query("UPDATE RiskTransaction rt SET rt.riskScore = :riskScore, rt.riskLevel = :riskLevel WHERE rt.id IN :ids")
    int batchUpdateRiskScores(@Param("ids") List<Long> ids, 
                            @Param("riskScore") BigDecimal riskScore, 
                            @Param("riskLevel") RiskTransaction.RiskLevel riskLevel);

    /**
     * 获取网络统计信息
     */
    @Query("SELECT rt.network, COUNT(rt), AVG(rt.riskScore), MAX(rt.riskScore) FROM RiskTransaction rt GROUP BY rt.network")
    List<Object[]> getNetworkStatistics();

    /**
     * 获取每日交易统计
     */
    @Query("SELECT DATE(rt.timestamp), COUNT(rt), SUM(rt.value) FROM RiskTransaction rt WHERE rt.timestamp >= :since GROUP BY DATE(rt.timestamp) ORDER BY DATE(rt.timestamp)")
    List<Object[]> getDailyTransactionStats(@Param("since") LocalDateTime since);
}