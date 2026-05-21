package com.example.myapplication

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class NoticeManager(
    private val context: Context,
    private val dataDisplay: TextView
) {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    private var currentPage = 1
    private val itemsPerPage = 5
    private var totalPages = 0 // 💡 제스처 감지를 위해 전역 변수로 승격
    private var allDocuments: List<DocumentSnapshot> = ArrayList()
    private var isCurrentUserTeacher = false

    private val currentPageNoticeIds = ArrayList<String>()
    private val currentPageTitles = ArrayList<String>()
    private var gestureDetector: GestureDetector? = null

    fun fetchNotices() {
        dataDisplay.text = "공지사항을 불러오는 중..."
        currentPage = 1
        currentPageNoticeIds.clear()
        currentPageTitles.clear()

        val uid = auth.currentUser?.uid
        if (uid == null) {
            isCurrentUserTeacher = false
            loadAllNotices()
            return
        }

        AuthManager.getRole(uid) { role ->
            isCurrentUserTeacher = (role == AuthManager.ROLE_TEACHER)
            loadAllNotices()
        }
    }

    private fun loadAllNotices() {
        db.collection("Notices")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                allDocuments = documents.documents
                renderNoticePage()
            }
            .addOnFailureListener {
                dataDisplay.text = "공지사항을 불러오지 못했습니다."
            }
    }

    private fun renderNoticePage() {
        val parentView = dataDisplay.parent as? ViewGroup ?: return

        // 🧹 기존 동적 선생님 패널 청소
        removeDynamicButton()

        currentPageNoticeIds.clear()
        currentPageTitles.clear()

        if (allDocuments.isEmpty()) {
            totalPages = 0
            dataDisplay.text = "📢 [학급 공지사항]\n--------------------------\n\n등록된 공지사항이 없습니다.\n\n--------------------------\n📄 페이지 0 / 0"
            showTeacherControlPanel(parentView)
            return
        }

        totalPages = kotlin.math.ceil(allDocuments.size.toDouble() / itemsPerPage).toInt()
        if (currentPage > totalPages) currentPage = totalPages

        val startIndex = (currentPage - 1) * itemsPerPage
        var endIndex = startIndex + itemsPerPage
        if (endIndex > allDocuments.size) endIndex = allDocuments.size

        // 📝 상단 헤더 문구 정돈 (지저분한 페이지 표시 제거)
        val header = " \n 📢 [학급 공지사항]\n💡. 슬라이드로 조작할 수 있어요!\n--------------------------\n\n"
        val fullStringBuilder = StringBuilder(header)
        val clickRegions = ArrayList<Pair<Int, Int>>()

        val pageSubList = allDocuments.subList(startIndex, endIndex)

        var displayIndex = startIndex + 1
        for (document in pageSubList) {
            val content = document.getString("content") ?: ""
            val date = document.getString("date") ?: ""
            val title = document.getString("title") ?: if (content.length > 15) content.substring(0, 15) + "..." else content

            currentPageNoticeIds.add(document.id)
            currentPageTitles.add(title)

            val itemText = "$displayIndex. 📌 $title  ($date)\n\n"
            val startPos = fullStringBuilder.length
            fullStringBuilder.append(itemText)
            val endPos = fullStringBuilder.length - 2

            clickRegions.add(Pair(startPos, endPos))
            displayIndex++
        }

        // 💡 [요청 사항] 공지사항 본문 내용의 맨 밑바닥에 페이지 정보 추가하기
        fullStringBuilder.append("\n--------------------------\n📄 현재 페이지: $currentPage / $totalPages")

        val spannableString = SpannableString(fullStringBuilder.toString())

        for (i in clickRegions.indices) {
            val region = clickRegions[i]
            val doc = pageSubList[i]
            val title = currentPageTitles[i]
            val content = doc.getString("content") ?: ""
            val date = doc.getString("date") ?: ""

            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    showNoticeDetailDialog(title, content, date)
                }
            }
            spannableString.setSpan(clickableSpan, region.first, region.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        dataDisplay.movementMethod = LinkMovementMethod.getInstance()
        dataDisplay.highlightColor = Color.TRANSPARENT
        dataDisplay.text = spannableString

        // 선생님 제어 버튼 레이아웃 출력 (페이징 버튼은 제외됨)
        showTeacherControlPanel(parentView)
    }

    // 💡 버튼들을 지우고 선생님 전용 [추가/삭제] 기능만 깔끔하게 남긴 관리 패널
    private fun showTeacherControlPanel(parentView: ViewGroup) {
        // 선생님이 아니면 상단에 아무것도 띄우지 않음
        if (!isCurrentUserTeacher) return

        val topPanel = LinearLayout(context).apply {
            id = R.id.teacher_button_panel
            tag = "DYNAMIC_TEACHER_PANEL"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val pParams = dataDisplay.layoutParams
        topPanel.layoutParams = if (pParams is LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(15, 5, 15, 20)
            }
        } else {
            ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(15, 5, 15, 20)
            }
        }

        // ➕ 공지 추가 버튼
        val btnAdd = Button(context).apply {
            text = "➕ 공지 추가"
            textSize = 12f
            setBackgroundColor(Color.parseColor("#6200EE"))
            setTextColor(Color.WHITE)
            setPadding(10, 10, 10, 10)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(5, 0, 10, 0)
            }
            setOnClickListener { showAddNoticeDialog() }
        }
        topPanel.addView(btnAdd)

        // ❌ 공지 삭제 버튼
        val btnDelete = Button(context).apply {
            text = "❌ 공지 삭제"
            textSize = 12f
            setBackgroundColor(Color.parseColor("#E53935"))
            setTextColor(Color.WHITE)
            setPadding(10, 10, 10, 10)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(10, 0, 5, 0)
            }
            isEnabled = totalPages > 0
            if (totalPages == 0) {
                setBackgroundColor(Color.GRAY)
            }
            setOnClickListener { showDeleteSelectDialog() }
        }
        topPanel.addView(btnDelete)

        val index = parentView.indexOfChild(dataDisplay)
        parentView.addView(topPanel, index)
        topPanel.bringToFront()
    }

    // 🚀 공지사항 전용 스와이프(슬라이드) 리스너 구현부
    @SuppressLint("ClickableViewAccessibility")
    fun attachSwipeListener(context: Context) {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 150

            override fun onDown(e: MotionEvent): Boolean {
                return true // 제스처 이벤트를 가로채기 위해 진입 허용
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || e2 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                // 가로 휙 넘기기 조건 매칭
                if (abs(diffX) > abs(diffY) && abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        // ◀ 우측 스와이프: 이전 페이지
                        if (currentPage > 1) {
                            currentPage--
                            renderNoticePage()
                        } else {
                            Toast.makeText(context, "첫 번째 페이지입니다.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // ▶ 좌측 스와이프: 다음 페이지
                        if (currentPage < totalPages) {
                            currentPage++
                            renderNoticePage()
                        } else {
                            Toast.makeText(context, "마지막 페이지입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return true
                }
                return false
            }
        })

        dataDisplay.setOnTouchListener { _, event ->
            gestureDetector?.onTouchEvent(event)
            false // 7교시 시간표처럼 본문 스크롤 및 링크 클릭 연동을 위해 false 반환
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun Tracy_detachSwipeListener() {
        // 안전한 분리를 위한 리스너 해제 기능
    }

    fun detachSwipeListener() {
        dataDisplay.setOnTouchListener(null)
    }

    private fun showNoticeDetailDialog(title: String, content: String, date: String) {
        AlertDialog.Builder(context)
            .setTitle("📌 $title")
            .setMessage("\n$content\n\n작성일: $date")
            .setPositiveButton("닫기", null)
            .show()
    }

    private fun showAddNoticeDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("새 공지사항 등록")

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val etTitle = EditText(context).apply {
            hint = "공지 제목을 입력하세요."
            maxLines = 1
        }
        val etContent = EditText(context).apply {
            hint = "공지 상세 내용을 입력하세요."
            minLines = 3
        }

        layout.addView(etTitle)
        layout.addView(etContent)
        builder.setView(layout)

        builder.setPositiveButton("등록") { dialog, _ ->
            val title = etTitle.text.toString().trim()
            val content = etContent.text.toString().trim()

            if (title.isNotEmpty() && content.isNotEmpty()) {
                uploadNotice(title, content)
            } else {
                Toast.makeText(context, "제목과 내용을 모두 입력해 주세요.", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("취소", null)
        builder.show()
    }

    private fun uploadNotice(title: String, content: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val formattedDate = sdf.format(Date())

        val noticeMap = hashMapOf(
            "title" to title,
            "content" to content,
            "date" to formattedDate,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("Notices").add(noticeMap)
            .addOnSuccessListener {
                Toast.makeText(context, "공지가 등록되었습니다.", Toast.LENGTH_SHORT).show()
                fetchNotices()
            }
    }

    private fun showDeleteSelectDialog() {
        if (currentPageNoticeIds.isEmpty()) {
            Toast.makeText(context, "삭제할 공지사항이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val deleteOptions = ArrayList<String>()
        for (i in currentPageTitles.indices) {
            val shortTitle = if (currentPageTitles[i].length > 15) {
                currentPageTitles[i].substring(0, 15) + "..."
            } else {
                currentPageTitles[i]
            }
            deleteOptions.add("[$((currentPage - 1) * itemsPerPage + i + 1)] $shortTitle")
        }

        AlertDialog.Builder(context)
            .setTitle("삭제할 공지를 선택하세요 (현재 페이지)")
            .setItems(deleteOptions.toTypedArray()) { _, which ->
                if (which < currentPageNoticeIds.size) {
                    val targetDocId = currentPageNoticeIds[which]
                    confirmDelete(targetDocId)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmDelete(docId: String) {
        AlertDialog.Builder(context)
            .setTitle("진짜 삭제할까요?")
            .setMessage("삭제된 공지는 복구할 수 없습니다.")
            .setPositiveButton("삭제") { _, _ ->
                db.collection("Notices").document(docId).delete()
                    .addOnSuccessListener {
                        Toast.makeText(context, "공지가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        fetchNotices()
                    }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    fun removeDynamicButton() {
        val parentView = dataDisplay.parent as? ViewGroup ?: return

        val childCount = parentView.childCount
        for (i in childCount - 1 downTo 0) {
            val child = parentView.getChildAt(i)
            if (child?.tag == "DYNAMIC_PAGE_PANEL" || child?.tag == "DYNAMIC_TEACHER_PANEL" || child?.id == R.id.teacher_button_panel) {
                parentView.removeView(child)
            }
        }
    }
}