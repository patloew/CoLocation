package com.patloew.colocation.request


class LocationRequest {

    companion object {
        const val PRIORITY_HIGH_ACCURACY = 100
        const val PRIORITY_BALANCED_POWER_ACCURACY = 102
        const val PRIORITY_LOW_POWER = 104
        const val PRIORITY_NO_POWER = 105

        fun create() = LocationRequest()
    }

    var priority: Int? = null
        private set
    var interval: Long? = null
        private set
    var fastestInterval: Long? = null
        private set
    var waitForAccurateLocation: Boolean? = null
        private set
    var expirationTime: Long? = null
        private set
    var numUpdates: Int? = null
        private set
    var smallestDisplacement: Float? = null
        private set
    var maxWaitTime: Long? = null
        private set

    // GMS specific
    var expirationDuration: Long? = null
        private set

    // HMS specific
    var needAddress: Boolean? = null
        private set
    var language: String? = null
        private set
    var countryCode: String? = null
        private set
    var extras: MutableMap<String, String>? = null
        private set

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

}
