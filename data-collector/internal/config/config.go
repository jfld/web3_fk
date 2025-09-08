package config

import (
	"github.com/spf13/viper"
)

type Config struct {
	Server         ServerConfig         `yaml:"server"`
	Blockchain     BlockchainConfig     `yaml:"blockchain"`
	Kafka          KafkaConfig          `yaml:"kafka"`
	InfluxDB       InfluxDBConfig       `yaml:"influxdb"`
	Redis          RedisConfig          `yaml:"redis"`
	Logging        LoggingConfig        `yaml:"logging"`
	Metrics        MetricsConfig        `yaml:"metrics"`
	DataProcessing DataProcessingConfig `yaml:"data_processing"`
}

type ServerConfig struct {
	Port int    `yaml:"port"`
	Mode string `yaml:"mode"`
}

type BlockchainConfig struct {
	Networks map[string]NetworkConfig `yaml:"networks"`
}

type NetworkConfig struct {
	RPCURL  string `yaml:"rpc_url"`
	WSURL   string `yaml:"ws_url"`
	ChainID int64  `yaml:"chain_id"`
	Enabled bool   `yaml:"enabled"`
}

type KafkaConfig struct {
	Brokers  []string     `yaml:"brokers"`
	Topics   TopicsConfig `yaml:"topics"`
	Producer ProducerConfig `yaml:"producer"`
}

type TopicsConfig struct {
	Transactions string `yaml:"transactions"`
	Blocks       string `yaml:"blocks"`
	Alerts       string `yaml:"alerts"`
}

type ProducerConfig struct {
	BatchSize    int    `yaml:"batch_size"`
	BatchTimeout string `yaml:"batch_timeout"`
}

type InfluxDBConfig struct {
	URL    string `yaml:"url"`
	Token  string `yaml:"token"`
	Org    string `yaml:"org"`
	Bucket string `yaml:"bucket"`
}

type RedisConfig struct {
	Host     string `yaml:"host"`
	Port     int    `yaml:"port"`
	Password string `yaml:"password"`
	DB       int    `yaml:"db"`
}

type LoggingConfig struct {
	Level  string `yaml:"level"`
	Format string `yaml:"format"`
}

type MetricsConfig struct {
	Enabled bool   `yaml:"enabled"`
	Path    string `yaml:"path"`
}

type DataProcessingConfig struct {
	FilterRules FilterRulesConfig `yaml:"filter_rules"`
	BatchSize   int               `yaml:"batch_size"`
	Workers     int               `yaml:"workers"`
}

type FilterRulesConfig struct {
	MinValueWei      string   `yaml:"min_value_wei"`
	ExcludeContracts []string `yaml:"exclude_contracts"`
	IncludeAddresses []string `yaml:"include_addresses"`
}

func Load(configPath string) (*Config, error) {
	viper.SetConfigFile(configPath)
	viper.SetConfigType("yaml")

	// 设置默认值
	setDefaults()

	// 读取配置文件
	if err := viper.ReadInConfig(); err != nil {
		return nil, err
	}

	// 允许环境变量覆盖配置
	viper.AutomaticEnv()

	var config Config
	if err := viper.Unmarshal(&config); err != nil {
		return nil, err
	}

	return &config, nil
}

func setDefaults() {
	viper.SetDefault("server.port", 8082)
	viper.SetDefault("server.mode", "debug")
	viper.SetDefault("logging.level", "info")
	viper.SetDefault("logging.format", "text")
	viper.SetDefault("metrics.enabled", true)
	viper.SetDefault("metrics.path", "/metrics")
	viper.SetDefault("data_processing.batch_size", 50)
	viper.SetDefault("data_processing.workers", 10)
}