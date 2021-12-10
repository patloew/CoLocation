package com.patloew.colocation

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.Locale
import java.util.concurrent.TimeUnit

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
@Timeout(5, unit = TimeUnit.SECONDS)
class CoGeocoderTest {

    private val context: Context = mockk()

    private val testCoroutineDispatcher = TestCoroutineDispatcher()
    private val testCoroutineScope = TestCoroutineScope()

    private val coLocation = CoGeocoderImpl(context, Locale.getDefault(), testCoroutineDispatcher)

    @BeforeEach
    fun before() {
        Dispatchers.setMain(testCoroutineDispatcher)
        mockkConstructor(Geocoder::class)
    }

    @AfterEach
    fun after() {
        testCoroutineDispatcher.cleanupTestCoroutines()
        testCoroutineScope.cleanupTestCoroutines()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `getAddressFromLocation with result`() {
        val latitude = 1.0
        val longitude = 2.0
        val location: Location = mockk()
        val address: Address = mockk()
        every { location.latitude } returns latitude
        every { location.longitude } returns longitude
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 1) } returns listOf(address)

        val result = runBlocking { coLocation.getAddressFromLocation(location) }

        assertEquals(address, result)
    }

    @Test
    fun `getAddressFromLocation without result`() {
        val latitude = 1.0
        val longitude = 2.0
        val location: Location = mockk()
        every { location.latitude } returns latitude
        every { location.longitude } returns longitude
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 1) } returns emptyList()

        val result = runBlocking { coLocation.getAddressFromLocation(location) }

        assertNull(result)
    }

    @Test
    fun `getAddressFromLocation lat lng with result`() {
        val latitude = 1.0
        val longitude = 2.0
        val address: Address = mockk()
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 1) } returns listOf(address)

        val result = runBlocking { coLocation.getAddressFromLocation(latitude, longitude) }

        assertEquals(address, result)
    }

    @Test
    fun `getAddressFromLocation lat lng without result`() {
        val latitude = 1.0
        val longitude = 2.0
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 1) } returns emptyList()

        val result = runBlocking { coLocation.getAddressFromLocation(latitude, longitude) }

        assertNull(result)
    }

    @Test
    fun `getAddressFromLocationName with result`() {
        val locationName = "SFO"
        val address: Address = mockk()
        every { anyConstructed<Geocoder>().getFromLocationName(locationName, 1) } returns listOf(address)

        val result = runBlocking { coLocation.getAddressFromLocationName(locationName) }

        assertEquals(address, result)
    }

    @Test
    fun `getAddressFromLocationName without result`() {
        val locationName = "SFO"
        every { anyConstructed<Geocoder>().getFromLocationName(locationName, 1) } returns emptyList()

        val result = runBlocking { coLocation.getAddressFromLocationName(locationName) }

        assertNull(result)
    }

    @Test
    fun `getAddressFromLocationName bounds with result`() {
        val locationName = "SFO"
        val lowerLeftLatitude = 1.0
        val lowerLeftLongitude = 2.0
        val upperRightLatitude = 3.0
        val upperRightLongitude = 4.0
        val address: Address = mockk()
        every {
            anyConstructed<Geocoder>().getFromLocationName(
                locationName,
                1,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude
            )
        } returns listOf(address)

        val result = runBlocking {
            coLocation.getAddressFromLocationName(
                locationName,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude
            )
        }

        assertEquals(address, result)
    }

    @Test
    fun `getAddressFromLocationName bounds without result`() {
        val locationName = "SFO"
        val lowerLeftLatitude = 1.0
        val lowerLeftLongitude = 2.0
        val upperRightLatitude = 3.0
        val upperRightLongitude = 4.0
        every {
            anyConstructed<Geocoder>().getFromLocationName(
                locationName,
                1,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude
            )
        } returns emptyList()

        val result = runBlocking {
            coLocation.getAddressFromLocationName(
                locationName,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude
            )
        }

        assertNull(result)
    }

    @Test
    fun `getAddressListFromLocation with result`() {
        val latitude = 1.0
        val longitude = 2.0
        val location: Location = mockk()
        val address: Address = mockk()
        every { location.latitude } returns latitude
        every { location.longitude } returns longitude
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 5) } returns listOf(address)

        val result = runBlocking { coLocation.getAddressListFromLocation(location, maxResults = 5) }

        assertEquals(listOf(address), result)
    }

    @Test
    fun `getAddressListFromLocation without result`() {
        val latitude = 1.0
        val longitude = 2.0
        val location: Location = mockk()
        every { location.latitude } returns latitude
        every { location.longitude } returns longitude
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 5) } returns emptyList()

        val result = runBlocking { coLocation.getAddressListFromLocation(location, maxResults = 5) }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAddressListFromLocation lat lng with result`() {
        val latitude = 1.0
        val longitude = 2.0
        val address: Address = mockk()
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 5) } returns listOf(address)

        val result =
            runBlocking { coLocation.getAddressListFromLocation(latitude, longitude, maxResults = 5) }

        assertEquals(listOf(address), result)
    }

    @Test
    fun `getAddressListFromLocation lat lng without result`() {
        val latitude = 1.0
        val longitude = 2.0
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 5) } returns emptyList()

        val result =
            runBlocking { coLocation.getAddressListFromLocation(latitude, longitude, maxResults = 5) }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAddressListFromLocationName with result`() {
        val locationName = "SFO"
        val address: Address = mockk()
        every { anyConstructed<Geocoder>().getFromLocationName(locationName, 5) } returns listOf(address)

        val result = runBlocking { coLocation.getAddressListFromLocationName(locationName, maxResults = 5) }

        assertEquals(listOf(address), result)
    }

    @Test
    fun `getAddressListFromLocationName without result`() {
        val locationName = "SFO"
        every { anyConstructed<Geocoder>().getFromLocationName(locationName, 5) } returns emptyList()

        val result = runBlocking { coLocation.getAddressListFromLocationName(locationName, maxResults = 5) }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAddressListFromLocationName bounds with result`() {
        val locationName = "SFO"
        val lowerLeftLatitude = 1.0
        val lowerLeftLongitude = 2.0
        val upperRightLatitude = 3.0
        val upperRightLongitude = 4.0
        val address: Address = mockk()
        every {
            anyConstructed<Geocoder>().getFromLocationName(
                locationName,
                5,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude
            )
        } returns listOf(address)

        val result = runBlocking {
            coLocation.getAddressListFromLocationName(
                locationName,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude,
                maxResults = 5
            )
        }

        assertEquals(listOf(address), result)
    }

    @Test
    fun `getAddressListFromLocationName bounds without result`() {
        val locationName = "SFO"
        val lowerLeftLatitude = 1.0
        val lowerLeftLongitude = 2.0
        val upperRightLatitude = 3.0
        val upperRightLongitude = 4.0
        every {
            anyConstructed<Geocoder>().getFromLocationName(
                locationName,
                5,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude
            )
        } returns emptyList()

        val result = runBlocking {
            coLocation.getAddressListFromLocationName(
                locationName,
                lowerLeftLatitude,
                lowerLeftLongitude,
                upperRightLatitude,
                upperRightLongitude,
                maxResults = 5
            )
        }

        assertTrue(result.isEmpty())
    }
}