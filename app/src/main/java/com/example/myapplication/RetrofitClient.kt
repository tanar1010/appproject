package com.example.myapplication // 본인의 패키지명과 일치하는지 확인하세요

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 기상청 단기예보 서비스 주소
    private const val BASE_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/"

    val service: WeatherInterface by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // JSON을 WeatherResponse 객체로 변환
            .build()
            .create(WeatherInterface::class.java)
    }
}