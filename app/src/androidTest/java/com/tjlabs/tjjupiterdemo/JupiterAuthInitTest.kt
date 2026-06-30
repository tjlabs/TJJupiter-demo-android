package com.tjlabs.tjjupiterdemo

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tjlabs.tjlabsjupiter_sdk_android.InOutState
import com.tjlabs.tjlabsjupiter_sdk_android.InitErrorCode
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterErrorCode
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterNavigationRoute
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterServiceCode
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterServiceManager
import com.tjlabs.tjlabsjupiter_sdk_android.TJJupiterAuth
import com.tjlabs.tjlabsjupiter_sdk_android.api.JupiterRegion
import com.tjlabs.tjlabsjupiter_sdk_android.api.JupiterResult
import com.tjlabs.tjlabsresource_sdk_android.ServerProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class JupiterAuthInitTest {

    @Test
    fun verifyAuthAndInit() {
        val args = InstrumentationRegistry.getArguments()
        val provider = args.getString("provider") ?: ServerProvider.GCP.value
        val region = args.getString("region") ?: JupiterRegion.KOREA.value
        val sectorId = args.getString("sectorId")?.toIntOrNull() ?: 20
        val label = "provider=$provider, region=$region, sectorId=$sectorId"

        val accessKey = BuildConfig.AUTH_ACCESS_KEY
        val accessSecretKey = BuildConfig.AUTH_SECRET_ACCESS_KEY
        assertTrue("AUTH_ACCESS_KEY missing", accessKey.isNotBlank())
        assertTrue("AUTH_SECRET_ACCESS_KEY missing", accessSecretKey.isNotBlank())

        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application

        TJJupiterAuth.setServerConfig(provider, region)

        val authLatch = CountDownLatch(1)
        var authSuccess = false
        var authCode = -1
        TJJupiterAuth.auth(application, accessKey, accessSecretKey) { code, success ->
            authCode = code
            authSuccess = success
            authLatch.countDown()
        }
        assertTrue("auth callback timeout ($label)", authLatch.await(AUTH_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertTrue("auth failed ($label, code=$authCode)", authSuccess)

        val manager = JupiterServiceManager(application, CI_USER_ID)
        val initLatch = CountDownLatch(1)
        var initSuccess = false
        var initErrorCode: InitErrorCode? = null
        val delegate = object : JupiterServiceManager.JupiterServiceManagerDelegate {
            override fun onInitSuccess(isSuccess: Boolean, errorCode: InitErrorCode?) {
                initSuccess = isSuccess
                initErrorCode = errorCode
                initLatch.countDown()
            }
            override fun onJupiterSuccess(isSuccess: Boolean, code: JupiterErrorCode?) = Unit
            override fun onJupiterReport(code: JupiterServiceCode, msg: String) = Unit
            override fun onJupiterResult(result: JupiterResult) = Unit
            override fun isJupiterInOutStateChanged(state: InOutState) = Unit
            override fun isUserGuidanceOut() = Unit
            override fun isNavigationRouteChanged(
                routeId: String?,
                totalDistance: Int?,
                routes: List<JupiterNavigationRoute>
            ) = Unit
            override fun isNavigationRouteFailed() = Unit
            override fun isWaypointChanged(waypoints: List<List<Double>>) = Unit
        }
        manager.initialize(sectorId, delegate)
        assertTrue("init callback timeout ($label)", initLatch.await(INIT_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertTrue("init failed ($label, errorCode=$initErrorCode)", initSuccess)
        assertNull("init returned errorCode ($label, errorCode=$initErrorCode)", initErrorCode)
    }

    companion object {
        private const val CI_USER_ID = "ci_verify_user_android"
        private const val AUTH_TIMEOUT_SEC = 60L
        private const val INIT_TIMEOUT_SEC = 120L
    }
}
