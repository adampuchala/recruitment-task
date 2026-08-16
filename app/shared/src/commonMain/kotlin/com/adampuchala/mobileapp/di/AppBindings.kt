package com.adampuchala.mobileapp.di

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.adampuchala.mobileapp.FakeTimeProvider
import com.adampuchala.mobileapp.ServerBasedTimeProvider
import com.adampuchala.mobileapp.TimeProvider
import com.adampuchala.mobileapp.URLs
import com.adampuchala.mobileapp.flags.Flags
import com.adampuchala.mobileapp.network.ApplicationApi
import com.adampuchala.mobileapp.storage.ApplicationStorage
import com.adampuchala.mobileapp.utils.Logger
import kotlin.reflect.KClass
import io.ktor.client.plugins.logging.Logger as KtorLogger

@BindingContainer
@ContributesTo(AppScope::class)
object AppBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideMetroViewModelFactory(
        viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
        manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
    ): MetroViewModelFactory = object : MetroViewModelFactory() {
        override val viewModelProviders get() = viewModelProviders
        override val manualAssistedFactoryProviders get() = manualAssistedFactoryProviders
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(
        applicationStorage: ApplicationStorage,
        @BaseUrl baseUrl: String,
        logger: Logger,
    ): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }

            install(Logging) {
                level = LogLevel.HEADERS
                this.logger = object : KtorLogger {
                    override fun log(message: String) {
                        logger.log("HttpClient") { message }
                    }
                }
            }

            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = 5000
            }

            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                exponentialDelay()
            }

            install(DefaultRequest) {
                url.takeFrom(baseUrl)
            }
        }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideTimeProvider(
        applicationStorage: ApplicationStorage,
        logger: Lazy<Logger>,
        applicationApi: Lazy<ApplicationApi>,
    ): TimeProvider {
        val flags = applicationStorage.getFlagsBlocking()
        return when {
            flags != null && flags.useFakeTime -> FakeTimeProvider(logger.value)
            else -> ServerBasedTimeProvider(applicationApi.value)
        }
    }
}
