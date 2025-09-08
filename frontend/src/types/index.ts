// 通用API响应类型
export interface ApiResponse<T = any> {
  success: boolean
  data?: T
  message?: string
  code?: number
  timestamp?: string
}

export interface PaginatedResponse<T> extends ApiResponse<T[]> {
  pagination: {
    current: number
    pageSize: number
    total: number
    totalPages: number
  }
}

// 风险等级枚举
export enum RiskLevel {
  INFO = 'INFO',
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  CRITICAL = 'CRITICAL'
}

// 风险交易类型
export interface RiskTransaction {
  id: number
  txHash: string
  blockNumber: number
  blockHash?: string
  transactionIndex?: number
  fromAddress: string
  toAddress?: string
  value: string
  gasLimit?: number
  gasPrice?: string
  gasUsed?: number
  nonce?: number
  inputData?: string
  timestamp: string
  network: string
  status?: number
  contractAddress?: string
  isContractCall: boolean
  isTokenTransfer: boolean
  tokenSymbol?: string
  tokenAmount?: string
  tokenDecimals?: number
  
  // 风险评估字段
  riskScore: number
  riskLevel: RiskLevel
  riskFactors: string[]
  riskMetadata?: string
  processed: boolean
  processingTime?: number
  errorMessage?: string
  createdAt: string
  updatedAt: string
}

// 风险地址类型
export interface RiskAddress {
  id: number
  address: string
  network: string
  riskScore: number
  riskLevel: RiskLevel
  riskTags: string[]
  addressType?: string
  firstSeen?: string
  lastActivity?: string
  transactionCount: number
  sentCount: number
  receivedCount: number
  totalVolumeSent: string
  totalVolumeReceived: string
  uniqueCounterparties: number
  maxSingleTransaction: string
  avgTransactionValue: string
  
  // 黑名单相关
  isBlacklisted: boolean
  blacklistSource?: string
  blacklistReason?: string
  blacklistedAt?: string
  
  // 标签相关
  labels: string[]
  labelSource?: string
  labelConfidence?: number
  
  // 合约相关
  contractName?: string
  contractCreator?: string
  creationBlock?: number
  creationTimestamp?: string
  isVerified: boolean
  proxyType?: string
  
  // 交易所相关
  exchangeName?: string
  isExchangeWallet: boolean
  isHotWallet: boolean
  isColdWallet: boolean
  
  // DeFi相关
  defiProtocols?: string
  isLiquidityProvider: boolean
  isYieldFarmer: boolean
  
  // 风险行为统计
  suspiciousActivityCount: number
  highRiskInteractions: number
  mixerInteractions: number
  sanctionInteractions: number
  
  // 时间模式分析
  mostActiveHour?: number
  weekendActivityRatio?: number
  nightActivityRatio?: number
  
  // 地理位置相关
  estimatedCountry?: string
  estimatedRegion?: string
  vpnUsageDetected: boolean
  
  // 元数据
  metadata?: string
  notes?: string
  analystReview: boolean
  reviewedBy?: string
  reviewedAt?: string
  
  createdAt: string
  updatedAt: string
}

// 告警类型
export enum AlertType {
  HIGH_RISK_TRANSACTION = 'HIGH_RISK_TRANSACTION',
  BLACKLIST_ADDRESS = 'BLACKLIST_ADDRESS',
  LARGE_TRANSFER = 'LARGE_TRANSFER',
  SUSPICIOUS_BEHAVIOR = 'SUSPICIOUS_BEHAVIOR',
  CONTRACT_EXPLOIT = 'CONTRACT_EXPLOIT',
  PHISHING_ATTACK = 'PHISHING_ATTACK',
  MONEY_LAUNDERING = 'MONEY_LAUNDERING',
  FLASH_LOAN_ATTACK = 'FLASH_LOAN_ATTACK',
  MEV_ATTACK = 'MEV_ATTACK',
  GOVERNANCE_ATTACK = 'GOVERNANCE_ATTACK',
  BRIDGE_EXPLOIT = 'BRIDGE_EXPLOIT',
  UNUSUAL_GAS_USAGE = 'UNUSUAL_GAS_USAGE',
  FAILED_TRANSACTION_BURST = 'FAILED_TRANSACTION_BURST',
  NEW_TOKEN_SCAM = 'NEW_TOKEN_SCAM',
  SANDWICH_ATTACK = 'SANDWICH_ATTACK',
  SYSTEM_ERROR = 'SYSTEM_ERROR',
  PERFORMANCE_DEGRADATION = 'PERFORMANCE_DEGRADATION',
  DATA_QUALITY_ISSUE = 'DATA_QUALITY_ISSUE'
}

export enum AlertStatus {
  ACTIVE = 'ACTIVE',
  ACKNOWLEDGED = 'ACKNOWLEDGED',
  INVESTIGATING = 'INVESTIGATING',
  RESOLVED = 'RESOLVED',
  CLOSED = 'CLOSED',
  SUPPRESSED = 'SUPPRESSED'
}

