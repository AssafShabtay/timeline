package com.example.myapplication.dataportability

import androidx.compose.ui.graphics.vector.Path
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

private const val BASE_URL = "https://dataportability.googleapis.com/"

interface DataPortabilityApi {
    @POST("v1/portabilityArchive:initiate")
    suspend fun initiate(@Body body: InitiateRequest): InitiateResponse

    @GET("v1/archiveJobs/{id}/portabilityArchiveState")
    suspend fun getState(@Path("id") id: String): ArchiveStateResponse
}

object DataPortabilityClient {
    fun create(accessToken: String): DataPortabilityApi {
        val ok = OkHttpClient.Builder().addInterceptor { chain ->
            val req: Request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            chain.proceed(req)
        }.build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(ok)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(DataPortabilityApi::class.java)
    }
}