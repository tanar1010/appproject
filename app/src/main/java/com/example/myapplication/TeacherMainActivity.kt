package com.example.myapplication
/*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar

class TeacherMainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // [핵심] 선생님도 똑같은 activity_main UI 사용
        setContentView(R.layout.activity_main)

        checkLocationPermissions()

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val dataDisplay = findViewById<TextView>(R.id.dataDisplay)

        // 툴바 타이틀만 선생님용으로 변경 (구분하기 쉽게)
        toolbar.title = "날씨 앱 (선생님용)"

        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // 선생님용 버튼 액션 (학생용과 구분되게 메시지 설정)
        findViewById<Button>(R.id.btn1).setOnClickListener {
            dataDisplay.text = "선생님 모드: 전체 기상 데이터 분석"
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<Button>(R.id.btn2).setOnClickListener {
            dataDisplay.text = "선생님 모드: 지역별 미세먼지 통계"
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<Button>(R.id.btn3).setOnClickListener {
            dataDisplay.text = "선생님 모드: 주간 예보 데이터 관리"
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<Button>(R.id.btn4).setOnClickListener {
            dataDisplay.text = "선생님 모드: 전국 기상망 확인"
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<Button>(R.id.btn5).setOnClickListener {
            Toast.makeText(this, "관리자 알림 설정 메뉴", Toast.LENGTH_SHORT).show()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<Button>(R.id.btn6).setOnClickListener {
            Toast.makeText(this, "교사용 시스템 정보", Toast.LENGTH_SHORT).show()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    private fun checkLocationPermissions() {
        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
}
*/
