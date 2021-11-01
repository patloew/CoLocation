package com.patloew.colocation

import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability
import com.huawei.hms.api.HuaweiApiAvailability

fun Context.getLocationServiceSource(): LocationServicesSource {
    val googleApi = GoogleApiAvailability.getInstance()
    if (googleApi.isGooglePlayServicesAvailable(this) == com.google.android.gms.common.ConnectionResult.SUCCESS) {
        return LocationServicesSource.GMS
    }

    val huaweiApi = HuaweiApiAvailability.getInstance()
    if (huaweiApi.isHuaweiMobileServicesAvailable(this) == com.huawei.hms.api.ConnectionResult.SUCCESS) {
        return LocationServicesSource.HMS
    }

    return LocationServicesSource.NONE
}

enum class LocationServicesSource() {
    GMS,
    HMS,
    NONE
}