package collector

import (
	"context"
	"fmt"
	"math/big"
	"strings"
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

// BlockchainCollector 区块链数据收集器
type BlockchainCollector struct {
	config           config.BlockchainConfig
	dataProcessor    *processor.DataProcessor
	metricsManager   *metrics.Manager
	connectors       map[string]*NetworkConnector
	mu               sync.RWMutex
	stopChan         chan struct{}
	wg               sync.WaitGroup
}

// NetworkConnector 网络连接器
type NetworkConnector struct {
	name          string
	config        config.NetworkConfig
	rpcClient     *ethclient.Client
	wsClient      *ethclient.Client
	isConnected   bool
	lastBlock     uint64
	errorCount    uint64
	mu            sync.RWMutex
}

// NewBlockchainCollector 创建新的区块链收集器
func NewBlockchainCollector(
	config config.BlockchainConfig,
	dataProcessor *processor.DataProcessor,
	metricsManager *metrics.Manager,
) *BlockchainCollector {
	return &BlockchainCollector{
		config:         config,
		dataProcessor:  dataProcessor,
		metricsManager: metricsManager,
		connectors:     make(map[string]*NetworkConnector),
		stopChan:       make(chan struct{}),
	}
}

// Start 启动收集器
func (bc *BlockchainCollector) Start(ctx context.Context) error {
	logrus.Info("Starting blockchain collector...")

	// 初始化网络连接器
	for name, networkConfig := range bc.config.Networks {
		if !networkConfig.Enabled {
			logrus.Infof("Network %s is disabled, skipping", name)
			continue
		}

		connector, err := bc.createNetworkConnector(name, networkConfig)
		if err != nil {
			logrus.Errorf("Failed to create connector for %s: %v", name, err)
			continue
		}

		bc.mu.Lock()
		bc.connectors[name] = connector
		bc.mu.Unlock()

		// 启动网络监控
		bc.wg.Add(1)
		go bc.monitorNetwork(ctx, connector)
	}

	// 等待停止信号
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-bc.stopChan:
		return nil
	}
}

// Stop 停止收集器
func (bc *BlockchainCollector) Stop() {
	logrus.Info("Stopping blockchain collector...")
	close(bc.stopChan)
	bc.wg.Wait()

	bc.mu.Lock()
	defer bc.mu.Unlock()

	for name, connector := range bc.connectors {
		if err := connector.Close(); err != nil {
			logrus.Errorf("Error closing connector %s: %v", name, err)
		}
	}

	logrus.Info("Blockchain collector stopped")
}

// createNetworkConnector 创建网络连接器
func (bc *BlockchainCollector) createNetworkConnector(name string, config config.NetworkConfig) (*NetworkConnector, error) {
	connector := &NetworkConnector{
		name:   name,
		config: config,
	}

	// 连接RPC客户端
	if config.RPCURL != "" {
		rpcClient, err := ethclient.Dial(config.RPCURL)
		if err != nil {
			return nil, fmt.Errorf("failed to connect to RPC: %w", err)
		}
		connector.rpcClient = rpcClient
	}

	// 连接WebSocket客户端
	if config.WSURL != "" {
		wsClient, err := ethclient.Dial(config.WSURL)
		if err != nil {
			logrus.Warnf("Failed to connect to WebSocket for %s: %v", name, err)
		} else {
			connector.wsClient = wsClient
		}
	}

	// 验证连接
	if err := connector.validateConnection(); err != nil {
		return nil, fmt.Errorf("connection validation failed: %w", err)
	}

	connector.isConnected = true
	logrus.Infof("Successfully connected to network: %s", name)

	return connector, nil
}

// monitorNetwork 监控单个网络
func (bc *BlockchainCollector) monitorNetwork(ctx context.Context, connector *NetworkConnector) {
	defer bc.wg.Done()

	logrus.Infof("Starting monitoring for network: %s", connector.name)

	// 获取当前最新区块号
	latestBlock, err := connector.getLatestBlockNumber(ctx)
	if err != nil {
		logrus.Errorf("Failed to get latest block for %s: %v", connector.name, err)
		return
	}

	connector.setLastBlock(latestBlock)
	logrus.Infof("Starting from block %d for network %s", latestBlock, connector.name)

	// 启动实时监控
	if connector.wsClient != nil {
		bc.wg.Add(1)
		go bc.subscribeToNewBlocks(ctx, connector)
	}

	// 启动定期轮询作为备用
	ticker := time.NewTicker(5 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-bc.stopChan:
			return
		case <-ticker.C:
			if err := bc.pollLatestBlocks(ctx, connector); err != nil {
				logrus.Errorf("Error polling latest blocks for %s: %v", connector.name, err)
				bc.metricsManager.IncrementError(connector.name, "polling_error")
			}
		}
	}
}

