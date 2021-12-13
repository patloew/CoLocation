package com.patloew.colocation

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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

/**
 * CoGeocoder wraps [Geocoder] in Kotlin coroutines.
 */
interface CoGeocoder {

    companion object {
        fun from(
            context: Context,
            locale: Locale = Locale.getDefault(),
            dispatcher: CoroutineDispatcher = Dispatchers.IO
        ): CoGeocoder = CoGeocoderImpl(context.applicationContext, locale, dispatcher)
    }

    /**
     * Returns an Address that is known to describe the area immediately surrounding the given [location], if available.
     * The returned address will be localized for the [locale] and defaults to [Locale.getDefault].
     *
     * The returned value may be obtained by means of a network lookup. The result is a best guess and is not
     * guaranteed to be meaningful or correct.
     */
    suspend fun getAddressFromLocation(location: Location, locale: Locale = Locale.getDefault()): Address?

    /**
     * Returns an Address that is known to describe the area immediately surrounding the given [latitude] and
     * [longitude], if available. The returned address will be localized for the [locale] and defaults to
     * [Locale.getDefault].
     *
     * The returned value may be obtained by means of a network lookup. The result is a best guess and is not
     * guaranteed to be meaningful or correct.
     */
    suspend fun getAddressFromLocation(
        latitude: Double,
        longitude: Double,
        locale: Locale = Locale.getDefault()
    ): Address?

    /**
     * Returns an Address that is known to describe the named location, if available. The named location may be a place
     * name such as "Dalvik, Iceland", an address such as "1600 Amphitheatre Parkway, Mountain View, CA", an airport
     * code such as "SFO", etc..  The returned address will be localized for the [locale] and defaults to
     * [Locale.getDefault].
     *
     * The returned value will be obtained by means of a network lookup. The result is a best guess and is not
     * guaranteed to be meaningful or correct.
     */
    suspend fun getAddressFromLocationName(locationName: String, locale: Locale = Locale.getDefault()): Address?

    /**
     * Returns an Address that is known to describe the named location, if available. The named location may be a place
     * name such as "Dalvik, Iceland", an address such as "1600 Amphitheatre Parkway, Mountain View, CA", an airport
     * code such as "SFO", etc.. The returned address will be localized for the [locale] and defaults to
     * [Locale.getDefault].
     *
     * A bounding box for the search results is specified by the Latitude and Longitude of the Lower Left point and
     * Upper Right point of the box.
     *
     * The returned value will be obtained by means of a network lookup. The result is a best guess and is not
     * guaranteed to be meaningful or correct.
     */
    suspend fun getAddressFromLocationName(
        locationName: String,
        lowerLeftLatitude: Double,
        lowerLeftLongitude: Double,
        upperRightLatitude: Double,
        upperRightLongitude: Double,
        locale: Locale = Locale.getDefault()
    ): Address?

    /**
     * Returns a list of Addresses that are known to describe the area immediately surrounding the given [location].
     * The returned addresses will be localized for the [locale] and defaults to [Locale.getDefault].
     *
     * The returned values may be obtained by means of a network lookup. The results are a best guess and are not
     * guaranteed to be meaningful or correct.
     */
    suspend fun getAddressListFromLocation(
        location: Location,
        locale: Locale = Locale.getDefault(),
        maxResults: Int = 5
    ): List<Address>


    /**
     * Returns a list of Addresses that are known to describe the area immediately surrounding the given [latitude]
     * and [longitude]. The returned addresses will be localized for the [locale] and defaults to [Locale.getDefault].
     *
     * The returned values may be obtained by means of a network lookup. The results are a best guess and are not
     * guaranteed to be meaningful or correct.
     */
    suspend fun getAddressListFromLocation(
        latitude: Double,
        longitude: Double,
        locale: Locale = Locale.getDefault(),
        maxResults: Int = 5
    ): List<Address>

    /**
     * Returns an array of Addresses that are known to describe the named location, which may be a place name such
     * as "Dalvik, Iceland", an address such as "1600 Amphitheatre Parkway, Mountain View, CA", an airport code such
     * as "SFO", etc..  The returned addresses will be localized for the [locale] and defaults to [Locale.getDefault].
     *
     * The returned values will be obtained by means of a network lookup. The results are a best guess and are not
     * guaranteed to be meaningful or correct.
     */
    suspend fun getAddressListFromLocationName(
        locationName: String,
        locale: Locale = Locale.getDefault(),
        maxResults: Int = 5
    ): List<Address>

    /**
     * Returns an array of Addresses that are known to describe the named location, which may be a place name such
     * as "Dalvik, Iceland", an address such as "1600 Amphitheatre Parkway, Mountain View, CA", an airport code such
     * as "SFO", etc.. The returned addresses will be localized for the [locale] and defaults to [Locale.getDefault].
     *
     * A bounding box for the search results is specified by the Latitude and Longitude of the Lower Left point and
     * Upper Right point of the box.
     *
     * The returned values will be obtained by means of a network lookup. The results are a best guess and are not
     * guaranteed to be meaningful or correct.
     */
    suspend fun getAddressListFromLocationName(
        locationName: String,
        lowerLeftLatitude: Double,
        lowerLeftLongitude: Double,
        upperRightLatitude: Double,
        upperRightLongitude: Double,
        locale: Locale = Locale.getDefault(),
        maxResults: Int = 5
    ): List<Address>

}