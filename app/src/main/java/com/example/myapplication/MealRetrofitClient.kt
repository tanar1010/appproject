package com.example.myapplication

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object MealRetrofitClient {
    private const val BASE_URL = "https://open.neis.go.kr/hub/"

    val service: MealInterface by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MealInterface::class.java)
    }
}