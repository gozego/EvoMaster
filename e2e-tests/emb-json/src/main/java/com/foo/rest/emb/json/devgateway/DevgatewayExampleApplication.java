package com.foo.rest.emb.json.devgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableSwagger2
@SpringBootApplication(exclude = SecurityAutoConfiguration.class)
public class DevgatewayExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevgatewayExampleApplication.class, args);
    }
}
