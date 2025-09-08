package collector

import (
	"context"
	"fmt"
	"math/big"
	"sync"
	"time"

	"web3-data-collector/internal/config"
	"web3-data-collector/internal/metrics"
	"web3-data-collector/internal/models"
	"web3-data-collector/internal/processor"

	"github.com/ethereum/go-ethereum"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/types"
	"github.com/ethereum/go-ethereum/ethclient"
	"github.com/sirupsen/logrus"
)

type BlockchainCollector struct {
	config           *config.BlockchainConfig
	processor        *processor.DataProcessor
	metricsManager   *metrics.Manager
	clients          map[string]*NetworkClient
	subscriptions    map[string]*Subscription
	mu               sync.RWMutex
	running          bool
	lastProcessed    map[string]*big.Int // 每个网络最后处理的区块号
}

type NetworkClient struct {
	Name      string
	ChainID   int64
	RpcClient *ethclient.Client
	WsClient  *ethclient.Client
	Config    config.NetworkConfig
	mu        sync.RWMutex
}

type Subscription struct {
	HeadersSub  ethereum.Subscription
	LogsSub     ethereum.Subscription
	PendingSub  ethereum.Subscription
	Cancel      context.CancelFunc
	LastBlock   *big.Int
	LastUpdate  time.Time
}

func NewBlockchainCollector(cfg *config.BlockchainConfig, processor *processor.DataProcessor, metrics *metrics.Manager) *BlockchainCollector {
	return &BlockchainCollector{
		config:         cfg,
		processor:      processor,
		metricsManager: metrics,
		clients:        make(map[string]*NetworkClient),
		subscriptions:  make(map[string]*Subscription),
		lastProcessed:  make(map[string]*big.Int),
	}
}

func (bc *BlockchainCollector) Start(ctx context.Context) error {
	logrus.Info("Starting blockchain collector...")

	// 初始化网络客户端
	if err := bc.initializeClients(); err != nil {
		return fmt.Errorf("failed to initialize clients: %w", err)
	}

	bc.running = true

	// 启动各网络的数据收集
	var wg sync.WaitGroup
	for networkName, client := range bc.clients {
		if !client.Config.Enabled {
			logrus.Infof("Network %s is disabled, skipping", networkName)
			continue
		}

		wg.Add(1)
		go func(name string, client *NetworkClient) {
			defer wg.Done()
			bc.collectNetworkData(ctx, name, client)
		}(networkName, client)
	}

	// 启动状态监控
	go bc.monitorStatus(ctx)

	// 等待所有goroutine完成
	wg.Wait()

	return nil
}

func (bc *BlockchainCollector) Stop() {
	logrus.Info("Stopping blockchain collector...")
	
	bc.mu.Lock()
	bc.running = false
	bc.mu.Unlock()

	// 取消所有订阅
	for _, sub := range bc.subscriptions {
		if sub.Cancel != nil {
			sub.Cancel()
		}
		if sub.HeadersSub != nil {
			sub.HeadersSub.Unsubscribe()
		}
		if sub.LogsSub != nil {
			sub.LogsSub.Unsubscribe()
		}
		if sub.PendingSub != nil {
			sub.PendingSub.Unsubscribe()
		}
	}

	// 关闭客户端连接
	for _, client := range bc.clients {
		if client.RpcClient != nil {
			client.RpcClient.Close()
		}
		if client.WsClient != nil {
			client.WsClient.Close()
		}
	}

	logrus.Info("Blockchain collector stopped")
}

