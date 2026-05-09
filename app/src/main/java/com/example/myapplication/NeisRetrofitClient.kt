package com.example.myapplication

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NeisRetrofitClient {
    private const val BASE_URL = "https://open.neis.go.kr/hub/"

    val service: NeisInterface by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NeisInterface::class.java)
    }
}