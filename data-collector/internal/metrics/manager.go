package metrics

import (
	"net/http"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/sirupsen/logrus"
)

// Manager 指标管理器
type Manager struct {
	// 计数器指标
	blocksProcessed     *prometheus.CounterVec
	transactionsProcessed *prometheus.CounterVec
	errorsTotal         *prometheus.CounterVec
	alertsGenerated     *prometheus.CounterVec

	// 直方图指标
	blockProcessingTime *prometheus.HistogramVec
	transactionProcessingTime *prometheus.HistogramVec
	kafkaPublishDuration *prometheus.HistogramVec

	// 仪表盘指标
	currentBlockNumber  *prometheus.GaugeVec
	transactionPoolSize *prometheus.GaugeVec
	connectionStatus    *prometheus.GaugeVec
	riskScoreDistribution *prometheus.HistogramVec

	registry *prometheus.Registry
}

// NewManager 创建新的指标管理器
func NewManager() *Manager {
	registry := prometheus.NewRegistry()

	manager := &Manager{
		registry: registry,

		// 计数器指标
		blocksProcessed: prometheus.NewCounterVec(
			prometheus.CounterOpts{
				Name: "web3_blocks_processed_total",
				Help: "Total number of blocks processed",
			},
			[]string{"network"},
		),

		transactionsProcessed: prometheus.NewCounterVec(
			prometheus.CounterOpts{
				Name: "web3_transactions_processed_total",
				Help: "Total number of transactions processed",
			},
			[]string{"network"},
		),

		errorsTotal: prometheus.NewCounterVec(
			prometheus.CounterOpts{
				Name: "web3_errors_total",
				Help: "Total number of errors",
			},
			[]string{"network", "type"},
		),

		alertsGenerated: prometheus.NewCounterVec(
			prometheus.CounterOpts{
				Name: "web3_alerts_generated_total",
				Help: "Total number of risk alerts generated",
			},
			[]string{"network", "level", "type"},
		),

		// 直方图指标
		blockProcessingTime: prometheus.NewHistogramVec(
			prometheus.HistogramOpts{
				Name:    "web3_block_processing_duration_seconds",
				Help:    "Time spent processing blocks",
				Buckets: prometheus.DefBuckets,
			},
			[]string{"network"},
		),

		transactionProcessingTime: prometheus.NewHistogramVec(
			prometheus.HistogramOpts{
				Name:    "web3_transaction_processing_duration_seconds",
				Help:    "Time spent processing transactions",
				Buckets: prometheus.DefBuckets,
			},
			[]string{"network"},
		),

		kafkaPublishDuration: prometheus.NewHistogramVec(
			prometheus.HistogramOpts{
				Name:    "web3_kafka_publish_duration_seconds",
				Help:    "Time spent publishing messages to Kafka",
				Buckets: prometheus.DefBuckets,
			},
			[]string{"topic"},
		),

		// 仪表盘指标
		currentBlockNumber: prometheus.NewGaugeVec(
			prometheus.GaugeOpts{
				Name: "web3_current_block_number",
				Help: "Current block number being processed",
			},
			[]string{"network"},
		),

		transactionPoolSize: prometheus.NewGaugeVec(
			prometheus.GaugeOpts{
				Name: "web3_transaction_pool_size",
				Help: "Current size of transaction processing pool",
			},
			[]string{"network"},
		),

		connectionStatus: prometheus.NewGaugeVec(
			prometheus.GaugeOpts{
				Name: "web3_connection_status",
				Help: "Connection status to blockchain networks (1=connected, 0=disconnected)",
			},
			[]string{"network", "type"},
		),

		riskScoreDistribution: prometheus.NewHistogramVec(
			prometheus.HistogramOpts{
				Name:    "web3_risk_score_distribution",
				Help:    "Distribution of risk scores",
				Buckets: []float64{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0},
			},
			[]string{"network"},
		),
	}

	// 注册所有指标
	manager.registerMetrics()

	logrus.Info("Metrics manager initialized")
	return manager
}

// registerMetrics 注册所有指标
func (m *Manager) registerMetrics() {
	m.registry.MustRegister(
		m.blocksProcessed,
		m.transactionsProcessed,
		m.errorsTotal,
		m.alertsGenerated,
		m.blockProcessingTime,
		m.transactionProcessingTime,
		m.kafkaPublishDuration,
		m.currentBlockNumber,
		m.transactionPoolSize,
		m.connectionStatus,
		m.riskScoreDistribution,
	)
}

// IncrementBlocksProcessed 增加已处理区块计数
func (m *Manager) IncrementBlocksProcessed(network string) {
	m.blocksProcessed.WithLabelValues(network).Inc()
}

// IncrementTransactionsProcessed 增加已处理交易计数
func (m *Manager) IncrementTransactionsProcessed(network string) {
	m.transactionsProcessed.WithLabelValues(network).Inc()
}

// IncrementError 增加错误计数
func (m *Manager) IncrementError(network, errorType string) {
	m.errorsTotal.WithLabelValues(network, errorType).Inc()
}