// subscribeToNewBlocks 订阅新区块
func (bc *BlockchainCollector) subscribeToNewBlocks(ctx context.Context, connector *NetworkConnector) {
	defer bc.wg.Done()

	if connector.wsClient == nil {
		return
	}

	logrus.Infof("Subscribing to new blocks for network: %s", connector.name)

	headers := make(chan *types.Header)
	sub, err := connector.wsClient.SubscribeNewHead(ctx, headers)
	if err != nil {
		logrus.Errorf("Failed to subscribe to new heads for %s: %v", connector.name, err)
		return
	}
	defer sub.Unsubscribe()

	for {
		select {
		case <-ctx.Done():
			return
		case <-bc.stopChan:
			return
		case err := <-sub.Err():
			logrus.Errorf("WebSocket subscription error for %s: %v", connector.name, err)
			bc.metricsManager.IncrementError(connector.name, "websocket_error")
			return
		case header := <-headers:
			if header != nil {
				bc.processNewBlock(ctx, connector, header.Number.Uint64())
			}
		}
	}
}

// pollLatestBlocks 轮询最新区块
func (bc *BlockchainCollector) pollLatestBlocks(ctx context.Context, connector *NetworkConnector) error {
	latestBlock, err := connector.getLatestBlockNumber(ctx)
	if err != nil {
		return err
	}

	lastProcessed := connector.getLastBlock()
	
	// 处理遗漏的区块
	for blockNum := lastProcessed + 1; blockNum <= latestBlock; blockNum++ {
		if err := bc.processNewBlock(ctx, connector, blockNum); err != nil {
			logrus.Errorf("Error processing block %d for %s: %v", blockNum, connector.name, err)
			continue
		}
		connector.setLastBlock(blockNum)
	}

	return nil
}

// processNewBlock 处理新区块
func (bc *BlockchainCollector) processNewBlock(ctx context.Context, connector *NetworkConnector, blockNumber uint64) error {
	startTime := time.Now()

	// 获取区块详细信息
	block, err := connector.getBlockByNumber(ctx, blockNumber)
	if err != nil {
		return fmt.Errorf("failed to get block %d: %w", blockNumber, err)
	}

	// 转换为内部模型
	blockModel := bc.convertToBlockModel(block, connector.name)

	// 处理区块数据
	if err := bc.dataProcessor.ProcessBlock(blockModel); err != nil {
		logrus.Errorf("Failed to process block %d: %v", blockNumber, err)
		return err
	}

	// 更新指标
	processingTime := time.Since(startTime)
	bc.metricsManager.RecordBlockProcessingTime(connector.name, processingTime)
	bc.metricsManager.IncrementBlocksProcessed(connector.name)

	logrus.Debugf("Processed block %d for %s in %v", blockNumber, connector.name, processingTime)

	return nil
}

// convertToBlockModel 转换区块为内部模型
func (bc *BlockchainCollector) convertToBlockModel(block *types.Block, network string) *models.Block {
	blockModel := &models.Block{
		Number:       block.NumberU64(),
		Hash:         block.Hash().Hex(),
		ParentHash:   block.ParentHash().Hex(),
		Timestamp:    time.Unix(int64(block.Time()), 0),
		Difficulty:   block.Difficulty(),
		GasLimit:     block.GasLimit(),
		GasUsed:      block.GasUsed(),
		Miner:        block.Coinbase().Hex(),
		Network:      network,
		Transactions: make([]models.Transaction, 0, len(block.Transactions())),
		TxCount:      len(block.Transactions()),
		Size:         block.Size(),
	}

	// 处理EIP-1559
	if block.BaseFee() != nil {
		blockModel.BaseFeePerGas = block.BaseFee()
	}

	// 转换交易
	for i, tx := range block.Transactions() {
		txModel := bc.convertToTransactionModel(tx, block, uint(i), network)
		blockModel.Transactions = append(blockModel.Transactions, *txModel)
	}

	return blockModel
}

