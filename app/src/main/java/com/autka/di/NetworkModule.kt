package com.autka.di

import com.autka.BuildConfig
import com.autka.data.remote.backend.BackendApi
import com.autka.data.remote.rates.NbpApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideNbpApi(client: OkHttpClient, json: Json): NbpApi =
        Retrofit.Builder()
            .baseUrl("https://api.nbp.pl/api/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NbpApi::class.java)

    @Provides
    @Singleton
    fun provideBackendApi(client: OkHttpClient, json: Json): BackendApi =
        Retrofit.Builder()
            // The deployed Worker URL. For local `wrangler dev` on an emulator use
            // "http://10.0.2.2:8787/"; override per build type/flavor for production.
            .baseUrl(BuildConfig.BACKEND_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BackendApi::class.java)
}
