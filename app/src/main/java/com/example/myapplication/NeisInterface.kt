package com.example.myapplication

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface NeisInterface {
    @GET("hisTimetable")
    fun getTimetable(
        @Query("KEY") key: String,
        @Query("Type") type: String = "json",
        @Query("ATPT_OFCDC_SC_CODE") officeCode: String = "J10", // 경기도교육청
        @Query("SD_SCHUL_CODE") schoolCode: String = "7530102",   // 용인고등학교
        @Query("GRADE") grade: String = "3",                     // 학년 고정
        @Query("CLASS_NM") classNm: String = "6",                // 반 고정
        @Query("ALL_TI_YMD") date: String                        // 조회 날짜
    ): Call<ResponseBody>
}