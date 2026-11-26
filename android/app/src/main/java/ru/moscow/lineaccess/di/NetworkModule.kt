package ru.moscow.lineaccess.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.moscow.lineaccess.BuildConfig
import ru.moscow.lineaccess.api.AccessPointsApi
import ru.moscow.lineaccess.data.CatalogError
import ru.moscow.lineaccess.data.CatalogRepository
import ru.moscow.lineaccess.data.CatalogResult
import ru.moscow.lineaccess.data.NetworkCatalogRepository

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Named("baseUrl")
    fun baseUrl(): String = BuildConfig.API_BASE_URL

    @Provides
    @Singleton
    fun catalogRepository(@Named("baseUrl") baseUrl: String): CatalogRepository {
        if (baseUrl.isEmpty()) {
            return CatalogRepository { CatalogResult.Failure(CatalogError.NOT_CONFIGURED) }
        }
        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        val api = Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AccessPointsApi::class.java)
        return NetworkCatalogRepository(api, json)
    }
}
