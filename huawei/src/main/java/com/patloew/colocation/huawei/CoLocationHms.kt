package com.patloew.colocation.huawei

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.huawei.hms.common.ResolvableApiException
import com.huawei.hms.location.*
import com.patloew.colocation.CoLocation
import com.patloew.colocation.TaskCancelledException
import com.patloew.colocation.huawei.util.await
import com.patloew.colocation.request.LocationRequest
import com.patloew.colocation.request.LocationSettingsRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/* Copyright 2020 Patrick Löwenstein
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
class CoLocationHms(private val context: Context) : CoLocation {

    private val locationProvider: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    private val settings: SettingsClient by lazy { LocationServices.getSettingsClient(context) }

    private val cancelledMessage = "Task was cancelled"

    override suspend fun flushLocations() {
        locationProvider.flushLocations().await()
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun isLocationAvailable(): Boolean =
        locationProvider.locationAvailability.await().isLocationAvailable

    /**
     * getCurrentLocation() is not available in HMS implementation
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun getCurrentLocation(priority: Int): Location? = null

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun getLastLocation(): Location? = locationProvider.lastLocation.await()

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun getLocationUpdate(locationRequest: LocationRequest): Location =
        suspendCancellableCoroutine { cont ->
            lateinit var callback: HmsClearableLocationCallback
            callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    cont.resume(result.lastLocation)
                    locationProvider.removeLocationUpdates(callback)
                    callback.clear()
                }
            }.let(::HmsClearableLocationCallback) // Needed since we would have memory leaks otherwise

            locationProvider.requestLocationUpdates(
                locationRequest.toHms(),
                callback,
                Looper.getMainLooper()
            ).apply {
                addOnCanceledListener {
                    callback.clear()
                    cont.resumeWithException(TaskCancelledException(cancelledMessage))
                }
                addOnFailureListener {
                    callback.clear()
                    cont.resumeWithException(it)
                }
            }
        }

    @ExperimentalCoroutinesApi
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override fun getLocationUpdates(
        locationRequest: LocationRequest,
        capacity: Int
    ): Flow<Location> =
        callbackFlow<Location> {
            val hmsRequest = locationRequest.toHms()

            val callback = object : LocationCallback() {
                private var counter: Int = 0
                override fun onLocationResult(result: LocationResult) {
                    trySendBlocking(result.lastLocation)
                    if (hmsRequest.numUpdates == ++counter) close()
                }
            }.let(::HmsClearableLocationCallback) // Needed since we would have memory leaks otherwise

            locationProvider.requestLocationUpdates(
                hmsRequest,
                callback,
                Looper.getMainLooper()
            ).apply {
                addOnCanceledListener {
                    cancel(
                        cancelledMessage,
                        TaskCancelledException(cancelledMessage)
                    )
                }
                addOnFailureListener { cancel("Error requesting location updates", it) }
            }

            awaitClose {
                locationProvider.removeLocationUpdates(callback)
                callback.clear()
            }
        }.buffer(capacity)

    override suspend fun checkLocationSettings(locationSettingsRequest: LocationSettingsRequest): CoLocation.SettingsResult =
        suspendCancellableCoroutine { cont ->
            settings.checkLocationSettings(locationSettingsRequest.toHms())
                .addOnSuccessListener { cont.resume(CoLocation.SettingsResult.Satisfied) }
                .addOnCanceledListener {
                    cont.resumeWithException(
                        TaskCancelledException(
                            cancelledMessage
                        )
                    )
                }
                .addOnFailureListener { exception ->
                    if (exception is ResolvableApiException) {
                        HsmResolvableApiExceptionResult(exception)
                    } else {
                        CoLocation.SettingsResult.NotResolvable(exception)
                    }.run(cont::resume)
                }
        }

    override suspend fun checkLocationSettings(locationRequest: LocationRequest): CoLocation.SettingsResult =
        checkLocationSettings(
            LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build()
        )

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun setMockLocation(location: Location) {
        locationProvider.setMockLocation(location).await()
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun setMockMode(isMockMode: Boolean) {
        locationProvider.setMockMode(isMockMode).await()
    }
}

fun LocationRequest.toHms(): com.huawei.hms.location.LocationRequest {
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

fun LocationSettingsRequest.toHms(): com.huawei.hms.location.LocationSettingsRequest {
    return com.huawei.hms.location.LocationSettingsRequest.Builder()
        .addAllLocationRequests(requests.map { it.toHms() })
        .setAlwaysShow(alwaysShow)
        .setNeedBle(needBle)
        .build()
}

class HsmResolvableApiExceptionResult(
    override val exception: ResolvableApiException
) : CoLocation.SettingsResult.Resolvable(exception) {

    override fun startResolutionForResult(activity: Activity, requestCode: Int) {
        exception.startResolutionForResult(activity, requestCode)
    }

    override fun getResolution(): PendingIntent = exception.resolution

    override fun getResolutionIntent(): Intent? = exception.resolutionIntent

}

/** Wraps [callback] so that the reference can be cleared */
private class HmsClearableLocationCallback(callback: LocationCallback) : LocationCallback() {

    private var callback: LocationCallback? = callback

    override fun onLocationAvailability(locationAvailability: LocationAvailability) {
        callback?.onLocationAvailability(locationAvailability)
    }

    override fun onLocationResult(locationResult: LocationResult) {
        callback?.onLocationResult(locationResult)
    }

    fun clear() {
        callback = null
    }

}