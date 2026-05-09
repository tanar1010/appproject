package com.example.myapplication

import android.util.Log
import android.widget.TextView
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class TimetableManager(private val dataDisplay: TextView) {

    // 설정 정보 (용인고 3학년 6반 고정)
    private val API_KEY = "4e2e494f4226457aacbad544f2af5675"
    private val OFFICE_CODE = "J10"      // 경기도교육청
    private val SCHOOL_CODE = "7530102"  // 용인고등학교 학교코드
    private val GRADE = "3"
    private val CLASS_NAME = "6"

    fun fetchTimetable() {
        // 오늘 날짜로 변경
        val cal = Calendar.getInstance()
        val targetDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.time)
        val displayDate = SimpleDateFormat("MM월 dd일(E)", Locale.getDefault()).format(cal.time)

        dataDisplay.text = "${displayDate} 시간표를 불러오는 중..."

        NeisRetrofitClient.service.getTimetable(
            key = API_KEY,
            date = targetDate,
            grade = GRADE,
            classNm = CLASS_NAME
        ).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                try {
                    val rawData = response.body()?.string() ?: ""
                    val json = JSONObject(rawData)

                    if (json.has("hisTimetable")) {
                        val hisTimetable = json.getJSONArray("hisTimetable")
                        val rowArray = hisTimetable.getJSONObject(1).getJSONArray("row")

                        // 교시 순 정렬을 위해 TreeMap 사용
                        val timetableMap = TreeMap<Int, String>()

                        for (i in 0 until rowArray.length()) {
                            val obj = rowArray.getJSONObject(i)
                            val period = obj.getString("PERIO").toInt()
                            val subject = obj.getString("ITRT_CNTNT")

                            // 중복 데이터 방지 (첫 번째 데이터 셋 채택)
                            if (!timetableMap.containsKey(period)) {
                                timetableMap[period] = subject
                            }
                        }

                        var result = "📅 ${displayDate} 시간표 (3-6)\n\n"
                        for ((period, subject) in timetableMap) {
                            result += "${period}교시: $subject\n"
                        }
                        dataDisplay.text = result

                    } else {
                        dataDisplay.text = "${displayDate}는 수업이 없는 날입니다. ✨"
                    }
                } catch (e: Exception) {
                    dataDisplay.text = "데이터 분석 중 오류가 발생했습니다."
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                dataDisplay.text = "서버 연결 실패"
            }
        })
    }
}