// IncrementAlerts 增加告警计数
func (m *Manager) IncrementAlerts(network, level, alertType string) {
	m.alertsGenerated.WithLabelValues(network, level, alertType).Inc()
}

// RecordBlockProcessingTime 记录区块处理时间
func (m *Manager) RecordBlockProcessingTime(network string, duration time.Duration) {
	m.blockProcessingTime.WithLabelValues(network).Observe(duration.Seconds())
}

// RecordTransactionProcessingTime 记录交易处理时间
func (m *Manager) RecordTransactionProcessingTime(network string, duration time.Duration) {
	m.transactionProcessingTime.WithLabelValues(network).Observe(duration.Seconds())
}

// RecordKafkaPublishDuration 记录Kafka发布时间
func (m *Manager) RecordKafkaPublishDuration(topic string, duration time.Duration) {
	m.kafkaPublishDuration.WithLabelValues(topic).Observe(duration.Seconds())
}

// SetCurrentBlockNumber 设置当前区块号
func (m *Manager) SetCurrentBlockNumber(network string, blockNumber uint64) {
	m.currentBlockNumber.WithLabelValues(network).Set(float64(blockNumber))
}

// SetTransactionPoolSize 设置交易池大小
func (m *Manager) SetTransactionPoolSize(network string, size int) {
	m.transactionPoolSize.WithLabelValues(network).Set(float64(size))
}

// SetConnectionStatus 设置连接状态
func (m *Manager) SetConnectionStatus(network, connType string, status bool) {
	var value float64
	if status {
		value = 1.0
	}
	m.connectionStatus.WithLabelValues(network, connType).Set(value)
}

// RecordRiskScore 记录风险评分
func (m *Manager) RecordRiskScore(network string, score float64) {
	m.riskScoreDistribution.WithLabelValues(network).Observe(score)
}

// GetStats 获取统计信息
func (m *Manager) GetStats() map[string]interface{} {
	stats := make(map[string]interface{})

	// 这里可以添加从指标中获取统计信息的逻辑
	// 例如：通过查询指标值来构建统计信息

	return stats
}

// Handler 返回Prometheus HTTP处理器
func (m *Manager) Handler() http.Handler {
	return promhttp.HandlerFor(m.registry, promhttp.HandlerOpts{})
}

// Reset 重置所有指标（主要用于测试）
func (m *Manager) Reset() {
	m.registry = prometheus.NewRegistry()
	m.registerMetrics()
	logrus.Info("Metrics reset")
}

// 自定义指标收集器

// NetworkCollector 网络状态收集器
type NetworkCollector struct {
	networks map[string]*NetworkStatus
}

// NetworkStatus 网络状态
type NetworkStatus struct {
	Name           string
	IsConnected    bool
	LastBlockTime  time.Time
	ErrorCount     int64
	BlocksPerHour  float64
	AvgProcessTime float64
}

// NewNetworkCollector 创建网络收集器
func NewNetworkCollector() *NetworkCollector {
	return &NetworkCollector{
		networks: make(map[string]*NetworkStatus),
	}
}

// UpdateNetworkStatus 更新网络状态
func (nc *NetworkCollector) UpdateNetworkStatus(name string, status *NetworkStatus) {
	nc.networks[name] = status
}

// GetNetworkStatus 获取网络状态
func (nc *NetworkCollector) GetNetworkStatus(name string) (*NetworkStatus, bool) {
	status, exists := nc.networks[name]
	return status, exists
}

// GetAllNetworkStatus 获取所有网络状态
func (nc *NetworkCollector) GetAllNetworkStatus() map[string]*NetworkStatus {
	return nc.networks
}

// PerformanceMetrics 性能指标
type PerformanceMetrics struct {
	ProcessedTxPerSecond    float64
	ProcessedBlocksPerHour  float64
	AvgBlockProcessingTime  float64
	AvgTxProcessingTime     float64
	ErrorRate               float64
	AlertsPerHour           float64
	KafkaPublishLatency     float64
	DatabaseWriteLatency    float64
}

// CalculatePerformanceMetrics 计算性能指标
func (m *Manager) CalculatePerformanceMetrics(timeWindow time.Duration) *PerformanceMetrics {
	// 这里应该从Prometheus查询API获取实际的指标数据
	// 为了简化，这里返回模拟数据
	return &PerformanceMetrics{
		ProcessedTxPerSecond:   100.5,
		ProcessedBlocksPerHour: 300.0,
		AvgBlockProcessingTime: 0.5,
		AvgTxProcessingTime:    0.001,
		ErrorRate:              0.01,
		AlertsPerHour:          5.0,
		KafkaPublishLatency:    0.01,
		DatabaseWriteLatency:   0.005,
	}
}

// HealthCheck 健康检查
func (m *Manager) HealthCheck() error {
	// 检查指标收集是否正常工作
	// 这里可以添加具体的健康检查逻辑
	return nil
}