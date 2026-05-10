package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        setupUI()
    }

    private fun setupUI() {
        val etEmail = findViewById<EditText>(R.id.et_email)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login)
        val btnGoRegister = findViewById<Button>(R.id.btn_go_register)

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

            // AuthManager 로그인 호출
            AuthManager.login(email, password) { role ->
                if (role != null) {
                    Toast.makeText(this, "로그인 성공! 권한: $role", Toast.LENGTH_SHORT).show()
                    moveToMain(role) // 여기서 이제 정상적으로 인식됩니다.
                } else {
                    Toast.makeText(this, "로그인 실패: 정보를 확인하세요.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    } // setupUI 함수 끝

    // [수정] 이 함수가 setupUI 밖으로 나와 있어야 합니다!
    private fun moveToMain(role: String) {
        val intent = when (role.uppercase()) {
           // "STUDENT" -> Intent(this, StudentMainActivity::class.java)
          //  "TEACHER" -> Intent(this, TeacherMainActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}