package com.example.myapplication

data class MealResponse(
    val mealServiceDietInfo: List<MealServiceDietInfo>?
)

data class MealServiceDietInfo(
    val row: List<MealRow>?
)

data class MealRow(
    val DDISH_NM: String?, // 메뉴 이름
    val CAL_INFO: String?  // 칼로리 (필요하면 사용)
)