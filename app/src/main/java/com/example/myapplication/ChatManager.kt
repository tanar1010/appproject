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
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class ChatManager(private val context: Context, private val dataDisplay: TextView) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var chatRootLayout: LinearLayout? = null
    private var messageListView: ListView? = null
    private var messageAdapter: ChatAdapter? = null
    private val messageList = ArrayList<ChatMessage>()

    private var messageListenerRegistration: ListenerRegistration? = null

    private var targetUid: String? = null
    private var targetName: String = "상대방"
    private var targetRole: String = "STUDENT"
    private var currentRoomId: String? = null

    // 메인 화면의 다른 뷰들을 가리기 위해 임시 저장할 리스트
    private val hiddenViews = ArrayList<View>()

    data class ChatMessage(
        val messageId: String = "",
        val senderId: String = "",
        val senderName: String = "",
        val message: String = "",
        @field:JvmField val isRead: Boolean = false, // 파이어베이스 불리언 매핑 보장
        var timestamp: Any? = null
    )

    data class UserItem(val uid: String, val displayName: String, val role: String)

    /**
     * 💬 1대1 메시지 상대 선택 및 입장
     */
    fun openChatRoom() {
        val currentUser = auth.currentUser ?: return
        val myUid = currentUser.uid

        db.collection("Users").document(myUid).get().addOnSuccessListener { myDoc ->
            val myName = myDoc.getString("name") ?: currentUser.email?.substringBefore("@") ?: "내 이름"
            val myRole = myDoc.getString("role") ?: "STUDENT"

            val targetRoleQuery = if (myRole.uppercase() == "STUDENT") "TEACHER" else "STUDENT"
            val targetRoleTitle = if (targetRoleQuery == "TEACHER") "선생님" else "학생"

            db.collection("Users")
                .whereEqualTo("role", targetRoleQuery)
                .get()
                .addOnSuccessListener { snapshots ->
                    if (snapshots.isEmpty) {
                        dataDisplay.text = "💬 대화할 수 있는 [${targetRoleTitle}] 유저가 없습니다."
                        return@addOnSuccessListener
                    }

                    val userList = ArrayList<UserItem>()
                    val nameItems = ArrayList<String>()

                    for (doc in snapshots.documents) {
                        if (doc.id == myUid) continue
                        val rawName = doc.getString("name")
                        val rawEmail = doc.getString("email")
                        val roleStr = doc.getString("role") ?: targetRoleQuery
                        val finalName = rawName ?: rawEmail?.substringBefore("@") ?: "사용자"

                        userList.add(UserItem(doc.id, finalName, roleStr))
                        nameItems.add("👤 $finalName ($targetRoleTitle)")
                    }

                    AlertDialog.Builder(context)
                        .setTitle("💬 대화할 상대를 선택하세요")
                        .setItems(nameItems.toTypedArray()) { _, which ->
                            val selectedUser = userList[which]
                            targetUid = selectedUser.uid
                            targetName = selectedUser.displayName
                            targetRole = selectedUser.role

                            currentRoomId = if (myUid < targetUid!!) "${myUid}_${targetUid}" else "${targetUid}_${myUid}"

                            // 1. 💥 메인 화면의 모든 하단 UI 및 레이아웃 숨기기
                            hideMainActivityUi()

                            // 2. 채팅 UI 빌드 및 실시간 연동 시작
                            buildKakaoTalkUI(currentRoomId!!, myName)
                            startListeningMessages(currentRoomId!!)
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }
        }
    }

    /**
     * 🎨 카카오톡 스타일 UI 생성 (상단바 나가기 버튼 추가 완료)
     */
    private fun buildKakaoTalkUI(roomId: String, myName: String) {
        val parent = dataDisplay.parent as? ViewGroup ?: return
        removeChatComponents()

        dataDisplay.visibility = View.GONE

        chatRootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#BACEE0"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 📌 상단바 (나가기 버튼 + 타이틀)
        val actionBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#A0B1C4"))
            padding(12, 10, 12, 10)
            gravity = Gravity.CENTER_VERTICAL
        }

        // ⬅️ [요청 반영] 메인화면으로 돌아가는 나가기 버튼
        val backButton = TextView(context).apply {
            text = "◀ 나가기"
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))
            setTypeface(null, Typeface.BOLD)
            setPadding(10, 10, 25, 10)
            setOnClickListener {
                // 나가기 버튼 클릭 시 채팅방 종료 및 메인 화면 복구
                removeChatComponents()
            }
        }
        actionBar.addView(backButton)

        // 상대방 이름 및 정보 버튼
        val titleTextView = TextView(context).apply {
            val roleText = if (targetRole.uppercase() == "TEACHER") "선생님" else "학생"
            text = "$targetName $roleText 님 [정보]"
            textSize = 15f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showTargetProfileDialog() }
        }
        actionBar.addView(titleTextView)
        chatRootLayout?.addView(actionBar)

        // 리스트뷰
        messageListView = ListView(context).apply {
            divider = null
            dividerHeight = 0
            selector = ContextCompat.getDrawable(context, android.R.color.transparent)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        messageAdapter = ChatAdapter(context, messageList, auth.currentUser?.uid ?: "")
        messageListView?.adapter = messageAdapter
        chatRootLayout?.addView(messageListView)

        // 하단 입력바
        val inputContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            padding(10, 8, 10, 8)
            gravity = Gravity.CENTER_VERTICAL
        }

        val messageInput = EditText(context).apply {
            hint = "메시지를 입력하세요..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.BLACK) // ✍️ 글씨 검은색 확실히 지정
            textSize = 15f
            setBackgroundResource(android.R.color.transparent)
            maxLines = 3
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val sendButton = Button(context).apply {
            text = "전송"
            setBackgroundColor(Color.parseColor("#FEE500"))
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                val text = messageInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendMessage(roomId, myName, text)
                    messageInput.setText("")
                }
            }
        }

        inputContainer.addView(messageInput)
        inputContainer.addView(sendButton)
        chatRootLayout?.addView(inputContainer)

        parent.addView(chatRootLayout)
    }

    /**
     * 📡 실시간 메시지 감지 및 [1 숫자 지우기] 업데이트 로직 수정 완료
     */
    private fun startListeningMessages(roomId: String) {
        val myUid = auth.currentUser?.uid ?: return

        val query = db.collection("Chats").document(roomId).collection("Messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)

        messageListenerRegistration = query.addSnapshotListener { snapshots, error ->
            if (error != null || snapshots == null) return@addSnapshotListener

            messageList.clear()
            for (doc in snapshots.documents) {
                try {
                    val msg = doc.toObject(ChatMessage::class.java)
                    if (msg != null) {
                        messageList.add(msg)

                        // 💥 [핵심 수정] 상대방이 보낸 글을 내가 읽었을 때, 무작위 Document ID를 정확히 타격하여 true 변경
                        if (msg.senderId != myUid && !msg.isRead) {
                            db.collection("Chats").document(roomId).collection("Messages")
                                .document(doc.id).update("isRead", true)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatManager", "파싱 에러", e)
                }
            }
            messageAdapter?.notifyDataSetChanged()
            if (messageList.isNotEmpty()) {
                messageListView?.setSelection(messageList.size - 1)
            }
        }
    }

    /**
     * ✉️ 메시지 전송 (문서 ID와 내부 messageId를 일치시킴)
     */
    private fun sendMessage(roomId: String, myName: String, text: String) {
        val myUid = auth.currentUser?.uid ?: return

        // 💥 [핵심 수정] 무작위 자동 문서 생성 구조 활용
        val msgRef = db.collection("Chats").document(roomId).collection("Messages").document()

        val msgData = hashMapOf(
            "messageId" to msgRef.id, // 문서 이름을 내부 필드 아이디와 100% 일치시킴
            "senderId" to myUid,
            "senderName" to myName,
            "message" to text,
            "isRead" to false,
            "timestamp" to FieldValue.serverTimestamp()
        )

        msgRef.set(msgData).addOnFailureListener { e ->
            Toast.makeText(context, "전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 🧭 [핵심 수정] 메인 화면의 하단 UI를 포함한 모든 레이아웃 강제 일시 차단
     */
    private fun hideMainActivityUi() {
        try {
            val parent = dataDisplay.parent as? ViewGroup ?: return
            hiddenViews.clear()

            // dataDisplay와 같은 선상(형제)에 있는 모든 다른 레이아웃과 하단 버튼 바를 찾아서 숨김 목록에 추가
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child != dataDisplay && child.visibility == View.VISIBLE) {
                    child.visibility = View.GONE
                    hiddenViews.add(child) // 나중에 복구하기 위해 기록
                }
            }
        } catch (e: Exception) {
            Log.e("ChatManager", "UI 숨기기 실패: ${e.message}")
        }
    }

    /**
     * 🚪 채팅방에서 나갈 때 메인 화면 UI 완벽 원상복구
     */
    fun removeChatComponents() {
        // 1. 실시간 파이어베이스 리스너 즉시 제거
        messageListenerRegistration?.remove()
        messageListenerRegistration = null

        // 2. 그려진 채팅방 레이아웃 화면에서 삭제
        chatRootLayout?.let {
            val parent = it.parent as? ViewGroup
            parent?.removeView(it)
            chatRootLayout = null
        }

        // 3. 메인 안내 텍스트 재노출
        dataDisplay.visibility = View.VISIBLE

        // 4. 💥 숨겨두었던 하단 UI 및 메인 레이아웃들 다시 전부 보이게 원상복구!
        for (view in hiddenViews) {
            view.visibility = View.VISIBLE
        }
        hiddenViews.clear()

        // 키보드 내리기
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(dataDisplay.windowToken, 0)
    }

    private fun showTargetProfileDialog() {
        val uid = targetUid ?: return
        db.collection("Users").document(uid).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val name = doc.getString("name") ?: "이름 미등록"
                val role = doc.getString("role") ?: "STUDENT"

                val extraInfo = if (role.uppercase() == "TEACHER") {
                    "• 담당 과목: ${doc.getString("subject") ?: "미등록"}\n• 학급 정보: ${doc.getString("roleInfo") ?: "미등록"}"
                } else {
                    "• 학년/반/번호: ${doc.getString("grade") ?: "-"}학년 ${doc.getString("classNum") ?: "-"}반 ${doc.getString("number") ?: "-"}번"
                }

                val roleTitle = if (role.uppercase() == "TEACHER") "선생님" else "학생"

                AlertDialog.Builder(context)
                    .setTitle("👤 $roleTitle 상세 정보")
                    .setMessage("• 이름: $name\n• 신분: $roleTitle\n$extraInfo")
                    .setPositiveButton("확인", null)
                    .show()
            }
        }
    }

    private fun View.padding(left: Int, top: Int, right: Int, bottom: Int) {
        val scale = resources.displayMetrics.density
        setPadding((left * scale).toInt(), (top * scale).toInt(), (right * scale).toInt(), (bottom * scale).toInt())
    }
}

/**
 * 🎨 카톡 1이 정상 작동하도록 매핑된 커스텀 어댑터
 */
class ChatAdapter(
    private val context: Context,
    private val list: List<ChatManager.ChatMessage>,
    private val myUid: String
) : BaseAdapter() {

    override fun getCount(): Int = list.size
    override fun getItem(position: Int): Any = list[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val msg = list[position]

        val cellLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 10, 20, 10)
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val nameTextView = TextView(context).apply {
            text = msg.senderName
            textSize = 11f
            setTextColor(Color.DKGRAY)
            setPadding(10, 0, 10, 4)
        }

        val bubbleTextView = TextView(context).apply {
            text = msg.message
            textSize = 15f
            setTextColor(Color.BLACK)
            setPadding(26, 16, 26, 16)
            setBackgroundResource(android.R.drawable.toast_frame)
        }

        val readStatusTextView = TextView(context).apply {
            text = "1"
            textSize = 11f
            setTextColor(Color.parseColor("#FEE500"))
            setTypeface(null, Typeface.BOLD)
            setPadding(10, 0, 10, 0)
            // 💥 데이터베이스에서 상대방이 읽었음(true)을 감지하면 화면에서 '1'을 지웁니다.
            visibility = if (msg.isRead) View.GONE else View.VISIBLE
        }

        val bubbleParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val statusParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        if (msg.senderId == myUid) {
            cellLayout.gravity = Gravity.END
            nameTextView.visibility = View.GONE
            bubbleTextView.background?.setTint(Color.parseColor("#FEE500"))

            contentLayout.addView(bubbleTextView, bubbleParams)
            cellLayout.addView(readStatusTextView, statusParams)
            cellLayout.addView(contentLayout)
        } else {
            cellLayout.gravity = Gravity.START
            nameTextView.visibility = View.VISIBLE
            bubbleTextView.background?.setTint(Color.WHITE)

            contentLayout.addView(nameTextView)
            contentLayout.addView(bubbleTextView, bubbleParams)
            cellLayout.addView(contentLayout)
            cellLayout.addView(readStatusTextView, statusParams)
        }

        return cellLayout
    }
}