package com.atipera.recruitment.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.context.annotation.RequestScope


@Configuration
class CoroutineConfig {

    /* Provides a new IO coroutine scope bean for each request.
    This approach avoids a global coroutine scope but also there
    is no need to create a coroutine scope in every local function. */
    @Bean
    @RequestScope
    fun coroutineScope(): CoroutineScope {
        return CoroutineScope(Dispatchers.IO)
    }
}