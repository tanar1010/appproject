package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
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

class NoticeManager(
    private val context: Context,
    private val dataDisplay: TextView
) {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    private var currentPage = 1
    private val itemsPerPage = 5
    private var allDocuments: List<DocumentSnapshot> = ArrayList()
    private var isCurrentUserTeacher = false

    private val currentPageNoticeIds = ArrayList<String>()
    private val currentPageTitles = ArrayList<String>()

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

        // 🧹 기존 동적 뷰 일단 청소
        removeDynamicButton()

        currentPageNoticeIds.clear()
        currentPageTitles.clear()

        // 🚨 [핵심 수정] 데이터가 아예 없을 때 예외 처리 메커니즘 전면 전개
        if (allDocuments.isEmpty()) {
            dataDisplay.text = "📢 [학급 공지사항] (페이지 0 / 0)\n--------------------------\n\n등록된 공지사항이 없습니다."

            // 데이터가 없어도 선생님 권한이거나 페이징 패널 레이아웃 틀 유지를 위해 총 페이지 0으로 강제 진입 유도
            showTopIntegratedControlPanel(parentView, 0)
            return
        }

        val totalPages = kotlin.math.ceil(allDocuments.size.toDouble() / itemsPerPage).toInt()
        if (currentPage > totalPages) currentPage = totalPages

        val startIndex = (currentPage - 1) * itemsPerPage
        var endIndex = startIndex + itemsPerPage
        if (endIndex > allDocuments.size) endIndex = allDocuments.size

        // 📝 공지사항 본문 타이틀 및 안내 문구 배치
        val header = "📢 [학급 공지사항] (페이지 $currentPage / $totalPages)\n클릭하면 상세 내용을 볼 수 있습니다.\n--------------------------\n\n"
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

        // 정상 데이터 존재 시 상단 패널 렌더링
        showTopIntegratedControlPanel(parentView, totalPages)
    }

    private fun showTopIntegratedControlPanel(parentView: ViewGroup, totalPages: Int) {
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

        // [선생님 권한] ➕ 추가 버튼 (데이터 유무 상관없이 무조건 렌더링)
        if (isCurrentUserTeacher) {
            val btnAdd = Button(context).apply {
                text = "➕ 추가"
                textSize = 12f
                setBackgroundColor(Color.parseColor("#6200EE"))
                setTextColor(Color.WHITE)
                setPadding(10, 10, 10, 10)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(5, 0, 15, 0)
                }
                setOnClickListener { showAddNoticeDialog() }
            }
            topPanel.addView(btnAdd)
        }

        // --- 중앙 순수 페이징 영역 ---
        val pagingContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val btnPrevPage = Button(context).apply {
            text = "◀"
            textSize = 13f
            setPadding(25, 12, 25, 12)
            // 데이터가 없거나 1페이지면 비활성화
            isEnabled = totalPages > 0 && currentPage > 1
            setOnClickListener {
                if (currentPage > 1) {
                    currentPage--
                    renderNoticePage()
                }
            }
        }

        val tvPageInfo = TextView(context).apply {
            text = "  ${if (totalPages == 0) 0 else currentPage} / $totalPages  "
            textSize = 14f
            setTextColor(Color.parseColor("#6200EE"))
            gravity = Gravity.CENTER
        }

        val btnNextPage = Button(context).apply {
            text = "▶"
            textSize = 13f
            setPadding(25, 12, 25, 12)
            // 데이터가 없거나 마지막 페이지면 비활성화
            isEnabled = totalPages > 0 && currentPage < totalPages
            setOnClickListener {
                if (currentPage < totalPages) {
                    currentPage++
                    renderNoticePage()
                }
            }
        }

        pagingContainer.addView(btnPrevPage)
        pagingContainer.addView(tvPageInfo)
        pagingContainer.addView(btnNextPage)
        topPanel.addView(pagingContainer)

        // [선생님 권한] ❌ 삭제 버튼
        if (isCurrentUserTeacher) {
            val btnDelete = Button(context).apply {
                text = "❌ 삭제"
                textSize = 12f
                setBackgroundColor(Color.parseColor("#E53935"))
                setTextColor(Color.WHITE)
                setPadding(10, 10, 10, 10)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(15, 0, 5, 0)
                }
                // 삭제할 데이터가 아예 없을 때는 버튼 비활성화 처리해서 크래시 방지
                isEnabled = totalPages > 0
                if (totalPages == 0) {
                    setBackgroundColor(Color.GRAY)
                }
                setOnClickListener { showDeleteSelectDialog() }
            }
            topPanel.addView(btnDelete)
        }

        val index = parentView.indexOfChild(dataDisplay)
        parentView.addView(topPanel, index)
        topPanel.bringToFront()
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