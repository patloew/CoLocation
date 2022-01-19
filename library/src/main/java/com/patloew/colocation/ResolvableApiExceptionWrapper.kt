package com.patloew.colocation

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import com.google.android.gms.common.api.ResolvableApiException as GmsResolvableApiException
import com.huawei.hms.common.ResolvableApiException as HmsResolvableApiException

data class ResolvableApiExceptionWrapper(
    private val exception: Exception
) {

    init {
        require(exception is GmsResolvableApiException || exception is HmsResolvableApiException)
    }

    fun startResolutionForResult(activity: Activity, requestCode: Int) {
        when (exception) {
            is GmsResolvableApiException -> {
                exception.startResolutionForResult(activity, requestCode)
            }
            is HmsResolvableApiException -> {
                exception.startResolutionForResult(activity, requestCode)
            }
        }
    }

    fun getResolution(): PendingIntent {
        return when (exception) {
            is GmsResolvableApiException -> {
                exception.resolution
            }
            is HmsResolvableApiException -> {
                exception.resolution
            }
            else -> throw IllegalStateException()
        }
    }

    /**
     * Specific for HMS
     */
    fun getResolutionIntent(): Intent? {
        return when (exception) {
            is GmsResolvableApiException -> {
                null
            }
            is HmsResolvableApiException -> {
                exception.resolutionIntent
            }
            else -> throw IllegalStateException()
        }
    }
}
