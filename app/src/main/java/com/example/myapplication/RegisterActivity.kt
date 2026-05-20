package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etEmail = findViewById<EditText>(R.id.et_reg_email)
        val etPassword = findViewById<EditText>(R.id.et_reg_password)
        val rgRole = findViewById<RadioGroup>(R.id.rg_reg_role)
        val btnSubmit = findViewById<Button>(R.id.btn_register_submit)

        btnSubmit.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            val role = if (rgRole.checkedRadioButtonId == R.id.rb_reg_student) "STUDENT" else "TEACHER"

            if (email.isEmpty() || password.length < 6) {
                Toast.makeText(this, "정보를 올바르게 입력하세요 (비번 6자리 이상)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // AuthManager 호출
            AuthManager.signUp(email, password, role) { success ->
                if (success) {
                    Toast.makeText(this, "가입 성공 및 자동 로그인! 🎉", Toast.LENGTH_SHORT).show()

                    // 🔑 회원가입 성공 시에도 다음 앱 실행을 위해 '자동 로그인 활성화' 처리해 주기
                    val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    sharedPref.edit().putBoolean("key_auto_login", true).apply()

                    // 🔑 로그인 화면으로 빠꾸치지 않고 바로 메인 화면 가동!
                    val intent = Intent(this, MainActivity::class.java)
                    // (선택 사항) 필요 시 MainActivity에 어떤 권한인지 intent extra로 던져줄 수 있음
                    // intent.putExtra("USER_ROLE", role)

                    // 기존 로그인 대기 스택을 싹 비우고 메인을 루트로 띄우기
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "가입 실패.. (이미 있는 계정이거나 네트워크 오류)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}