func (bc *BlockchainCollector) initializeClients() error {
	for networkName, networkCfg := range bc.config.Networks {
		if !networkCfg.Enabled {
			continue
		}

		logrus.Infof("Initializing client for network: %s", networkName)

		client := &NetworkClient{
			Name:    networkName,
			ChainID: networkCfg.ChainID,
			Config:  networkCfg,
		}

		// 创建RPC客户端
		rpcClient, err := ethclient.Dial(networkCfg.RpcURL)
		if err != nil {
			return fmt.Errorf("failed to connect to %s RPC: %w", networkName, err)
		}
		client.RpcClient = rpcClient

		// 创建WebSocket客户端（如果配置了WebSocket URL）
		if networkCfg.WsURL != "" {
			wsClient, err := ethclient.Dial(networkCfg.WsURL)
			if err != nil {
				logrus.Warnf("Failed to connect to %s WebSocket, will use RPC only: %v", networkName, err)
			} else {
				client.WsClient = wsClient
			}
		}

		bc.clients[networkName] = client

		// 验证连接
		if err := bc.validateConnection(client); err != nil {
			return fmt.Errorf("failed to validate %s connection: %w", networkName, err)
		}

		logrus.Infof("Successfully connected to %s (ChainID: %d)", networkName, networkCfg.ChainID)
	}

	return nil
}

func (bc *BlockchainCollector) validateConnection(client *NetworkClient) error {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// 获取链ID验证
	chainID, err := client.RpcClient.ChainID(ctx)
	if err != nil {
		return fmt.Errorf("failed to get chain ID: %w", err)
	}

	if chainID.Int64() != client.ChainID {
		return fmt.Errorf("chain ID mismatch: expected %d, got %d", client.ChainID, chainID.Int64())
	}

	// 获取最新区块
	_, err = client.RpcClient.BlockByNumber(ctx, nil)
	if err != nil {
		return fmt.Errorf("failed to get latest block: %w", err)
	}

	return nil
}

func (bc *BlockchainCollector) collectNetworkData(ctx context.Context, networkName string, client *NetworkClient) {
	logrus.Infof("Starting data collection for network: %s", networkName)

	// 创建网络特定的context
	networkCtx, cancel := context.WithCancel(ctx)
	defer cancel()

	// 创建订阅记录
	subscription := &Subscription{
		Cancel:     cancel,
		LastUpdate: time.Now(),
	}
	bc.subscriptions[networkName] = subscription

	// 启动实时订阅（如果有WebSocket客户端）
	if client.WsClient != nil {
		go bc.subscribeToNewHeads(networkCtx, networkName, client, subscription)
		go bc.subscribeToLogs(networkCtx, networkName, client, subscription)
		go bc.subscribeToMempool(networkCtx, networkName, client, subscription)
	}

	// 启动历史数据同步
	go bc.syncHistoricalData(networkCtx, networkName, client)

	// 启动定期健康检查
	go bc.healthCheck(networkCtx, networkName, client)

	// 等待context取消
	<-networkCtx.Done()
	logrus.Infof("Stopped data collection for network: %s", networkName)
}

func (bc *BlockchainCollector) subscribeToNewHeads(ctx context.Context, networkName string, client *NetworkClient, sub *Subscription) {
	headers := make(chan *types.Header)
	
	headersSub, err := client.WsClient.SubscribeNewHead(ctx, headers)
	if err != nil {
		logrus.Errorf("Failed to subscribe to new heads for %s: %v", networkName, err)
		return
	}
	sub.HeadersSub = headersSub

	defer headersSub.Unsubscribe()

	logrus.Infof("Subscribed to new heads for network: %s", networkName)

	for {
		select {
		case err := <-headersSub.Err():
			logrus.Errorf("New heads subscription error for %s: %v", networkName, err)
			return
		case header := <-headers:
			if header != nil {
				bc.processNewHeader(ctx, networkName, client, header)
				sub.LastBlock = header.Number
				sub.LastUpdate = time.Now()
			}
		case <-ctx.Done():
			return
		}
	}
}

func (bc *BlockchainCollector) subscribeToLogs(ctx context.Context, networkName string, client *NetworkClient, sub *Subscription) {
	// 订阅所有日志
	query := ethereum.FilterQuery{}
	logs := make(chan types.Log)

	logsSub, err := client.WsClient.SubscribeFilterLogs(ctx, query, logs)
	if err != nil {
		logrus.Errorf("Failed to subscribe to logs for %s: %v", networkName, err)
		return
	}
	sub.LogsSub = logsSub

	defer logsSub.Unsubscribe()

	logrus.Infof("Subscribed to logs for network: %s", networkName)

	for {
		select {
		case err := <-logsSub.Err():
			logrus.Errorf("Logs subscription error for %s: %v", networkName, err)
			return
		case log := <-logs:
			bc.processLogEvent(ctx, networkName, &log)
		case <-ctx.Done():
			return
		}
	}
}

