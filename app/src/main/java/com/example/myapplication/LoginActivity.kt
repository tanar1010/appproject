package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 🔑 [안전한 자동 로그인 체크]
        // 이미 파이어베이스 세션이 기기에 남아있고 자동 로그인을 켜놨다면 바로 메인으로 프리패스!
        if (checkAutoLogin()) {
            return // 바로 메인으로 가니까 밑에 UI 세팅은 건너뜀
        }

        setupUI()
    }

    private fun checkAutoLogin(): Boolean {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isAutoLoginChecked = sharedPref.getBoolean("key_auto_login", false)
        val currentUser = FirebaseAuth.getInstance().currentUser

        // 🌟 패스워드 없이 안전하게 세션 확인만으로 메인 화면 진입
        if (isAutoLoginChecked && currentUser != null) {
            // 크래시 유발하던 AuthManager.login 호출을 과감히 제거하고
            // 이미 로그인이 검증됐으니 곧바로 메인으로 다이렉트 슛!
            moveToMain("STUDENT") // 기본 권한으로 일단 진입 (MainActivity에서 세션을 다시 검사하므로 안전)
            return true
        }
        return false
    }

    private fun setupUI() {
        val etEmail = findViewById<EditText>(R.id.et_email)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login)
        val btnGoRegister = findViewById<Button>(R.id.btn_go_register)
        val cbAutoLogin = findViewById<CheckBox>(R.id.cb_auto_login)

        // 회원가입 버튼
        btnGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // 로그인 버튼
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일과 비번을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // AuthManager 로그인 호출 (여기서는 아이디, 비번을 다 넣으니까 정상 작동)
            AuthManager.login(email, password) { role ->
                if (role != null) {
                    // 로그인 성공 시에만 체크박스 상태를 기기에 저장
                    val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    sharedPref.edit().putBoolean("key_auto_login", cbAutoLogin.isChecked).apply()

                    Toast.makeText(this, "로그인 성공! 권한: $role", Toast.LENGTH_SHORT).show()
                    moveToMain(role)
                } else {
                    Toast.makeText(this, "로그인 실패: 정보를 확인하세요.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun moveToMain(role: String) {
        val intent = Intent(this, MainActivity::class.java)
        // 혹시 학생/선생님 화면이 완전히 갈리는 구조라면 아래 주석을 풀어서 대응 가능해 형
        // val intent = when (role.uppercase()) {
        //     "TEACHER" -> Intent(this, TeacherMainActivity::class.java)
        //     else -> Intent(this, MainActivity::class.java)
        // }

        // 로그인창 스택 찌꺼기 안 남게 깔끔하게 날리기
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}