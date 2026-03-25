package com.desafio.prevencao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PrevencaoFraudesApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrevencaoFraudesApplication.class, args);
    }
}
