package publisher

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"web3-data-collector/internal/config"
	"web3-data-collector/internal/models"

	"github.com/segmentio/kafka-go"
	"github.com/sirupsen/logrus"
)

// KafkaPublisher Kafka消息发布器
type KafkaPublisher struct {
	config      config.KafkaConfig
	writers     map[string]*kafka.Writer
	batchSize   int
	batchTimeout time.Duration
}

// NewKafkaPublisher 创建新的Kafka发布器
func NewKafkaPublisher(config config.KafkaConfig) (*KafkaPublisher, error) {
	batchTimeout, err := time.ParseDuration(config.Producer.BatchTimeout)
	if err != nil {
		batchTimeout = 1 * time.Second
	}

	publisher := &KafkaPublisher{
		config:       config,
		writers:      make(map[string]*kafka.Writer),
		batchSize:    config.Producer.BatchSize,
		batchTimeout: batchTimeout,
	}

	// 创建各主题的写入器
	if err := publisher.createWriters(); err != nil {
		return nil, fmt.Errorf("failed to create Kafka writers: %w", err)
	}

	return publisher, nil
}

// createWriters 创建Kafka写入器
func (kp *KafkaPublisher) createWriters() error {
	topics := map[string]string{
		"transactions": kp.config.Topics.Transactions,
		"blocks":       kp.config.Topics.Blocks,
		"alerts":       kp.config.Topics.Alerts,
	}

	for name, topic := range topics {
		writer := &kafka.Writer{
			Addr:         kafka.TCP(kp.config.Brokers...),
			Topic:        topic,
			Balancer:     &kafka.LeastBytes{},
			BatchSize:    kp.batchSize,
			BatchTimeout: kp.batchTimeout,
			RequiredAcks: kafka.RequireOne,
			Async:        true,
			ErrorLogger:  kafka.LoggerFunc(logrus.Errorf),
		}

		kp.writers[name] = writer
		logrus.Infof("Created Kafka writer for topic: %s", topic)
	}

	return nil
}

// PublishTransaction 发布交易数据
func (kp *KafkaPublisher) PublishTransaction(tx *models.Transaction) error {
	writer, exists := kp.writers["transactions"]
	if !exists {
		return fmt.Errorf("transaction writer not found")
	}

	// 序列化交易数据
	data, err := json.Marshal(tx)
	if err != nil {
		return fmt.Errorf("failed to marshal transaction: %w", err)
	}

	// 创建消息
	message := kafka.Message{
		Key:   []byte(tx.Hash),
		Value: data,
		Headers: []kafka.Header{
			{Key: "network", Value: []byte(tx.Network)},
			{Key: "block_number", Value: []byte(fmt.Sprintf("%d", tx.BlockNumber))},
			{Key: "timestamp", Value: []byte(fmt.Sprintf("%d", tx.Timestamp.Unix()))},
			{Key: "message_type", Value: []byte("transaction")},
		},
		Time: tx.Timestamp,
	}

	// 发送消息
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := writer.WriteMessages(ctx, message); err != nil {
		return fmt.Errorf("failed to write transaction message: %w", err)
	}

	logrus.Debugf("Published transaction %s to Kafka", tx.Hash)
	return nil
}

// PublishBlock 发布区块数据
func (kp *KafkaPublisher) PublishBlock(block *models.Block) error {
	writer, exists := kp.writers["blocks"]
	if !exists {
		return fmt.Errorf("block writer not found")
	}

	// 序列化区块数据
	data, err := json.Marshal(block)
	if err != nil {
		return fmt.Errorf("failed to marshal block: %w", err)
	}

	// 创建消息
	message := kafka.Message{
		Key:   []byte(fmt.Sprintf("%d", block.Number)),
		Value: data,
		Headers: []kafka.Header{
			{Key: "network", Value: []byte(block.Network)},
			{Key: "block_number", Value: []byte(fmt.Sprintf("%d", block.Number))},
			{Key: "timestamp", Value: []byte(fmt.Sprintf("%d", block.Timestamp.Unix()))},
			{Key: "message_type", Value: []byte("block")},
			{Key: "tx_count", Value: []byte(fmt.Sprintf("%d", block.TxCount))},
		},
		Time: block.Timestamp,
	}

	// 发送消息
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := writer.WriteMessages(ctx, message); err != nil {
		return fmt.Errorf("failed to write block message: %w", err)
	}

	logrus.Debugf("Published block %d to Kafka", block.Number)
	return nil
}

