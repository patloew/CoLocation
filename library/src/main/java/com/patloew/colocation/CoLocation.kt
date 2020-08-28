package com.patloew.colocation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.location.Location
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
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
interface CoLocation {

    companion object {
        fun from(context: Context): CoLocation = CoLocationImpl(context.applicationContext)
    }

    suspend fun flushLocations()

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    suspend fun isLocationAvailable(): Boolean

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    suspend fun lastKnownLocation(): Location?

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    suspend fun locationUpdate(locationRequest: LocationRequest): Location?

    /**
     * Gets location updates.
     *
     * @param capacity type/capacity of the buffer between coroutines. Allowed values are the same as in `Channel(...)`
     * factory function: [BUFFERED][Channel.BUFFERED], [CONFLATED][Channel.CONFLATED] (by default),
     * [RENDEZVOUS][Channel.RENDEZVOUS], [UNLIMITED][Channel.UNLIMITED] or a non-negative value indicating an
     * explicitly requested size.
     */
    @ExperimentalCoroutinesApi
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    fun locationUpdates(locationRequest: LocationRequest, capacity: Int = Channel.CONFLATED): Flow<Location>

    suspend fun areSettingsSatisfied(locationSettingsRequest: LocationSettingsRequest): SettingsResult

    sealed class SettingsResult {
        /** Location settings are satisfied. */
        object Satisfied : SettingsResult()

        /**
         * Location settings are not satisfied, but this can be fixed by showing the user a dialog.
         *
         *     resolvable.resolve(activity, REQUEST_CODE_SETTINGS)
         */
        class Resolvable(val exception: ResolvableApiException) : SettingsResult() {
            /**
             * Show the dialog to the user. The Activity's onActivityResult method will be invoked after the user is
             * done. If the resultCode is RESULT_OK, the location settings are now satisfied.
             */
            fun resolve(activity: Activity, requestCode: Int) {
                try {
                    exception.startResolutionForResult(activity, requestCode)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }

        /** Location settings are not satisfied and this can't be fixed by showing the user a dialog */
        class NotResolvable(val exception: Exception) : SettingsResult()

        /** The task was canceled while checking the settings */
        object Canceled : SettingsResult()
    }

}

internal class CoLocationImpl(context: Context) : CoLocation {

    private val locationProvider: FusedLocationProviderClient by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val settings: SettingsClient by lazy { LocationServices.getSettingsClient(context) }

    override suspend fun flushLocations() =
            suspendCancellableCoroutine<Unit> { cont ->
                locationProvider.flushLocations().apply {
                    addOnSuccessListener { cont.resume(Unit) }
                    addOnCanceledListener { cont.resumeWithException(CancellationException("Task for flushing locations was canceled")) }
                    addOnFailureListener { cont.resumeWithException(it) }
                }
            }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun isLocationAvailable(): Boolean =
            suspendCancellableCoroutine { cont ->
                locationProvider.locationAvailability.apply {
                    addOnSuccessListener { cont.resume(it.isLocationAvailable) }
                    addOnCanceledListener { cont.resumeWithException(CancellationException("Task for getting the location availability was canceled")) }
                    addOnFailureListener { cont.resumeWithException(it) }
                }
            }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun lastKnownLocation(): Location? =
            suspendCancellableCoroutine { cont ->
                locationProvider.lastLocation.apply {
                    addOnSuccessListener { cont.resume(it) }
                    addOnCanceledListener { cont.resume(null) }
                    addOnFailureListener { cont.resumeWithException(it) }
                }
            }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun locationUpdate(locationRequest: LocationRequest): Location? =
            suspendCancellableCoroutine { cont ->
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        cont.resume(result.lastLocation)
                        locationProvider.removeLocationUpdates(this)
                    }
                }

                locationProvider.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper()).apply {
                    addOnCanceledListener { cont.resume(null) }
                    addOnFailureListener { cont.resumeWithException(it) }
                }
            }

    @ExperimentalCoroutinesApi
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override fun locationUpdates(locationRequest: LocationRequest, capacity: Int): Flow<Location> =
            callbackFlow<Location> {
                val callback = object : LocationCallback() {
                    private var counter: Int = 0
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.also { location -> sendBlocking(location) }
                        if (locationRequest.numUpdates == ++counter) close()
                    }
                }.let(::ClearableLocationCallback) // Needed since we would have memory leaks otherwise

                locationProvider.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper()).apply {
                    addOnCanceledListener { cancel(CancellationException("Task for requesting location updates was cancelled")) }
                    addOnFailureListener { cancel(CancellationException("Error requesting location updates", it)) }
                }

                awaitClose {
                    locationProvider.removeLocationUpdates(callback)
                    callback.clear()
                }
            }.buffer(capacity)

    override suspend fun areSettingsSatisfied(locationSettingsRequest: LocationSettingsRequest): CoLocation.SettingsResult =
            suspendCancellableCoroutine { cont ->
                settings.checkLocationSettings(locationSettingsRequest)
                        .addOnSuccessListener { cont.resume(CoLocation.SettingsResult.Satisfied) }
                        .addOnCanceledListener { cont.resume(CoLocation.SettingsResult.Canceled) }
                        .addOnFailureListener { exception ->
                            if (exception is ResolvableApiException) {
                                CoLocation.SettingsResult.Resolvable(exception)
                            } else {
                                CoLocation.SettingsResult.NotResolvable(exception)
                            }.run(cont::resume)
                        }
            }
}

/** Wraps [callback] so that the reference can be cleared */
private class ClearableLocationCallback(callback: LocationCallback) : LocationCallback() {

    private var callback: LocationCallback? = callback

    override fun onLocationAvailability(locationAvailability: LocationAvailability?) {
        callback?.onLocationAvailability(locationAvailability)
    }

    override fun onLocationResult(locationResult: LocationResult?) {
        callback?.onLocationResult(locationResult)
    }

    fun clear() {
        callback = null
    }

}