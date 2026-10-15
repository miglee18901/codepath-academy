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
        SpringApplication.run(CodePathAcademyApplication.class, args);
    }
}
