package database

import (
	"context"
	"fmt"
	"time"

	"web3-data-collector/internal/config"

	"github.com/go-redis/redis/v8"
	"github.com/sirupsen/logrus"
)

// RedisClient Redis客户端封装
type RedisClient struct {
	client *redis.Client
	config config.RedisConfig
}

// NewRedisClient 创建新的Redis客户端
func NewRedisClient(config config.RedisConfig) (*RedisClient, error) {
	// 创建Redis客户端
	client := redis.NewClient(&redis.Options{
		Addr:     fmt.Sprintf("%s:%d", config.Host, config.Port),
		Password: config.Password,
		DB:       config.DB,
	})

	// 测试连接
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	_, err := client.Ping(ctx).Result()
	if err != nil {
		return nil, fmt.Errorf("failed to connect to Redis: %w", err)
	}

	logrus.Infof("Successfully connected to Redis at %s:%d", config.Host, config.Port)

	return &RedisClient{
		client: client,
		config: config,
	}, nil
}

// Set 设置键值对
func (rc *RedisClient) Set(key string, value interface{}, expiration time.Duration) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.Set(ctx, key, value, expiration).Err()
}

// Get 获取值
func (rc *RedisClient) Get(key string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.Get(ctx, key).Result()
}

// GetInt64 获取整数值
func (rc *RedisClient) GetInt64(key string) (int64, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	val, err := rc.client.Get(ctx, key).Result()
	if err != nil {
		return 0, err
	}

	var result int64
	_, err = fmt.Sscanf(val, "%d", &result)
	return result, err
}

// Exists 检查键是否存在
func (rc *RedisClient) Exists(key string) (bool, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	count, err := rc.client.Exists(ctx, key).Result()
	return count > 0, err
}

// Delete 删除键
func (rc *RedisClient) Delete(key string) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.Del(ctx, key).Err()
}

// HSet 设置哈希字段
func (rc *RedisClient) HSet(key string, field string, value interface{}) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.HSet(ctx, key, field, value).Err()
}

// HGet 获取哈希字段值
func (rc *RedisClient) HGet(key string, field string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.HGet(ctx, key, field).Result()
}

// HGetAll 获取所有哈希字段
func (rc *RedisClient) HGetAll(key string) (map[string]string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.HGetAll(ctx, key).Result()
}

// HMSet 批量设置哈希字段
func (rc *RedisClient) HMSet(key string, fields map[string]interface{}) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.HMSet(ctx, key, fields).Err()
}

// HMSetString 批量设置哈希字段（字符串值）
func (rc *RedisClient) HMSetString(key string, fields map[string]string) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	// 转换为 map[string]interface{}
	interfaceFields := make(map[string]interface{})
	for k, v := range fields {
		interfaceFields[k] = v
	}

	return rc.client.HMSet(ctx, key, interfaceFields).Err()
}

// Incr 递增计数器
func (rc *RedisClient) Incr(key string) (int64, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.Incr(ctx, key).Result()
}

// IncrBy 按指定值递增
func (rc *RedisClient) IncrBy(key string, value int64) (int64, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.IncrBy(ctx, key, value).Result()
}

// Decr 递减计数器
func (rc *RedisClient) Decr(key string) (int64, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.Decr(ctx, key).Result()
}

// ZAdd 添加有序集合成员
func (rc *RedisClient) ZAdd(key string, score float64, member string) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	z := &redis.Z{
		Score:  score,
		Member: member,
	}

	return rc.client.ZAdd(ctx, key, z).Err()
}

// ZRange 获取有序集合范围内的成员
func (rc *RedisClient) ZRange(key string, start, stop int64) ([]string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.ZRange(ctx, key, start, stop).Result()
}

// ZRevRange 倒序获取有序集合范围内的成员
func (rc *RedisClient) ZRevRange(key string, start, stop int64) ([]string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.ZRevRange(ctx, key, start, stop).Result()
}

// ZRangeByScore 按分数范围获取有序集合成员
func (rc *RedisClient) ZRangeByScore(key string, min, max string) ([]string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	opt := &redis.ZRangeBy{
		Min: min,
		Max: max,
	}

	return rc.client.ZRangeByScore(ctx, key, opt).Result()
}

// LPush 从列表左侧推入元素
func (rc *RedisClient) LPush(key string, values ...interface{}) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.LPush(ctx, key, values...).Err()
}

// RPush 从列表右侧推入元素
func (rc *RedisClient) RPush(key string, values ...interface{}) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.RPush(ctx, key, values...).Err()
}

// LPop 从列表左侧弹出元素
func (rc *RedisClient) LPop(key string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.LPop(ctx, key).Result()
}

// RPop 从列表右侧弹出元素
func (rc *RedisClient) RPop(key string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.RPop(ctx, key).Result()
}

// LLen 获取列表长度
func (rc *RedisClient) LLen(key string) (int64, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.LLen(ctx, key).Result()
}

// LRange 获取列表范围内的元素
func (rc *RedisClient) LRange(key string, start, stop int64) ([]string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.LRange(ctx, key, start, stop).Result()
}

// SAdd 添加集合成员
func (rc *RedisClient) SAdd(key string, members ...interface{}) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.SAdd(ctx, key, members...).Err()
}

// SMembers 获取集合所有成员
func (rc *RedisClient) SMembers(key string) ([]string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.SMembers(ctx, key).Result()
}

// SIsMember 检查是否为集合成员
func (rc *RedisClient) SIsMember(key string, member interface{}) (bool, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.SIsMember(ctx, key, member).Result()
}

// Expire 设置键过期时间
func (rc *RedisClient) Expire(key string, expiration time.Duration) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.Expire(ctx, key, expiration).Err()
}

// TTL 获取键剩余生存时间
func (rc *RedisClient) TTL(key string) (time.Duration, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.TTL(ctx, key).Result()
}

// Keys 查找匹配模式的键
func (rc *RedisClient) Keys(pattern string) ([]string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	return rc.client.Keys(ctx, pattern).Result()
}

// FlushDB 清空当前数据库
func (rc *RedisClient) FlushDB() error {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	return rc.client.FlushDB(ctx).Err()
}

// Ping 测试连接
func (rc *RedisClient) Ping() error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.Ping(ctx).Err()
}

// Info 获取Redis信息
func (rc *RedisClient) Info() (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	return rc.client.Info(ctx).Result()
}

// Pipeline 创建管道
func (rc *RedisClient) Pipeline() redis.Pipeliner {
	return rc.client.Pipeline()
}

// TxPipeline 创建事务管道
func (rc *RedisClient) TxPipeline() redis.Pipeliner {
	return rc.client.TxPipeline()
}

// Close 关闭连接
func (rc *RedisClient) Close() error {
	err := rc.client.Close()
	logrus.Info("Redis client closed")
	return err
}