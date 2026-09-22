package com.epam.rd.autotasks.springemployeecatalog;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.stereotype.Component;

@OpenAPIDefinition(
        info = @Info(
                title = "Employee management service API",
                description = "Приклад реєстрації сервісу в інфраструктурі",
                version = "1.0.0"
        ),
        servers = @Server(
                url = "https://localhost:8443",
                description = "Gateway"
        ),
        security = @SecurityRequirement(
                name = "bearerAuth"
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer"
)
@SpringBootApplication
@EnableDiscoveryClient
public class EmployeeRestService {
    public static void main(String[] args) {
        SpringApplication.run(EmployeeRestService.class, args);
    }
}



