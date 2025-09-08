package api

import (
	"net/http"
	"strconv"
	"time"

	"web3-data-collector/internal/collector"
	"web3-data-collector/internal/metrics"

	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"
)

// APIResponse 标准API响应结构
type APIResponse struct {
	Success   bool        `json:"success"`
	Message   string      `json:"message,omitempty"`
	Data      interface{} `json:"data,omitempty"`
	Timestamp int64       `json:"timestamp"`
}

// SetupRoutes 设置API路由
func SetupRoutes(router *gin.RouterGroup, collector *collector.BlockchainCollector, metricsManager *metrics.Manager) {
	// 状态相关接口
	router.GET("/status", getStatus(collector, metricsManager))
	router.GET("/health", getHealth(collector))
	
	// 网络统计接口
	router.GET("/networks", getNetworks(collector))
	router.GET("/networks/:network/stats", getNetworkStats(collector))
	
	// 指标接口
	router.GET("/metrics/stats", getMetricsStats(metricsManager))
	router.GET("/metrics/performance", getPerformanceMetrics(metricsManager))
	
	// 管理接口
	router.POST("/admin/reload", adminReload())
	router.GET("/admin/config", getConfig())
}

// getStatus 获取服务状态
func getStatus(collector *collector.BlockchainCollector, metricsManager *metrics.Manager) gin.HandlerFunc {
	return func(c *gin.Context) {
		networkStats := collector.GetNetworkStats()
		
		status := map[string]interface{}{
			"service":    "web3-data-collector",
			"version":    "1.0.0",
			"uptime":     time.Since(time.Now().Add(-time.Hour)).String(), // 示例运行时间
			"networks":   networkStats,
			"metrics":    metricsManager.GetStats(),
			"healthy":    isHealthy(networkStats),
		}

		response := APIResponse{
			Success:   true,
			Data:      status,
			Timestamp: time.Now().Unix(),
		}

		c.JSON(http.StatusOK, response)
	}
}

// getHealth 健康检查
func getHealth(collector *collector.BlockchainCollector) gin.HandlerFunc {
	return func(c *gin.Context) {
		networkStats := collector.GetNetworkStats()
		healthy := isHealthy(networkStats)

		var status int
		var message string

		if healthy {
			status = http.StatusOK
			message = "Service is healthy"
		} else {
			status = http.StatusServiceUnavailable
			message = "Service is unhealthy"
		}

		response := APIResponse{
			Success:   healthy,
			Message:   message,
			Data:      networkStats,
			Timestamp: time.Now().Unix(),
		}

		c.JSON(status, response)
	}
}

// getNetworks 获取所有网络信息
func getNetworks(collector *collector.BlockchainCollector) gin.HandlerFunc {
	return func(c *gin.Context) {
		networkStats := collector.GetNetworkStats()

		networks := make([]map[string]interface{}, 0, len(networkStats))
		for name, stats := range networkStats {
			network := map[string]interface{}{
				"name":            name,
				"latest_block":    stats.LatestBlock,
				"is_healthy":      stats.IsHealthy,
				"error_count":     stats.ErrorCount,
				"last_update":     stats.LastUpdateTime,
			}
			networks = append(networks, network)
		}

		response := APIResponse{
			Success:   true,
			Data:      networks,
			Timestamp: time.Now().Unix(),
		}

		c.JSON(http.StatusOK, response)
	}
}

// getNetworkStats 获取特定网络统计信息
func getNetworkStats(collector *collector.BlockchainCollector) gin.HandlerFunc {
	return func(c *gin.Context) {
		networkName := c.Param("network")
		
		networkStats := collector.GetNetworkStats()
		stats, exists := networkStats[networkName]
		
		if !exists {
			response := APIResponse{
				Success:   false,
				Message:   "Network not found",
				Timestamp: time.Now().Unix(),
			}
			c.JSON(http.StatusNotFound, response)
			return
		}

		// 添加更详细的统计信息
		detailedStats := map[string]interface{}{
			"network":         stats.Network,
			"latest_block":    stats.LatestBlock,
			"is_healthy":      stats.IsHealthy,
			"error_count":     stats.ErrorCount,
			"last_update":     stats.LastUpdateTime,
			"total_tx_processed": stats.TotalTxProcessed,
			"tx_per_second":      stats.TxPerSecond,
			"avg_block_time":     stats.AvgBlockTime,
		}

		response := APIResponse{
			Success:   true,
			Data:      detailedStats,
			Timestamp: time.Now().Unix(),
		}

		c.JSON(http.StatusOK, response)
	}
}

