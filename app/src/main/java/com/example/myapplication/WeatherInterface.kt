package com.example.myapplication

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherInterface {
    @GET("getVilageFcst")
    fun getWeather(
        @Query("serviceKey") key: String,
        @Query("numOfRows") num: Int,
        @Query("pageNo") page: Int,
        @Query("dataType") type: String,
        @Query("base_date") date: String,
        @Query("base_time") time: String,
        @Query("nx") nx: Int,
        @Query("ny") ny: Int
    ): Call<WeatherResponse>
}