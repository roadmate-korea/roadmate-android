package com.roadmatekorea.android.location

import android.content.Context
import com.roadmatekorea.android.rules.SchoolZoneMap

/**
 * 앱에 넣어 둔 어린이보호구역 시설 좌표(`assets/school_zones.csv`, 공공데이터포털 전국어린이보호구역표준데이터를
 * `tools/school-zones/build.py`로 줄인 것). 네트워크 없이 기기 안에서만 쓴다.
 */
object SchoolZoneAsset {
    const val FILE = "school_zones.csv"

    /** 입출력 스레드에서 부른다. */
    fun load(context: Context): SchoolZoneMap =
        context.assets.open(FILE).bufferedReader().useLines { SchoolZoneMap.parse(it) }
}
