# Web3区块链风控监控平台

## 项目概述

本项目是一个基于Java和Go语言的Web3区块链风控监控平台，主要用于实时监控区块链交易、识别潜在风险、生成告警和提供可视化分析。

### 技术架构

- **后端服务**: Java 17 + Spring Boot 3.x
- **数据采集**: Go + Gin + go-ethereum  
- **前端界面**: React 18 + TypeScript + Ant Design
- **数据库**: PostgreSQL + Redis + InfluxDB
- **消息队列**: Apache Kafka
- **容器化**: Docker + Docker Compose
- **监控**: Prometheus + Grafana

### 项目结构

```
├── backend/                    # Java后端服务
│   ├── risk-engine/           # 风险引擎服务
│   ├── alert-service/         # 告警服务
│   ├── user-service/          # 用户服务
│   └── report-service/        # 报告服务
├── data-collector/            # Go数据采集服务
│   ├── blockchain-connector/  # 区块链连接器
│   ├── data-processor/        # 数据处理器
│   └── message-publisher/     # 消息发布器
├── frontend/                  # React前端应用
├── docker/                    # Docker配置文件
├── docs/                      # 项目文档
└── scripts/                   # 部署和运维脚本
```

### 核心功能模块

#### 1. 实时区块链数据采集与监控
- 多链数据采集(以太坊、BSC、Polygon、Arbitrum)
- 实时交易监控和事件订阅
- 数据清洗和标准化处理
- 内存池监控和预警

#### 2. 智能风险识别与评估
- 地址风险评估和黑名单检测
- 交易行为异常分析
- 智能合约安全扫描
- 资金流向风险评估

#### 3. 告警管理与通知系统
- 多级别告警机制
- 实时告警触发(5秒内)
- 多渠道通知(邮件、短信、微信等)
- 告警处理流程管理

#### 4. 交易行为深度分析
- 用户行为画像分析
- 机器学习异常检测
- 关联关系图谱分析
- 模式识别和聚类分析

#### 5. 资金流向追踪系统
- 可视化资金流向图
- 多跳转账路径重构
- 跨链资金追踪
- 合规性检查(AML/KYC)

#### 6. 风险报告与分析
- 实时风险仪表盘
- 定期风险报告生成
- 自定义分析工具
- 数据导出功能

### 快速开始

#### 环境要求
- Java 17+
- Go 1.21+
- Node.js 18+
- Docker & Docker Compose
- PostgreSQL 15+
- Redis 7+

#### 启动服务

1. 启动基础设施
```bash
cd docker
docker-compose up -d postgres redis kafka
```

2. 启动Go数据采集服务
```bash
cd data-collector
go mod tidy
go run main.go
```

3. 启动Java后端服务
```bash
cd backend/risk-engine
./mvnw spring-boot:run
```

4. 启动前端应用
```bash
cd frontend
npm install
npm start
```

### API文档

- Swagger UI: http://localhost:8080/swagger-ui.html
- API文档: http://localhost:8080/v3/api-docs

### 监控面板

- Grafana: http://localhost:3000
- Prometheus: http://localhost:9090

### 许可证

MIT License