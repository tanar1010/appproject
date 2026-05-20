package com.example.myapplication

import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MealManager(private val dataDisplay: TextView) {

    private val API_KEY = "e405ca73355d4c37b66461abb154aaaf"
    private val OFFICE_CODE = "J10"
    private val SCHOOL_CODE = "7530102"

    // 🔍 사용자가 현재 보고 있는 날짜를 기억할 변수
    private var currentCalendar: Calendar = Calendar.getInstance()

    // 하루 앞으로 이동
    fun moveToNextDay() {
        currentCalendar.add(Calendar.DAY_OF_YEAR, 1)
        fetchMeal()
    }

    // 하루 뒤로 이동
    fun moveToPrevDay() {
        currentCalendar.add(Calendar.DAY_OF_YEAR, -1)
        fetchMeal()
    }

    // 오늘 날짜로 초기화해서 가져오기 (처음 급식 버튼 눌렀을 때용)
    fun fetchTodayMeal() {
        currentCalendar = Calendar.getInstance()
        fetchMeal()
    }

    // 실제 데이터를 가져와서 꽂아주는 핵심 함수
    private fun fetchMeal() {
        val targetDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(currentCalendar.time)
        val displayDate = SimpleDateFormat("MM월 dd일(E)", Locale.getDefault()).format(currentCalendar.time)

        dataDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        dataDisplay.gravity = Gravity.CENTER
        dataDisplay.text = "$displayDate 급식을 불러오는 중... ⏳"

        MealRetrofitClient.service.getMeal(API_KEY, "json", 1, 10, OFFICE_CODE, SCHOOL_CODE, targetDate)
            .enqueue(object : Callback<MealResponse> {
                override fun onResponse(call: Call<MealResponse>, response: Response<MealResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        val info = responseBody?.mealServiceDietInfo

                        if (info != null && info.size > 1) {
                            val mealRow = info[1].row?.get(0)
                            if (mealRow != null) {
                                val cleanMenu = mealRow.DDISH_NM
                                    ?.replace(Regex("[0-9.*]+"), "")
                                    ?.replace("<br/>", "\n")
                                    ?.trim()

                                val kcal = mealRow.CAL_INFO ?: "정보 없음"

                                dataDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                                dataDisplay.gravity = Gravity.CENTER
                                dataDisplay.text = "🍱 $displayDate 메뉴 🍱\n\n$cleanMenu\n\n📊 칼로리: $kcal"
                            }
                        } else {
                            dataDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                            dataDisplay.gravity = Gravity.CENTER
                            dataDisplay.text = "📅 $displayDate\n\n급식 정보가 없습니다. ✨"
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