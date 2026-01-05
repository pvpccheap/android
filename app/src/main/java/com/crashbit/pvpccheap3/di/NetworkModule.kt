package com.crashbit.pvpccheap3.di

import com.crashbit.pvpccheap3.BuildConfig
import com.crashbit.pvpccheap3.data.api.AuthInterceptor
import com.crashbit.pvpccheap3.data.api.PvpcApi
import com.crashbit.pvpccheap3.data.api.TokenAuthenticator
import com.crashbit.pvpccheap3.data.local.AuthEvent
import com.crashbit.pvpccheap3.data.local.AuthStateManager
import com.crashbit.pvpccheap3.data.local.TokenManager
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        tokenManager: TokenManager,
        authStateManager: AuthStateManager,
        gson: Gson
    ): TokenAuthenticator {
        return TokenAuthenticator(
            tokenManager = tokenManager,
            baseUrl = BuildConfig.API_BASE_URL,
            gson = gson
        ).apply {
            setOnAuthenticationRequired {
                authStateManager.tryEmitEvent(AuthEvent.AuthenticationRequired)
            }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(tokenAuthenticator)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providePvpcApi(retrofit: Retrofit): PvpcApi {
        return retrofit.create(PvpcApi::class.java)
    }
}
