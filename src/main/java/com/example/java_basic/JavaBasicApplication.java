package com.example.java_basic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.jms.annotation.EnableJms;

@SpringBootApplication
@EnableJms
@EnableCaching
public class JavaBasicApplication {

  public static void main(String[] args) {
    SpringApplication.run(JavaBasicApplication.class, args);
  }
}
