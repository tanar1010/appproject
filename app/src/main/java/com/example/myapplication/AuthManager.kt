// 1. 패키지 경로는 파일 최상단에 하나만 있어야 하며, 실제 폴더 위치와 같아야 합니다.
// (팁: 파일 맨 위 빨간 줄에 마우스를 올리고 'Move to package...'를 클릭하면 자동 수정됩니다.)
package com.example.myapplication

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

object AuthManager {

    // 2. static 필드에 인스턴스를 미리 담아두지 않고, 필요할 때 get()으로 호출하여 메모리 누수 방지
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    const val ROLE_STUDENT = "STUDENT"
    const val ROLE_TEACHER = "TEACHER"

    fun getRole(uid: String, callback: (String?) -> Unit) {
        db.collection("Users").document(uid).get()
            .addOnSuccessListener { document ->
                // [해결] 여기서 String? 타입을 명시적으로 넘겨줍니다.
                val role = document.getString("role")
                callback(role)
            }
            .addOnFailureListener {
                callback(null)
            }
    }

    fun login(email: String, password: String, callback: (String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        // Firestore에서 해당 유저의 role(학생/선생님)을 가져옴
                        db.collection("Users").document(uid).get()
                            .addOnSuccessListener { document ->
                                val role = document.getString("role")
                                callback(role) // "STUDENT" 또는 "TEACHER" 반환
                            }
                            .addOnFailureListener {
                                callback(null)
                            }
                    }
                } else {
                    callback(null)
                }
            }
    }
    fun signUp(email: String, password: String, role: String, callback: (Boolean) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // 1. 가입 성공 시 유저의 고유 UID 가져오기
                    val uid = auth.currentUser?.uid

                    if (uid != null) {
                        // 2. 저장할 데이터 맵 생성
                        val userMap = hashMapOf("role" to role)

                        // 3. Firestore에 "Users" 컬렉션 아래 "UID"라는 이름의 문서를 '자동' 생성
                        db.collection("Users").document(uid).set(userMap)
                            .addOnSuccessListener {
                                Log.d("AuthManager", "Firestore 저장 완료: $uid")
                                callback(true)
                            }
                            .addOnFailureListener { e ->
                                Log.e("AuthManager", "Firestore 저장 실패", e)
                                callback(false)
                            }
                    }
                } else {
                    Log.e("AuthManager", "계정 생성 실패", task.exception)
                    callback(false)
                }
            }
    }

    fun signInAndRoute(
        context: Context,
        email: String,
        pw: String,
        onStudent: () -> Unit,
        onTeacher: () -> Unit
    ) {
        if (email.isEmpty() || pw.isEmpty()) {
            Toast.makeText(context, "아이디와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, pw)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                uid?.let { fetchUserRole(it, onStudent, onTeacher) }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "로그인 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchUserRole(uid: String, onStudent: () -> Unit, onTeacher: () -> Unit) {
        db.collection("Users").document(uid).get()
            .addOnSuccessListener { document: DocumentSnapshot ->
                val role = document.getString("role")
                when (role) {
                    ROLE_STUDENT -> onStudent()
                    ROLE_TEACHER -> onTeacher()
                    else -> Log.e("AuthManager", "권한 정보가 없습니다.")
                }
            }
    }

    fun signOut() {
        auth.signOut()
    }
}