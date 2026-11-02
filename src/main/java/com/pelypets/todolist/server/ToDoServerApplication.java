package com.pelypets.todolist.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ToDoServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToDoServerApplication.class, args);
    }
}
