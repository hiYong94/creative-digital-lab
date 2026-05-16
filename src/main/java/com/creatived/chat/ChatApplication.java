package com.creatived.chat;

import com.creatived.chat.infrastructure.config.RedisProperties;
import com.creatived.chat.infrastructure.config.SnapshotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({SnapshotProperties.class, RedisProperties.class})
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
