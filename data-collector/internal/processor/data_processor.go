package processor

import (
	"encoding/json"
	"fmt"
	"math/big"
	"strings"
	"time"

	"web3-data-collector/internal/config"
	"web3-data-collector/internal/database"
	"web3-data-collector/internal/metrics"
	"web3-data-collector/internal/models"
	"web3-data-collector/internal/publisher"

	"github.com/sirupsen/logrus"
)

// DataProcessor 数据处理器
type DataProcessor struct {
	config           config.DataProcessingConfig
	kafkaPublisher   *publisher.KafkaPublisher
	influxClient     *database.InfluxDBClient
	redisClient      *database.RedisClient
	metricsManager   *metrics.Manager
	riskDetector     *RiskDetector
	filterEngine     *FilterEngine
}

// NewDataProcessor 创建新的数据处理器
func NewDataProcessor(
	config config.DataProcessingConfig,
	kafkaPublisher *publisher.KafkaPublisher,
	influxClient *database.InfluxDBClient,
	redisClient *database.RedisClient,
	metricsManager *metrics.Manager,
) *DataProcessor {
	return &DataProcessor{
		config:         config,
		kafkaPublisher: kafkaPublisher,
		influxClient:   influxClient,
		redisClient:    redisClient,
		metricsManager: metricsManager,
		riskDetector:   NewRiskDetector(),
		filterEngine:   NewFilterEngine(config.FilterRules),
	}
}

// ProcessBlock 处理区块数据
func (dp *DataProcessor) ProcessBlock(block *models.Block) error {
	startTime := time.Now()

	logrus.Debugf("Processing block %d with %d transactions", block.Number, len(block.Transactions))

	// 发布区块数据到Kafka
	if err := dp.kafkaPublisher.PublishBlock(block); err != nil {
		logrus.Errorf("Failed to publish block to Kafka: %v", err)
		dp.metricsManager.IncrementError(block.Network, "kafka_publish_block_error")
	}

	// 存储区块指标到InfluxDB
	if err := dp.storeBlockMetrics(block); err != nil {
		logrus.Errorf("Failed to store block metrics: %v", err)
		dp.metricsManager.IncrementError(block.Network, "influxdb_store_error")
	}

	// 处理区块中的每个交易
	for _, tx := range block.Transactions {
		if err := dp.ProcessTransaction(&tx); err != nil {
			logrus.Errorf("Failed to process transaction %s: %v", tx.Hash, err)
			continue
		}
	}

	// 更新Redis中的最新区块信息
	if err := dp.updateLatestBlockInfo(block); err != nil {
		logrus.Errorf("Failed to update latest block info: %v", err)
	}

	processingTime := time.Since(startTime)
	dp.metricsManager.RecordBlockProcessingTime(block.Network, processingTime)

	logrus.Debugf("Block %d processed in %v", block.Number, processingTime)

	return nil
}

// ProcessTransaction 处理单个交易
func (dp *DataProcessor) ProcessTransaction(tx *models.Transaction) error {
	startTime := time.Now()

	// 应用过滤规则
	filterResult := dp.filterEngine.ShouldProcess(tx)
	if !filterResult.ShouldProcess {
		logrus.Debugf("Transaction %s filtered out: %s", tx.Hash, strings.Join(filterResult.FilteredReasons, ", "))
		return nil
	}

	// 发布交易数据到Kafka
	if err := dp.kafkaPublisher.PublishTransaction(tx); err != nil {
		logrus.Errorf("Failed to publish transaction to Kafka: %v", err)
		dp.metricsManager.IncrementError(tx.Network, "kafka_publish_tx_error")
	}

	// 存储交易指标到InfluxDB
	if err := dp.storeTransactionMetrics(tx); err != nil {
		logrus.Errorf("Failed to store transaction metrics: %v", err)
	}

	// 风险检测
	riskResult := dp.riskDetector.AnalyzeTransaction(tx)
	if riskResult.RiskDetected {
		alert := dp.createRiskAlert(tx, riskResult)
		if err := dp.kafkaPublisher.PublishAlert(alert); err != nil {
			logrus.Errorf("Failed to publish risk alert: %v", err)
		}
		
		// 记录高风险交易到Redis
		if err := dp.recordHighRiskTransaction(tx, riskResult); err != nil {
			logrus.Errorf("Failed to record high risk transaction: %v", err)
		}
	}

	// 更新地址统计信息
	if err := dp.updateAddressStats(tx); err != nil {
		logrus.Errorf("Failed to update address stats: %v", err)
	}

	processingTime := time.Since(startTime)
	dp.metricsManager.RecordTransactionProcessingTime(tx.Network, processingTime)
	dp.metricsManager.IncrementTransactionsProcessed(tx.Network)

	return nil
}

