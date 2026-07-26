package com.nutriai.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.nutriai.BuildConfig
import com.nutriai.data.remote.AuthInterceptor
import com.nutriai.data.remote.NutriApi
import com.nutriai.data.remote.RefreshApi
import com.nutriai.data.remote.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator) // refresh + retry on 401
            .addInterceptor(logging)
            // Render free tier can cold-start (~30-60s); be generous on the read timeout.
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(70, TimeUnit.SECONDS)
            .build()
    }

    /** Bare Retrofit (no interceptor/authenticator) used only to refresh tokens. */
    @Provides
    @Singleton
    fun provideRefreshApi(json: Json): RefreshApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(70, TimeUnit.SECONDS)
            .build()
        val base = BuildConfig.API_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(RefreshApi::class.java)
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        // Retrofit requires the base URL to end with '/'.
        val base = BuildConfig.API_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideNutriApi(retrofit: Retrofit): NutriApi = retrofit.create(NutriApi::class.java)
}
