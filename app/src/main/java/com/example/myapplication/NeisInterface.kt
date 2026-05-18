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
        @Query("ATPT_OFCDC_SC_CODE") officeCode: String = "J10", // 경기도교육청 고정
        @Query("SD_SCHUL_CODE") schoolCode: String = "7530102",   // 용인고등학교 고정

        // 고정되어 있던 잘못된 기본값들을 제거하여 호출부 데이터가 그대로 바인딩되도록 수정
        @Query("GRADE") grade: String,
        @Query("CLASS_NM") classNm: String,

        @Query("ALL_TI_YMD") date: String                        // 조회 날짜
    ): Call<ResponseBody>
}