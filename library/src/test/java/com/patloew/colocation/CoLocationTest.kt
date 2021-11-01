package com.patloew.colocation

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit
import kotlin.IllegalStateException

@Timeout(5, unit = TimeUnit.SECONDS)
class CoLocationTest {

    private val context: Context = mockk()

    private val gmsSource = LocationServicesSource.GMS
    private val hmsSource = LocationServicesSource.HMS
    private val noneSource = LocationServicesSource.NONE

    @Test
    fun testCorrectSourceIsUsed() {
        val gmsCoLocation = CoLocation.from(context, gmsSource)
        val hmsCoLocation = CoLocation.from(context, hmsSource)

        assert(gmsCoLocation is CoLocationGms)
        assert(hmsCoLocation is CoLocationHms)
    }

    @Test
    fun testCorrectSourceIsFound() {
        mockkStatic(context::getLocationServiceSource)

        every {
            context.getLocationServiceSource()
        } returns gmsSource
        val gmsCoLocation = CoLocation.from(context)

        every {
            context.getLocationServiceSource()
        } returns hmsSource
        val hmsCoLocation = CoLocation.from(context)

        assert(gmsCoLocation is CoLocationGms)
        assert(hmsCoLocation is CoLocationHms)
    }

    @Test
    fun throwsOnIncorrectSource() {
        assertThrows<IllegalStateException> { CoLocation.from(context, noneSource) }
    }
}