func (bc *BlockchainCollector) subscribeToMempool(ctx context.Context, networkName string, client *NetworkClient, sub *Subscription) {
	// 订阅待处理交易
	txHashes := make(chan common.Hash)
	
	pendingSub, err := client.WsClient.SubscribePendingTransactions(ctx, txHashes)
	if err != nil {
		logrus.Errorf("Failed to subscribe to pending transactions for %s: %v", networkName, err)
		return
	}
	sub.PendingSub = pendingSub

	defer pendingSub.Unsubscribe()

	logrus.Infof("Subscribed to mempool for network: %s", networkName)

	for {
		select {
		case err := <-pendingSub.Err():
			logrus.Errorf("Pending transactions subscription error for %s: %v", networkName, err)
			return
		case txHash := <-txHashes:
			// 获取交易详情并处理
			go bc.processPendingTransaction(ctx, networkName, client, txHash)
		case <-ctx.Done():
			return
		}
	}
}

func (bc *BlockchainCollector) processNewHeader(ctx context.Context, networkName string, client *NetworkClient, header *types.Header) {
	// 获取完整区块信息
	block, err := client.RpcClient.BlockByHash(ctx, header.Hash())
	if err != nil {
		logrus.Errorf("Failed to get block by hash for %s: %v", networkName, err)
		return
	}

	// 处理区块
	blockData := bc.convertBlock(networkName, block)
	if err := bc.processor.ProcessBlock(blockData); err != nil {
		logrus.Errorf("Failed to process block for %s: %v", networkName, err)
	}

	// 处理区块中的所有交易
	for _, tx := range block.Transactions() {
		receipt, err := client.RpcClient.TransactionReceipt(ctx, tx.Hash())
		if err != nil {
			logrus.Errorf("Failed to get transaction receipt for %s: %v", networkName, err)
			continue
		}

		txData := bc.convertTransaction(networkName, tx, receipt, block)
		if err := bc.processor.ProcessTransaction(txData); err != nil {
			logrus.Errorf("Failed to process transaction for %s: %v", networkName, err)
		}
	}

	// 更新指标
	bc.metricsManager.RecordBlocksProcessed(networkName, 1)
	bc.metricsManager.RecordTransactionsProcessed(networkName, float64(len(block.Transactions())))

	logrus.Debugf("Processed block %d for %s with %d transactions", 
		header.Number.Uint64(), networkName, len(block.Transactions()))
}

func (bc *BlockchainCollector) processLogEvent(ctx context.Context, networkName string, log *types.Log) {
	// 处理事件日志
	eventData := bc.convertLogEvent(networkName, log)
	if err := bc.processor.ProcessEvent(eventData); err != nil {
		logrus.Errorf("Failed to process event for %s: %v", networkName, err)
	}
}

func (bc *BlockchainCollector) processPendingTransaction(ctx context.Context, networkName string, client *NetworkClient, txHash common.Hash) {
	tx, _, err := client.RpcClient.TransactionByHash(ctx, txHash)
	if err != nil {
		logrus.Debugf("Failed to get pending transaction %s for %s: %v", txHash.Hex(), networkName, err)
		return
	}

	// 处理待处理交易（用于内存池监控）
	pendingTxData := bc.convertPendingTransaction(networkName, tx)
	if err := bc.processor.ProcessPendingTransaction(pendingTxData); err != nil {
		logrus.Errorf("Failed to process pending transaction for %s: %v", networkName, err)
	}
}