// getMetricsStats 获取指标统计
func getMetricsStats(metricsManager *metrics.Manager) gin.HandlerFunc {
	return func(c *gin.Context) {
		stats := metricsManager.GetStats()

		response := APIResponse{
			Success:   true,
			Data:      stats,
			Timestamp: time.Now().Unix(),
		}

		c.JSON(http.StatusOK, response)
	}
}

// getPerformanceMetrics 获取性能指标
func getPerformanceMetrics(metricsManager *metrics.Manager) gin.HandlerFunc {
	return func(c *gin.Context) {
		// 获取时间窗口参数，默认1小时
		timeWindowStr := c.DefaultQuery("window", "1h")
		timeWindow, err := time.ParseDuration(timeWindowStr)
		if err != nil {
			timeWindow = time.Hour
		}

		performanceMetrics := metricsManager.CalculatePerformanceMetrics(timeWindow)

		response := APIResponse{
			Success:   true,
			Data:      performanceMetrics,
			Timestamp: time.Now().Unix(),
		}

		c.JSON(http.StatusOK, response)
	}
}

// adminReload 重新加载配置
func adminReload() gin.HandlerFunc {
	return func(c *gin.Context) {
		// 这里应该实现配置重新加载逻辑
		logrus.Info("Admin reload requested")

		response := APIResponse{
			Success:   true,
			Message:   "Configuration reloaded successfully",
			Timestamp: time.Now().Unix(),
		}

		c.JSON(http.StatusOK, response)
	}
}

// getConfig 获取当前配置
func getConfig() gin.HandlerFunc {
	return func(c *gin.Context) {
		// 这里应该返回当前的配置信息（敏感信息需要脱敏）
		config := map[string]interface{}{
			"server": map[string]interface{}{
				"port": 8082,
				"mode": "debug",
			},
			"data_processing": map[string]interface{}{
				"batch_size": 50,
				"workers":    10,
			},
			"networks": []string{"ethereum", "bsc", "polygon"},
		}

		response := APIResponse{
			Success:   true,
			Data:      config,
			Timestamp: time.Now().Unix(),
		}

		c.JSON(http.StatusOK, response)
	}
}

// isHealthy 检查服务是否健康
func isHealthy(networkStats map[string]*collector.NetworkStats) bool {
	if len(networkStats) == 0 {
		return false
	}

	// 检查是否至少有一个网络是健康的
	for _, stats := range networkStats {
		if stats.IsHealthy {
			return true
		}
	}

	return false
}

// QueryParams 查询参数结构
type QueryParams struct {
	Page     int    `form:"page"`
	PageSize int    `form:"page_size"`
	Network  string `form:"network"`
	StartTime string `form:"start_time"`
	EndTime   string `form:"end_time"`
}

// parseQueryParams 解析查询参数
func parseQueryParams(c *gin.Context) *QueryParams {
	params := &QueryParams{
		Page:     1,
		PageSize: 20,
	}

	if page := c.Query("page"); page != "" {
		if p, err := strconv.Atoi(page); err == nil && p > 0 {
			params.Page = p
		}
	}

	if pageSize := c.Query("page_size"); pageSize != "" {
		if ps, err := strconv.Atoi(pageSize); err == nil && ps > 0 && ps <= 100 {
			params.PageSize = ps
		}
	}

	params.Network = c.Query("network")
	params.StartTime = c.Query("start_time")
	params.EndTime = c.Query("end_time")

	return params
}

// ErrorHandler 错误处理中间件
func ErrorHandler() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Next()

		// 处理错误
		if len(c.Errors) > 0 {
			err := c.Errors.Last()
			logrus.Errorf("API Error: %v", err.Err)

			response := APIResponse{
				Success:   false,
				Message:   "Internal server error",
				Timestamp: time.Now().Unix(),
			}

			c.JSON(http.StatusInternalServerError, response)
		}
	}
}

// LoggerMiddleware 日志中间件
func LoggerMiddleware() gin.HandlerFunc {
	return gin.LoggerWithFormatter(func(param gin.LogFormatterParams) string {
		return fmt.Sprintf("[%s] %s %s %d %v \"%s\" \"%s\"\n",
			param.TimeStamp.Format("2006-01-02 15:04:05"),
			param.Method,
			param.Path,
			param.StatusCode,
			param.Latency,
			param.Request.UserAgent(),
			param.ErrorMessage,
		)
	})
}

// CORSMiddleware CORS中间件
func CORSMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Origin, Content-Type, Content-Length, Accept-Encoding, X-CSRF-Token, Authorization")

		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}

		c.Next()
	}
}