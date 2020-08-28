package com.patloew.colocation

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnCanceledListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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
class CoLocationTest {

    private val locationProvider: FusedLocationProviderClient = mockk()
    private val settings: SettingsClient = mockk()
    private val geocoder: Geocoder = mockk()
    private val context: Context = mockk()
    private val testCoroutineDispatcher = TestCoroutineDispatcher()
    private val testCoroutineScope = TestCoroutineScope()

    private val coLocation = CoLocationImpl(context)

    @BeforeEach
    fun before() {
        Dispatchers.setMain(testCoroutineDispatcher)
        mockkStatic(LocationServices::class)
        every { LocationServices.getFusedLocationProviderClient(context) } returns locationProvider
        every { LocationServices.getSettingsClient(context) } returns settings
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
    fun flushLocations() {
        testTaskWithCancelThrows(
            createTask = { locationProvider.flushLocations() },
            taskResult = null,
            expectedResult = Unit,
            expectedErrorException = TestException(),
            expectedCancelException = TaskCancelledException(""),
            coLocationCall = { coLocation.flushLocations() }
        )
    }

    @Test
    fun `isLocationAvailable true`() {
        val locationAvailability = mockk<LocationAvailability> {
            every { isLocationAvailable } returns true
        }
        testTaskWithCancelThrows(
            createTask = { locationProvider.locationAvailability },
            taskResult = locationAvailability,
            expectedResult = true,
            expectedErrorException = TestException(),
            expectedCancelException = TaskCancelledException(""),
            coLocationCall = { coLocation.isLocationAvailable() }
        )
    }

    @Test
    fun `isLocationAvailable false`() {
        val locationAvailability = mockk<LocationAvailability> {
            every { isLocationAvailable } returns false
        }
        testTaskWithCancelThrows(
            createTask = { locationProvider.locationAvailability },
            taskResult = locationAvailability,
            expectedResult = false,
            expectedErrorException = TestException(),
            expectedCancelException = TaskCancelledException(""),
            coLocationCall = { coLocation.isLocationAvailable() }
        )
    }

    @Test
    fun getLastLocation() {
        val location = mockk<Location>()
        testTaskWithCancelReturns(
            createTask = { locationProvider.lastLocation },
            taskResult = location,
            expectedResult = location,
            expectedErrorException = TestException(),
            cancelResult = null,
            coLocationCall = { coLocation.getLastLocation() }
        )
    }

    @Test
    fun setMockLocation() {
        val location: Location = mockk()
        testTaskWithCancelThrows(
            createTask = { locationProvider.setMockLocation(location) },
            taskResult = null,
            expectedResult = Unit,
            expectedErrorException = TestException(),
            expectedCancelException = TaskCancelledException(""),
            coLocationCall = { coLocation.setMockLocation(location) }
        )
    }

    @Test
    fun setMockMode() {
        testTaskWithCancelThrows(
            createTask = { locationProvider.setMockMode(true) },
            taskResult = null,
            expectedResult = Unit,
            expectedErrorException = TestException(),
            expectedCancelException = TaskCancelledException(""),
            coLocationCall = { coLocation.setMockMode(true) }
        )
    }

    @Test
    fun `getLocationUpdate success`() {
        val requestTask: Task<Void> = mockk(relaxed = true)
        val removeTask: Task<Void> = mockk(relaxed = true)
        val locationRequest: LocationRequest = mockk {
            every { numUpdates } returns Integer.MAX_VALUE
        }
        val locations: List<Location> = MutableList(5) { mockk() }
        val callbackSlot = slot<LocationCallback>()
        every {
            locationProvider.requestLocationUpdates(
                locationRequest,
                capture(callbackSlot),
                any()
            )
        } returns requestTask
        var result: Location? = null

        val job = testCoroutineScope.launch { result = coLocation.getLocationUpdate(locationRequest) }

        runBlocking {
            callbackSlot.waitForCapture()
            every { locationProvider.removeLocationUpdates(callbackSlot.captured) } returns removeTask
            locations.forEach { location ->
                callbackSlot.captured.onLocationResult(LocationResult.create(listOf(location)))
                delay(10)
            }
            job.cancelAndJoin()
        }

        assertEquals(locations[0], result)
        verify { locationProvider.removeLocationUpdates(callbackSlot.captured) }
    }

    @Test
    fun `getLocationUpdate error`() {
        val requestTask: Task<Void> = mockk(relaxed = true)
        val testException = TestException()
        requestTask.mockError(testException)
        val locationRequest: LocationRequest = mockk()
        every { locationProvider.requestLocationUpdates(locationRequest, any(), any()) } returns requestTask
        var result: Location? = null
        var resultException: TestException? = null

        testCoroutineScope.runBlockingTest {
            try {
                result = coLocation.getLocationUpdate(locationRequest)
            } catch (e: TestException) {
                resultException = e
            }
        }

        assertNull(result)
        assertNotNull(resultException)
    }

    @Test
    fun `getLocationUpdate cancel`() {
        val requestTask: Task<Void> = mockk(relaxed = true)
        requestTask.mockCanceled()
        val locationRequest: LocationRequest = mockk()
        every { locationProvider.requestLocationUpdates(locationRequest, any(), any()) } returns requestTask
        var result: Location? = null
        var resultException: TaskCancelledException? = null

        testCoroutineScope.runBlockingTest {
            try {
                result = coLocation.getLocationUpdate(locationRequest)
            } catch (e: TaskCancelledException) {
                resultException = e
            }
        }

        assertNull(result)
        assertNotNull(resultException)
    }

    @Test
    fun `getLocationUpdates success`() {
        val requestTask: Task<Void> = mockk(relaxed = true)
        val removeTask: Task<Void> = mockk(relaxed = true)
        val locationRequest: LocationRequest = mockk {
            every { numUpdates } returns Integer.MAX_VALUE
        }
        val locations: List<Location> = MutableList(5) { mockk() }
        val callbackSlot = slot<LocationCallback>()
        every {
            locationProvider.requestLocationUpdates(
                locationRequest,
                capture(callbackSlot),
                any()
            )
        } returns requestTask

        val flowResults = mutableListOf<Location>()

        val job = testCoroutineScope.launch { coLocation.getLocationUpdates(locationRequest).collect(flowResults::add) }

        runBlocking {
            callbackSlot.waitForCapture()
            every { locationProvider.removeLocationUpdates(callbackSlot.captured) } returns removeTask
            locations.forEach { location ->
                callbackSlot.captured.onLocationResult(LocationResult.create(listOf(location)))
                delay(10)
            }
            job.cancelAndJoin()
        }

        assertEquals(locations, flowResults)
        verify { locationProvider.removeLocationUpdates(callbackSlot.captured) }
    }

    @Test
    fun `getLocationUpdates error`() {
        val requestTask: Task<Void> = mockk(relaxed = true)
        val testException = TestException()
        requestTask.mockError(testException)
        val locationRequest: LocationRequest = mockk()
        every { locationProvider.requestLocationUpdates(locationRequest, any(), any()) } returns requestTask
        every { locationProvider.removeLocationUpdates(any<LocationCallback>()) } returns mockk()
        var resultException: CancellationException? = null

        testCoroutineScope.runBlockingTest {
            try {
                coLocation.getLocationUpdates(locationRequest).collect()
            } catch (e: CancellationException) {
                resultException = e
            }
        }

        assertNotNull(resultException)
        assertEquals(testException, resultException!!.cause!!.cause)
    }

    @Test
    fun `getLocationUpdates cancel`() {
        val requestTask: Task<Void> = mockk(relaxed = true)
        requestTask.mockCanceled()
        val locationRequest: LocationRequest = mockk()
        every { locationProvider.requestLocationUpdates(locationRequest, any(), any()) } returns requestTask
        every { locationProvider.removeLocationUpdates(any<LocationCallback>()) } returns mockk()
        var resultException: CancellationException? = null

        testCoroutineScope.runBlockingTest {
            try {
                coLocation.getLocationUpdates(locationRequest).collect()
            } catch (e: CancellationException) {
                resultException = e
            }
        }

        assertTrue(resultException!!.cause!!.cause is TaskCancelledException)
    }

    @Test
    fun `checkLocationSettings satisfied`() {
        val locationSettingsRequest: LocationSettingsRequest = mockk()
        testTaskSuccess(
            createTask = { settings.checkLocationSettings(locationSettingsRequest) },
            taskResult = mockk(),
            expectedResult = CoLocation.SettingsResult.Satisfied,
            coLocationCall = { coLocation.checkLocationSettings(locationSettingsRequest) }
        )
    }

    @Test
    fun `checkLocationSettings resolvable`() {
        val locationSettingsRequest: LocationSettingsRequest = mockk()
        val errorTask = mockTask<LocationSettingsResponse>()
        every { settings.checkLocationSettings(locationSettingsRequest) } returns errorTask
        errorTask.mockError(mockk<ResolvableApiException>())
        val result = runBlockingWithTimeout { coLocation.checkLocationSettings(locationSettingsRequest) }
        assertTrue(result is CoLocation.SettingsResult.Resolvable)
    }

    @Test
    fun `checkLocationSettings not resolvable`() {
        val locationSettingsRequest: LocationSettingsRequest = mockk()
        val errorTask = mockTask<LocationSettingsResponse>()
        every { settings.checkLocationSettings(locationSettingsRequest) } returns errorTask
        errorTask.mockError(TestException())
        val result = runBlockingWithTimeout { coLocation.checkLocationSettings(locationSettingsRequest) }
        assertTrue(result is CoLocation.SettingsResult.NotResolvable)
    }

    @Test
    fun `checkLocationSettings canceled`() {
        val locationSettingsRequest: LocationSettingsRequest = mockk()
        assertThrows(TaskCancelledException::class.java) {
            testTaskCancel(
                createTask = { settings.checkLocationSettings(locationSettingsRequest) },
                coLocationCall = { coLocation.checkLocationSettings(locationSettingsRequest) }
            )
        }
    }

    @Test
    fun `checkLocationSettings locationRequest success`() {
        val locationRequest: LocationRequest = mockk()
        testTaskSuccess(
            createTask = { settings.checkLocationSettings(any()) },
            taskResult = mockk(),
            expectedResult = CoLocation.SettingsResult.Satisfied,
            coLocationCall = { coLocation.checkLocationSettings(locationRequest) }
        )
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

        val result = coLocation.getAddressFromLocation(location)

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

        val result = coLocation.getAddressFromLocation(location)

        assertNull(result)
    }

    @Test
    fun `getAddressFromLocation lat lng with result`() {
        val latitude = 1.0
        val longitude = 2.0
        val address: Address = mockk()
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 1) } returns listOf(address)

