package com.patloew.colocation.request

data class LocationSettingsRequest(
    val requests: List<LocationRequest>,
    val alwaysShow: Boolean,
    val needBle: Boolean
) {

    fun toGms(): com.google.android.gms.location.LocationSettingsRequest {
        return com.google.android.gms.location.LocationSettingsRequest.Builder()
            .addAllLocationRequests(requests.map { it.toGms() })
            .setAlwaysShow(alwaysShow)
            .setNeedBle(needBle)
            .build()
    }

    fun toHms(): com.huawei.hms.location.LocationSettingsRequest {
        return com.huawei.hms.location.LocationSettingsRequest.Builder()
            .addAllLocationRequests(requests.map { it.toHms() })
            .setAlwaysShow(alwaysShow)
            .setNeedBle(needBle)
            .build()
    }

    class Builder {
        private val requests: MutableList<LocationRequest> = mutableListOf()
        private var alwaysShow: Boolean = false
        private var needBle: Boolean = false

        fun addAllLocationRequests(requests: Collection<LocationRequest>): Builder {
            this.requests.addAll(requests)
            return this
        }

        fun addLocationRequest(request: LocationRequest): Builder {
            this.requests.add(request)
            return this
        }

        fun setAlwaysShow(alwaysShow: Boolean): Builder {
            this.alwaysShow = alwaysShow
            return this
        }

        fun setNeedBle(needBle: Boolean): Builder {
            this.needBle = needBle
            return this
        }

        fun build(): LocationSettingsRequest =
            LocationSettingsRequest(requests, alwaysShow, needBle)
    }
}