package com.web3.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Web3用户服务主应用类
 * 
 * @author Web3 Risk Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}