package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"web3-data-collector/internal/api"
	"web3-data-collector/internal/collector"
	"web3-data-collector/internal/config"
	"web3-data-collector/internal/database"
	"web3-data-collector/internal/metrics"
	"web3-data-collector/internal/processor"
	"web3-data-collector/internal/publisher"

	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"
)

func main() {
	// 加载配置
	cfg, err := config.Load("config.yml")
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	// 初始化日志
	initLogger(cfg.Logging.Level, cfg.Logging.Format)

	logrus.Info("Starting Web3 Data Collector...")

	// 初始化指标收集
	metricsManager := metrics.NewManager()

	// 初始化数据库连接
	influxClient, err := database.NewInfluxDBClient(cfg.InfluxDB)
	if err != nil {
		logrus.Fatalf("Failed to connect to InfluxDB: %v", err)
	}
	defer influxClient.Close()

	redisClient, err := database.NewRedisClient(cfg.Redis)
	if err != nil {
		logrus.Fatalf("Failed to connect to Redis: %v", err)
	}
	defer redisClient.Close()

	// 初始化消息发布器
	kafkaPublisher, err := publisher.NewKafkaPublisher(cfg.Kafka)
	if err != nil {
		logrus.Fatalf("Failed to create Kafka publisher: %v", err)
	}
	defer kafkaPublisher.Close()

	// 初始化数据处理器
	dataProcessor := processor.NewDataProcessor(
		cfg.DataProcessing,
		kafkaPublisher,
		influxClient,
		redisClient,
		metricsManager,
	)

	// 初始化区块链收集器
	blockchainCollector := collector.NewBlockchainCollector(
		cfg.Blockchain,
		dataProcessor,
		metricsManager,
	)

	// 启动收集器
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	go func() {
		if err := blockchainCollector.Start(ctx); err != nil {
			logrus.Errorf("Blockchain collector error: %v", err)
		}
	}()

	// 初始化并启动HTTP服务器
	router := setupRouter(cfg, metricsManager, blockchainCollector)
	
	server := &http.Server{
		Addr:    fmt.Sprintf(":%d", cfg.Server.Port),
		Handler: router,
	}

	// 启动HTTP服务器
	go func() {
		logrus.Infof("Starting HTTP server on port %d", cfg.Server.Port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logrus.Fatalf("Failed to start server: %v", err)
		}
	}()

	// 等待中断信号
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	logrus.Info("Shutting down server...")

	// 优雅关闭
	ctx, cancel = context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		logrus.Errorf("Server forced to shutdown: %v", err)
	}

	logrus.Info("Server exited")
}

func setupRouter(cfg *config.Config, metricsManager *metrics.Manager, collector *collector.BlockchainCollector) *gin.Engine {
	if cfg.Server.Mode == "release" {
		gin.SetMode(gin.ReleaseMode)
	}

	router := gin.Default()

	// 健康检查
	router.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{
			"status":    "ok",
			"timestamp": time.Now().Unix(),
		})
	})

	// 指标端点
	if cfg.Metrics.Enabled {
		router.GET(cfg.Metrics.Path, gin.WrapH(metricsManager.Handler()))
	}

	// API路由
	apiGroup := router.Group("/api/v1")
	api.SetupRoutes(apiGroup, collector, metricsManager)

	return router
}

func initLogger(level, format string) {
	// 设置日志级别
	logLevel, err := logrus.ParseLevel(level)
	if err != nil {
		logLevel = logrus.InfoLevel
	}
	logrus.SetLevel(logLevel)

	// 设置日志格式
	if format == "json" {
		logrus.SetFormatter(&logrus.JSONFormatter{
			TimestampFormat: time.RFC3339,
		})
	} else {
		logrus.SetFormatter(&logrus.TextFormatter{
			FullTimestamp:   true,
			TimestampFormat: time.RFC3339,
		})
	}
}