export enum Severity {
  CRITICAL = 'CRITICAL',
  HIGH = 'HIGH',
  MEDIUM = 'MEDIUM',
  LOW = 'LOW',
  INFO = 'INFO'
}

export interface Alert {
  id: number
  alertId: string
  alertType: AlertType
  severity: Severity
  status: AlertStatus
  source: string
  title: string
  description?: string
  targetAddress?: string
  transactionHash?: string
  blockNumber?: number
  network?: string
  riskScore?: number
  tags: string[]
  metadata?: string
  triggerRule?: string
  
  // 通知相关
  notified: boolean
  notificationChannels: string[]
  notificationCount: number
  lastNotificationAt?: string
  
  // 升级相关
  escalated: boolean
  escalatedAt?: string
  escalationReason?: string
  
  // 处理相关
  assignedTo?: string
  resolvedAt?: string
  resolutionReason?: string
  falsePositive: boolean
  feedbackScore?: number
  feedbackNotes?: string
  
  // 关联信息
  relatedAlertIds: string[]
  parentAlertId?: string
  childAlertCount: number
  durationSeconds?: number
  autoResolveAt?: string
  
  createdAt: string
  updatedAt: string
}

// 规则类型
export enum RuleType {
  TRANSACTION = 'TRANSACTION',
  ADDRESS = 'ADDRESS',
  PATTERN = 'PATTERN',
  BEHAVIORAL = 'BEHAVIORAL',
  BLACKLIST = 'BLACKLIST',
  WHITELIST = 'WHITELIST',
  THRESHOLD = 'THRESHOLD',
  TIME_BASED = 'TIME_BASED',
  NETWORK = 'NETWORK',
  COMPOSITE = 'COMPOSITE'
}

export enum RuleCategory {
  AML = 'AML',
  SANCTIONS = 'SANCTIONS',
  FRAUD = 'FRAUD',
  MARKET_MANIPULATION = 'MARKET_MANIPULATION',
  INSIDER_TRADING = 'INSIDER_TRADING',
  MEV = 'MEV',
  PHISHING = 'PHISHING',
  MIXER = 'MIXER',
  DEFI_EXPLOIT = 'DEFI_EXPLOIT',
  FLASH_LOAN = 'FLASH_LOAN',
  GOVERNANCE = 'GOVERNANCE',
  BRIDGE_EXPLOIT = 'BRIDGE_EXPLOIT',
  GENERAL = 'GENERAL'
}

export enum RuleAction {
  ALERT = 'ALERT',
  BLOCK = 'BLOCK',
  MONITOR = 'MONITOR',
  LOG = 'LOG',
  SCORE = 'SCORE',
  FLAG = 'FLAG',
  QUARANTINE = 'QUARANTINE',
  REPORT = 'REPORT'
}

export interface RiskRule {
  id: number
  ruleName: string
  ruleDescription?: string
  ruleType: RuleType
  ruleCategory?: RuleCategory
  conditionJson: string
  action: RuleAction
  riskWeight: number
  severity: Severity
  priority: number
  enabled: boolean
  networks?: string
  
  // 统计信息
  executionCount: number
  matchCount: number
  falsePositiveCount: number
  lastExecution?: string
  lastMatch?: string
  executionTimeAvg: number
  executionTimeMax: number
  
  // 元信息
  author?: string
  version: string
  validFrom?: string
  validTo?: string
  tags?: string
  metadata?: string
  testCases?: string
  
  createdAt: string
  updatedAt: string
}

// 统计数据类型
export interface DashboardStats {
  totalTransactions: number
  highRiskTransactions: number
  totalAddresses: number
  highRiskAddresses: number
  activeAlerts: number
  criticalAlerts: number
  rulesExecuted: number
  avgRiskScore: number
}

export interface ChartData {
  name: string
  value: number
  time?: string
}

export interface TimeSeriesData {
  timestamp: string
  value: number
  category?: string
}

// 搜索和筛选参数
export interface SearchParams {
  keyword?: string
  network?: string
  riskLevel?: RiskLevel
  alertType?: AlertType
  status?: string
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

// 表格列配置
export interface TableColumn {
  key: string
  title: string
  dataIndex?: string
  width?: number | string
  align?: 'left' | 'center' | 'right'
  sorter?: boolean | ((a: any, b: any) => number)
  filters?: Array<{ text: string; value: any }>
  render?: (value: any, record: any, index: number) => React.ReactNode
  fixed?: 'left' | 'right'
}

// 导出选项
export interface ExportOptions {
  format: 'excel' | 'csv' | 'pdf'
  includeHeaders: boolean
  dateRange?: {
    start: string
    end: string
  }
  filters?: Record<string, any>
}

// WebSocket消息类型
export interface WSMessage<T = any> {
  type: string
  data: T
  timestamp: string
}

export interface RealTimeUpdate {
  type: 'new_transaction' | 'new_alert' | 'rule_triggered' | 'status_update'
  payload: any
}