package com.sharath.paycore.gateway.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/**
 * Enforces JWT authentication on every proxied request. The /api/auth/**
 * endpoints (login/register) live in the gateway itself and stay public.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/api/auth/")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring("Bearer ".length()).trim();
        }

        if (token != null && !token.isEmpty() && jwtService.isValid(token)) {
            return chain.filter(exchange);
        }

        return unauthorized(exchange, path);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String path) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            ErrorBody body = new ErrorBody(
                    Instant.now().toString(),
                    HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized",
                    "Missing or invalid Bearer token",
                    path);
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception ex) {
            String fallback = "{\"status\":401,\"error\":\"Unauthorized\"}";
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory()
                            .wrap(fallback.getBytes(StandardCharsets.UTF_8))));
        }
    }

    private record ErrorBody(String timestamp, int status, String error, String message, String path) {
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
