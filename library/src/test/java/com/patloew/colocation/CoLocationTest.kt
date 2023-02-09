package com.patloew.colocation

import android.content.Context
import android.location.Location
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.Executor
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
class CoLocationTest {

    private val locationProvider: FusedLocationProviderClient = mockk()
    private val settings: SettingsClient = mockk()
    private val context: Context = mockk()

    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    private val coLocation = CoLocationImpl(context)

    @BeforeEach
    fun before() {
        Dispatchers.setMain(mainThreadSurrogate)
        mockkStatic(LocationServices::class)
        every { LocationServices.getFusedLocationProviderClient(context) } returns locationProvider
        every { LocationServices.getSettingsClient(context) } returns settings
    }

    @AfterEach
    fun after() {
        Dispatchers.resetMain()
        mainThreadSurrogate.close()
        unmockkAll()
    }

    @Test
    fun flushLocations() {
        testTaskWithCancelThrows(
            createTask = { locationProvider.flushLocations() },
            taskResult = null,
            expectedResult = Unit,
            expectedErrorException = TestException(),
            coLocationCall = { coLocation.flushLocations() }
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun isLocationAvailable(locationAvailable: Boolean) {
        val locationAvailability = mockk<LocationAvailability> {
            every { isLocationAvailable } returns locationAvailable
        }
        testTaskWithCancelThrows(
            createTask = { locationProvider.locationAvailability },
            taskResult = locationAvailability,
            expectedResult = locationAvailable,
            expectedErrorException = TestException(),
            coLocationCall = { coLocation.isLocationAvailable() }
        )
    }

    @ParameterizedTest
    @ValueSource(
        ints = [
            LocationRequest.PRIORITY_HIGH_ACCURACY,
            LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY,
            LocationRequest.PRIORITY_LOW_POWER,
            LocationRequest.PRIORITY_NO_POWER
        ]
    )
    fun getCurrentLocation(priority: Int) {
        val location = mockk<Location>()
        testTaskWithCancelReturns(
            createTask = { locationProvider.getCurrentLocation(priority, any()) },
            taskResult = location,
            expectedResult = location,
            expectedErrorException = TestException(),
            cancelResult = null,
            coLocationCall = { coLocation.getCurrentLocation(priority) }
        )
    }

    @ParameterizedTest
    @ValueSource(
        ints = [
            LocationRequest.PRIORITY_HIGH_ACCURACY,
            LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY,
            LocationRequest.PRIORITY_LOW_POWER,
            LocationRequest.PRIORITY_NO_POWER
        ]
    )
    fun `cancelling getCurrentLocation cancels task`(priority: Int) = runTest {
        val tokenSlot = slot<CancellationToken>()

        every {
            locationProvider.getCurrentLocation(priority, capture(tokenSlot))
        } returns mockk(relaxed = true)

        val deferred = async(start = CoroutineStart.UNDISPATCHED) {
            coLocation.getCurrentLocation(priority)
        }

        deferred.cancel()

        assertTrue(deferred.isCancelled)
        assertTrue(tokenSlot.captured.isCancellationRequested)
    }

    @Test
    fun getLastLocation() {
        val location = mockk<Location>()
        testTaskWithCancelThrows(
            createTask = { locationProvider.lastLocation },
            taskResult = location,
            expectedResult = location,
            expectedErrorException = TestException(),
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
            coLocationCall = { coLocation.setMockLocation(location) }
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun setMockMode(mockMode: Boolean) {
        testTaskWithCancelThrows(
            createTask = { locationProvider.setMockMode(mockMode) },
            taskResult = null,
            expectedResult = Unit,
            expectedErrorException = TestException(),
            coLocationCall = { coLocation.setMockMode(mockMode) }
        )
    }

    @Test
    fun `getLocationUpdate success`() = runTest {
        val requestTask: Task<Void> = mockk(relaxed = true)
        val removeTask: Task<Void> = mockk(relaxed = true)
        val locationRequest: LocationRequest = mockk {
            every { numUpdates } returns Integer.MAX_VALUE
        }
        val locations: List<Location> = MutableList(5) { mockk() }
        every { locationProvider.removeLocationUpdates(any<LocationCallback>()) } returns removeTask
        every {
            locationProvider.requestLocationUpdates(
                locationRequest,
                any<LocationCallback>(),
                any()
            )
        } answers {
            val callback = secondArg<LocationCallback>()
            locations.forEach { location ->
                callback.onLocationResult(LocationResult.create(listOf(location)))
            }
            requestTask
        }

        assertEquals(locations[0], coLocation.getLocationUpdate(locationRequest))
        verify { locationProvider.removeLocationUpdates(any<LocationCallback>()) }
    }

    @Test
    fun `getLocationUpdate error`() = runTest {
        val requestTask: Task<Void> = mockk(relaxed = true)
        val testException = TestException()
        requestTask.mockError(testException)
        val locationRequest: LocationRequest = mockk()
        every {
            locationProvider.requestLocationUpdates(
                locationRequest,
                any<LocationCallback>(),
                any()
            )
        } returns requestTask
        var result: Location? = null
        var resultException: TestException? = null

        try {
            result = coLocation.getLocationUpdate(locationRequest)
        } catch (e: TestException) {
            resultException = e
        }

        assertNull(result)
        assertNotNull(resultException)
    }

    @Test
    fun `getLocationUpdate cancel`() = runTest {
        val requestTask: Task<Void> = mockk(relaxed = true)
        requestTask.mockCanceled()
        val locationRequest: LocationRequest = mockk()
        every {
            locationProvider.requestLocationUpdates(
                locationRequest,
                any<LocationCallback>(),
                any()
            )
        } returns requestTask
        var result: Location? = null
        var resultException: TaskCancelledException? = null

        try {
            result = coLocation.getLocationUpdate(locationRequest)
        } catch (e: TaskCancelledException) {
            resultException = e
        }

        assertNull(result)
        assertNotNull(resultException)
    }

    @Test
    fun `getLocationUpdates success`() = runTest {
        val requestTask: Task<Void> = mockk(relaxed = true)
        val removeTask: Task<Void> = mockk(relaxed = true)
        val locationRequest: LocationRequest = mockk {
            every { numUpdates } returns 5
        }
        val locations: List<Location> = MutableList(5) { mockk() }
        every { locationProvider.removeLocationUpdates(any<LocationCallback>()) } returns removeTask
        every {
            locationProvider.requestLocationUpdates(
                locationRequest,
                any<LocationCallback>(),
                any()
            )
        } answers {
            val callback = secondArg<LocationCallback>()
            locations.forEach { location ->
                callback.onLocationResult(LocationResult.create(listOf(location)))
            }
            requestTask
        }
        assertEquals(
            locations,
            coLocation.getLocationUpdates(locationRequest, capacity = 5).toList()
        )
        verify { locationProvider.removeLocationUpdates(any<LocationCallback>()) }
    }

    @Test
    fun `getLocationUpdates error`() = runTest {
        val requestTask: Task<Void> = mockk(relaxed = true)
        val testException = TestException()
        requestTask.mockError(testException)
        val locationRequest: LocationRequest = mockk()
        every {
            locationProvider.requestLocationUpdates(
                locationRequest,
                any<LocationCallback>(),
                any()
            )
        } returns requestTask
        every { locationProvider.removeLocationUpdates(any<LocationCallback>()) } returns mockk()
        var resultException: CancellationException? = null

        try {
            coLocation.getLocationUpdates(locationRequest).collect()
        } catch (e: CancellationException) {
            resultException = e
        }

        assertNotNull(resultException)
        assertEquals(testException, resultException!!.cause!!.cause)
    }

    @Test
    fun `getLocationUpdates cancel`() = runTest {
        val requestTask: Task<Void> = mockk(relaxed = true)
        requestTask.mockCanceled()
        val locationRequest: LocationRequest = mockk()
        every {
            locationProvider.requestLocationUpdates(
                locationRequest,
                any<LocationCallback>(),
                any()
            )
        } returns requestTask
        every { locationProvider.removeLocationUpdates(any<LocationCallback>()) } returns mockk()
        var resultException: CancellationException? = null

        try {
            coLocation.getLocationUpdates(locationRequest).collect()
        } catch (e: CancellationException) {
            resultException = e
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
        val result = runBlocking { coLocation.checkLocationSettings(locationSettingsRequest) }
        assertTrue(result is CoLocation.SettingsResult.Resolvable)
    }

    @Test
    fun `checkLocationSettings not resolvable`() {
        val locationSettingsRequest: LocationSettingsRequest = mockk()
        val errorTask = mockTask<LocationSettingsResponse>()
        every { settings.checkLocationSettings(locationSettingsRequest) } returns errorTask
        errorTask.mockError(TestException())
        val result = runBlocking { coLocation.checkLocationSettings(locationSettingsRequest) }
        assertTrue(result is CoLocation.SettingsResult.NotResolvable)
    }

    @Test
    fun `checkLocationSettings canceled`() {
        val locationSettingsRequest: LocationSettingsRequest = mockk()
        assertThrows(TaskCancelledException::class.java) {
            testTaskCancel(
                createTask = { settings.checkLocationSettings(locationSettingsRequest) },
                expectedResult = null,
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

    private fun <T, R> testTaskWithCancelThrows(
        createTask: MockKMatcherScope.() -> Task<T>,
        taskResult: T,
        expectedResult: R,
        expectedErrorException: Exception,
        coLocationCall: suspend CoroutineScope.() -> R
    ) {
        testTaskSuccess(createTask, taskResult, expectedResult, coLocationCall)
        testTaskFailure(createTask, expectedErrorException, coLocationCall)
        assertThrows(CancellationException::class.java) {
            testTaskCancel(
                createTask,
                null,
                coLocationCall
            )
        }
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
        testTaskCancel(createTask, cancelResult, coLocationCall)
    }

    private fun <T, R> testTaskCancel(
        createTask: MockKMatcherScope.() -> Task<T>,
        expectedResult: R?,
        coLocationCall: suspend CoroutineScope.() -> R
    ) = runTest {
        val cancelTask = mockTask<T>()
        every { createTask() } returns cancelTask

        cancelTask.mockCanceled()

        assertEquals(expectedResult, coLocationCall())
    }

    private fun <T, R> testTaskSuccess(
        createTask: MockKMatcherScope.() -> Task<T>,
        taskResult: T,
        expectedResult: R,
        coLocationCall: suspend CoroutineScope.() -> R
    ) = runTest {
        val successTask = mockTask<T>()
        every { createTask() } returns successTask

        successTask.mockSuccess(taskResult)

        assertEquals(expectedResult, coLocationCall())
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
            runBlocking { coLocationCall() }
        }

    }


    private fun <T> Task<T>.mockSuccess(taskResult: T) {
        every { isComplete } returns false
        every { isCanceled } returns false
        every { exception } returns null
        every { result } returns taskResult
        every { addOnCompleteListener(any<Executor>(), any()) } answers {
            val task = self as Task<T>
            secondArg<OnCompleteListener<T>>().onComplete(task)
            task
        }
        every { addOnSuccessListener(any()) } answers {
            firstArg<OnSuccessListener<T>>().onSuccess(taskResult)
            self as Task<T>
        }
    }


    private fun <E : Exception, T> Task<T>.mockError(e: E) {
        every { isComplete } returns false
        every { exception } returns e
        every { addOnCompleteListener(any<Executor>(), any()) } answers {
            val task = self as Task<T>
            secondArg<OnCompleteListener<T>>().onComplete(task)
            task
        }
        every { addOnFailureListener(any()) } answers {
            firstArg<OnFailureListener>().onFailure(e)
            self as Task<T>
        }
    }

    private fun <T> Task<T>.mockCanceled() {
        every { isComplete } returns false
        every { isCanceled } returns true
        every { exception } returns null
        every { addOnCompleteListener(any<Executor>(), any()) } answers {
            val task = self as Task<T>
            secondArg<OnCompleteListener<T>>().onComplete(task)
            task
        }
        every { addOnCanceledListener(any()) } answers {
            firstArg<OnCanceledListener>().onCanceled()
            self as Task<T>
        }
    }

    private fun <T> mockTask() = mockk<Task<T>>().apply {
        every { addOnSuccessListener(any()) } returns this
        every { addOnCanceledListener(any()) } returns this
        every { addOnFailureListener(any()) } returns this
        every { addOnCompleteListener(any<Executor>(), any()) } returns this
    }

    private suspend fun CapturingSlot<*>.waitForCapture() {
        while (!isCaptured) {
            delay(1)
        }
    }

    class TestException : Exception()
}