func (bc *BlockchainCollector) syncHistoricalData(ctx context.Context, networkName string, client *NetworkClient) {
	// 获取最后处理的区块号
	lastProcessed := bc.getLastProcessedBlock(networkName)
	
	// 获取当前最新区块
	latestBlock, err := client.RpcClient.BlockByNumber(ctx, nil)
	if err != nil {
		logrus.Errorf("Failed to get latest block for %s: %v", networkName, err)
		return
	}

	currentBlock := lastProcessed
	if currentBlock == nil {
		// 如果没有历史记录，从最新区块开始
		currentBlock = new(big.Int).Sub(latestBlock.Number(), big.NewInt(10))
	}

	logrus.Infof("Starting historical sync for %s from block %d to %d", 
		networkName, currentBlock.Uint64(), latestBlock.Number().Uint64())

	for currentBlock.Cmp(latestBlock.Number()) < 0 {
		select {
		case <-ctx.Done():
			return
		default:
		}

		// 获取区块
		block, err := client.RpcClient.BlockByNumber(ctx, currentBlock)
		if err != nil {
			logrus.Errorf("Failed to get historical block %d for %s: %v", currentBlock.Uint64(), networkName, err)
			time.Sleep(1 * time.Second)
			continue
		}

		// 处理区块（与实时处理相同）
		bc.processNewHeader(ctx, networkName, client, block.Header())

		// 更新进度
		bc.setLastProcessedBlock(networkName, currentBlock)
		currentBlock = new(big.Int).Add(currentBlock, big.NewInt(1))

		// 控制处理速度，避免过载
		time.Sleep(100 * time.Millisecond)
	}

	logrus.Infof("Historical sync completed for %s", networkName)
}

func (bc *BlockchainCollector) healthCheck(ctx context.Context, networkName string, client *NetworkClient) {
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			// 检查连接状态
			if err := bc.validateConnection(client); err != nil {
				logrus.Errorf("Health check failed for %s: %v", networkName, err)
				bc.metricsManager.RecordConnectionError(networkName)
				
				// 尝试重新连接
				bc.attemptReconnection(networkName, client)
			} else {
				bc.metricsManager.RecordConnectionSuccess(networkName)
			}
		}
	}
}

func (bc *BlockchainCollector) attemptReconnection(networkName string, client *NetworkClient) {
	logrus.Infof("Attempting to reconnect to %s", networkName)
	
	// 关闭现有连接
	if client.RpcClient != nil {
		client.RpcClient.Close()
	}
	if client.WsClient != nil {
		client.WsClient.Close()
	}

	// 重新建立连接
	time.Sleep(5 * time.Second) // 等待一段时间再重连
	
	rpcClient, err := ethclient.Dial(client.Config.RpcURL)
	if err != nil {
		logrus.Errorf("Failed to reconnect RPC for %s: %v", networkName, err)
		return
	}
	client.RpcClient = rpcClient

	if client.Config.WsURL != "" {
		wsClient, err := ethclient.Dial(client.Config.WsURL)
		if err != nil {
			logrus.Warnf("Failed to reconnect WebSocket for %s: %v", networkName, err)
		} else {
			client.WsClient = wsClient
		}
	}

	logrus.Infof("Successfully reconnected to %s", networkName)
}

func (bc *BlockchainCollector) monitorStatus(ctx context.Context) {
	ticker := time.NewTicker(1 * time.Minute)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			bc.logStatus()
		}
	}
}

func (bc *BlockchainCollector) logStatus() {
	bc.mu.RLock()
	defer bc.mu.RUnlock()

	for networkName, sub := range bc.subscriptions {
		if sub.LastBlock != nil {
			logrus.Infof("Network %s: last block %d, last update %v", 
				networkName, sub.LastBlock.Uint64(), sub.LastUpdate)
		}
	}
}

// 辅助方法
func (bc *BlockchainCollector) getLastProcessedBlock(networkName string) *big.Int {
	bc.mu.RLock()
	defer bc.mu.RUnlock()
	return bc.lastProcessed[networkName]
}

func (bc *BlockchainCollector) setLastProcessedBlock(networkName string, blockNumber *big.Int) {
	bc.mu.Lock()
	defer bc.mu.Unlock()
	bc.lastProcessed[networkName] = new(big.Int).Set(blockNumber)
}

