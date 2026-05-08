package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
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

    // TODO: 공공데이터포털에서 복사한 '디코딩(Decoding)' 인증키를 아래에 붙여넣으세요.
    private val SERVICE_KEY = "859a622fe6b7f612605ae804aa607702fa0ffa900bcf6d0fbd721193b240fe17"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 뷰 초기화
        drawerLayout = findViewById(R.id.drawer_layout)
        dataDisplay = findViewById(R.id.dataDisplay)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)

        // 2. 메뉴 헤더 내 텍스트뷰 연결 (ID가 activity_main.xml에 있어야 함)
        tvMenuTemp = findViewById(R.id.tv_menu_temp)
        tvMenuDesc = findViewById(R.id.tv_menu_desc)

        // 3. 툴바 및 햄버거 버튼 설정
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
            // 메뉴가 열릴 때 위치 정보를 확인하고 날씨를 가져옴
            checkPermissionAndGetWeather()
        }

        setupMenuButtons()
    }

    private fun checkPermissionAndGetWeather() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            // 권한이 없으면 요청
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
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

        // 1. 현재 날짜와 시간 가져오기
        val dateSdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val timeSdf = SimpleDateFormat("HH", Locale.getDefault())
        val mmSdf = SimpleDateFormat("mm", Locale.getDefault())

        var baseDate = dateSdf.format(cal.time)
        val hour = timeSdf.format(cal.time).toInt()
        val min = mmSdf.format(cal.time).toInt()

        /**
         * 2. 기상청 단기예보 발표 시간 계산 로직
         * 발표 시간: 02, 05, 08, 11, 14, 17, 20, 23 (3시간 간격)
         * 실제 데이터 생성은 발표 시간 + 10~15분 정도 걸림
         */
        val baseTime = when {
            hour < 2 || (hour == 2 && min < 15) -> {
                // 새벽 2시 15분 이전이면 어제 23시 데이터를 가져와야 함
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

        Log.d("WeatherAPI", "Request Date: $baseDate, Time: $baseTime, NX: $nx, NY: $ny")

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
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                if (response.isSuccessful) {
                    val weatherResponse = response.body()
                    val resultCode = weatherResponse?.response?.header?.resultCode

                    if (resultCode == "00") {
                        val items = weatherResponse.response.body?.items?.item
                        // T1H(초단기 기온) 또는 TMP(단기 기온)
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
                        // 여기에 "NO DATA" 오류가 찍혔던 것임. 시간을 맞췄으니 이제 데이터가 나올 겁니다.
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
        val btnIds = arrayOf(R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6)
        for (i in btnIds.indices) {
            findViewById<Button>(btnIds[i])?.setOnClickListener {
                dataDisplay.text = "기능 ${i + 1} 실행 중"
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
    }
}

// 좌표 변환 객체 (MainActivity.kt 파일 하단에 포함)
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