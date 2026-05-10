package com.example.myapplication // 1. 본인의 실제 패키지명으로 꼭 수정!

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
                    Toast.makeText(this, "가입 성공!", Toast.LENGTH_SHORT).show()
                    finish() // 로그인 화면으로 돌아가기
                } else {
                    Toast.makeText(this, "가입 실패.. (이미 있는 계정이거나 네트워크 오류)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}