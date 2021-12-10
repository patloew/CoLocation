package com.patloew.colocation

import android.app.Activity
import android.app.PendingIntent
import com.patloew.colocation.google.GmsResolvableApiExceptionResult
import com.patloew.colocation.huawei.HsmResolvableApiExceptionResult
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
    fun getResolutionIntentReturnsNull() {
        val gmsException: GmsResolvableApiException = mockk()
        val gmsResolvableResult = GmsResolvableApiExceptionResult(gmsException)
        assertNull(gmsResolvableResult.getResolutionIntent())
    }

    @Test
    fun startResolutionForResultIsCalled() {
        val testActivity: Activity = mockk(relaxed = true)
        val testRequestCode = 565

        val gmsException: GmsResolvableApiException = mockk(relaxed = true)
        val hmsException: HmsResolvableApiException = mockk(relaxed = true)

        val gmsResolvableResult = GmsResolvableApiExceptionResult(gmsException)
        val hsmResolvableResult = HsmResolvableApiExceptionResult(hmsException)

        gmsResolvableResult.startResolutionForResult(testActivity, testRequestCode)
        hsmResolvableResult.startResolutionForResult(testActivity, testRequestCode)

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

        val gmsResolvableResult = GmsResolvableApiExceptionResult(gmsException)
        val hsmResolvableResult = HsmResolvableApiExceptionResult(hmsException)

        assertEquals(gmsResolvableResult.getResolution(), testResolution)
        assertEquals(hsmResolvableResult.getResolution(), testResolution)
    }
}