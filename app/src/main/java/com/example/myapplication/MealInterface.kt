package com.example.myapplication

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface MealInterface {
    @GET("mealServiceDietInfo")
    fun getMeal(
        @Query("KEY") key: String,
        @Query("Type") type: String,
        @Query("pIndex") index: Int,
        @Query("pSize") size: Int,
        @Query("ATPT_OFCDC_SC_CODE") officeCode: String,
        @Query("SD_SCHUL_CODE") schoolCode: String,
        @Query("MLSV_YMD") date: String
    ): Call<MealResponse>
}