package com.patloew.colocation

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.Locale

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
internal class CoGeocoderImpl(
    context: Context,
    locale: Locale,
    private val dispatcher: CoroutineDispatcher
) : CoGeocoder {

    private val geocoder: Geocoder by lazy { Geocoder(context, locale) }

    override suspend fun getAddressFromLocation(location: Location, locale: Locale): Address? =
        getAddressListFromLocation(location.latitude, location.longitude, locale, 1).firstOrNull()

    override suspend fun getAddressFromLocation(latitude: Double, longitude: Double, locale: Locale): Address? =
        getAddressListFromLocation(latitude, longitude, locale, 1).firstOrNull()

    override suspend fun getAddressFromLocationName(locationName: String, locale: Locale): Address? =
        getAddressListFromLocationName(locationName, locale, 1).firstOrNull()

    override suspend fun getAddressFromLocationName(
        locationName: String,
        lowerLeftLatitude: Double,
        lowerLeftLongitude: Double,
        upperRightLatitude: Double,
        upperRightLongitude: Double,
        locale: Locale
    ): Address? = getAddressListFromLocationName(
        locationName,
        lowerLeftLatitude,
        lowerLeftLongitude,
        upperRightLatitude,
        upperRightLongitude,
        locale,
        1
    ).firstOrNull()

    override suspend fun getAddressListFromLocation(
        location: Location,
        locale: Locale,
        maxResults: Int
    ): List<Address> = getAddressListFromLocation(location.latitude, location.longitude, locale, maxResults)

    override suspend fun getAddressListFromLocation(
        latitude: Double,
        longitude: Double,
        locale: Locale,
        maxResults: Int
    ): List<Address> = withContext(dispatcher) { geocoder.getFromLocation(latitude, longitude, maxResults) }

    override suspend fun getAddressListFromLocationName(
        locationName: String,
        locale: Locale,
        maxResults: Int
    ): List<Address> =
        withContext(dispatcher) { geocoder.getFromLocationName(locationName, maxResults) }

    override suspend fun getAddressListFromLocationName(
        locationName: String,
        lowerLeftLatitude: Double,
        lowerLeftLongitude: Double,
        upperRightLatitude: Double,
        upperRightLongitude: Double,
        locale: Locale,
        maxResults: Int
    ): List<Address> = withContext(dispatcher) {
        geocoder.getFromLocationName(
            locationName,
            maxResults,
            lowerLeftLatitude,
            lowerLeftLongitude,
            upperRightLatitude,
            upperRightLongitude
        )
    }
}