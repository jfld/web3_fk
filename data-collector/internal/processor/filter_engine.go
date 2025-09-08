package processor

import (
	"math/big"
	"strings"

	"web3-data-collector/internal/config"
	"web3-data-collector/internal/models"
)

// FilterEngine 过滤引擎
type FilterEngine struct {
	config           config.FilterRulesConfig
	minValueWei      *big.Int
	excludeContracts map[string]bool
	includeAddresses map[string]bool
}

// NewFilterEngine 创建新的过滤引擎
func NewFilterEngine(config config.FilterRulesConfig) *FilterEngine {
	fe := &FilterEngine{
		config:           config,
		excludeContracts: make(map[string]bool),
		includeAddresses: make(map[string]bool),
	}

	// 解析最小价值阈值
	if config.MinValueWei != "" {
		minValue := new(big.Int)
		if _, ok := minValue.SetString(config.MinValueWei, 10); ok {
			fe.minValueWei = minValue
		}
	}

	// 初始化排除合约列表
	for _, contract := range config.ExcludeContracts {
		fe.excludeContracts[strings.ToLower(contract)] = true
	}

	// 初始化包含地址列表
	for _, address := range config.IncludeAddresses {
		fe.includeAddresses[strings.ToLower(address)] = true
	}

	return fe
}

// ShouldProcess 判断是否应该处理交易
func (fe *FilterEngine) ShouldProcess(tx *models.Transaction) *models.FilterResult {
	result := &models.FilterResult{
		ShouldProcess:   true,
		FilteredReasons: []string{},
		RiskScore:       0.0,
	}

	// 如果地址在包含列表中，优先处理
	if fe.isIncludedAddress(tx) {
		result.RiskScore += 0.1
		return result
	}

	// 检查最小价值阈值
	if fe.minValueWei != nil && tx.Value.Cmp(fe.minValueWei) < 0 {
		result.ShouldProcess = false
		result.FilteredReasons = append(result.FilteredReasons, "below_min_value")
	}

	// 检查排除合约
	if fe.isExcludedContract(tx) {
		result.ShouldProcess = false
		result.FilteredReasons = append(result.FilteredReasons, "excluded_contract")
	}

	// 检查零值交易（除非是合约调用）
	if tx.Value.Cmp(big.NewInt(0)) == 0 && !tx.IsContractCall {
		result.ShouldProcess = false
		result.FilteredReasons = append(result.FilteredReasons, "zero_value_non_contract")
	}

	// 检查失败的交易
	if tx.Status == 0 {
		result.ShouldProcess = false
		result.FilteredReasons = append(result.FilteredReasons, "failed_transaction")
	}

	// 检查内部交易（创建合约）
	if tx.ToAddress == "" && len(tx.InputData) == 0 {
		result.ShouldProcess = false
		result.FilteredReasons = append(result.FilteredReasons, "empty_transaction")
	}

	// 检查垃圾交易（非常低的Gas费用）
	if fe.isSpamTransaction(tx) {
		result.ShouldProcess = false
		result.FilteredReasons = append(result.FilteredReasons, "spam_transaction")
	}

	// 检查重复交易
	if fe.isDuplicateTransaction(tx) {
		result.ShouldProcess = false
		result.FilteredReasons = append(result.FilteredReasons, "duplicate_transaction")
	}

	return result
}

// isIncludedAddress 检查是否为包含地址
func (fe *FilterEngine) isIncludedAddress(tx *models.Transaction) bool {
	return fe.includeAddresses[strings.ToLower(tx.FromAddress)] ||
		fe.includeAddresses[strings.ToLower(tx.ToAddress)]
}

// isExcludedContract 检查是否为排除合约
func (fe *FilterEngine) isExcludedContract(tx *models.Transaction) bool {
	if tx.ToAddress == "" {
		return false
	}
	return fe.excludeContracts[strings.ToLower(tx.ToAddress)]
}

// isSpamTransaction 检查是否为垃圾交易
func (fe *FilterEngine) isSpamTransaction(tx *models.Transaction) bool {
	// 检查非常低的Gas价格（小于1 Gwei）
	oneGwei := big.NewInt(1000000000) // 1 Gwei = 10^9 wei
	if tx.GasPrice.Cmp(oneGwei) < 0 {
		return true
	}

	// 检查异常的Gas限制
	if tx.Gas < 21000 { // 基础交易至少需要21000 gas
		return true
	}

	return false
}

// isDuplicateTransaction 检查是否为重复交易
func (fe *FilterEngine) isDuplicateTransaction(tx *models.Transaction) bool {
	// 这里可以实现基于Redis的重复检测逻辑
	// 简单实现：检查相同nonce的交易
	// 在实际应用中，应该查询Redis或数据库
	return false
}

// AddExcludeContract 添加排除合约
func (fe *FilterEngine) AddExcludeContract(contractAddress string) {
	fe.excludeContracts[strings.ToLower(contractAddress)] = true
}

// RemoveExcludeContract 移除排除合约
func (fe *FilterEngine) RemoveExcludeContract(contractAddress string) {
	delete(fe.excludeContracts, strings.ToLower(contractAddress))
}

// AddIncludeAddress 添加包含地址
func (fe *FilterEngine) AddIncludeAddress(address string) {
	fe.includeAddresses[strings.ToLower(address)] = true
}

// RemoveIncludeAddress 移除包含地址
func (fe *FilterEngine) RemoveIncludeAddress(address string) {
	delete(fe.includeAddresses, strings.ToLower(address))
}

// SetMinValueThreshold 设置最小价值阈值
func (fe *FilterEngine) SetMinValueThreshold(threshold *big.Int) {
	fe.minValueWei = threshold
}

// GetFilterStats 获取过滤统计信息
func (fe *FilterEngine) GetFilterStats() map[string]interface{} {
	return map[string]interface{}{
		"min_value_wei":        fe.minValueWei.String(),
		"exclude_contracts":    len(fe.excludeContracts),
		"include_addresses":    len(fe.includeAddresses),
		"exclude_contract_list": fe.getExcludeContractList(),
		"include_address_list":  fe.getIncludeAddressList(),
	}
}

// getExcludeContractList 获取排除合约列表
func (fe *FilterEngine) getExcludeContractList() []string {
	contracts := make([]string, 0, len(fe.excludeContracts))
	for contract := range fe.excludeContracts {
		contracts = append(contracts, contract)
	}
	return contracts
}

// getIncludeAddressList 获取包含地址列表
func (fe *FilterEngine) getIncludeAddressList() []string {
	addresses := make([]string, 0, len(fe.includeAddresses))
	for address := range fe.includeAddresses {
		addresses = append(addresses, address)
	}
	return addresses
}