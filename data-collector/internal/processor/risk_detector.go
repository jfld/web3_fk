package processor

import (
	"math/big"
	"strings"
	"time"

	"web3-data-collector/internal/models"
)

// RiskDetector 风险检测器
type RiskDetector struct {
	blacklistedAddresses map[string]bool
	suspiciousContracts  map[string]bool
	highValueThreshold   *big.Int
}

// RiskResult 风险检测结果
type RiskResult struct {
	RiskDetected bool     `json:"risk_detected"`
	RiskScore    float64  `json:"risk_score"`
	RiskLevel    string   `json:"risk_level"`
	RiskType     string   `json:"risk_type"`
	RiskFactors  []string `json:"risk_factors"`
	Title        string   `json:"title"`
	Description  string   `json:"description"`
}

// NewRiskDetector 创建新的风险检测器
func NewRiskDetector() *RiskDetector {
	// 初始化高价值阈值 (1000 ETH)
	highValueThreshold := new(big.Int)
	highValueThreshold.SetString("1000000000000000000000", 10) // 1000 * 10^18 wei

	return &RiskDetector{
		blacklistedAddresses: initBlacklistedAddresses(),
		suspiciousContracts:  initSuspiciousContracts(),
		highValueThreshold:   highValueThreshold,
	}
}

// AnalyzeTransaction 分析交易风险
func (rd *RiskDetector) AnalyzeTransaction(tx *models.Transaction) *RiskResult {
	result := &RiskResult{
		RiskDetected: false,
		RiskScore:    0.0,
		RiskLevel:    "LOW",
		RiskFactors:  []string{},
	}

	// 检查黑名单地址
	if rd.checkBlacklistedAddress(tx) {
		result.RiskDetected = true
		result.RiskScore += 0.8
		result.RiskFactors = append(result.RiskFactors, "blacklisted_address")
		result.RiskType = "BLACKLIST"
		result.Title = "黑名单地址交易"
		result.Description = "检测到与黑名单地址相关的交易"
	}

	// 检查高价值交易
	if rd.checkHighValueTransaction(tx) {
		result.RiskDetected = true
		result.RiskScore += 0.6
		result.RiskFactors = append(result.RiskFactors, "high_value_transaction")
		if result.RiskType == "" {
			result.RiskType = "HIGH_VALUE"
			result.Title = "大额交易告警"
			result.Description = "检测到大额资金转移"
		}
	}

	// 检查可疑合约
	if rd.checkSuspiciousContract(tx) {
		result.RiskDetected = true
		result.RiskScore += 0.7
		result.RiskFactors = append(result.RiskFactors, "suspicious_contract")
		if result.RiskType == "" {
			result.RiskType = "SUSPICIOUS_CONTRACT"
			result.Title = "可疑合约交互"
			result.Description = "检测到与可疑智能合约的交互"
		}
	}

	// 检查异常Gas费用
	if rd.checkAbnormalGasFee(tx) {
		result.RiskScore += 0.3
		result.RiskFactors = append(result.RiskFactors, "abnormal_gas_fee")
	}

	// 检查异常时间
	if rd.checkAbnormalTime(tx) {
		result.RiskScore += 0.2
		result.RiskFactors = append(result.RiskFactors, "abnormal_time")
	}

	// 检查自转账
	if rd.checkSelfTransfer(tx) {
		result.RiskScore += 0.1
		result.RiskFactors = append(result.RiskFactors, "self_transfer")
	}

	// 检查零值交易
	if rd.checkZeroValueTransaction(tx) {
		result.RiskScore += 0.1
		result.RiskFactors = append(result.RiskFactors, "zero_value_transaction")
	}

	// 计算最终风险等级
	result.RiskLevel = rd.calculateRiskLevel(result.RiskScore)

	// 确保有风险类型
	if result.RiskDetected && result.RiskType == "" {
		result.RiskType = "GENERAL"
		result.Title = "一般风险交易"
		result.Description = "检测到潜在风险因素"
	}

	return result
}

// checkBlacklistedAddress 检查黑名单地址
func (rd *RiskDetector) checkBlacklistedAddress(tx *models.Transaction) bool {
	return rd.blacklistedAddresses[strings.ToLower(tx.FromAddress)] ||
		rd.blacklistedAddresses[strings.ToLower(tx.ToAddress)]
}

