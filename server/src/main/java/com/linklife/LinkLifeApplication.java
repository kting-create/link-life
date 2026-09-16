package com.linklife;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.linklife.**.mapper")
public class LinkLifeApplication {
    public static void main(String[] args) {
        SpringApplication.run(LinkLifeApplication.class, args);
    }
}
