package com.patloew.colocationsample

import android.location.Address
import android.location.Location
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.android.gms.location.LocationRequest
import com.patloew.colocation.CoGeocoder
import com.patloew.colocation.CoLocation
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
class MainViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val coLocation: CoLocation = mockk()
    private val coGeocoder: CoGeocoder = mockk()

    private val viewModel = MainViewModel(coLocation, coGeocoder)

    @Before
    fun before() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun after() = Dispatchers.resetMain()

    @Test
    fun `onResume with location settings satisfied`() {
        val lastKnownLocation: Location = mockk()
        val updatedLocation: Location = mockk()
        val lastKnownAddress: Address = mockk()
        val updatedAddress: Address = mockk()
        coEvery { coLocation.checkLocationSettings(any<LocationRequest>()) } returns CoLocation.SettingsResult.Satisfied
        coEvery { coLocation.getLastLocation() } returns lastKnownLocation
        coEvery { coGeocoder.getAddressFromLocation(lastKnownLocation) } returns lastKnownAddress
        coEvery { coGeocoder.getAddressFromLocation(updatedLocation) } returns updatedAddress
        every { coLocation.getLocationUpdates(any()) } returns flowOf(updatedLocation)
        val locations = mutableListOf<Location>()
        viewModel.locationUpdates.observeForever(locations::add)
        val addresses = mutableListOf<Address?>()
        viewModel.addressUpdates.observeForever(addresses::add)

        viewModel.onResume()

        assertEquals(listOf(lastKnownLocation, updatedLocation), locations)
        assertEquals(listOf(lastKnownAddress, updatedAddress), addresses)
    }
}