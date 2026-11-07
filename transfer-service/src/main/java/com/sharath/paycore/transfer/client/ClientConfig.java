package com.sharath.paycore.transfer.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The {@link LoadBalanced} post-processor on this {@link RestClient.Builder}
 * bean makes {@code lb://} URIs resolve through Eureka.
 */
@Configuration
public class ClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
