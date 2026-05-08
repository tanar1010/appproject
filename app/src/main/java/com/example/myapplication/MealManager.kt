package com.example.myapplication

import android.util.TypedValue
import android.widget.TextView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class MealManager(private val dataDisplay: TextView) {

    private val API_KEY = "e405ca73355d4c37b66461abb154aaaf" // 여기에 나이스 키를 넣으세요
    private val OFFICE_CODE = "J10" // 교육청 코드 (서울: B10)
    private val SCHOOL_CODE = "7530102" // 학교 코드 (본인 학교 코드)

    fun fetchTodayMeal() {
        // 1. 현재 날짜 구하기 (오늘)
        val cal = Calendar.getInstance()
        val targetDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.time)
        val displayDate = SimpleDateFormat("MM월 dd일", Locale.getDefault()).format(cal.time)

        dataDisplay.text = "오늘($displayDate)의 급식을 불러오는 중..."

        MealRetrofitClient.service.getMeal(API_KEY, "json", 1, 10, OFFICE_CODE, SCHOOL_CODE, targetDate)
            .enqueue(object : Callback<MealResponse> {
                override fun onResponse(call: Call<MealResponse>, response: Response<MealResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()

                        // 나이스 API의 데이터 존재 여부 확인
                        val info = responseBody?.mealServiceDietInfo

                        if (info != null && info.size > 1) {
                            val mealRow = info[1].row?.get(0)
                            if (mealRow != null) {
                                // 메뉴 이름 정제 (알레르기 번호 및 줄바꿈 처리)
                                val cleanMenu = mealRow.DDISH_NM
                                    ?.replace(Regex("[0-9.*]+"), "") // 숫자와 마침표, 별표 제거
                                    ?.replace("<br/>", "\n")        // <br/> 태그를 줄바꿈으로 변경
                                    ?.trim()

                                dataDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                                dataDisplay.text = "🍱 오늘의 메뉴 🍱\n\n$cleanMenu"
                                dataDisplay.gravity = android.view.Gravity.CENTER
                            }
                        } else {
                            // 주말이나 공휴일 등 데이터가 없는 경우
                            dataDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                            dataDisplay.text = "오늘($displayDate)은\n급식 정보가 없습니다. ✨"
                            dataDisplay.gravity = android.view.Gravity.CENTER
                        }
                    } else {
                        dataDisplay.text = "데이터를 가져오는 데 실패했습니다."
                    }
                }

                override fun onFailure(call: Call<MealResponse>, t: Throwable) {
                    dataDisplay.text = "네트워크 상태를 확인해주세요."
                }
            })
    }
}