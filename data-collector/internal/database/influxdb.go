package database

import (
	"context"
	"fmt"
	"time"

	"web3-data-collector/internal/config"

	influxdb2 "github.com/influxdata/influxdb-client-go/v2"
	"github.com/influxdata/influxdb-client-go/v2/api"
	"github.com/sirupsen/logrus"
)

// InfluxDBClient InfluxDB客户端
type InfluxDBClient struct {
	client   influxdb2.Client
	writeAPI api.WriteAPI
	queryAPI api.QueryAPI
	config   config.InfluxDBConfig
}

// NewInfluxDBClient 创建新的InfluxDB客户端
func NewInfluxDBClient(config config.InfluxDBConfig) (*InfluxDBClient, error) {
	// 创建InfluxDB客户端
	client := influxdb2.NewClient(config.URL, config.Token)

	// 测试连接
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	health, err := client.Health(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to connect to InfluxDB: %w", err)
	}

	if health.Status != "pass" {
		return nil, fmt.Errorf("InfluxDB health check failed: %s", health.Status)
	}

	// 创建写入和查询API
	writeAPI := client.WriteAPI(config.Org, config.Bucket)
	queryAPI := client.QueryAPI(config.Org)

	logrus.Infof("Successfully connected to InfluxDB at %s", config.URL)

	return &InfluxDBClient{
		client:   client,
		writeAPI: writeAPI,
		queryAPI: queryAPI,
		config:   config,
	}, nil
}

// WritePoint 写入数据点
func (idb *InfluxDBClient) WritePoint(
	measurement string,
	tags map[string]string,
	fields map[string]interface{},
	timestamp time.Time,
) error {
	// 创建数据点
	point := influxdb2.NewPoint(measurement, tags, fields, timestamp)

	// 写入数据点
	idb.writeAPI.WritePoint(point)

	return nil
}

// WriteBatch 批量写入数据点
func (idb *InfluxDBClient) WriteBatch(points []*influxdb2.Point) error {
	for _, point := range points {
		idb.writeAPI.WritePoint(point)
	}

	// 强制刷新
	idb.writeAPI.Flush()

	return nil
}

// Query 执行查询
func (idb *InfluxDBClient) Query(query string) ([]map[string]interface{}, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	result, err := idb.queryAPI.Query(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("query failed: %w", err)
	}

	var records []map[string]interface{}

	for result.Next() {
		record := make(map[string]interface{})
		for key, value := range result.Record().Values() {
			record[key] = value
		}
		records = append(records, record)
	}

	if result.Err() != nil {
		return nil, fmt.Errorf("query result error: %w", result.Err())
	}

	return records, nil
}

// GetTransactionStats 获取交易统计
func (idb *InfluxDBClient) GetTransactionStats(network string, timeRange string) (map[string]interface{}, error) {
	query := fmt.Sprintf(`
		from(bucket: "%s")
		|> range(start: -%s)
		|> filter(fn: (r) => r["_measurement"] == "transactions")
		|> filter(fn: (r) => r["network"] == "%s")
		|> group(columns: ["network"])
		|> count()
	`, idb.config.Bucket, timeRange, network)

	records, err := idb.Query(query)
	if err != nil {
		return nil, err
	}

	stats := make(map[string]interface{})
	if len(records) > 0 {
		stats = records[0]
	}

	return stats, nil
}

// GetBlockStats 获取区块统计
func (idb *InfluxDBClient) GetBlockStats(network string, timeRange string) (map[string]interface{}, error) {
	query := fmt.Sprintf(`
		from(bucket: "%s")
		|> range(start: -%s)
		|> filter(fn: (r) => r["_measurement"] == "blocks")
		|> filter(fn: (r) => r["network"] == "%s")
		|> group(columns: ["network"])
		|> count()
	`, idb.config.Bucket, timeRange, network)

	records, err := idb.Query(query)
	if err != nil {
		return nil, err
	}

	stats := make(map[string]interface{})
	if len(records) > 0 {
		stats = records[0]
	}

	return stats, nil
}

// GetLatestBlockNumber 获取最新区块号
func (idb *InfluxDBClient) GetLatestBlockNumber(network string) (uint64, error) {
	query := fmt.Sprintf(`
		from(bucket: "%s")
		|> range(start: -1h)
		|> filter(fn: (r) => r["_measurement"] == "blocks")
		|> filter(fn: (r) => r["network"] == "%s")
		|> filter(fn: (r) => r["_field"] == "number")
		|> last()
	`, idb.config.Bucket, network)

	records, err := idb.Query(query)
	if err != nil {
		return 0, err
	}

	if len(records) == 0 {
		return 0, fmt.Errorf("no block data found for network %s", network)
	}

	if value, ok := records[0]["_value"].(int64); ok {
		return uint64(value), nil
	}

	return 0, fmt.Errorf("invalid block number format")
}

// GetTransactionVolume 获取交易量统计
func (idb *InfluxDBClient) GetTransactionVolume(network string, timeRange string) ([]map[string]interface{}, error) {
	query := fmt.Sprintf(`
		from(bucket: "%s")
		|> range(start: -%s)
		|> filter(fn: (r) => r["_measurement"] == "transactions")
		|> filter(fn: (r) => r["network"] == "%s")
		|> filter(fn: (r) => r["_field"] == "value")
		|> window(every: 1h)
		|> sum()
	`, idb.config.Bucket, timeRange, network)

	return idb.Query(query)
}

// Flush 刷新写入缓冲区
func (idb *InfluxDBClient) Flush() {
	idb.writeAPI.Flush()
}

// Close 关闭连接
func (idb *InfluxDBClient) Close() {
	if idb.writeAPI != nil {
		idb.writeAPI.Flush()
	}
	if idb.client != nil {
		idb.client.Close()
	}
	logrus.Info("InfluxDB client closed")
}