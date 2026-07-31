package com.tjlabs.tjjupiterdemo

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tjlabs.tjlabsjupiter_sdk_android.InOutState
import com.tjlabs.tjlabsjupiter_sdk_android.InitErrorCode
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterErrorCode
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterMockMode
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterNavigationRoute
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterServiceCode
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterServiceManager
import com.tjlabs.tjlabsjupiter_sdk_android.TJJupiterAuth
import com.tjlabs.tjlabscommon_sdk_android.uvd.UserMode
import com.tjlabs.tjlabsjupiter_sdk_android.api.JupiterRegion
import com.tjlabs.tjlabsjupiter_sdk_android.api.JupiterResult
import com.tjlabs.tjlabsresource_sdk_android.ServerProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class JupiterMockModeTest {

    @Test
    fun verifyMockModesEmitJupiterResult() {
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

        grantRuntimePermissions()
        TJJupiterAuth.setServerConfig(provider, region)
        authenticate(application, accessKey, accessSecretKey, label)

        val scenarios = listOf(
            MockScenario("vehicle", JupiterMockMode.VEHICLE_INDOOR_OUTDOOR, UserMode.MODE_VEHICLE),
        )

        scenarios.forEach { scenario ->
            verifyMockScenario(application, sectorId, label, scenario)
        }
    }

    private fun authenticate(
        application: Application,
        accessKey: String,
        accessSecretKey: String,
        label: String
    ) {
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
    }

    private fun verifyMockScenario(
        application: Application,
        sectorId: Int,
        baseLabel: String,
        scenario: MockScenario
    ) {
        val manager = JupiterServiceManager(application, "${CI_USER_ID}_${scenario.label}")
        val initLatch = CountDownLatch(1)
        val resultLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        val resultCount = AtomicInteger(0)
        val initSuccess = AtomicReference(false)
        val initErrorCode = AtomicReference<InitErrorCode?>(null)
        val startSuccess = AtomicReference<Boolean?>(null)
        val startErrorCode = AtomicReference<JupiterErrorCode?>(null)
        val stopSuccess = AtomicReference<Boolean?>(null)

        val delegate = object : JupiterServiceManager.JupiterServiceManagerDelegate {
            override fun onInitSuccess(isSuccess: Boolean, errorCode: InitErrorCode?) {
                initSuccess.set(isSuccess)
                initErrorCode.set(errorCode)
                initLatch.countDown()
            }

            override fun onJupiterSuccess(isSuccess: Boolean, code: JupiterErrorCode?) {
                startSuccess.set(isSuccess)
                startErrorCode.set(code)
            }

            override fun onJupiterReport(code: JupiterServiceCode, msg: String) = Unit

            override fun onJupiterResult(result: JupiterResult) {
                resultCount.incrementAndGet()
                resultLatch.countDown()
            }

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

        val scenarioLabel = "$baseLabel, mock=${scenario.mockMode.name}, mode=${scenario.userMode.value}"
        manager.initialize(sectorId, delegate)
        assertTrue("init callback timeout ($scenarioLabel)", initLatch.await(INIT_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertTrue("init failed ($scenarioLabel, errorCode=${initErrorCode.get()})", initSuccess.get())
        assertNull("init returned errorCode ($scenarioLabel, errorCode=${initErrorCode.get()})", initErrorCode.get())

        manager.setMockMode(scenario.mockMode)
        manager.startService(scenario.userMode, delegate)
        assertTrue(
            "mock result timeout ($scenarioLabel, startSuccess=${startSuccess.get()}, startErrorCode=${startErrorCode.get()})",
            resultLatch.await(RESULT_TIMEOUT_SEC, TimeUnit.SECONDS)
        )
        assertTrue("mock result not received ($scenarioLabel)", resultCount.get() > 0)

        manager.stopService { success, _ ->
            stopSuccess.set(success)
            stopLatch.countDown()
        }
        assertTrue("stop callback timeout ($scenarioLabel)", stopLatch.await(STOP_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertTrue("stop failed ($scenarioLabel)", stopSuccess.get() == true)
    }

    private fun grantRuntimePermissions() {
        grantPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            grantPermission(Manifest.permission.BLUETOOTH_SCAN)
        }
    }

    private fun grantPermission(permission: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant $packageName $permission"
        ).close()
    }

    private data class MockScenario(
        val label: String,
        val mockMode: JupiterMockMode,
        val userMode: UserMode
    )

    companion object {
        private const val CI_USER_ID = "ci_mock_verify_user_android"
        private const val AUTH_TIMEOUT_SEC = 60L
        private const val INIT_TIMEOUT_SEC = 120L
        private const val RESULT_TIMEOUT_SEC = 60L
        private const val STOP_TIMEOUT_SEC = 30L
    }
}
