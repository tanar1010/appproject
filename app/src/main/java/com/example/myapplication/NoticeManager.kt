package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class NoticeManager(
    private val context: Context,
    private val dataDisplay: TextView
) {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    // 가져온 공지들의 문서 ID를 저장해둘 리스트
    private val noticeIdList = ArrayList<String>()

    /**
     * 1. 하단 버튼 1을 누르거나 새로고침할 때 호출되는 함수
     */
    fun fetchNotices() {
        dataDisplay.text = "공지사항을 불러오는 중..."
        noticeIdList.clear()

        val uid = auth.currentUser?.uid
        if (uid == null) {
            loadNoticesText(isTeacher = false)
            return
        }

        AuthManager.getRole(uid) { role ->
            val isTeacher = (role == AuthManager.ROLE_TEACHER)
            loadNoticesText(isTeacher)
        }
    }

    /**
     * 2. 오직 텍스트로만 공지를 띄우고, 선생님일 때만 상단에 관리 버튼 패널을 주입하는 함수
     */
    private fun loadNoticesText(isTeacher: Boolean) {
        db.collection("Notices")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val parentView = dataDisplay.parent as? ViewGroup ?: return@addOnSuccessListener

                // 🧹 [청소] 새로 그리기 전에 기존에 만들어둔 버튼 패널이 있다면 확실하게 제거
                val oldPanel = parentView.findViewById<View>(R.id.teacher_button_panel)
                if (oldPanel != null) {
                    parentView.removeView(oldPanel)
                }

                // 🆕 [선생님 권한일 때만] 화면 상단에 추가/삭제 버튼 레이아웃 배치
                if (isTeacher) {
                    val buttonPanel = LinearLayout(context).apply {
                        id = R.id.teacher_button_panel
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 0, 30) // 공지 텍스트와 간격 벌리기
                        }
                    }

                    // [공지 추가] 버튼
                    val btnAdd = Button(context).apply {
                        text = "➕ 공지 추가"
                        setBackgroundColor(Color.parseColor("#6200EE")) // 보라색
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            setMargins(0, 0, 10, 0)
                        }
                        setOnClickListener { showAddNoticeDialog() }
                    }

                    // [공지 삭제] 버튼
                    val btnDelete = Button(context).apply {
                        text = "❌ 공지 삭제"
                        setBackgroundColor(Color.parseColor("#E53935")) // 빨간색
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            setMargins(10, 0, 0, 0)
                        }
                        setOnClickListener { showDeleteSelectDialog() }
                    }

                    buttonPanel.addView(btnAdd)
                    buttonPanel.addView(btnDelete)

                    // dataDisplay 바로 위에 버튼 패널을 끼워넣습니다.
                    val index = parentView.indexOfChild(dataDisplay)
                    parentView.addView(buttonPanel, index)
                }

                // 데이터가 없을 때의 처리
                if (documents.isEmpty) {
                    dataDisplay.text = "📢 [학급 공지사항]\n--------------------------\n\n등록된 공지사항이 없습니다."
                    return@addOnSuccessListener
                }

                // 급식표처럼 순수 텍스트 조합 시작
                var result = "📢 [학급 공지사항]\n--------------------------\n\n"
                var index = 1

                for (document in documents) {
                    val content = document.getString("content") ?: ""
                    val date = document.getString("date") ?: ""

                    result += "$index. 📌 $content\n   └ ($date)\n\n"
                    noticeIdList.add(document.id)
                    index++
                }

                // 텍스트 주입 (중첩 절대 없음)
                dataDisplay.text = result
            }
            .addOnFailureListener {
                dataDisplay.text = "공지사항을 불러오지 못했습니다."
            }
    }

    /**
     * 3. 새 공지 추가 팝업
     */
    private fun showAddNoticeDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("새 공지사항 등록")

        val input = EditText(context)
        input.hint = "공지 내용을 입력하세요."
        builder.setView(input)

        builder.setPositiveButton("등록") { dialog, _ ->
            val content = input.text.toString().trim()
            if (content.isNotEmpty()) {
                uploadNotice(content)
            } else {
                Toast.makeText(context, "내용이 비어있습니다.", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("취소", null)
        builder.show()
    }

    private fun uploadNotice(content: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val formattedDate = sdf.format(Date())

        val noticeMap = hashMapOf(
            "content" to content,
            "date" to formattedDate,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("Notices").add(noticeMap)
            .addOnSuccessListener {
                Toast.makeText(context, "공지가 등록되었습니다.", Toast.LENGTH_SHORT).show()
                fetchNotices() // 화면 자동 새로고침
            }
    }

    /**
     * 4. 삭제할 공지 선택 팝업 (오타 수정한 정상 버전)
     */
    /**
     * 4. 삭제할 공지 선택 팝업 (번호 대신 공지 본문 앞부분을 잘라서 보여주는 버전)
     */
    private fun showDeleteSelectDialog() {
        if (noticeIdList.isEmpty()) {
            Toast.makeText(context, "삭제할 공지사항이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val deleteOptions = ArrayList<String>()
        val currentLines = dataDisplay.text.toString().split("\n")

        var matchIndex = 0
        for (line in currentLines) {
            // 공지 본문 내용이 담긴 라인을 찾아냅니다.
            if (line.contains("📌")) {
                // "📌 " 기호를 떼고 순수 글자만 추출
                val pureContent = line.replace("📌", "").trim()

                // 글자가 너무 길면 15자까지만 자르고 뒤에 '...' 붙이기
                val shortContent = if (pureContent.length > 15) {
                    pureContent.substring(0, 15) + "..."
                } else {
                    pureContent
                }

                // 선택지 목록에 추가 (예: "[1] 내일은 준비물..." )
                deleteOptions.add("[${matchIndex + 1}] $shortContent")
                matchIndex++
            }
        }

        // 만약 텍스트 파싱 중 싱크가 깨질 경우를 대비한 안전장치
        if (deleteOptions.size != noticeIdList.size) {
            // 파싱이 꼬였다면 안전하게 기본 인덱스로 대체 출력
            deleteOptions.clear()
            for (i in noticeIdList.indices) {
                deleteOptions.add("${i + 1}번 공지 삭제")
            }
        }

        AlertDialog.Builder(context)
            .setTitle("삭제할 공지를 선택하세요")
            .setItems(deleteOptions.toTypedArray()) { _, which ->
                if (which < noticeIdList.size) {
                    val targetDocId = noticeIdList[which]
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
                        fetchNotices() // 화면 자동 새로고침
                    }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * 5. 급식이나 시간표 등 다른 메뉴로 넘어갈 때 상단 버튼 패널을 깔끔하게 지워주는 함수
     */
    fun removeDynamicButton() {
        val parentView = dataDisplay.parent as? ViewGroup ?: return
        val oldPanel = parentView.findViewById<View>(R.id.teacher_button_panel)
        if (oldPanel != null) {
            parentView.removeView(oldPanel)
        }
    }
}