        val result = coLocation.getAddressFromLocation(latitude, longitude)

        assertEquals(address, result)
    }

    @Test
    fun `getAddressFromLocation lat lng without result`() {
        val latitude = 1.0
        val longitude = 2.0
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 1) } returns emptyList()

        val result = coLocation.getAddressFromLocation(latitude, longitude)

        assertNull(result)
    }

    @Test
    fun `getAddressFromLocationName with result`() {
        val locationName = "SFO"
        val address: Address = mockk()
        every { anyConstructed<Geocoder>().getFromLocationName(locationName, 1) } returns listOf(address)

        val result = coLocation.getAddressFromLocationName(locationName)

        assertEquals(address, result)
    }

    @Test
    fun `getAddressFromLocationName without result`() {
        val locationName = "SFO"
        every { anyConstructed<Geocoder>().getFromLocationName(locationName, 1) } returns emptyList()

        val result = coLocation.getAddressFromLocationName(locationName)

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

        val result = coLocation.getAddressFromLocationName(
            locationName,
            lowerLeftLatitude,
            lowerLeftLongitude,
            upperRightLatitude,
            upperRightLongitude
        )

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

        val result = coLocation.getAddressFromLocationName(
            locationName,
            lowerLeftLatitude,
            lowerLeftLongitude,
            upperRightLatitude,
            upperRightLongitude
        )

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

        val result = coLocation.getAddressListFromLocation(location, maxResults = 5)

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

        val result = coLocation.getAddressListFromLocation(location, maxResults = 5)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAddressListFromLocation lat lng with result`() {
        val latitude = 1.0
        val longitude = 2.0
        val address: Address = mockk()
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 5) } returns listOf(address)

        val result = coLocation.getAddressListFromLocation(latitude, longitude, maxResults = 5)

        assertEquals(listOf(address), result)
    }

    @Test
    fun `getAddressListFromLocation lat lng without result`() {
        val latitude = 1.0
        val longitude = 2.0
        every { anyConstructed<Geocoder>().getFromLocation(latitude, longitude, 5) } returns emptyList()

        val result = coLocation.getAddressListFromLocation(latitude, longitude, maxResults = 5)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAddressListFromLocationName with result`() {
        val locationName = "SFO"
        val address: Address = mockk()
        every { anyConstructed<Geocoder>().getFromLocationName(locationName, 5) } returns listOf(address)

        val result = coLocation.getAddressListFromLocationName(locationName, maxResults = 5)

        assertEquals(listOf(address), result)
    }

    @Test
    fun `getAddressListFromLocationName without result`() {
        val locationName = "SFO"
        every { anyConstructed<Geocoder>().getFromLocationName(locationName, 5) } returns emptyList()

        val result = coLocation.getAddressListFromLocationName(locationName, maxResults = 5)

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

        val result = coLocation.getAddressListFromLocationName(
            locationName,
            lowerLeftLatitude,
            lowerLeftLongitude,
            upperRightLatitude,
            upperRightLongitude,
            maxResults = 5
        )

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

        val result = coLocation.getAddressListFromLocationName(
            locationName,
            lowerLeftLatitude,
            lowerLeftLongitude,
            upperRightLatitude,
            upperRightLongitude,
            maxResults = 5
        )

        assertTrue(result.isEmpty())
    }
}

