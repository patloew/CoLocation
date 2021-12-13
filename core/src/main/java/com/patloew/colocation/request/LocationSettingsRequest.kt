package com.patloew.colocation.request

data class LocationSettingsRequest(
    val requests: List<LocationRequest>,
    val alwaysShow: Boolean,
    val needBle: Boolean
) {

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