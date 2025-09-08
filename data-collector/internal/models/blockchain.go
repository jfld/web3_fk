package models

import (
	"math/big"
	"time"
)

// Transaction 表示区块链交易
type Transaction struct {
	Hash              string    `json:"hash"`
	BlockNumber       uint64    `json:"block_number"`
	BlockHash         string    `json:"block_hash"`
	TransactionIndex  uint      `json:"transaction_index"`
	FromAddress       string    `json:"from_address"`
	ToAddress         string    `json:"to_address,omitempty"`
	Value             *big.Int  `json:"value"`
	Gas               uint64    `json:"gas"`
	GasPrice          *big.Int  `json:"gas_price"`
	GasUsed           uint64    `json:"gas_used,omitempty"`
	Nonce             uint64    `json:"nonce"`
	InputData         string    `json:"input_data,omitempty"`
	Timestamp         time.Time `json:"timestamp"`
	Network           string    `json:"network"`
	Status            uint64    `json:"status"`
	ContractAddress   string    `json:"contract_address,omitempty"`
	IsContractCall    bool      `json:"is_contract_call"`
	IsTokenTransfer   bool      `json:"is_token_transfer"`
	TokenSymbol       string    `json:"token_symbol,omitempty"`
	TokenAmount       *big.Int  `json:"token_amount,omitempty"`
	TokenDecimals     uint8     `json:"token_decimals,omitempty"`
	MaxFeePerGas      *big.Int  `json:"max_fee_per_gas,omitempty"`
	MaxPriorityFeePerGas *big.Int `json:"max_priority_fee_per_gas,omitempty"`
	TransactionType   uint8     `json:"transaction_type"`
}

// Block 表示区块信息
type Block struct {
	Number       uint64      `json:"number"`
	Hash         string      `json:"hash"`
	ParentHash   string      `json:"parent_hash"`
	Timestamp    time.Time   `json:"timestamp"`
	Difficulty   *big.Int    `json:"difficulty"`
	GasLimit     uint64      `json:"gas_limit"`
	GasUsed      uint64      `json:"gas_used"`
	Miner        string      `json:"miner"`
	Network      string      `json:"network"`
	Transactions []Transaction `json:"transactions"`
	TxCount      int         `json:"tx_count"`
	Size         uint64      `json:"size"`
	BaseFeePerGas *big.Int   `json:"base_fee_per_gas,omitempty"`
}

// TokenTransfer 表示代币转账事件
type TokenTransfer struct {
	TransactionHash string    `json:"transaction_hash"`
	BlockNumber     uint64    `json:"block_number"`
	LogIndex        uint      `json:"log_index"`
	ContractAddress string    `json:"contract_address"`
	FromAddress     string    `json:"from_address"`
	ToAddress       string    `json:"to_address"`
	TokenAmount     *big.Int  `json:"token_amount"`
	TokenSymbol     string    `json:"token_symbol"`
	TokenDecimals   uint8     `json:"token_decimals"`
	Timestamp       time.Time `json:"timestamp"`
	Network         string    `json:"network"`
}

// Event 表示智能合约事件
type Event struct {
	TransactionHash string      `json:"transaction_hash"`
	BlockNumber     uint64      `json:"block_number"`
	LogIndex        uint        `json:"log_index"`
	ContractAddress string      `json:"contract_address"`
	EventName       string      `json:"event_name"`
	EventSignature  string      `json:"event_signature"`
	Topics          []string    `json:"topics"`
	Data            string      `json:"data"`
	DecodedData     interface{} `json:"decoded_data,omitempty"`
	Timestamp       time.Time   `json:"timestamp"`
	Network         string      `json:"network"`
}

// RiskAlert 表示风险告警
type RiskAlert struct {
	ID              string                 `json:"id"`
	Type            string                 `json:"type"`
	Level           string                 `json:"level"`
	Title           string                 `json:"title"`
	Description     string                 `json:"description"`
	TransactionHash string                 `json:"transaction_hash,omitempty"`
	Address         string                 `json:"address,omitempty"`
	Network         string                 `json:"network"`
	RiskScore       float64                `json:"risk_score"`
	RiskFactors     []string               `json:"risk_factors"`
	Metadata        map[string]interface{} `json:"metadata"`
	Timestamp       time.Time              `json:"timestamp"`
	Status          string                 `json:"status"`
}

// NetworkStats 表示网络统计信息
type NetworkStats struct {
	Network          string    `json:"network"`
	LatestBlock      uint64    `json:"latest_block"`
	TotalTxProcessed uint64    `json:"total_tx_processed"`
	TxPerSecond      float64   `json:"tx_per_second"`
	AvgBlockTime     float64   `json:"avg_block_time"`
	LastUpdateTime   time.Time `json:"last_update_time"`
	IsHealthy        bool      `json:"is_healthy"`
	ErrorCount       uint64    `json:"error_count"`
}

// ProcessingResult 表示数据处理结果
type ProcessingResult struct {
	TransactionHash string `json:"transaction_hash"`
	Processed       bool   `json:"processed"`
	FilterMatched   bool   `json:"filter_matched"`
	RiskDetected    bool   `json:"risk_detected"`
	ProcessingTime  int64  `json:"processing_time_ms"`
	ErrorMessage    string `json:"error_message,omitempty"`
}

// FilterResult 表示过滤结果
type FilterResult struct {
	ShouldProcess   bool     `json:"should_process"`
	FilteredReasons []string `json:"filtered_reasons"`
	RiskScore       float64  `json:"risk_score"`
}

// ContractInfo 表示智能合约信息
type ContractInfo struct {
	Address       string    `json:"address"`
	Name          string    `json:"name,omitempty"`
	Symbol        string    `json:"symbol,omitempty"`
	Decimals      uint8     `json:"decimals,omitempty"`
	TotalSupply   *big.Int  `json:"total_supply,omitempty"`
	ContractType  string    `json:"contract_type"`
	IsVerified    bool      `json:"is_verified"`
	CreationBlock uint64    `json:"creation_block"`
	Creator       string    `json:"creator"`
	CreatedAt     time.Time `json:"created_at"`
	Network       string    `json:"network"`
	ABI           string    `json:"abi,omitempty"`
	SourceCode    string    `json:"source_code,omitempty"`
}