private fun <T, R> testTaskWithCancelThrows(
    createTask: MockKMatcherScope.() -> Task<T>,
    taskResult: T,
    expectedResult: R,
    expectedErrorException: Exception,
    expectedCancelException: Exception,
    coLocationCall: suspend CoroutineScope.() -> R
) {
    testTaskSuccess(createTask, taskResult, expectedResult, coLocationCall)
    testTaskFailure(createTask, expectedErrorException, coLocationCall)
    assertThrows(expectedCancelException::class.java) { testTaskCancel(createTask, coLocationCall) }
}

private fun <T, R> testTaskWithCancelReturns(
    createTask: MockKMatcherScope.() -> Task<T>,
    taskResult: T,
    expectedResult: R?,
    expectedErrorException: Exception,
    cancelResult: R?,
    coLocationCall: suspend CoroutineScope.() -> R
) {
    testTaskSuccess(createTask, taskResult, expectedResult, coLocationCall)
    testTaskFailure(createTask, expectedErrorException, coLocationCall)
    assertEquals(cancelResult, testTaskCancel(createTask, coLocationCall))
}

private fun <T, R> testTaskCancel(
    createTask: MockKMatcherScope.() -> Task<T>,
    coLocationCall: suspend CoroutineScope.() -> R
): R? {
    val cancelTask = mockTask<T>()
    every { createTask() } returns cancelTask

    cancelTask.mockCanceled()

    return runBlockingWithTimeout { coLocationCall() }
}

