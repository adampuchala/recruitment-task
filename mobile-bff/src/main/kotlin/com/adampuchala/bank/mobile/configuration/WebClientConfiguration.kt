// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.mobile.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfiguration {
    @Bean
    fun accountServiceWebClient(@Value("\${mobile.account-service-base-url}") baseUrl: String): WebClient =
        WebClient.builder().baseUrl(baseUrl).build()

    @Bean
    fun financialOperationsWebClient(@Value("\${mobile.financial-operations-service-base-url}") baseUrl: String): WebClient =
        WebClient.builder().baseUrl(baseUrl).build()
}
