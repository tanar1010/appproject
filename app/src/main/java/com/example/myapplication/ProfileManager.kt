package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileManager(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    // 💡 형이 로그인할 때 쓰는 Firestore 데이터베이스로 똑같이 연동!
    private val db = FirebaseFirestore.getInstance()

    private val userId: String?
        get() = auth.currentUser?.uid

    /**
     * 사용자의 프로필 상세 정보(이름 등)가 이미 Firestore에 등록되어 있는지 확인
     */
    fun checkProfileSetup(onComplete: (isSetup: Boolean) -> Unit) {
        val uid = userId
        if (uid == null) {
            onComplete(false)
            return
        }

        // Firestore의 Users 컬렉션에서 내 UID 문서를 확인합니다.
        db.collection("Users").document(uid).get()
            .addOnSuccessListener { document ->
                // 이미 이름(name)을 적어서 프로필 세팅이 완료되었는지 체크
                val isSetup = document.exists() && document.contains("name")
                onComplete(isSetup)
            }
            .addOnFailureListener {
                onComplete(false)
            }
    }

    /**
     * 🆕 [핵심 기능] 로그인 직후 혹은 햄버거 메뉴 클릭 시 작동하는 프로필 생성/수정 창
     * Firestore에 저장된 유저의 role ("TEACHER" 또는 "STUDENT")을 직접 읽어서 UI를 완전히 격리합니다.
     */
    fun showProfileEditDialog(onSaveSuccess: () -> Unit = {}) {
        val uid = userId ?: return

        // 1. 화면을 만들기 전에 Firestore에서 현재 로그인한 유저의 정보를 먼저 완전히 가져옵니다.
        db.collection("Users").document(uid).get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    Toast.makeText(context, "유저 권한 정보가 존재하지 않습니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // 2. AuthManager와 동일하게 대문자 문자열 "TEACHER" / "STUDENT"로 역할을 확인합니다.
                val role = document.getString("role") ?: "STUDENT"
                val isTeacher = role.equals("TEACHER", ignoreCase = true)

                // 3. 다이얼로그 생성 및 세팅
                val builder = AlertDialog.Builder(context)
                builder.setTitle(if (isTeacher) "👨‍🏫 교사 프로필 설정" else "🧑‍🎓 학생 프로필 설정")
                builder.setCancelable(false) // 최초 입력 강제를 위해 바깥 클릭 취소 막음

                val layout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 40)
                }

                // [공통 항목] 이름 입력창
                val etName = EditText(context).apply { hint = "이름 (예: 홍길동)" }
                layout.addView(etName)

                // 신분별 입력 필드 참조 변수 선언
                var etGrade: EditText? = null
                var etClass: EditText? = null
                var etNumber: EditText? = null

                var etSubject: EditText? = null
                var etRole: EditText? = null
                var etSubRole: EditText? = null

                // 4. 🔥 신분에 따라 입력창 종류를 원천적으로 격리 분리!
                if (!isTeacher) {
                    // STUDENT인 경우: 학년, 반, 번호만 배치
                    etGrade = EditText(context).apply { hint = "학년 (숫자만)" }
                    etClass = EditText(context).apply { hint = "반 (숫자만)" }
                    etNumber = EditText(context).apply { hint = "번호 (숫자만)" }
                    layout.addView(etGrade)
                    layout.addView(etClass)
                    layout.addView(etNumber)
                } else {
                    // TEACHER인 경우: 담당 과목, 담임반, 부담임반만 배치 (학생 필드는 절대 안 뜸)
                    etSubject = EditText(context).apply { hint = "담당 과목 (예: 수학)" }
                    etRole = EditText(context).apply { hint = "담임 학년/반 (예: 3학년 2반 / 없으면 '없음')" }
                    etSubRole = EditText(context).apply { hint = "부담임 학년/반 (없으면 '없음')" }
                    layout.addView(etSubject)
                    layout.addView(etRole)
                    layout.addView(etSubRole)
                }

                builder.setView(layout)

                // 5. 🆕 수정창을 열었을 때: 기존에 작성해 둔 데이터가 있다면 폼에 꽉 채워주기
                etName.setText(document.getString("name") ?: "")
                if (!isTeacher) {
                    etGrade?.setText(document.getString("grade") ?: "")
                    etClass?.setText(document.getString("classNum") ?: "")
                    etNumber?.setText(document.getString("number") ?: "")
                } else {
                    etSubject?.setText(document.getString("subject") ?: "")
                    etRole?.setText(document.getString("roleInfo") ?: "") // 'role'은 회원가입용 신분 키값이므로 'roleInfo'로 분리저장
                    etSubRole?.setText(document.getString("subRole") ?: "")
                }

                // 6. 저장 버튼 리스너 정의
                builder.setPositiveButton("저장") { dialog, _ ->
                    val name = etName.text.toString().trim()

                    if (name.isEmpty()) {
                        Toast.makeText(context, "이름은 필수 입력 항목입니다.", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    // Firestore에 기존 가입 정보(role)를 유지하면서 새 프로필 데이터를 누적 업데이트할 맵 패키징
                    val profileData = hashMapOf<String, Any>()
                    profileData["name"] = name
                    profileData["role"] = role // 기존 가입 권한(STUDENT/TEACHER) 그대로 유지

                    if (!isTeacher) {
                        profileData["grade"] = etGrade?.text.toString().trim()
                        profileData["classNum"] = etClass?.text.toString().trim()
                        profileData["number"] = etNumber?.text.toString().trim()
                    } else {
                        profileData["subject"] = etSubject?.text.toString().trim()
                        profileData["roleInfo"] = etRole?.text.toString().trim()
                        profileData["subRole"] = etSubRole?.text.toString().trim()
                    }

                    // Firestore의 Users -> UID 문서에 프로필 정보 병합(Update) 저장!
                    db.collection("Users").document(uid).update(profileData)
                        .addOnSuccessListener {
                            Toast.makeText(context, "프로필이 연동 저장되었습니다.", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            onSaveSuccess()
                        }
                        .addOnFailureListener { exception ->
                            // 만약 최초 등록이라서 update가 안 될 경우를 대비해 set(Merge)으로 안전 장치
                            db.collection("Users").document(uid).set(profileData, com.google.firebase.firestore.SetOptions.merge())
                                .addOnSuccessListener {
                                    Toast.makeText(context, "프로필이 연동 저장되었습니다.", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                    onSaveSuccess()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "저장 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                }

                builder.setNegativeButton("취소") { dialog, _ ->
                    dialog.dismiss()
                }

                // 완벽히 비동기가 맞춰진 시점에 다이얼로그 팝업 가동
                builder.show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "권한 조회 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}