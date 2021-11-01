package com.patloew.colocation.request


class LocationRequest {

    companion object {
        const val PRIORITY_HIGH_ACCURACY = 100
        const val PRIORITY_BALANCED_POWER_ACCURACY = 102
        const val PRIORITY_LOW_POWER = 104
        const val PRIORITY_NO_POWER = 105

        fun create() = LocationRequest()
    }

    private var priority: Int? = null
    private var interval: Long? = null
    private var fastestInterval: Long? = null
    private var waitForAccurateLocation: Boolean? = null
    private var expirationTime: Long? = null
    private var numUpdates: Int? = null
    private var smallestDisplacement: Float? = null
    private var maxWaitTime: Long? = null

    // GMS specific
    private var expirationDuration: Long? = null

    // HMS specific
    private var needAddress: Boolean? = null
    private var language: String? = null
    private var countryCode: String? = null
    private var extras: MutableMap<String, String>? = null


    fun setExpirationDuration(millis: Long): LocationRequest {
        this.expirationDuration = millis
        return this
    }

    fun setExpirationTime(millis: Long): LocationRequest {
        this.expirationTime = millis
        return this
    }

    fun setFastestInterval(millis: Long): LocationRequest {
        this.fastestInterval = millis
        return this
    }

    fun setInterval(millis: Long): LocationRequest {
        this.interval = millis
        return this
    }

    fun setMaxWaitTime(millis: Long): LocationRequest {
        this.maxWaitTime = millis
        return this
    }

    fun setNumUpdates(numUpdates: Int): LocationRequest {
        this.numUpdates = numUpdates
        return this
    }

    fun setPriority(priority: Int): LocationRequest {
        this.priority = priority
        return this
    }

    fun setSmallestDisplacement(smallestDisplacementMeters: Float): LocationRequest {
        this.smallestDisplacement = smallestDisplacementMeters
        return this
    }

    /**
     * GMS specific
     */
    fun setWaitForAccurateLocation(waitForAccurateLocation: Boolean): LocationRequest {
        this.waitForAccurateLocation = waitForAccurateLocation
        return this
    }

    /**
     * HMS specific
     */
    fun setCountryCode(countryCode: String): LocationRequest {
        this.countryCode = countryCode
        return this
    }

    /**
     * HMS specific
     */
    fun setNeedAddress(needAddress: Boolean): LocationRequest {
        this.needAddress = needAddress
        return this
    }

    /**
     * HMS specific
     */
    fun setLanguage(language: String): LocationRequest {
        this.language = language
        return this
    }

    /**
     * HMS specific
     */
    fun putExtras(key: String, value: String): LocationRequest {
        if (this.extras == null) {
            this.extras = mutableMapOf()
        }
        extras?.put(key, value)
        return this
    }

    fun toGms(): com.google.android.gms.location.LocationRequest {
        val request = com.google.android.gms.location.LocationRequest.create()
        priority?.let { request.setPriority(it) }
        interval?.let { request.setInterval(it) }
        fastestInterval?.let { request.setFastestInterval(it) }
        expirationTime?.let { request.setExpirationTime(it) }
        numUpdates?.let { request.setNumUpdates(it) }
        smallestDisplacement?.let { request.setSmallestDisplacement(it) }
        maxWaitTime?.let { request.setMaxWaitTime(it) }
        expirationDuration?.let { request.setExpirationDuration(it) }

        // GMS specific functions
        waitForAccurateLocation?.let { request.setWaitForAccurateLocation(it) }
        return request
    }

    fun toHms(): com.huawei.hms.location.LocationRequest {
        val request = com.huawei.hms.location.LocationRequest.create()
        priority?.let { request.setPriority(it) }
        interval?.let { request.setInterval(it) }
        fastestInterval?.let { request.setFastestInterval(it) }
        expirationTime?.let { request.setExpirationTime(it) }
        numUpdates?.let { request.setNumUpdates(it) }
        smallestDisplacement?.let { request.setSmallestDisplacement(it) }
        maxWaitTime?.let { request.setMaxWaitTime(it) }
        expirationDuration?.let { request.setExpirationDuration(it) }

        // HMS specific functions
        needAddress?.let { request.setNeedAddress(it) }
        language?.let { request.setLanguage(it) }
        countryCode?.let { request.setCountryCode(it) }
        extras?.let { it.forEach { (k, v) -> request.putExtras(k, v) } }
        return request
    }
}
