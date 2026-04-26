package com.labs.k8s;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class K8sAutoscalingApplication {
    public static void main(String[] args) {
        SpringApplication.run(K8sAutoscalingApplication.class, args);
    }
}
