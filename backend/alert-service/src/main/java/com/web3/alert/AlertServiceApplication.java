package com.web3.alert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Web3告警服务主应用类
 * 
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableKafka
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class AlertServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertServiceApplication.class, args);
    }
}