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
import com.google.android.gms.tasks.Task
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

/**
 * CoLocation wraps [FusedLocationProviderClient] and [SettingsClient] in Kotlin coroutines and [Flow].
 */
interface CoLocation {

    companion object {
        fun from(context: Context): CoLocation = CoLocationImpl(context.applicationContext)
    }

    /**
     * Flushes any locations currently being batched and sends them to all registered listeners. This call is only
     * useful when batching is specified using setMaxWaitTime(long), otherwise locations are already delivered
     * immediately when available.
     *
     * When this returns, then you can assume that any pending batched locations have already been delivered.
     */
    suspend fun flushLocations()

    /**
     * Returns the availability of location data. When isLocationAvailable() returns true, then the location returned
     * by getLastLocation() will be reasonably up to date within the hints specified by the active LocationRequests.
     *
     * If the client isn't connected to Google Play services and the request times out, null is returned.
     *
     * Note it's always possible for getLastLocation() to return null even when this method returns true
     * (e.g. location settings were disabled between calls).
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    suspend fun isLocationAvailable(): Boolean

    /**
     * Returns the best most recent location currently available.
     *
     * If a location is not available, which should happen very rarely, null will be returned. The best accuracy
     * available while respecting the location permissions will be returned.
     *
     * This method provides a simplified way to get location. It is particularly well suited for applications that do
     * not require an accurate location and that do not want to maintain extra logic for location updates.
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    suspend fun getLastLocation(): Location?

    /**
     * Requests a single location update. This is a convenience fun which can be used instead of [getLocationUpdates]
     * if only one update is needed and [getLastLocation] is not sufficient.
     *
     * This call will keep the Google Play services connection active, until the coroutine is cancelled or the location
     * update was returned.
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    suspend fun getLocationUpdate(locationRequest: LocationRequest): Location?

    /**
     * Requests location updates. This method is suited for the foreground use cases. For background use cases, the
     * PendingIntent version of the method is recommended, see requestLocationUpdates(LocationRequest, PendingIntent).
     *
     * This call will keep the Google Play services connection active, until the coroutine is cancelled or the maximum
     * number of updates that are requested via [LocationRequest.getNumUpdates] are delivered.
     *
     * @param capacity type/capacity of the buffer between coroutines. Allowed values are the same as in `Channel(...)`
     * factory function: [BUFFERED][Channel.BUFFERED], [CONFLATED][Channel.CONFLATED] (by default),
     * [RENDEZVOUS][Channel.RENDEZVOUS], [UNLIMITED][Channel.UNLIMITED] or a non-negative value indicating an
     * explicitly requested size.
     */
    @ExperimentalCoroutinesApi
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    fun getLocationUpdates(locationRequest: LocationRequest, capacity: Int = Channel.CONFLATED): Flow<Location>

    /**
     * Sets the mock location to be used for the location provider. This location will be used in place of any actual
     * locations from the underlying providers (network or gps).
     *
     * setMockMode(boolean) must be called and set to true prior to calling this method.
     *
     * Care should be taken in specifying the timestamps as many applications require them to be monotonically
     * increasing.
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    suspend fun setMockLocation(location: Location)

    /**
     * Sets whether or not the location provider is in mock mode.
     *
     * The underlying providers (network and gps) will be stopped (except by direct LocationManager access), and only
     * locations specified in setMockLocation(Location) will be reported. This will effect all location clients connected using the FusedLocationProviderApi, including geofencer clients (i.e. geofences can be triggered based on mock locations).
     *
     * The client must remain connected in order for mock mode to remain active. If the client dies the system will return to its normal state.
     *
     * Calls are not nested, and mock mode will be set directly regardless of previous calls.
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    suspend fun setMockMode(isMockMode: Boolean)

    /** Checks if the relevant system settings are enabled on the device to carry out the desired location requests. */
    suspend fun checkLocationSettings(locationSettingsRequest: LocationSettingsRequest): SettingsResult

    /** Checks if the relevant system settings are enabled on the device to carry out the desired location request. */
    suspend fun checkLocationSettings(locationRequest: LocationRequest): SettingsResult

    /** Result from a call to [CoLocation.checkLocationSettings]. */
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

    private val cancelledMessage = "Task was cancelled"

    override suspend fun flushLocations() = locationProvider.flushLocations().asCoroutine()

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun isLocationAvailable(): Boolean =
            locationProvider.locationAvailability.asCoroutine { it.isLocationAvailable }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun getLastLocation(): Location? =
            suspendCancellableCoroutine { cont ->
                locationProvider.lastLocation.apply {
                    addOnSuccessListener { cont.resume(it) }
                    addOnCanceledListener { cont.resume(null) }
                    addOnFailureListener { cont.resumeWithException(it) }
                }
            }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun getLocationUpdate(locationRequest: LocationRequest): Location? =
            suspendCancellableCoroutine { cont ->
                lateinit var callback: ClearableLocationCallback
                callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        cont.resume(result.lastLocation)
                        locationProvider.removeLocationUpdates(this)
                        callback.clear()
                    }
                }.let(::ClearableLocationCallback) // Needed since we would have memory leaks otherwise

                locationProvider.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper()).apply {
                    addOnCanceledListener {
                        callback.clear()
                        cont.resume(null)
                    }
                    addOnFailureListener {
                        callback.clear()
                        cont.resumeWithException(it)
                    }
                }
            }

    @ExperimentalCoroutinesApi
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override fun getLocationUpdates(locationRequest: LocationRequest, capacity: Int): Flow<Location> =
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

    override suspend fun checkLocationSettings(locationSettingsRequest: LocationSettingsRequest): CoLocation.SettingsResult =
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

    override suspend fun checkLocationSettings(locationRequest: LocationRequest): CoLocation.SettingsResult =
            checkLocationSettings(LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build())

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun setMockLocation(location: Location) = locationProvider.setMockLocation(location).asCoroutine()

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    override suspend fun setMockMode(isMockMode: Boolean) = locationProvider.setMockMode(isMockMode).asCoroutine()

    private suspend inline fun <T, R> Task<T>.asCoroutine(crossinline transformResult: (T) -> R): R =
            suspendCancellableCoroutine { cont ->
                addOnSuccessListener { cont.resume(transformResult(it)) }
                addOnCanceledListener { cont.resumeWithException(CancellationException(cancelledMessage)) }
                addOnFailureListener { cont.resumeWithException(it) }
            }

    private suspend inline fun Task<Void>.asCoroutine() = asCoroutine { Unit }

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