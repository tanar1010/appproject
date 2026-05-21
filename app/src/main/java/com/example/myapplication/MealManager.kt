package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.TextView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class MealManager(private val dataDisplay: TextView) {

    private val API_KEY = "e405ca73355d4c37b66461abb154aaaf" // 형의 나이스 인증키
    private val OFFICE_CODE = "J10" // 교육청 코드
    private val SCHOOL_CODE = "7530102" // 형네 진짜 학교 코드 고정!

    // 💡 좌우 슬라이드로 날짜를 조작하기 위한 캘린더 변수 및 제스처 디텍터 추가
    private var currentCalendar: Calendar = Calendar.getInstance()
    private var gestureDetector: GestureDetector? = null

    // ◀ 오른쪽으로 슬라이드 하면 호출할 이전 날짜 함수
    fun moveToPrevDay() {
        currentCalendar.add(Calendar.DAY_OF_YEAR, -1)
        fetchMeal()
    }

    // ▶ 왼쪽으로 슬라이드 하면 호출할 다음 날짜 함수
    fun moveToNextDay() {
        currentCalendar.add(Calendar.DAY_OF_YEAR, 1)
        fetchMeal()
    }

    // 처음 급식 메뉴 버튼을 눌렀을 때 실행될 초기화 함수
    fun fetchTodayMeal() {
        currentCalendar = Calendar.getInstance() // 오늘 날짜로 리셋
        fetchMeal()
    }

    // 🚀 핵심 급식 데이터 호출부 (원본 MealRetrofitClient 구조 완벽 유지)
    fun fetchMeal() {
        val targetDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(currentCalendar.time)
        val displayDate = SimpleDateFormat("MM월 dd일(E)", Locale.getDefault()).format(currentCalendar.time)

        dataDisplay.text = "$displayDate 급식을 불러오는 중..."
        dataDisplay.scrollTo(0, 0) // 날짜 바뀔 때 스크롤 상단으로 초기화

        MealRetrofitClient.service.getMeal(API_KEY, "json", 1, 10, OFFICE_CODE, SCHOOL_CODE, targetDate)
            .enqueue(object : Callback<MealResponse> {
                override fun onResponse(call: Call<MealResponse>, response: Response<MealResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        val info = responseBody?.mealServiceDietInfo

                        if (info != null && info.size > 1) {
                            val mealRow = info[1].row?.get(0)
                            if (mealRow != null) {
                                // 메뉴 이름 정제 (알레르기 번호 및 줄바꿈 처리)
                                val cleanMenu = mealRow.DDISH_NM
                                    ?.replace(Regex("[0-9.*()]+"), "") // 숫자, 마침표, 별표, 괄호까지 깔끔하게 제거
                                    ?.replace("<br/>", "\n")        // <br/> 태그를 줄바꿈으로 변경
                                    ?.trim()

                                dataDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                                dataDisplay.text = "🍱 $displayDate 메뉴 🍱\n\n$cleanMenu"
                                dataDisplay.gravity = android.view.Gravity.CENTER
                            }
                        } else {
                            // 주말이나 공휴일 등 데이터가 없는 경우
                            dataDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                            dataDisplay.text = "$displayDate\n급식 정보가 없습니다. ✨"
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

    // 🚀 급식 화면 전용 좌우 스와이프 제스처 리스너 장착 함수
    @SuppressLint("ClickableViewAccessibility")
    fun attachSwipeListener(context: Context) {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 150

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || e2 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                // 가로 스와이프 폭이 세로보다 크고 임계값을 넘겼을 때만 작동 (세로 스크롤 간섭 방지)
                if (abs(diffX) > abs(diffY) && abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        moveToPrevDay() // ◀ 오른쪽으로 밀면 이전 날 급식
                    } else {
                        moveToNextDay() // ▶ 왼쪽으로 밀면 다음 날 급식
                    }
                    return true
                }
                return false
            }
        })

        // dataDisplay 터치 이벤트를 제스처 디텍터에 전달
        dataDisplay.setOnTouchListener { _, event ->
            gestureDetector?.onTouchEvent(event)
            false // TextView 자체 세로 스크롤도 먹혀야 하니까 false 리턴!
        }
    }

    // 다른 메뉴 클릭 시 터치 리스너를 해제해서 충돌을 막아주는 안전 함수
    @SuppressLint("ClickableViewAccessibility")
    fun detachSwipeListener() {
        dataDisplay.setOnTouchListener(null)
    }
}