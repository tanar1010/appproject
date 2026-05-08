package com.example.myapplication

class Common {
    object TransLocal {
        // 위경도를 기상청 격자 좌표로 변환하는 공식
        fun convertGrid(lat: Double, lon: Double): Pair<Int, Int> {
            val RE = 6371.00877 // 지구 반지름(km)
            val GRID = 5.0      // 격자 간격(km)
            val SLAT1 = 30.0    // 표준 위도 1(degree)
            val SLAT2 = 60.0    // 표준 위도 2(degree)
            val OLON = 126.0    // 기준점 경도(degree)
            val OLAT = 38.0     // 기준점 위도(degree)
            val XO = 43         // 기준점 X좌표(GRID)
            val YO = 136        // 기준점 Y좌표(GRID)

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
}