// 数据转换方法
func (bc *BlockchainCollector) convertBlock(networkName string, block *types.Block) *models.BlockData {
	return &models.BlockData{
		Network:         networkName,
		Hash:           block.Hash().Hex(),
		Number:         block.Number().Uint64(),
		ParentHash:     block.ParentHash().Hex(),
		Timestamp:      block.Time(),
		Difficulty:     block.Difficulty().String(),
		GasLimit:       block.GasLimit(),
		GasUsed:        block.GasUsed(),
		Miner:          block.Coinbase().Hex(),
		Size:           block.Size(),
		TransactionCount: len(block.Transactions()),
		Nonce:          block.Nonce(),
		MixHash:        block.MixDigest().Hex(),
		ExtraData:      fmt.Sprintf("%x", block.Extra()),
	}
}

func (bc *BlockchainCollector) convertTransaction(networkName string, tx *types.Transaction, receipt *types.Receipt, block *types.Block) *models.TransactionData {
	var to string
	if tx.To() != nil {
		to = tx.To().Hex()
	}

	return &models.TransactionData{
		Network:           networkName,
		Hash:             tx.Hash().Hex(),
		BlockNumber:      block.Number().Uint64(),
		BlockHash:        block.Hash().Hex(),
		TransactionIndex: int(receipt.TransactionIndex),
		From:             receipt.From.Hex(),
		To:               to,
		Value:            tx.Value().String(),
		GasLimit:         tx.Gas(),
		GasPrice:         tx.GasPrice().String(),
		GasUsed:          receipt.GasUsed,
		Nonce:            tx.Nonce(),
		InputData:        fmt.Sprintf("%x", tx.Data()),
		Timestamp:        block.Time(),
		Status:           receipt.Status,
		ContractAddress:  receipt.ContractAddress.Hex(),
		Logs:            bc.convertLogs(receipt.Logs),
		LogsBloom:       fmt.Sprintf("%x", receipt.Bloom.Bytes()),
		Type:            tx.Type(),
	}
}

func (bc *BlockchainCollector) convertLogEvent(networkName string, log *types.Log) *models.EventData {
	var topics []string
	for _, topic := range log.Topics {
		topics = append(topics, topic.Hex())
	}

	return &models.EventData{
		Network:         networkName,
		Address:         log.Address.Hex(),
		Topics:          topics,
		Data:           fmt.Sprintf("%x", log.Data),
		BlockNumber:    log.BlockNumber,
		BlockHash:      log.BlockHash.Hex(),
		TransactionHash: log.TxHash.Hex(),
		TransactionIndex: log.TxIndex,
		LogIndex:       log.Index,
		Removed:        log.Removed,
	}
}

func (bc *BlockchainCollector) convertPendingTransaction(networkName string, tx *types.Transaction) *models.PendingTransactionData {
	var to string
	if tx.To() != nil {
		to = tx.To().Hex()
	}

	return &models.PendingTransactionData{
		Network:    networkName,
		Hash:      tx.Hash().Hex(),
		From:      "", // 需要通过其他方式获取
		To:        to,
		Value:     tx.Value().String(),
		GasLimit:  tx.Gas(),
		GasPrice:  tx.GasPrice().String(),
		Nonce:     tx.Nonce(),
		InputData: fmt.Sprintf("%x", tx.Data()),
		Timestamp: time.Now().Unix(),
	}
}

func (bc *BlockchainCollector) convertLogs(logs []*types.Log) []models.LogData {
	var result []models.LogData
	for _, log := range logs {
		var topics []string
		for _, topic := range log.Topics {
			topics = append(topics, topic.Hex())
		}
		
		result = append(result, models.LogData{
			Address:         log.Address.Hex(),
			Topics:          topics,
			Data:           fmt.Sprintf("%x", log.Data),
			LogIndex:       log.Index,
			TransactionIndex: log.TxIndex,
			Removed:        log.Removed,
		})
	}
	return result
}