// convertToTransactionModel 转换交易为内部模型
func (bc *BlockchainCollector) convertToTransactionModel(
	tx *types.Transaction,
	block *types.Block,
	txIndex uint,
	network string,
) *models.Transaction {
	var toAddress string
	if tx.To() != nil {
		toAddress = tx.To().Hex()
	}

	// 获取发送者地址
	signer := types.LatestSignerForChainID(tx.ChainId())
	fromAddress, _ := types.Sender(signer, tx)

	txModel := &models.Transaction{
		Hash:             tx.Hash().Hex(),
		BlockNumber:      block.NumberU64(),
		BlockHash:        block.Hash().Hex(),
		TransactionIndex: txIndex,
		FromAddress:      fromAddress.Hex(),
		ToAddress:        toAddress,
		Value:            tx.Value(),
		Gas:              tx.Gas(),
		GasPrice:         tx.GasPrice(),
		Nonce:            tx.Nonce(),
		Timestamp:        time.Unix(int64(block.Time()), 0),
		Network:          network,
		TransactionType:  tx.Type(),
		IsContractCall:   toAddress != "" && len(tx.Data()) > 0,
	}

	// 处理输入数据
	if len(tx.Data()) > 0 {
		txModel.InputData = fmt.Sprintf("0x%x", tx.Data())
		txModel.IsContractCall = true
	}

	// 处理EIP-1559交易
	if tx.Type() == types.DynamicFeeTxType {
		txModel.MaxFeePerGas = tx.GasFeeCap()
		txModel.MaxPriorityFeePerGas = tx.GasTipCap()
	}

	// 检查是否为代币转账
	if bc.isTokenTransfer(tx) {
		txModel.IsTokenTransfer = true
		// 这里可以进一步解析代币转账详情
	}

	return txModel
}

// isTokenTransfer 检查是否为代币转账
func (bc *BlockchainCollector) isTokenTransfer(tx *types.Transaction) bool {
	if tx.To() == nil || len(tx.Data()) < 4 {
		return false
	}

	// 检查是否为ERC20 transfer方法调用 (0xa9059cbb)
	transferMethodID := "0xa9059cbb"
	inputData := fmt.Sprintf("0x%x", tx.Data()[:4])
	
	return strings.EqualFold(inputData, transferMethodID)
}

// GetNetworkStats 获取网络统计信息
func (bc *BlockchainCollector) GetNetworkStats() map[string]*models.NetworkStats {
	bc.mu.RLock()
	defer bc.mu.RUnlock()

	stats := make(map[string]*models.NetworkStats)
	
	for name, connector := range bc.connectors {
		stats[name] = &models.NetworkStats{
			Network:        name,
			LatestBlock:    connector.getLastBlock(),
			IsHealthy:      connector.isConnected,
			ErrorCount:     connector.getErrorCount(),
			LastUpdateTime: time.Now(),
		}
	}

	return stats
}

// NetworkConnector 方法实现

func (nc *NetworkConnector) validateConnection() error {
	if nc.rpcClient == nil {
		return fmt.Errorf("no RPC client available")
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// 测试连接
	_, err := nc.rpcClient.ChainID(ctx)
	return err
}

func (nc *NetworkConnector) getLatestBlockNumber(ctx context.Context) (uint64, error) {
	if nc.rpcClient == nil {
		return 0, fmt.Errorf("no RPC client available")
	}

	return nc.rpcClient.BlockNumber(ctx)
}

func (nc *NetworkConnector) getBlockByNumber(ctx context.Context, number uint64) (*types.Block, error) {
	if nc.rpcClient == nil {
		return nil, fmt.Errorf("no RPC client available")
	}

	return nc.rpcClient.BlockByNumber(ctx, big.NewInt(int64(number)))
}

func (nc *NetworkConnector) setLastBlock(blockNumber uint64) {
	nc.mu.Lock()
	defer nc.mu.Unlock()
	nc.lastBlock = blockNumber
}

func (nc *NetworkConnector) getLastBlock() uint64 {
	nc.mu.RLock()
	defer nc.mu.RUnlock()
	return nc.lastBlock
}

func (nc *NetworkConnector) getErrorCount() uint64 {
	nc.mu.RLock()
	defer nc.mu.RUnlock()
	return nc.errorCount
}

func (nc *NetworkConnector) incrementErrorCount() {
	nc.mu.Lock()
	defer nc.mu.Unlock()
	nc.errorCount++
}

func (nc *NetworkConnector) Close() error {
	var err error
	
	if nc.rpcClient != nil {
		nc.rpcClient.Close()
	}
	
	if nc.wsClient != nil {
		nc.wsClient.Close()
	}
	
	nc.isConnected = false
	return err
}