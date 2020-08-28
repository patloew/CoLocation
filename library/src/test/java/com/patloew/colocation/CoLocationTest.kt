package com.patloew.colocation

import android.content.Context
import android.location.Location
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
        every { locationProvider.requestLocationUpdates(locationRequest, capture(callbackSlot), any()) } returns requestTask
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
        every { locationProvider.requestLocationUpdates(locationRequest, capture(callbackSlot), any()) } returns requestTask

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
    val cancelTask = mockk<Task<T>>(relaxed = true)
    every { createTask() } returns cancelTask

    cancelTask.mockCanceled()

    return runBlockingWithTimeout(5000) { coLocationCall() }
}

private fun <T, R> testTaskSuccess(
        createTask: MockKMatcherScope.() -> Task<T>,
        taskResult: T,
        expectedResult: R,
        coLocationCall: suspend CoroutineScope.() -> R
) {
    val successTask = mockk<Task<T>>(relaxed = true)
    every { createTask() } returns successTask

    successTask.mockSuccess(taskResult)

    assertEquals(expectedResult, runBlockingWithTimeout(5000) { coLocationCall() })
}

private fun <T, R> testTaskFailure(
        createTask: MockKMatcherScope.() -> Task<T>,
        expectedErrorException: Exception,
        coLocationCall: suspend CoroutineScope.() -> R
) {
    val errorTask = mockk<Task<T>>(relaxed = true)
    every { createTask() } returns errorTask

    assertThrows(expectedErrorException::class.java) {
        errorTask.mockError(expectedErrorException)
        runBlockingWithTimeout(5000) { coLocationCall() }
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

private class TestException : Exception()

private fun <T> runBlockingWithTimeout(
        timeoutMillis: Long,
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> T
): T = runBlocking(context) { withTimeout(timeoutMillis) { withContext(Dispatchers.Default) { block() } } }

private suspend fun CapturingSlot<*>.waitForCapture() {
    while (!isCaptured) {
        delay(1)
    }
}