package com.football.livescores.matchlive.di

import android.content.Context
import com.football.livescores.matchlive.data.remote.ApiService
import com.football.livescores.matchlive.data.remote.RemoteConfigManager
import com.football.livescores.matchlive.data.remote.DecryptionInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.1teps.com/livescore/"

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        loggingInterceptor: HttpLoggingInterceptor,
        requestLimitInterceptor: RequestLimitInterceptor,
        remoteConfigManager: RemoteConfigManager
    ): OkHttpClient {
        val param1 = "1"
        val param2 = "1"

        return OkHttpClient.Builder()
            .addInterceptor(requestLimitInterceptor)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val newUrl = originalRequest.url.newBuilder()
                    .addQueryParameter("param1", param1)
                    .addQueryParameter("param2", param2)
                    .build()

                val request = originalRequest.newBuilder()
                    .url(newUrl)
                    .addHeader("x-param1", param1)
                    .addHeader("x-param2", param2)
                    .addHeader("x-apisports-key", remoteConfigManager.getApiKey())
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .addInterceptor(DecryptionInterceptor(param1, param2))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideLiveScoreApiService(retrofit: Retrofit): com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.LiveScoreApiService {
        return retrofit.create(com.football.livescores.matchlive.utils.LivescoreTrackingSDKKotlin.LiveScoreApiService::class.java)
    }


    @Provides
    @Singleton
    fun provideGsmAuthInterceptor(): com.football.livescores.matchlive.data.remote.gsm.GsmAuthInterceptor {
        return com.football.livescores.matchlive.data.remote.gsm.GsmAuthInterceptor()
    }

    @Provides
    @Singleton
    @GsmNetwork
    fun provideGsmOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        gsmAuthInterceptor: com.football.livescores.matchlive.data.remote.gsm.GsmAuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(gsmAuthInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @GsmNetwork
    fun provideGsmRetrofit(@GsmNetwork okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(com.football.livescores.matchlive.data.remote.gsm.GsmConfig.CURRENT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideGsmApiService(@GsmNetwork retrofit: Retrofit): com.football.livescores.matchlive.data.remote.gsm.GsmApiService {
        return retrofit.create(com.football.livescores.matchlive.data.remote.gsm.GsmApiService::class.java)
    }
}

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GsmNetwork
