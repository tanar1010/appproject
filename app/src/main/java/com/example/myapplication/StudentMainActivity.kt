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

class StudentMainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // [핵심] 네가 준 메인 UI(activity_main)를 그대로 사용
        setContentView(R.layout.activity_main)

        // 위치 권한 팝업 띄우기
        checkLocationPermissions()

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val dataDisplay = findViewById<TextView>(R.id.dataDisplay)

        // 삼선 메뉴 아이콘 클릭 시 메뉴 열기
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // [핵심] 네 XML에 있는 버튼 ID들 하나하나 연결 (btn1~btn6)
        findViewById<Button>(R.id.btn1).setOnClickListener {
            dataDisplay.text = "학생 모드: 실시간 날씨 갱신 중..."
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<Button>(R.id.btn2).setOnClickListener {
            dataDisplay.text = "학생 모드: 미세먼지 정보 확인"
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<Button>(R.id.btn3).setOnClickListener {
            dataDisplay.text = "학생 모드: 내일 예보 확인"
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<Button>(R.id.btn4).setOnClickListener {
            dataDisplay.text = "학생 모드: 전국 날씨 확인"
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<Button>(R.id.btn5).setOnClickListener {
            Toast.makeText(this, "학생 전용 알림 설정", Toast.LENGTH_SHORT).show()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<Button>(R.id.btn6).setOnClickListener {
            Toast.makeText(this, "학생용 앱 정보", Toast.LENGTH_SHORT).show()
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