// PublishAlert 发布告警数据
func (kp *KafkaPublisher) PublishAlert(alert *models.RiskAlert) error {
	writer, exists := kp.writers["alerts"]
	if !exists {
		return fmt.Errorf("alert writer not found")
	}

	// 序列化告警数据
	data, err := json.Marshal(alert)
	if err != nil {
		return fmt.Errorf("failed to marshal alert: %w", err)
	}

	// 创建消息
	message := kafka.Message{
		Key:   []byte(alert.ID),
		Value: data,
		Headers: []kafka.Header{
			{Key: "alert_type", Value: []byte(alert.Type)},
			{Key: "alert_level", Value: []byte(alert.Level)},
			{Key: "network", Value: []byte(alert.Network)},
			{Key: "timestamp", Value: []byte(fmt.Sprintf("%d", alert.Timestamp.Unix()))},
			{Key: "message_type", Value: []byte("alert")},
			{Key: "risk_score", Value: []byte(fmt.Sprintf("%.2f", alert.RiskScore))},
		},
		Time: alert.Timestamp,
	}

	// 发送消息
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := writer.WriteMessages(ctx, message); err != nil {
		return fmt.Errorf("failed to write alert message: %w", err)
	}

	logrus.Infof("Published alert %s (Level: %s) to Kafka", alert.ID, alert.Level)
	return nil
}

// PublishBatch 批量发布消息
func (kp *KafkaPublisher) PublishBatch(topicName string, messages []kafka.Message) error {
	writer, exists := kp.writers[topicName]
	if !exists {
		return fmt.Errorf("writer for topic %s not found", topicName)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := writer.WriteMessages(ctx, messages...); err != nil {
		return fmt.Errorf("failed to write batch messages: %w", err)
	}

	logrus.Debugf("Published %d messages to topic %s", len(messages), topicName)
	return nil
}

// PublishTransactionBatch 批量发布交易
func (kp *KafkaPublisher) PublishTransactionBatch(transactions []*models.Transaction) error {
	messages := make([]kafka.Message, 0, len(transactions))

	for _, tx := range transactions {
		data, err := json.Marshal(tx)
		if err != nil {
			logrus.Errorf("Failed to marshal transaction %s: %v", tx.Hash, err)
			continue
		}

		message := kafka.Message{
			Key:   []byte(tx.Hash),
			Value: data,
			Headers: []kafka.Header{
				{Key: "network", Value: []byte(tx.Network)},
				{Key: "block_number", Value: []byte(fmt.Sprintf("%d", tx.BlockNumber))},
				{Key: "timestamp", Value: []byte(fmt.Sprintf("%d", tx.Timestamp.Unix()))},
				{Key: "message_type", Value: []byte("transaction")},
			},
			Time: tx.Timestamp,
		}

		messages = append(messages, message)
	}

	return kp.PublishBatch("transactions", messages)
}

// GetStats 获取发布器统计信息
func (kp *KafkaPublisher) GetStats() map[string]interface{} {
	stats := make(map[string]interface{})

	for name, writer := range kp.writers {
		writerStats := writer.Stats()
		stats[name] = map[string]interface{}{
			"writes":      writerStats.Writes,
			"messages":    writerStats.Messages,
			"bytes":       writerStats.Bytes,
			"errors":      writerStats.Errors,
			"batch_time":  writerStats.BatchTime.String(),
			"batch_size":  writerStats.BatchSize,
		}
	}

	return stats
}

// Flush 刷新所有写入器的缓冲区
func (kp *KafkaPublisher) Flush() error {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	for name, writer := range kp.writers {
		if err := writer.Close(); err != nil {
			logrus.Errorf("Error flushing writer %s: %v", name, err)
			return err
		}
	}

	return nil
}

// Close 关闭所有Kafka写入器
func (kp *KafkaPublisher) Close() error {
	var lastErr error

	for name, writer := range kp.writers {
		if err := writer.Close(); err != nil {
			logrus.Errorf("Error closing writer %s: %v", name, err)
			lastErr = err
		}
	}

	logrus.Info("Kafka publisher closed")
	return lastErr
}

// CreateTopicIfNotExists 创建主题（如果不存在）
func (kp *KafkaPublisher) CreateTopicIfNotExists(topicName string, numPartitions int, replicationFactor int) error {
	// 这里可以添加创建主题的逻辑
	// 在生产环境中，通常由管理员预先创建主题
	logrus.Infof("Topic creation for %s would be handled by Kafka admin", topicName)
	return nil
}

// HealthCheck 健康检查
func (kp *KafkaPublisher) HealthCheck() error {
	// 检查所有写入器的连接状态
	for name, writer := range kp.writers {
		// 尝试发送一个测试消息
		testMessage := kafka.Message{
			Key:   []byte("health_check"),
			Value: []byte("ping"),
			Headers: []kafka.Header{
				{Key: "type", Value: []byte("health_check")},
			},
		}

		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		err := writer.WriteMessages(ctx, testMessage)
		cancel()

		if err != nil {
			return fmt.Errorf("health check failed for writer %s: %w", name, err)
		}
	}

	return nil
}