package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.location.LocationServices
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

@Suppress("SetTextI18n", "PrivatePropertyName")
class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var dataDisplay: TextView
    private var tvMenuTemp: TextView? = null
    private var tvMenuDesc: TextView? = null
    private lateinit var profileManager: ProfileManager
    private lateinit var calendarManager: CalendarManager

    private var mealButtonPanel: ViewGroup? = null
    private var timetableButtonPanel: ViewGroup? = null

    private val serviceKey = "859a622fe6b7f612605ae804aa607702fa0ffa900bcf6d0fbd721193b240fe17"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hideSystemUI()

        drawerLayout = findViewById(R.id.drawer_layout)
        dataDisplay = findViewById(R.id.dataDisplay)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        profileManager = ProfileManager(this)
        calendarManager = CalendarManager(this, dataDisplay)

        tvMenuTemp = findViewById(R.id.tv_menu_temp)
        tvMenuDesc = findViewById(R.id.tv_menu_desc)

        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
            checkPermissionAndGetWeather()
        }

        setupMenuButtons()
        checkUserLoginAndProfile()
    }

    private fun checkUserLoginAndProfile() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            dataDisplay.text = "❌ 로그인 세션이 없습니다. 다시 로그인해주세요."
        } else {
            enforceProfileSetup()
        }
    }

    private fun enforceProfileSetup() {
        profileManager.checkProfileSetup { isSetup ->
            if (!isSetup) {
                profileManager.showProfileEditDialog {
                    dataDisplay.text = "🎉 프로필 작성이 완료되었습니다!"
                }
            } else {
                dataDisplay.text = "🏠 메인 화면에 진입했습니다. 기능을 선택해 주세요."
            }
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemUI()

        val noticePanel = findViewById<View>(R.id.teacher_button_panel)
        if (noticePanel != null || dataDisplay.text.toString().contains("📢 [학급 공지사항]")) {
            val noticeManager = NoticeManager(this, dataDisplay)
            noticeManager.fetchNotices()
        }
    }

    private fun checkPermissionAndGetWeather() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) {
            getCurrentLocation()
        } else {
            tvMenuDesc?.text = "위치 권한 필요"
            Toast.makeText(this, "권한이 없으면 날씨를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val grid = TransLocal.convertGrid(location.latitude, location.longitude)
                    fetchWeather(grid.first, grid.second)
                } else {
                    tvMenuDesc?.text = "GPS를 켜주세요"
                }
            }
        } catch (e: SecurityException) {
            Log.e("PermissionError", "권한 거부됨: ${e.message}")
        }
    }

    private fun fetchWeather(nx: Int, ny: Int) {
        val cal = Calendar.getInstance()
        val dateSdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val timeSdf = SimpleDateFormat("HH", Locale.getDefault())
        val mmSdf = SimpleDateFormat("mm", Locale.getDefault())

        var baseDate = dateSdf.format(cal.time)
        val hour = timeSdf.format(cal.time).toInt()
        val min = mmSdf.format(cal.time).toInt()

        val baseTime = when {
            hour < 2 || (hour == 2 && min < 15) -> {
                cal.add(Calendar.DATE, -1)
                baseDate = dateSdf.format(cal.time)
                "2300"
            }
            hour < 5 || (hour == 5 && min < 15) -> "0200"
            hour < 8 || (hour == 8 && min < 15) -> "0500"
            hour < 11 || (hour == 11 && min < 15) -> "0800"
            hour < 14 || (hour == 14 && min < 15) -> "1100"
            hour < 17 || (hour == 17 && min < 15) -> "1400"
            hour < 20 || (hour == 20 && min < 15) -> "1700"
            hour < 23 || (hour == 23 && min < 15) -> "2000"
            else -> "2300"
        }

        RetrofitClient.service.getWeather(
            key = serviceKey, num = 10, page = 1, type = "JSON", date = baseDate, time = baseTime, nx = nx, ny = ny
        ).enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                if (response.isSuccessful) {
                    val weatherResponse = response.body()
                    val resultCode = weatherResponse?.response?.header?.resultCode

                    if (resultCode == "00") {
                        val items = weatherResponse.response.body?.items?.item
                        val temp = items?.find { it.category == "TMP" || it.category == "T1H" }?.fcstValue ?: "--"
                        val sky = items?.find { it.category == "SKY" }?.fcstValue ?: "1"

                        val skyText = when (sky) {
                            "1" -> "맑음"
                            "3" -> "구름많음"
                            "4" -> "흐림"
                            else -> "정보없음"
                        }
                        tvMenuTemp?.text = "$temp°"
                        tvMenuDesc?.text = "현재 위치 날씨: $skyText"
                    } else {
                        val msg = weatherResponse?.response?.header?.resultMsg ?: "에러"
                        tvMenuDesc?.text = "오류: $msg ($resultCode)"
                    }
                }
            }
            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                tvMenuDesc?.text = "네트워크 연결 실패"
            }
        })
    }

    private fun setupMenuButtons() {
        val timetableManager = TimetableManager(dataDisplay)
        val noticeManager = NoticeManager(this, dataDisplay)
        val chatManager = ChatManager(this, dataDisplay)
        val mealManager = MealManager(dataDisplay)

        dataDisplay.setOnLongClickListener {
            timetableManager.showTeacherInputDialog(this)
            true
        }

        // 📅 [btn1] 학사일정 버튼
        findViewById<Button>(R.id.btn1)?.setOnClickListener {
            clearAllSubFeatures(noticeManager, chatManager)
            calendarManager.openCalendar()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // 🍱 [btn2] 급식 확인 버튼
        findViewById<Button>(R.id.btn2)?.setOnClickListener {
            clearAllSubFeatures(noticeManager, chatManager)

            mealManager.fetchTodayMeal()

            val rootLayout = dataDisplay.parent as? ViewGroup
            if (rootLayout != null) {
                val linearLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 20
                        bottomMargin = 20
                    }
                }

                val btnPrev = Button(this).apply {
                    text = "◀ 이전 날짜"
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(250, 110)
                    setOnClickListener { mealManager.moveToPrevDay() }
                }

                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(60, 1)
                }

                val btnNext = Button(this).apply {
                    text = "다음 날짜 ▶"
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(250, 110)
                    setOnClickListener { mealManager.moveToNextDay() }
                }

                linearLayout.addView(btnPrev)
                linearLayout.addView(spacer)
                linearLayout.addView(btnNext)

                // dataDisplay 아래쪽에 안정적으로 안착 유도 및 강제 가시성 확보
                val index = rootLayout.indexOfChild(dataDisplay)
                if (index >= 0) {
                    rootLayout.addView(linearLayout, index + 1)
                } else {
                    rootLayout.addView(linearLayout)
                }
                linearLayout.bringToFront()

                mealButtonPanel = linearLayout
            }

            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // ⏱️ [btn3] 시간표 버튼 (UI 짤림/버튼 증발 완전 차단)
        findViewById<Button>(R.id.btn3)?.setOnClickListener {
            clearAllSubFeatures(noticeManager, chatManager)

            timetableManager.fetchTodayTimetable()

            val rootLayout = dataDisplay.parent as? ViewGroup
            if (rootLayout != null) {
                val linearLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 20
                        bottomMargin = 20
                    }
                }

                val btnPrev = Button(this).apply {
                    text = "◀ 이전 날짜"
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(250, 110)
                    setOnClickListener { timetableManager.moveToPrevDay() }
                }

                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(60, 1)
                }

                val btnNext = Button(this).apply {
                    text = "다음 날짜 ▶"
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(250, 110)
                    setOnClickListener { timetableManager.moveToNextDay() }
                }

                linearLayout.addView(btnPrev)
                linearLayout.addView(spacer)
                linearLayout.addView(btnNext)

                // 가려지는 버그 원천 봉쇄: 정확히 dataDisplay 바로 한 칸 뒤(아래)에 뷰 삽입
                val index = rootLayout.indexOfChild(dataDisplay)
                if (index >= 0) {
                    rootLayout.addView(linearLayout, index + 1)
                } else {
                    rootLayout.addView(linearLayout)
                }
                linearLayout.bringToFront()

                timetableButtonPanel = linearLayout
            }

            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // 👤 [btn4] 프로필 수정 버튼
        findViewById<Button>(R.id.btn4)?.setOnClickListener {
            profileManager.showProfileEditDialog()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // 🚪 [btn5] 로그아웃 버튼
        findViewById<Button>(R.id.btn5)?.setOnClickListener {
            clearAllSubFeatures(noticeManager, chatManager)

            val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            sharedPref.edit { putBoolean("key_auto_login", false) }

            FirebaseAuth.getInstance().signOut()

            Toast.makeText(this, "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // 📢 하단 버튼 1 (공지사항)
        findViewById<Button>(R.id.btn_bottom_1)?.setOnClickListener {
            clearAllSubFeatures(noticeManager, chatManager)
            noticeManager.fetchNotices()
        }

        // 💬 하단 버튼 2 (질의응답/채팅)
        findViewById<Button>(R.id.btn_bottom_2)?.setOnClickListener {
            clearAllSubFeatures(noticeManager, chatManager)
            openChatWithAuth(chatManager)
        }
    }

    private fun openChatWithAuth(chatManager: ChatManager) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            chatManager.openChatRoom()
        } else {
            dataDisplay.text = "❌ 인증되지 않은 사용자입니다. 로그인을 다시 진행해주세요."
            Toast.makeText(this, "로그인이 필요한 서비스입니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearAllSubFeatures(noticeManager: NoticeManager, chatManager: ChatManager) {
        noticeManager.removeDynamicButton()
        chatManager.removeChatComponents()

        mealButtonPanel?.let { panel ->
            (panel.parent as? ViewGroup)?.removeView(panel)
            mealButtonPanel = null
        }

        timetableButtonPanel?.let { panel ->
            (panel.parent as? ViewGroup)?.removeView(panel)
            timetableButtonPanel = null
        }

        try {
            calendarManager.removeCalendarComponents()
        } catch (_: Exception) {
        }
    }
}

object TransLocal {
    fun convertGrid(lat: Double, lon: Double): Pair<Int, Int> {
        val reIdx = 6371.00877 / 5.0
        val slat1Rad = 30.0 * PI / 180.0
        val slat2Rad = 60.0 * PI / 180.0
        val oLonRad = 126.0 * PI / 180.0
        val oLatRad = 38.0 * PI / 180.0

        val snVal = log(cos(slat1Rad) / cos(slat2Rad), java.lang.Math.E) /
                log(tan(PI * 0.25 + slat2Rad * 0.5) / tan(PI * 0.25 + slat1Rad * 0.5), java.lang.Math.E)

        val sfVal = tan(PI * 0.25 + slat1Rad * 0.5).pow(snVal) * cos(slat1Rad) / snVal
        val roVal = reIdx * sfVal / tan(PI * 0.25 + oLatRad * 0.5).pow(snVal)

        var raVal = tan(PI * 0.25 + lat * (PI / 180.0) * 0.5)
        raVal = reIdx * sfVal / raVal.pow(snVal)

        var theta = lon * (PI / 180.0) - oLonRad
        if (theta > PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= snVal

        val x = (raVal * sin(theta) + 43 + 0.5).toInt()
        val y = (roVal - raVal * cos(theta) + 136 + 0.5).toInt()

        return Pair(x, y)
    }
}