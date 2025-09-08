#!/bin/bash

# Web3区块链风控监控平台启动脚本

echo "=== Web3区块链风控监控平台启动脚本 ==="

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo "错误: Docker未安装，请先安装Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "错误: Docker Compose未安装，请先安装Docker Compose"
    exit 1
fi

# 进入docker目录
cd docker

echo "1. 启动基础设施服务..."
docker-compose up -d postgres redis kafka zookeeper influxdb prometheus grafana

echo "等待服务启动..."
sleep 30

echo "2. 检查服务状态..."
docker-compose ps

echo "3. 初始化数据库..."
# 这里可以添加数据库初始化脚本

echo "4. 编译Go数据采集服务..."
cd ../data-collector
go mod tidy
go build -o bin/data-collector main.go

echo "5. 启动Go数据采集服务..."
nohup ./bin/data-collector > logs/data-collector.log 2>&1 &
echo "Go数据采集服务已启动，PID: $!"

echo "6. 编译Java风险引擎服务..."
cd ../backend/risk-engine
./mvnw clean package -DskipTests

echo "7. 启动Java风险引擎服务..."
nohup java -jar target/risk-engine-1.0.0.jar > logs/risk-engine.log 2>&1 &
echo "Java风险引擎服务已启动，PID: $!"

echo "8. 服务访问地址:"
echo "   - Grafana监控面板: http://localhost:3000 (admin/admin123)"
echo "   - Prometheus: http://localhost:9090"
echo "   - Go数据采集API: http://localhost:8082/api/v1"
echo "   - Java风险引擎API: http://localhost:8080/api/v1"
echo "   - Swagger API文档: http://localhost:8080/swagger-ui.html"

echo "=== 平台启动完成 ==="
echo "查看日志: tail -f logs/*.log"