// checkHighValueTransaction 检查高价值交易
func (rd *RiskDetector) checkHighValueTransaction(tx *models.Transaction) bool {
	return tx.Value.Cmp(rd.highValueThreshold) > 0
}

// checkSuspiciousContract 检查可疑合约
func (rd *RiskDetector) checkSuspiciousContract(tx *models.Transaction) bool {
	if tx.ToAddress == "" {
		return false
	}
	return rd.suspiciousContracts[strings.ToLower(tx.ToAddress)]
}

// checkAbnormalGasFee 检查异常Gas费用
func (rd *RiskDetector) checkAbnormalGasFee(tx *models.Transaction) bool {
	// 计算总Gas费用
	totalGasFee := new(big.Int).Mul(tx.GasPrice, big.NewInt(int64(tx.Gas)))
	
	// 异常高的Gas费用阈值 (100 ETH)
	abnormalThreshold := new(big.Int)
	abnormalThreshold.SetString("100000000000000000000", 10) // 100 * 10^18 wei
	
	return totalGasFee.Cmp(abnormalThreshold) > 0
}

// checkAbnormalTime 检查异常时间
func (rd *RiskDetector) checkAbnormalTime(tx *models.Transaction) bool {
	hour := tx.Timestamp.Hour()
	// 凌晨2点到早上6点视为异常时间
	return hour >= 2 && hour <= 6
}

// checkSelfTransfer 检查自转账
func (rd *RiskDetector) checkSelfTransfer(tx *models.Transaction) bool {
	return strings.EqualFold(tx.FromAddress, tx.ToAddress)
}

// checkZeroValueTransaction 检查零值交易
func (rd *RiskDetector) checkZeroValueTransaction(tx *models.Transaction) bool {
	return tx.Value.Cmp(big.NewInt(0)) == 0 && tx.IsContractCall
}

// calculateRiskLevel 计算风险等级
func (rd *RiskDetector) calculateRiskLevel(score float64) string {
	if score >= 0.8 {
		return "CRITICAL"
	} else if score >= 0.6 {
		return "HIGH"
	} else if score >= 0.4 {
		return "MEDIUM"
	} else if score >= 0.2 {
		return "LOW"
	}
	return "INFO"
}

// initBlacklistedAddresses 初始化黑名单地址
func initBlacklistedAddresses() map[string]bool {
	// 这里应该从数据库或外部API加载真实的黑名单
	blacklist := map[string]bool{
		// 示例黑名单地址（小写）
		"0x7f367cc41522ce07553e823bf3be79a889debe1b": true, // 币安黑客地址示例
		"0x098b716b8aaf21512996dc57eb0615e2383e2f96": true, // CreamFinance黑客
		"0x5d4b302506645c37ff133b98c4b50a5ae14841659738d6d733d59d0d217a93bf": true, // 其他已知恶意地址
	}
	return blacklist
}

// initSuspiciousContracts 初始化可疑合约
func initSuspiciousContracts() map[string]bool {
	// 这里应该从数据库加载已知的可疑合约地址
	suspicious := map[string]bool{
		// 示例可疑合约地址（小写）
		"0x1234567890abcdef1234567890abcdef12345678": true, // 混币器合约
		"0xabcdef1234567890abcdef1234567890abcdef12": true, // 钓鱼合约
	}
	return suspicious
}

// UpdateBlacklist 更新黑名单
func (rd *RiskDetector) UpdateBlacklist(addresses []string) {
	for _, addr := range addresses {
		rd.blacklistedAddresses[strings.ToLower(addr)] = true
	}
}

// RemoveFromBlacklist 从黑名单移除
func (rd *RiskDetector) RemoveFromBlacklist(address string) {
	delete(rd.blacklistedAddresses, strings.ToLower(address))
}

// IsBlacklisted 检查地址是否在黑名单中
func (rd *RiskDetector) IsBlacklisted(address string) bool {
	return rd.blacklistedAddresses[strings.ToLower(address)]
}

// GetBlacklistSize 获取黑名单大小
func (rd *RiskDetector) GetBlacklistSize() int {
	return len(rd.blacklistedAddresses)
}

// SetHighValueThreshold 设置高价值阈值
func (rd *RiskDetector) SetHighValueThreshold(threshold *big.Int) {
	rd.highValueThreshold = threshold
}