// storeBlockMetrics 存储区块指标到InfluxDB
func (dp *DataProcessor) storeBlockMetrics(block *models.Block) error {
	point := map[string]interface{}{
		"number":      block.Number,
		"tx_count":    block.TxCount,
		"gas_used":    block.GasUsed,
		"gas_limit":   block.GasLimit,
		"size":        block.Size,
		"difficulty":  block.Difficulty.String(),
	}

	if block.BaseFeePerGas != nil {
		point["base_fee"] = block.BaseFeePerGas.String()
	}

	tags := map[string]string{
		"network": block.Network,
		"miner":   block.Miner,
	}

	return dp.influxClient.WritePoint("blocks", tags, point, block.Timestamp)
}

// storeTransactionMetrics 存储交易指标到InfluxDB
func (dp *DataProcessor) storeTransactionMetrics(tx *models.Transaction) error {
	point := map[string]interface{}{
		"value":           tx.Value.String(),
		"gas":             tx.Gas,
		"gas_price":       tx.GasPrice.String(),
		"gas_used":        tx.GasUsed,
		"is_contract":     tx.IsContractCall,
		"is_token":        tx.IsTokenTransfer,
		"transaction_type": tx.TransactionType,
	}

	if tx.MaxFeePerGas != nil {
		point["max_fee_per_gas"] = tx.MaxFeePerGas.String()
	}

	if tx.MaxPriorityFeePerGas != nil {
		point["max_priority_fee_per_gas"] = tx.MaxPriorityFeePerGas.String()
	}

	tags := map[string]string{
		"network":      tx.Network,
		"from_address": tx.FromAddress,
		"to_address":   tx.ToAddress,
	}

	return dp.influxClient.WritePoint("transactions", tags, point, tx.Timestamp)
}

// updateLatestBlockInfo 更新最新区块信息到Redis
func (dp *DataProcessor) updateLatestBlockInfo(block *models.Block) error {
	key := fmt.Sprintf("latest_block:%s", block.Network)
	data := map[string]interface{}{
		"number":    block.Number,
		"hash":      block.Hash,
		"timestamp": block.Timestamp.Unix(),
		"tx_count":  block.TxCount,
	}

	return dp.redisClient.HMSet(key, data)
}

// updateAddressStats 更新地址统计信息
func (dp *DataProcessor) updateAddressStats(tx *models.Transaction) error {
	// 更新发送方地址统计
	if err := dp.updateSingleAddressStats(tx.FromAddress, tx, true); err != nil {
		return err
	}

	// 更新接收方地址统计
	if tx.ToAddress != "" {
		if err := dp.updateSingleAddressStats(tx.ToAddress, tx, false); err != nil {
			return err
		}
	}

	return nil
}

