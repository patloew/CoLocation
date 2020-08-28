package com.patloew.colocation

import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.OnCanceledListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.*
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CancellationException
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

    private val coLocation = CoLocationImpl(context)

    @BeforeEach
    fun before() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        mockkStatic(LocationServices::class)
        every { LocationServices.getFusedLocationProviderClient(context) } returns locationProvider
        every { LocationServices.getSettingsClient(context) } returns settings
    }

    @AfterEach
    fun after() = unmockkAll()

    @Test
    fun flushLocations() {
        testTaskWithCancelException<Void, TestException, CancellationException>(
                { locationProvider.flushLocations() },
                null,
                { coLocation.flushLocations(); null }
        )
    }

    @Test
    fun lastKnownLocation() {
        val location = mockk<Location>()
        testTaskWithCancelReturnValue<Location, TestException>(
                { locationProvider.lastLocation },
                location,
                null,
                { coLocation.getLastLocation() }
        )
    }
}

inline fun <T, reified EFailure : Exception, reified ECancel : Exception> testTaskWithCancelException(
        noinline createTaskFun: () -> Task<T>,
        success: T?,
        noinline block: suspend CoroutineScope.() -> T?
) {
    testTaskSuccess(createTaskFun, success, block)
    testTaskFailure<T, EFailure>(createTaskFun, block)
    assertThrows<ECancel> { testTaskCancel(createTaskFun, block) }
}

inline fun <T, reified E : Exception> testTaskWithCancelReturnValue(
        noinline createTaskFun: () -> Task<T>,
        success: T?,
        cancel: T?,
        noinline block: suspend CoroutineScope.() -> T?
) {
    testTaskSuccess(createTaskFun, success, block)
    testTaskFailure<T, E>(createTaskFun, block)
    assertEquals(cancel, testTaskCancel(createTaskFun, block))
}

fun <T> testTaskCancel(
        createTaskFun: () -> Task<T>,
        block: suspend CoroutineScope.() -> T?
): T? {
    val cancelTask = mockk<Task<T>>(relaxed = true)
    every { createTaskFun() } returns cancelTask

    every { cancelTask.addOnCanceledListener(any()) } answers {
        firstArg<OnCanceledListener>().onCanceled()
        self as Task<T>
    }

    return runBlockingWithTimeout(5000) { block() }
}

fun <T> testTaskSuccess(
        createTaskFun: () -> Task<T>,
        success: T?,
        block: suspend CoroutineScope.() -> T?
) {
    val successTask = mockk<Task<T>>(relaxed = true)
    every { createTaskFun() } returns successTask

    every { successTask.addOnSuccessListener(any()) } answers {
        firstArg<OnSuccessListener<T>>().onSuccess(success)
        self as Task<T>
    }

    assertEquals(success, runBlockingWithTimeout(5000) { block() })
}

inline fun <T, reified E : Exception> testTaskFailure(
        crossinline createTaskFun: () -> Task<T>,
        crossinline block: suspend CoroutineScope.() -> T?
) {
    val errorTask = mockk<Task<T>>(relaxed = true)
    every { createTaskFun() } returns errorTask

    assertThrows<E> {
        every { errorTask.addOnFailureListener(any()) } answers {
            firstArg<OnFailureListener>().onFailure(E::class.java.newInstance())
            self as Task<T>
        }

        runBlockingWithTimeout(5000) { block() }
    }

}

class TestException : Exception()

fun <T> runBlockingWithTimeout(
        timeoutMillis: Long,
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> T
): T = runBlocking(context) { withTimeout(timeoutMillis) { withContext(Dispatchers.Default) { block() } } }