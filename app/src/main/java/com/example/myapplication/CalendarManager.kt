package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Date

class CalendarManager(private val context: Context, private val dataDisplay: TextView) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var calendarRootLayout: LinearLayout? = null
    private val hiddenViews = ArrayList<View>()

    // 달력 연산용 변수
    private var currentCalendar: Calendar = Calendar.getInstance()
    private var daysGridView: GridView? = null
    private var monthTitleTextView: TextView? = null

    // 데이터 캐싱 (메모가 있는 날짜 점 표시용)
    private val personalMemoDays = HashSet<String>() // format: YYYY-MM-DD
    private val teacherMemoDays = HashSet<String>()

    /**
     * 📅 메인 캘린더 오픈 함수
     */
    fun openCalendar() {
        val currentUser = auth.currentUser ?: return

        // 1. 메인 UI 가리기
        hideMainActivityUi()

        // 2. 파이어베이스로부터 등록된 메모 날짜들 미리 긁어오기 (점 표시용)
        fetchMemoDates {
            // 3. 동적 캘린더 UI 구성
            buildCalendarUI()
        }
    }

    /**
     * 📡 Firestore에서 메모가 등록된 날짜 목록을 캐싱하는 함수
     */
    private fun fetchMemoDates(onComplete: () -> Unit) {
        val myUid = auth.currentUser?.uid ?: return
        personalMemoDays.clear()
        teacherMemoDays.clear()

        // 1. 개인 메모 날짜 로드
        db.collection("CalendarMemos").document(myUid).collection("Personal")
            .get()
            .addOnSuccessListener { snapshots ->
                for (doc in snapshots.documents) {
                    personalMemoDays.add(doc.id) // 문서 ID가 날짜(YYYY-MM-DD) 형식
                }

                // 2. 선생님 공용 메모 날짜 로드
                db.collection("CalendarMemos").document("SharedTeacherMemos").collection("Memos")
                    .get()
                    .addOnSuccessListener { teacherSnapshots ->
                        for (doc in teacherSnapshots.documents) {
                            teacherMemoDays.add(doc.id)
                        }
                        onComplete()
                    }
                    .addOnFailureListener { onComplete() }
            }
            .addOnFailureListener { onComplete() }
    }

    /**
     * 🎨 캘린더 레이아웃 동적 생성 및 조립
     */
    private fun buildCalendarUI() {
        val parent = dataDisplay.parent as? ViewGroup ?: return
        removeCalendarComponents()
        dataDisplay.visibility = View.GONE

        // 최상위 루트
        calendarRootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 📌 상단 바 (나가기 + 월 이동 제어 바)
        val actionBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#4A6572"))
            padding(12, 10, 12, 10)
            gravity = Gravity.CENTER_VERTICAL
        }

        // 나가기 버튼
        val backButton = TextView(context).apply {
            text = "◀ 나가기"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(10, 10, 20, 10)
            setOnClickListener { removeCalendarComponents() }
        }
        actionBar.addView(backButton)

        // 이전 달 버튼
        val prevButton = Button(context).apply {
            text = "<"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            textSize = 18f
            setOnClickListener {
                currentCalendar.add(Calendar.MONTH, -1)
                refreshCalendar()
            }
        }
        actionBar.addView(prevButton)

        // 년/월 표시 텍스트
        monthTitleTextView = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        actionBar.addView(monthTitleTextView)

        // 다음 달 버튼
        val nextButton = Button(context).apply {
            text = ">"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            textSize = 18f
            setOnClickListener {
                currentCalendar.add(Calendar.MONTH, 1)
                refreshCalendar()
            }
        }
        actionBar.addView(nextButton)
        calendarRootLayout?.addView(actionBar)

        // 요일 헤더 라벨 바 (일~토)
        val weekHeaderLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            padding(0, 8, 0, 8)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        val weekDays = arrayOf("일", "월", "화", "수", "목", "금", "토")
        for (i in weekDays.indices) {
            val tv = TextView(context).apply {
                text = weekDays[i]
                gravity = Gravity.CENTER
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                // 색상 부여 (일요일 빨강, 토요일 파랑)
                when (i) {
                    0 -> setTextColor(Color.RED)
                    6 -> setTextColor(Color.BLUE)
                    else -> setTextColor(Color.DKGRAY)
                }
            }
            weekHeaderLayout.addView(tv)
        }
        calendarRootLayout?.addView(weekHeaderLayout)

        // 날짜가 뿌려질 그리드뷰 세팅
        daysGridView = GridView(context).apply {
            numColumns = 7
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        calendarRootLayout?.addView(daysGridView)
        parent.addView(calendarRootLayout)

        // 첫 가동 시 데이터 갱신 매핑
        refreshCalendar()
    }

    /**
     * 🔄 월 이동 시 달력 그리드를 리프레시하는 함수
     */
    private fun refreshCalendar() {
        val year = currentCalendar.get(Calendar.YEAR)
        val month = currentCalendar.get(Calendar.MONTH) + 1
        monthTitleTextView?.text = "${year}년 ${month}월"

        // 날짜 배열 생성
        val dayList = ArrayList<Date?>()
        val monthlyCal = currentCalendar.clone() as Calendar
        monthlyCal.set(Calendar.DAY_OF_MONTH, 1)

        // 1일 시작 전 빈 칸 공백 채우기용
        val firstDayOfWeek = monthlyCal.get(Calendar.DAY_OF_WEEK) - 1
        for (i in 0 until firstDayOfWeek) {
            dayList.add(null)
        }

        // 해당 월의 총 일수만큼 날짜 채우기
        val maxDay = monthlyCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 1..maxDay) {
            monthlyCal.set(Calendar.DAY_OF_MONTH, i)
            dayList.add(monthlyCal.time)
        }

        // 어댑터 연결
        daysGridView?.adapter = CalendarGridAdapter(context, dayList, currentCalendar, personalMemoDays, teacherMemoDays) { selectedDateStr ->
            // 날짜 클릭 시 메모 다이얼로그 호출
            showMemoDialog(selectedDateStr)
        }
    }

    /**
     * ✍️ 날짜 클릭 시 메모를 읽고 쓸 수 있는 팝업창 (글자 검정색 패치 완료)
     */
    private fun showMemoDialog(dateStr: String) {
        val myUid = auth.currentUser?.uid ?: return

        // 유저 신분 분석 선행
        db.collection("Users").document(myUid).get().addOnSuccessListener { myDoc ->
            val myRole = myDoc.getString("role") ?: "STUDENT"
            val isTeacher = myRole.uppercase() == "TEACHER"

            // 팝업에 배치할 입력 및 내용 UI 구성
            val dialogLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                padding(20, 15, 20, 15)
            }

            // 1. 선생님 전체 공지 라벨 및 텍스트뷰
            val teacherLabel = TextView(context).apply {
                text = "[📢 선생님의 공지 사항 (전체 공유)]"
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#D32F2F"))
                padding(0, 0, 0, 4)
            }
            val teacherDisplay = TextView(context).apply {
                text = "불러오는 중..."
                textSize = 14f
                setPadding(10, 10, 10, 10)
                setBackgroundColor(Color.parseColor("#FFF3E0"))

                // 🎨 [패치] 하얀색으로 안 보이던 선생님 공지 글자색을 검은색으로 고정!
                setTextColor(Color.BLACK)
            }
            dialogLayout.addView(teacherLabel)
            dialogLayout.addView(teacherDisplay)

            // 선생님 전용 작성 에디터 (선생님 로그인 시에만 노출)
            val teacherInput = EditText(context).apply {
                hint = "공지 내용을 입력하세요 (선생님 전용)"
                visibility = if (isTeacher) View.VISIBLE else View.GONE
                setPadding(10, 10, 10, 10)

                // 🎨 [패치] 선생님 입력창 글자색 검은색 고정!
                setTextColor(Color.BLACK)
                setHintTextColor(Color.GRAY)
            }
            if (isTeacher) dialogLayout.addView(teacherInput)

            // 구분선 추가
            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 20, 0, 20) }
                setBackgroundColor(Color.LTGRAY)
            }
            dialogLayout.addView(divider)

            // 2. 학생 개인 메모 라벨 및 에디터
            val personalLabel = TextView(context).apply {
                text = "[🔒 나만의 비밀 메모 (개인용)]"
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1976D2"))
                padding(0, 0, 0, 4)
            }
            val personalInput = EditText(context).apply {
                hint = "오늘의 할 일이나 메모를 적어보세요..."
                setPadding(10, 10, 10, 10)
                setBackgroundColor(Color.parseColor("#E3F2FD"))

                // 🎨 [패치] 하얀색이라 안 보이던 개인 입력창 글자색 검은색 고정!
                setTextColor(Color.BLACK)
                setHintTextColor(Color.GRAY)
            }
            dialogLayout.addView(personalLabel)
            dialogLayout.addView(personalInput)

            // 📡 데이터 로드 기동
            // 선생님 공용 데이터 가져오기
            db.collection("CalendarMemos").document("SharedTeacherMemos").collection("Memos").document(dateStr)
                .get().addOnSuccessListener { tDoc ->
                    val tMemo = tDoc.getString("content") ?: "등록된 선생님 공지가 없습니다."
                    teacherDisplay.text = tMemo
                    if (isTeacher && tDoc.contains("content")) {
                        teacherInput.setText(tDoc.getString("content"))
                    }

                    // 내 비밀 개인 데이터 가져오기
                    db.collection("CalendarMemos").document(myUid).collection("Personal").document(dateStr)
                        .get().addOnSuccessListener { pDoc ->
                            if (pDoc.exists()) {
                                personalInput.setText(pDoc.getString("content") ?: "")
                            }

                            // 다이얼로그 띄우기
                            AlertDialog.Builder(context)
                                .setTitle("📅 $dateStr 일정 등록")
                                .setView(dialogLayout)
                                .setPositiveButton("저장") { _, _ ->
                                    val pText = personalInput.text.toString().trim()

                                    // 개인 메모 저장 및 삭제 분기 처리
                                    if (pText.isNotEmpty()) {
                                        db.collection("CalendarMemos").document(myUid).collection("Personal").document(dateStr)
                                            .set(hashMapOf("content" to pText))
                                    } else {
                                        db.collection("CalendarMemos").document(myUid).collection("Personal").document(dateStr).delete()
                                    }

                                    // 선생님일 때 공지사항 저장 처리 분기
                                    if (isTeacher) {
                                        val tText = teacherInput.text.toString().trim()
                                        if (tText.isNotEmpty()) {
                                            db.collection("CalendarMemos").document("SharedTeacherMemos").collection("Memos").document(dateStr)
                                                .set(hashMapOf("content" to tText))
                                        } else {
                                            db.collection("CalendarMemos").document("SharedTeacherMemos").collection("Memos").document(dateStr).delete()
                                        }
                                    }

                                    // 저장 후 리프레시하여 즉시 점 표시 상태 동기화
                                    fetchMemoDates { refreshCalendar() }
                                }
                                .setNegativeButton("닫기", null)
                                .show()
                        }
                }
        }
    }

    private fun hideMainActivityUi() {
        try {
            val parent = dataDisplay.parent as? ViewGroup ?: return
            hiddenViews.clear()
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child != dataDisplay && child.visibility == View.VISIBLE) {
                    child.visibility = View.GONE
                    hiddenViews.add(child)
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarManager", "UI 가리기 실패: ${e.message}")
        }
    }

    fun removeCalendarComponents() {
        calendarRootLayout?.let {
            val parent = it.parent as? ViewGroup
            parent?.removeView(it)
            calendarRootLayout = null
        }
        dataDisplay.visibility = View.VISIBLE
        for (view in hiddenViews) {
            view.visibility = View.VISIBLE
        }
        hiddenViews.clear()

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(dataDisplay.windowToken, 0)
    }

    private fun View.padding(left: Int, top: Int, right: Int, bottom: Int) {
        val scale = resources.displayMetrics.density
        setPadding((left * scale).toInt(), (top * scale).toInt(), (right * scale).toInt(), (bottom * scale).toInt())
    }
}

