package com.example.myapplication

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    @SerializedName("response")
    val response: WeatherData
)

data class WeatherData(
    @SerializedName("header")
    val header: WeatherHeader,
    @SerializedName("body")
    val body: WeatherBody?
)

data class WeatherHeader(
    @SerializedName("resultCode")
    val resultCode: String,
    @SerializedName("resultMsg")
    val resultMsg: String
)

data class WeatherBody(
    @SerializedName("items")
    val items: WeatherItems
)

data class WeatherItems(
    @SerializedName("item")
    val item: List<WeatherItem>
)

// 여기서 fcstTime이 정의되어야 MainActivity에서 오류가 안 납니다.
data class WeatherItem(
    @SerializedName("category")
    val category: String,
    @SerializedName("fcstDate")
    val fcstDate: String,
    @SerializedName("fcstTime")
    val fcstTime: String,
    @SerializedName("fcstValue")
    val fcstValue: String,
    @SerializedName("nx")
    val nx: Int,
    @SerializedName("ny")
    val ny: Int
)