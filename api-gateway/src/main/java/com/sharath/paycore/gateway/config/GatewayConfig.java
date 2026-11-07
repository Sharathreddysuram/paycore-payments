package com.sharath.paycore.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway routes. Downstream services already serve under /api/..., so no
 * path rewriting is needed — requests are forwarded to the Eureka
 * load-balanced instances as-is.
 */
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("account-service", r -> r
                        .path("/api/accounts/**")
                        .uri("lb://account-service"))
                .route("transfer-service", r -> r
                        .path("/api/transfers/**")
                        .uri("lb://transfer-service"))
                .build();
    }
}