private fun <T, R> testTaskSuccess(
    createTask: MockKMatcherScope.() -> Task<T>,
    taskResult: T,
    expectedResult: R,
    coLocationCall: suspend CoroutineScope.() -> R
) {
    val successTask = mockTask<T>()
    every { createTask() } returns successTask

    successTask.mockSuccess(taskResult)

    assertEquals(expectedResult, runBlockingWithTimeout { coLocationCall() })
}

private fun <T, R> testTaskFailure(
    createTask: MockKMatcherScope.() -> Task<T>,
    expectedErrorException: Exception,
    coLocationCall: suspend CoroutineScope.() -> R
) {
    val errorTask = mockTask<T>()
    every { createTask() } returns errorTask

    assertThrows(expectedErrorException::class.java) {
        errorTask.mockError(expectedErrorException)
        runBlockingWithTimeout { coLocationCall() }
    }

}

private fun <T> Task<T>.mockSuccess(taskResult: T) = every { addOnSuccessListener(any()) } answers {
    firstArg<OnSuccessListener<T>>().onSuccess(taskResult)
    self as Task<T>
}

private fun <E : Exception> Task<*>.mockError(exception: E) = every { addOnFailureListener(any()) } answers {
    firstArg<OnFailureListener>().onFailure(exception)
    self as Task<*>
}

private fun Task<*>.mockCanceled() = every { addOnCanceledListener(any()) } answers {
    firstArg<OnCanceledListener>().onCanceled()
    self as Task<*>
}

private fun <T> mockTask() = mockk<Task<T>>().apply {
    every { addOnSuccessListener(any()) } returns this
    every { addOnCanceledListener(any()) } returns this
    every { addOnFailureListener(any()) } returns this
}

private class TestException : Exception()

private fun <T> runBlockingWithTimeout(
    timeoutMillis: Long = 5000,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T = runBlocking(context) { withTimeout(timeoutMillis) { withContext(Dispatchers.Default) { block() } } }

private suspend fun CapturingSlot<*>.waitForCapture() {
    while (!isCaptured) {
        delay(1)
    }
}