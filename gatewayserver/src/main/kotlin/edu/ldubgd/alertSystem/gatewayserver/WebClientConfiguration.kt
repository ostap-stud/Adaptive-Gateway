package edu.ldubgd.alertSystem.gatewayserver

import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfiguration {

    @Bean
    @LoadBalanced
    fun loadBalancedWebBuilder(): WebClient.Builder = WebClient.builder()

}