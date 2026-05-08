package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback // 추가
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var dataDisplay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        dataDisplay = findViewById(R.id.dataDisplay)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)

        // 1. 햄버거 버튼 클릭 시 드로어 열기
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // 2. [최신 방식] 뒤로가기 버튼/제스처 처리
        val callback = object : OnBackPressedCallback(false) {
            // 처음에는 false로 설정 (드로어가 열릴 때만 활성화할 거니까요)
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)

        // 3. 드로어 상태에 따라 뒤로가기 콜백 활성화/비활성화
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerOpened(drawerView: android.view.View) {
                callback.isEnabled = true // 메뉴가 열리면 뒤로가기 가로채기 활성화
            }
            override fun onDrawerClosed(drawerView: android.view.View) {
                callback.isEnabled = false // 메뉴가 닫히면 비활성화 (시스템 뒤로가기 사용)
            }
            override fun onDrawerSlide(drawerView: android.view.View, slideOffset: Float) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        // 4. 메뉴 버튼들 설정
        val btn1 = findViewById<Button>(R.id.btn1)
        btn1.setOnClickListener {
            dataDisplay.text = "기능 1 선택됨"
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // ... btn2, btn3 등 나머지 버튼 설정
    }

    // 기존의 override fun onBackPressed() { ... } 코드는 삭제하세요!
}