package com.patloew.colocation

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.location.Location
import androidx.annotation.RequiresPermission
import com.patloew.colocation.request.LocationRequest
import com.patloew.colocation.request.LocationSettingsRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.full.primaryConstructor

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
        fun from(context: Context): CoLocation {

            val coLocationGms = getGmsImplementation(context)
            if (coLocationGms != null) {
                return coLocationGms
            }

            val coLocationHms = getHmsImplementation(context)
            if (coLocationHms != null) {
                return coLocationHms
            }

            throw IllegalStateException( "Can't find supported platform, make sure to include one of the next artifacts:"
                    + " ':google', or ':huawei'")
        }

        private fun getGmsImplementation (context: Context): CoLocation? {
            val gmsClass = try {
                Class.forName("com.patloew.colocation.google.CoLocationGms").kotlin
            } catch (e: Exception) {
                return null
            }
            return gmsClass.primaryConstructor?.call(context) as? CoLocation
        }

        private fun getHmsImplementation (context: Context): CoLocation? {
            val hmsClass = try {
                Class.forName("com.patloew.colocation.huawei.CoLocationHms").kotlin
            } catch (e: Exception) {
                return null
            }
            return hmsClass.primaryConstructor!!.call(context) as CoLocation
        }
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
     * If the client isn't connected to Google/Huawei mobile services and the request times out, null is returned.
     *
     * Note it's always possible for getLastLocation() to return null even when this method returns true
     * (e.g. location settings were disabled between calls).
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    suspend fun isLocationAvailable(): Boolean

    /**
     * Returns a single current location fix on the device. Unlike [getLastLocation()] that returns a cached location,
     * this method could cause active location computation on the device. A single fresh location will be returned if
     * the device location can be determined within reasonable time (tens of seconds), otherwise null will be returned.
     *
     * This method may return locations that are a few seconds old, but never returns much older locations. This is
     * suitable for foreground applications that need a single fresh current location.
     *
     * Background apps calling this method will be throttled under background location limits, so background apps may
     * find the method returns null locations more often.
     *
     * Always returns null when HMS provider is used (no such method in HMS location provider)
     *
     * @param priority One the PRIORITY_* in [LocationRequest].
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    suspend fun getCurrentLocation(priority: Int): Location?

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
     * This call will keep the Google/Huawei mobile services connection active, until the coroutine is cancelled or the location
     * update was returned.
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    suspend fun getLocationUpdate(locationRequest: LocationRequest): Location

    /**
     * Requests location updates. This method is suited for the foreground use cases. For background use cases, the
     * PendingIntent version of the method is recommended, see requestLocationUpdates(LocationRequest, PendingIntent).
     *
     * This call will keep the Google/Huawei mobile services connection active, until the coroutine is cancelled or the maximum
     * number of updates that are requested via [LocationRequest.numUpdates] are delivered.
     *
     * @param capacity type/capacity of the buffer between coroutines. Allowed values are the same as in `Channel(...)`
     * factory function: [BUFFERED][Channel.BUFFERED], [CONFLATED][Channel.CONFLATED] (by default),
     * [RENDEZVOUS][Channel.RENDEZVOUS], [UNLIMITED][Channel.UNLIMITED] or a non-negative value indicating an
     * explicitly requested size.
     */
    @ExperimentalCoroutinesApi
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    fun getLocationUpdates(
        locationRequest: LocationRequest,
        capacity: Int = Channel.CONFLATED
    ): Flow<Location>

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
     * locations specified in setMockLocation(Location) will be reported. This will effect all location clients
     * connected using the FusedLocationProviderApi, including geofencer clients (i.e. geofences can be triggered based
     * on mock locations).
     *
     * The client must remain connected in order for mock mode to remain active. If the client dies the system will
     * return to its normal state.
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
        abstract class Resolvable(open val exception: Exception) : SettingsResult() {
            /**
             * Show the dialog to the user. The Activity's onActivityResult method will be invoked after the user is
             * done. If the resultCode is RESULT_OK, the location settings are now satisfied.
             */
            fun resolve(activity: Activity, requestCode: Int) {
                try {
                    startResolutionForResult(activity, requestCode)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }

            abstract fun startResolutionForResult(activity: Activity, requestCode: Int)

            abstract fun getResolution(): PendingIntent

            /**
             * Specific for HMS
             */
            abstract fun getResolutionIntent(): Intent?
        }

        /** Location settings are not satisfied and this can't be fixed by showing the user a dialog */
        class NotResolvable(val exception: Exception) : SettingsResult()
    }

}