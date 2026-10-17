package com.codepath.academy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.jms.annotation.EnableJms;

@SpringBootApplication
@EnableJms
@EnableCaching
public class CodePathAcademyApplication {

    public static void main(String[] args) {
        SpringApplication.run("worker".equals(System.getenv("APP_ROLE"))
                ? com.codepath.worker.WorkerApplication.class : CodePathAcademyApplication.class, args);
    }
}
