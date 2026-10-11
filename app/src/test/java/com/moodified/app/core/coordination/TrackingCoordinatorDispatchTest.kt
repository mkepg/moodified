package com.moodified.app.core.coordination

import com.moodified.app.core.coordination.TrackingCoordinator.Dispatch
import com.moodified.app.core.service.TrackingService
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingCoordinatorDispatchTest {
    private fun dispatch(
        action: String,
        canPostNotifications: Boolean,
    ) = TrackingCoordinator.dispatchFor(action, canPostNotifications)

    @Test
    fun `a start goes to the foreground when notifications are allowed`() {
        assertEquals(Dispatch.FOREGROUND, dispatch(TrackingService.ACTION_START_SLEEP, canPostNotifications = true))
    }

    @Test
    fun `a start is skipped when notifications are blocked`() {
        assertEquals(Dispatch.SKIP, dispatch(TrackingService.ACTION_START_SLEEP, canPostNotifications = false))
    }

    @Test
    fun `a stop never promises a foreground service`() {
        // Promising one made Android kill the app when the service stopped without a notification.
        assertEquals(Dispatch.BACKGROUND, dispatch(TrackingService.ACTION_STOP_ACTIVITY, canPostNotifications = false))
        assertEquals(Dispatch.BACKGROUND, dispatch(TrackingService.ACTION_STOP_ACTIVITY, canPostNotifications = true))
    }

    @Test
    fun `a pause never promises a foreground service`() {
        assertEquals(Dispatch.BACKGROUND, dispatch(TrackingService.ACTION_PAUSE_INTERACTION, canPostNotifications = false))
    }
}
