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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var dataDisplay: TextView
    private var tvMenuTemp: TextView? = null
    private var tvMenuDesc: TextView? = null

    // 공공데이터포털 기상청 인증키
    private val SERVICE_KEY = "859a622fe6b7f612605ae804aa607702fa0ffa900bcf6d0fbd721193b240fe17"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 뷰 초기화
        drawerLayout = findViewById(R.id.drawer_layout)
        dataDisplay = findViewById(R.id.dataDisplay)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)

        // 2. 메뉴 헤더 내 텍스트뷰 연결
        tvMenuTemp = findViewById(R.id.tv_menu_temp)
        tvMenuDesc = findViewById(R.id.tv_menu_desc)

        // 3. 툴바 및 햄버거 버튼 설정
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
            checkPermissionAndGetWeather()
        }

        // 4. 모든 버튼 기능 연동 및 초기화
        setupMenuButtons()
    }

    /**
     * 화면이 회전할 때, 가로/세로 비율에 맞춰 공지 화면을 중첩 없이 새로고침하는 함수
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // 화면 방향이 바뀌었을 때 공지사항 화면이 떠 있다면 안전하게 새로고침을 태웁니다.
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
            key = SERVICE_KEY,
            num = 10,
            page = 1,
            type = "JSON",
            date = baseDate,
            time = baseTime,
            nx = nx,
            ny = ny
        ).enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(
                call: Call<WeatherResponse>,
                response: Response<WeatherResponse>
            ) {
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

    /**
     * 버튼 클릭 이벤트 통합 관리 함수
     */
    private fun setupMenuButtons() {
        val mealManager = MealManager(dataDisplay)
        val timetableManager = TimetableManager(dataDisplay)
        val noticeManager = NoticeManager(this, dataDisplay)

        // 사이드바 2번 버튼: 기존 급식 기능
        findViewById<Button>(R.id.btn2)?.setOnClickListener {
            noticeManager.removeDynamicButton() // 급식으로 갈 때 상단 공지 버튼 패널 지우기
            mealManager.fetchTodayMeal()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // 사이드바 3번 버튼: 나이스 시간표 기능
        findViewById<Button>(R.id.btn3)?.setOnClickListener {
            noticeManager.removeDynamicButton() // 시간표로 갈 때 상단 공지 버튼 패널 지우기
            timetableManager.fetchTimetable()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // 하단 버튼 1: 공지사항 기능 연결
        findViewById<Button>(R.id.btn_bottom_1)?.setOnClickListener {
            // 복잡한 토글 검사 없이, 누르면 언제나 깔끔하게 공지를 새로고침(조회)합니다.
            noticeManager.fetchNotices()
        }
    }
}

// 좌표 변환 객체
object TransLocal {
    fun convertGrid(lat: Double, lon: Double): Pair<Int, Int> {
        val RE = 6371.00877
        val GRID = 5.0
        val SLAT1 = 30.0
        val SLAT2 = 60.0
        val OLON = 126.0
        val OLAT = 38.0
        val XO = 43
        val YO = 136

        val DEGRAD = Math.PI / 180.0
        val re = RE / GRID
        val sn = Math.tan(Math.PI * 0.25 + SLAT2 * DEGRAD * 0.5) / Math.tan(Math.PI * 0.25 + SLAT1 * DEGRAD * 0.5)
        val sn_val = Math.log(Math.cos(SLAT1 * DEGRAD) / Math.cos(SLAT2 * DEGRAD)) / Math.log(sn)
        val sf = Math.tan(Math.PI * 0.25 + SLAT1 * DEGRAD * 0.5)
        val sf_val = Math.pow(sf, sn_val) * Math.cos(SLAT1 * DEGRAD) / sn_val
        val ro = Math.tan(Math.PI * 0.25 + OLAT * DEGRAD * 0.5)
        val ro_val = re * sf_val / Math.pow(ro, sn_val)

        var ra = Math.tan(Math.PI * 0.25 + lat * DEGRAD * 0.5)
        ra = re * sf_val / Math.pow(ra, sn_val)
        var theta = lon * DEGRAD - OLON * DEGRAD
        if (theta > Math.PI) theta -= 2.0 * Math.PI
        if (theta < -Math.PI) theta += 2.0 * Math.PI
        theta *= sn_val

        val x = (ra * Math.sin(theta) + XO + 0.5).toInt()
        val y = (ro_val - ra * Math.cos(theta) + YO + 0.5).toInt()

        return Pair(x, y)
    }
}