/**
 * 🎨 캘린더 그리드 개별 날짜 칸을 그려주는 맞춤형 커스텀 어댑터
 */
class CalendarGridAdapter(
    private val context: Context,
    private val days: List<Date?>,
    private val currentCalendar: Calendar,
    private val personalMemos: HashSet<String>,
    private val teacherMemos: HashSet<String>,
    private val onDateClick: (String) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = days.size
    override fun getItem(position: Int): Any? = days[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val date = days[position]

        // 하나의 셀(날짜 칸) 레이아웃 구성
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 15, 0, 15)
            // 격자 구분을 위해 투명 테두리 패딩 설정
            minimumHeight = 140
        }

        if (date == null) return cell // 빈 칸공백 리턴

        val cal = Calendar.getInstance().apply { time = date }
        val dayNum = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val dateKey = String.format("%04d-%02d-%02d", year, month, dayNum)

        // 1. 날짜 숫자 텍스트뷰
        val dayTextView = TextView(context).apply {
            text = dayNum.toString()
            textSize = 14f
            gravity = Gravity.CENTER
        }

        // 2. 공휴일 문구 전용 텍스트뷰
        val holidayTextView = TextView(context).apply {
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(Color.RED)
            visibility = View.GONE
        }

        // 3. ✨ 메모 유무 감지 점 표시 아이콘
        val dotIndicator = TextView(context).apply {
            text = "●"
            textSize = 9f
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
        }

        // 색상 로직 검사 및 공휴일 체크
        val holidayName = getHolidayName(month, dayNum)
        val isHoliday = holidayName != null

        if (dayOfWeek == Calendar.SUNDAY || isHoliday) {
            // 💥 일요일 및 공휴일 -> 빨간색!
            dayTextView.setTextColor(Color.RED)
            if (isHoliday) {
                holidayTextView.text = holidayName
                holidayTextView.visibility = View.VISIBLE
            }
        } else if (dayOfWeek == Calendar.SATURDAY) {
            // 💥 토요일 -> 파란색!
            dayTextView.setTextColor(Color.BLUE)
        } else {
            // 평일 -> 검은색
            dayTextView.setTextColor(Color.BLACK)
        }

        // 오늘 날짜 하이라이트 동그라미 처리
        val today = Calendar.getInstance()
        if (today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) + 1 == month && today.get(Calendar.DAY_OF_MONTH) == dayNum) {
            dayTextView.setTypeface(null, Typeface.BOLD)
            dayTextView.setBackgroundColor(Color.parseColor("#E0E0E0"))
        }

        // 💥 메모 기록 점등 매핑
        if (personalMemos.contains(dateKey) || teacherMemos.contains(dateKey)) {
            dotIndicator.visibility = View.VISIBLE
            // 선생님 공지가 섞여 있으면 주황색 점, 개인 비밀 메모만 있으면 하늘색 점으로 디테일 분류!
            if (teacherMemos.contains(dateKey)) {
                dotIndicator.setTextColor(Color.parseColor("#FF9800"))
            } else {
                dotIndicator.setTextColor(Color.parseColor("#2196F3"))
            }
        }

        cell.addView(dayTextView)
        cell.addView(holidayTextView)
        cell.addView(dotIndicator)

        // 셀 터치 시 이벤트 리스너 바인딩
        cell.setOnClickListener {
            onDateClick(dateKey)
        }

        return cell
    }

    /**
     * 🇰🇷 자동으로 반영될 고정 양력 대한민국 공휴일 검출기 로직
     */
    private fun getHolidayName(month: Int, day: Int): String? {
        return when {
            month == 1 && day == 1 -> "신정"
            month == 3 && day == 1 -> "삼일절"
            month == 5 && day == 5 -> "어린이날"
            month == 6 && day == 6 -> "현충일"
            month == 8 && day == 15 -> "광복절"
            month == 10 && day == 3 -> "개천절"
            month == 10 && day == 9 -> "한글날"
            month == 12 && day == 25 -> "성탄절"
            else -> null
        }
    }
}