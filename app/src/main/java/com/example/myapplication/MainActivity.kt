package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
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

@Suppress("SetTextI18n", "PrivatePropertyName", "LocalVariableName", "SpellCheckingInspection")
class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var dataDisplay: TextView
    private var tvMenuTemp: TextView? = null
    private var tvMenuDesc: TextView? = null
    private lateinit var profileManager: ProfileManager

    // 🎨 [기능 추가] 형의 소중한 원본 유지하면서 캘린더 매니저만 안전하게 주입
    private lateinit var calendarManager: CalendarManager

    private val serviceKey = "859a622fe6b7f612605ae804aa607702fa0ffa900bcf6d0fbd721193b240fe17"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hideSystemUI()

        drawerLayout = findViewById(R.id.drawer_layout)
        dataDisplay = findViewById(R.id.dataDisplay)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        profileManager = ProfileManager(this)

        // 🎨 [기능 추가] 캘린더 매니저 초기화 등록
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
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
            key = serviceKey,
            num = 10,
            page = 1,
            type = "JSON",
            date = baseDate,
            time = baseTime,
            nx = nx,
            ny = ny
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

        // 🔄 [사이드바] 2번 버튼: 기존 급식 기능 100% 원본 유지
        findViewById<Button>(R.id.btn2)?.setOnClickListener {
            clearAllSubFeatures(noticeManager, chatManager)
            mealManager.fetchTodayMeal()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // [사이드바] 3번 버튼: 나이스 시간표 기능 100% 원본 유지
        findViewById<Button>(R.id.btn3)?.setOnClickListener {
            clearAllSubFeatures(noticeManager, chatManager)
            timetableManager.fetchTimetable()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // [사이드바] 4번 버튼: 내 프로필 정보 관리 및 수정 창 원본 유지
        findViewById<Button>(R.id.btn4)?.setOnClickListener {
            profileManager.showProfileEditDialog()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // [메인화면 하단 버튼 1]: 공지사항 기능 실행 원본 유지
        findViewById<Button>(R.id.btn_bottom_1)?.setOnClickListener {
            clearAllSubFeatures(noticeManager, chatManager)
            noticeManager.fetchNotices()
        }

        // [메인화면 하단 버튼 2]: 1대1 채팅방 실행 원본 유지
        findViewById<Button>(R.id.btn_bottom_2)?.setOnClickListener {
            clearAllSubFeatures(noticeManager, chatManager)
            openChatWithAuth(chatManager)
        }

        // 📅 [기능 추가]: 형, 캘린더 기능을 작동시키기 위해 비어있던 사이드바 1번 버튼(btn1)이나 메인 하단 남는 버튼에 아래 한 줄만 심어 쓰면 돼!
        // 예시로 btn1(사이드바 첫번째 메뉴)에 연동해둘게! 만약 하단 버튼에 넣고 싶으면 R.id.btn_bottom_3 같은 걸로 바꾸기만 하면 끝이야!
        findViewById<Button>(R.id.btn1)?.setOnClickListener {
            clearAllSubFeatures(noticeManager, chatManager)
            calendarManager.openCalendar() // 👈 안전하게 캘린더 화면 열기
            drawerLayout.closeDrawer(GravityCompat.START)
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

    /**
     * 🧹 [기능 추가] 화면 전환 시 컴포넌트 찌꺼기와 리스너들을 원천 안전 청소하는 함수
     */
    private fun clearAllSubFeatures(noticeManager: NoticeManager, chatManager: ChatManager) {
        noticeManager.removeDynamicButton()
        chatManager.removeChatComponents()
        try {
            calendarManager.removeCalendarComponents() // 👈 캘린더 컴포넌트 제거 연동
        } catch (e: Exception) {
            // 혹시 아직 컴포넌트 제거가 정의되지 않았을 경우 크래시 예방 백업
        }
    }
}

object TransLocal {
    fun convertGrid(lat: Double, lon: Double): Pair<Int, Int> {
        val reIdx = 6371.00877 / 5.0
        val slat1Rad = 30.0 * PI / 180.0
        val slat2Rad = 60.0 * PI / 180.0
        val olonRad = 126.0 * PI / 180.0
        val olatRad = 38.0 * PI / 180.0

        val snVal = log(cos(slat1Rad) / cos(slat2Rad), java.lang.Math.E) /
                log(tan(PI * 0.25 + slat2Rad * 0.5) / tan(PI * 0.25 + slat1Rad * 0.5), java.lang.Math.E)

        val sfVal = tan(PI * 0.25 + slat1Rad * 0.5).pow(snVal) * cos(slat1Rad) / snVal
        val roVal = reIdx * sfVal / tan(PI * 0.25 + olatRad * 0.5).pow(snVal)

        var raVal = tan(PI * 0.25 + lat * (PI / 180.0) * 0.5)
        raVal = reIdx * sfVal / raVal.pow(snVal)

        var theta = lon * (PI / 180.0) - olonRad
        if (theta > PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= snVal

        val x = (raVal * sin(theta) + 43 + 0.5).toInt()
        val y = (roVal - raVal * cos(theta) + 136 + 0.5).toInt()

        return Pair(x, y)
    }
}