// updateSingleAddressStats 更新单个地址统计
func (dp *DataProcessor) updateSingleAddressStats(address string, tx *models.Transaction, isSender bool) error {
	key := fmt.Sprintf("address_stats:%s:%s", tx.Network, address)
	
	// 获取当前统计
	stats, err := dp.redisClient.HGetAll(key)
	if err != nil {
		// 如果不存在，创建新的统计
		stats = make(map[string]string)
	}

	// 更新统计信息
	if isSender {
		// 更新发送统计
		if err := dp.incrementCounterInMap(stats, "sent_count"); err != nil {
			return err
		}
		if err := dp.addValueInMap(stats, "sent_volume", tx.Value); err != nil {
			return err
		}
	} else {
		// 更新接收统计
		if err := dp.incrementCounterInMap(stats, "received_count"); err != nil {
			return err
		}
		if err := dp.addValueInMap(stats, "received_volume", tx.Value); err != nil {
			return err
		}
	}

	// 更新最后活动时间
	stats["last_activity"] = fmt.Sprintf("%d", tx.Timestamp.Unix())

	// 设置首次见到时间（如果不存在）
	if _, exists := stats["first_seen"]; !exists {
		stats["first_seen"] = fmt.Sprintf("%d", tx.Timestamp.Unix())
	}

	// 保存到Redis
	return dp.redisClient.HMSetString(key, stats)
}

// incrementCounterInMap 在map中递增计数器
func (dp *DataProcessor) incrementCounterInMap(stats map[string]string, key string) error {
	currentValue := int64(0)
	if val, exists := stats[key]; exists {
		if parsed, err := parseInt64(val); err == nil {
			currentValue = parsed
		}
	}
	stats[key] = fmt.Sprintf("%d", currentValue+1)
	return nil
}

// addValueInMap 在map中累加数值
func (dp *DataProcessor) addValueInMap(stats map[string]string, key string, value *big.Int) error {
	currentValue := big.NewInt(0)
	if val, exists := stats[key]; exists {
		if parsed, ok := currentValue.SetString(val, 10); ok {
			currentValue = parsed
		}
	}
	currentValue = currentValue.Add(currentValue, value)
	stats[key] = currentValue.String()
	return nil
}

// createRiskAlert 创建风险告警
func (dp *DataProcessor) createRiskAlert(tx *models.Transaction, riskResult *RiskResult) *models.RiskAlert {
	return &models.RiskAlert{
		ID:              fmt.Sprintf("alert_%s_%d", tx.Hash, time.Now().UnixNano()),
		Type:            riskResult.RiskType,
		Level:           riskResult.RiskLevel,
		Title:           riskResult.Title,
		Description:     riskResult.Description,
		TransactionHash: tx.Hash,
		Address:         tx.FromAddress,
		Network:         tx.Network,
		RiskScore:       riskResult.RiskScore,
		RiskFactors:     riskResult.RiskFactors,
		Metadata: map[string]interface{}{
			"block_number": tx.BlockNumber,
			"value":        tx.Value.String(),
			"gas_price":    tx.GasPrice.String(),
			"to_address":   tx.ToAddress,
		},
		Timestamp: tx.Timestamp,
		Status:    "ACTIVE",
	}
}

// recordHighRiskTransaction 记录高风险交易
func (dp *DataProcessor) recordHighRiskTransaction(tx *models.Transaction, riskResult *RiskResult) error {
	key := fmt.Sprintf("high_risk_tx:%s", tx.Network)
	
	record := map[string]interface{}{
		"hash":         tx.Hash,
		"from_address": tx.FromAddress,
		"to_address":   tx.ToAddress,
		"value":        tx.Value.String(),
		"risk_score":   riskResult.RiskScore,
		"risk_type":    riskResult.RiskType,
		"timestamp":    tx.Timestamp.Unix(),
	}

	data, err := json.Marshal(record)
	if err != nil {
		return err
	}

	// 使用交易哈希作为分数，时间戳作为值
	return dp.redisClient.ZAdd(key, float64(tx.Timestamp.Unix()), string(data))
}

// 辅助函数
func parseInt64(s string) (int64, error) {
	var result int64
	_, err := fmt.Sscanf(s, "%d", &result)
	return result, err
}