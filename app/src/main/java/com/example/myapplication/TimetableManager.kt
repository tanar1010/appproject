package com.example.myapplication

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
    private val API_KEY = "4e2e494f4226457aacbad544f2af5675"

    // 📅 급식 기능과 동기화되는 전역 캘린더 변수
    private var currentCalendar: Calendar = Calendar.getInstance()

    // ◀ 이전 날짜 버튼 클릭 시 호출
    fun moveToPrevDay() {
        currentCalendar.add(Calendar.DAY_OF_YEAR, -1)
        fetchTimetable()
    }

    // ▶ 다음 날짜 버튼 클릭 시 호출
    fun moveToNextDay() {
        currentCalendar.add(Calendar.DAY_OF_YEAR, 1)
        fetchTimetable()
    }

    // 시간표 메뉴 진입 시 오늘 날짜로 초기화 후 로드
    fun fetchTodayTimetable() {
        currentCalendar = Calendar.getInstance()
        fetchTimetable()
    }

    fun fetchTimetable() {
        val myUid = auth.currentUser?.uid
        if (myUid == null) {
            dataDisplay.text = "로그인이 필요합니다."
            return
        }

        dataDisplay.text = "시간표 정보를 확인하는 중..."

        db.collection("Users").document(myUid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val role = document.getString("role") ?: "STUDENT"

                    Log.d("TimetableRole", "현재 로그인한 유저의 role 필드 값: $role")

                    // 👨‍🏫 권한 분기 로직
                    if (role.uppercase(Locale.ROOT).contains("TEACHER")) {
                        loadTeacherTimetable(myUid)
                    } else {
                        val userGrade = document.get("grade")?.toString() ?: "1"
                        val userClass = document.get("classNum")?.toString() ?: "1"
                        loadNeisTimetable(userGrade, userClass)
                    }
                } else {
                    dataDisplay.text = "유저 프로필 정보를 찾을 수 없습니다."
                }
            }
            .addOnFailureListener { e ->
                Log.e("TimetableManager", "파이어베이스 에러: ${e.message}")
                dataDisplay.text = "데이터 로드 실패: ${e.message}"
            }
    }

    private fun loadTeacherTimetable(uid: String) {
        val displayDate = SimpleDateFormat("MM월 dd일(E)", Locale.getDefault()).format(currentCalendar.time)

        val dayOfWeek = currentCalendar.get(Calendar.DAY_OF_WEEK)
        val dayString = when (dayOfWeek) {
            Calendar.MONDAY -> "월"
            Calendar.TUESDAY -> "화"
            Calendar.WEDNESDAY -> "수"
            Calendar.THURSDAY -> "목"
            Calendar.FRIDAY -> "금"
            else -> "주말"
        }

        if (dayString == "주말") {
            dataDisplay.text = "📅 ${displayDate}\n────────────────────\n\n  주말은 수업이 없습니다. 푹 쉬세요! ✨"
            return
        }

        dataDisplay.text = "선생님 시간표를 불러오는 중..."

        db.collection("TeacherTimetables").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val todaySchedule = document.get(dayString) as? Map<String, String>

                    var result = "📅 ${displayDate}\n👨‍🏫 선생님 시간표\n"
                    result += "────────────────────\n\n"

                    if (todaySchedule != null && todaySchedule.isNotEmpty()) {
                        val sortedSchedule = todaySchedule.toSortedMap()
                        for ((period, subject) in sortedSchedule) {
                            result += "  ${period}교시  |  $subject\n\n"
                        }
                    } else {
                        result += "  오늘 등록된 수업이 없습니다.\n\n"
                    }
                    dataDisplay.text = result
                } else {
                    dataDisplay.text = "📅 ${displayDate}\n────────────────────\n\n" +
                            "⚠️ 아직 오늘 시간표가 설정되지 않았습니다!\n\n" +
                            "💡 현재 시간표 화면을 [꾹~ 누르면]\n" +
                            "   요일별로 1~7교시 시간표를 설정/수정할 수 있어요."
                }
            }
            .addOnFailureListener {
                dataDisplay.text = "선생님 시간표를 불러오지 못했습니다."
            }
    }

    private fun loadNeisTimetable(grade: String, classNm: String) {
        val targetDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(currentCalendar.time)
        val displayDate = SimpleDateFormat("MM월 dd일(E)", Locale.getDefault()).format(currentCalendar.time)

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

                        val timetableMap = TreeMap<Int, String>()
                        for (i in 0 until rowArray.length()) {
                            val obj = rowArray.getJSONObject(i)
                            val period = obj.getString("PERIO").toInt()
                            val subject = obj.getString("ITRT_CNTNT")

                            if (!timetableMap.containsKey(period)) {
                                timetableMap[period] = subject
                            }
                        }

                        dataDisplay.gravity = android.view.Gravity.START
                        var result = "📅 ${displayDate}\n${grade}학년 ${classNm}반 시간표\n"
                        result += "────────────────────\n\n"
                        for ((period, subject) in timetableMap) {
                            result += "  ${period}교시  |  $subject\n\n"
                        }
                        dataDisplay.text = result

                    } else {
                        dataDisplay.text = "📅 ${displayDate}\n────────────────────\n\n  수업이 없는 날입니다. ✨"
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

    // 🔄 [복구 완료] 월~금 스크롤 내리며 일괄 정하는 원본 다이얼로그 로직
    fun showTeacherInputDialog(context: Context) {
        val myUid = auth.currentUser?.uid ?: return

        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_teacher_timetable, null)

        // 월요일 EditText 바인딩
        val monETs = listOf(
            dialogView.findViewById<EditText>(R.id.et_mon_1), dialogView.findViewById<EditText>(R.id.et_mon_2),
            dialogView.findViewById<EditText>(R.id.et_mon_3), dialogView.findViewById<EditText>(R.id.et_mon_4),
            dialogView.findViewById<EditText>(R.id.et_mon_5), dialogView.findViewById<EditText>(R.id.et_mon_6),
            dialogView.findViewById<EditText>(R.id.et_mon_7)
        )
        // 화요일 EditText 바인딩
        val tueETs = listOf(
            dialogView.findViewById<EditText>(R.id.et_tue_1), dialogView.findViewById<EditText>(R.id.et_tue_2),
            dialogView.findViewById<EditText>(R.id.et_tue_3), dialogView.findViewById<EditText>(R.id.et_tue_4),
            dialogView.findViewById<EditText>(R.id.et_tue_5), dialogView.findViewById<EditText>(R.id.et_tue_6),
            dialogView.findViewById<EditText>(R.id.et_tue_7)
        )
        // 수요일 EditText 바인딩
        val wedETs = listOf(
            dialogView.findViewById<EditText>(R.id.et_wed_1), dialogView.findViewById<EditText>(R.id.et_wed_2),
            dialogView.findViewById<EditText>(R.id.et_wed_3), dialogView.findViewById<EditText>(R.id.et_wed_4),
            dialogView.findViewById<EditText>(R.id.et_wed_5), dialogView.findViewById<EditText>(R.id.et_wed_6),
            dialogView.findViewById<EditText>(R.id.et_wed_7)
        )
        // 목요일 EditText 바인딩
        val thuETs = listOf(
            dialogView.findViewById<EditText>(R.id.et_thu_1), dialogView.findViewById<EditText>(R.id.et_thu_2),
            dialogView.findViewById<EditText>(R.id.et_thu_3), dialogView.findViewById<EditText>(R.id.et_thu_4),
            dialogView.findViewById<EditText>(R.id.et_thu_5), dialogView.findViewById<EditText>(R.id.et_thu_6),
            dialogView.findViewById<EditText>(R.id.et_thu_7)
        )
        // 금요일 EditText 바인딩
        val friETs = listOf(
            dialogView.findViewById<EditText>(R.id.et_fri_1), dialogView.findViewById<EditText>(R.id.et_fri_2),
            dialogView.findViewById<EditText>(R.id.et_fri_3), dialogView.findViewById<EditText>(R.id.et_fri_4),
            dialogView.findViewById<EditText>(R.id.et_fri_5), dialogView.findViewById<EditText>(R.id.et_fri_6),
            dialogView.findViewById<EditText>(R.id.et_fri_7)
        )

        val daysMap = mapOf("월" to monETs, "화" to tueETs, "수" to wedETs, "목" to thuETs, "금" to friETs)

        // 🔄 팝업 켜지자마자 기존 월~금 데이터 한꺼번에 로드해서 빈칸 다 채우기
        db.collection("TeacherTimetables").document(myUid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    for ((dayName, editTexts) in daysMap) {
                        @Suppress("UNCHECKED_CAST")
                        val daySchedule = document.get(dayName) as? Map<String, String>
                        if (daySchedule != null) {
                            for (i in 1..7) {
                                editTexts[i - 1].setText(daySchedule[i.toString()] ?: "")
                            }
                        }
                    }
                }
            }

        // 하나의 저장 버튼으로 일괄 처리
        AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton("한번에 저장") { _, _ ->
                val allWeekSchedule = mutableMapOf<String, Any>()

                // 월~금 루프 돌면서 데이터 뽑기
                for ((dayName, editTexts) in daysMap) {
                    val daySchedule = mutableMapOf<String, String>()
                    for (i in 1..7) {
                        val text = editTexts[i - 1].text.toString().trim()
                        if (text.isNotEmpty()) {
                            daySchedule[i.toString()] = text
                        }
                    }
                    allWeekSchedule[dayName] = daySchedule
                }

                // 🚀 파이어베이스에 일주일치 통째로 한 번에 Merge 저장!
                db.collection("TeacherTimetables").document(myUid)
                    .set(allWeekSchedule, SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(context, "🗓️ 주간 시간표가 일괄 저장되었습니다!", Toast.LENGTH_SHORT).show()
                        fetchTimetable()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "저장 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}