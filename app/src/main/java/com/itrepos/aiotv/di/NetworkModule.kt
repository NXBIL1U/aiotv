package com.itrepos.aiotv.di

import com.itrepos.aiotv.data.remote.iptv.XtreamApi
import com.itrepos.aiotv.data.remote.stremio.StremioApi
import com.itrepos.aiotv.data.remote.torbox.TorBoxApi
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
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .followRedirects(true)
        .build()

    @Provides
    @Singleton
    @Named("torbox")
    fun provideTorBoxRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.torbox.app/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @Named("generic")
    fun provideGenericRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://placeholder.invalid/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideTorBoxApi(@Named("torbox") retrofit: Retrofit): TorBoxApi =
        retrofit.create(TorBoxApi::class.java)

    @Provides
    @Singleton
    fun provideStremioApi(@Named("generic") retrofit: Retrofit): StremioApi =
        retrofit.create(StremioApi::class.java)

    @Provides
    @Singleton
    fun provideXtreamApi(@Named("generic") retrofit: Retrofit): XtreamApi =
        retrofit.create(XtreamApi::class.java)
}
