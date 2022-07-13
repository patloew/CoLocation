package com.patloew.colocation

import android.app.Activity
import android.app.PendingIntent
import io.mockk.every
import com.huawei.hms.common.ResolvableApiException as HmsResolvableApiException
import com.google.android.gms.common.api.ResolvableApiException as GmsResolvableApiException
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit

@Timeout(5, unit = TimeUnit.SECONDS)
class ResolvableApiExceptionWrapperTest {

    @Test
    fun throwsOnWrongException() {
        assertThrows<IllegalArgumentException> {
            ResolvableApiExceptionWrapper(NullPointerException())
        }
    }

    @Test
    fun getResolutionIntentReturnsNull() {
        val gmsException: GmsResolvableApiException = mockk()
        val wrapper = ResolvableApiExceptionWrapper(gmsException)
        assertNull(wrapper.getResolutionIntent())
    }

    @Test
    fun startResolutionForResultIsCalled() {
        val testActivity: Activity = mockk(relaxed = true)
        val testRequestCode = 565

        val gmsException: GmsResolvableApiException = mockk(relaxed = true)
        val hmsException: HmsResolvableApiException = mockk(relaxed = true)

        val gmsWrapper = ResolvableApiExceptionWrapper(gmsException)
        val hmsWrapper = ResolvableApiExceptionWrapper(hmsException)

        gmsWrapper.startResolutionForResult(testActivity, testRequestCode)
        hmsWrapper.startResolutionForResult(testActivity, testRequestCode)

        verifyOrder {
            gmsException.startResolutionForResult(testActivity, testRequestCode)
            hmsException.startResolutionForResult(testActivity, testRequestCode)
        }
    }

    @Test
    fun getResolutionReturnsValue() {
        val testResolution: PendingIntent = mockk()
        val gmsException: GmsResolvableApiException = mockk {
            every { resolution } returns testResolution
        }
        val hmsException: HmsResolvableApiException = mockk {
            every { resolution } returns testResolution
        }

        val gmsWrapper = ResolvableApiExceptionWrapper(gmsException)
        val hmsWrapper = ResolvableApiExceptionWrapper(hmsException)

        assertEquals(gmsWrapper.getResolution(), testResolution)
        assertEquals(hmsWrapper.getResolution(), testResolution)
    }
}