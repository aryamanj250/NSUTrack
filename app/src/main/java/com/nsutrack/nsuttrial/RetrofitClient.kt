package com.nsutrack.nsuttrial

import com.google.gson.JsonObject
import com.yourname.nsutrack.data.model.LoginRequest
import com.yourname.nsutrack.data.model.SessionResponse
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

// Updated RetrofitClient.kt with optimized configuration
object RetrofitClient {
    private const val BASE_URL = "http://10.50.56.185:6969/"

    // Configure OkHttpClient with optimized parameters
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)     // Reduced from 30 seconds
            .readTimeout(30, TimeUnit.SECONDS)       // Reduced from 60 seconds
            .writeTimeout(10, TimeUnit.SECONDS)      // Reduced from 30 seconds
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    // You can keep the instance property as an alias if needed
    val instance: ApiService get() = apiService
}

interface ApiService {
    // Step 1: Get session ID
    @GET("get-captcha")
    suspend fun getSessionId(): SessionResponse

    // Step 4: Submit credentials
    @POST("submit-text")
    suspend fun login(@Body request: LoginRequest): Response<Unit>

    // Step 7: Check for login errors
    @GET("check-login-errors/{sessionId}")
    suspend fun checkLoginErrors(@Path("sessionId") sessionId: String): JsonObject

    // Step 9: Get attendance data
    @GET("notify-attendance/{sessionId}")
    suspend fun getAttendanceData(@Path("sessionId") sessionId: String): Response<ResponseBody>

    // Additional endpoints for profile and timetable data
    @GET("notify-profile/{sessionId}")
    suspend fun getProfileData(@Path("sessionId") sessionId: String): Response<ResponseBody>

    // Timetable endpoint
    @GET("notify-timetable/{sessionId}")
    suspend fun getTimetableData(@Path("sessionId") sessionId: String): Response<ResponseBody>


}