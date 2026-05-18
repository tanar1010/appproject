package com.example.myapplication

import android.util.Log
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class TimetableManager(private val dataDisplay: TextView) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // NEIS API 설정 정보
    private val API_KEY = "4e2e494f4226457aacbad544f2af5675"
    private val OFFICE_CODE = "J10"      // 경기도교육청
    private val SCHOOL_CODE = "7530102"  // 용인고등학교

    fun fetchTimetable() {
        val myUid = auth.currentUser?.uid
        if (myUid == null) {
            dataDisplay.text = "로그인이 필요합니다."
            return
        }

        dataDisplay.text = "프로필 정보를 불러오는 중..."

        // 1. 👤 파이어베이스에서 내 UID 문서 읽기
        db.collection("Users").document(myUid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {

                    // 형이 알려준 'classNum'이랑 'grade' 필드 매핑 (없으면 기본값 "1")
                    val userGrade = document.get("grade")?.toString() ?: "1"
                    val userClass = document.get("classNum")?.toString() ?: "1"

                    Log.d("TimetableManager", "프로필 연동 성공 -> ${userGrade}학년 ${userClass}반")

                    // 2. 📡 동적 학년/반 데이터로 시간표 호출
                    loadNeisTimetable(userGrade, userClass)
                } else {
                    dataDisplay.text = "유저 프로필 정보를 찾을 수 없습니다."
                }
            }
            .addOnFailureListener { e ->
                Log.e("TimetableManager", "파이어베이스 에러: ${e.message}")
                dataDisplay.text = "프로필 로드 실패: ${e.message}"
            }
    }

    /**
     * NEIS API 호출 및 결과 화면 표시
     */
    private fun loadNeisTimetable(grade: String, classNm: String) {
        val cal = Calendar.getInstance()
        val targetDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.time)
        val displayDate = SimpleDateFormat("MM월 dd일(E)", Locale.getDefault()).format(cal.time)

        dataDisplay.text = "${displayDate} 시간표를 불러오는 중..."

        NeisRetrofitClient.service.getTimetable(
            key = API_KEY,
            date = targetDate,
            grade = grade,
            classNm = classNm
        ).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                try {
                    val rawData = response.body()?.string() ?: ""
                    val json = JSONObject(rawData)

                    if (json.has("hisTimetable")) {
                        val hisTimetable = json.getJSONArray("hisTimetable")
                        val rowArray = hisTimetable.getJSONObject(1).getJSONArray("row")

                        // 교시 순 정렬 (TreeMap)
                        val timetableMap = TreeMap<Int, String>()

                        for (i in 0 until rowArray.length()) {
                            val obj = rowArray.getJSONObject(i)
                            val period = obj.getString("PERIO").toInt()
                            val subject = obj.getString("ITRT_CNTNT")

                            if (!timetableMap.containsKey(period)) {
                                timetableMap[period] = subject
                            }
                        }

                        // 결과 출력
                        var result = "📅 ${displayDate} 시간표 (${grade}-${classNm})\n\n"
                        for ((period, subject) in timetableMap) {
                            result += "${period}교시: $subject\n"
                        }
                        dataDisplay.text = result

                    } else {
                        dataDisplay.text = "${displayDate}는 수업이 없는 날입니다. ✨"
                    }
                } catch (e: Exception) {
                    Log.e("TimetableManager", "파싱 에러: ${e.message}")
                    dataDisplay.text = "데이터 분석 중 오류가 발생했습니다."
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                dataDisplay.text = "서버 연결 실패"
            }
        })
    }
}