package com.tjlabs.tjjupiterdemo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tjlabs.tjlabsjupiter_sdk_android.InOutState
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterErrorCode
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterNavigationRoute
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterServiceCode
import com.tjlabs.tjlabscommon_sdk_android.uvd.UserMode
import com.tjlabs.tjlabsjupiter_sdk_android.InitErrorCode
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterServiceManager
import com.tjlabs.tjlabsjupiter_sdk_android.api.JupiterRegion
import com.tjlabs.tjlabsjupiter_sdk_android.api.JupiterResult
import com.tjlabs.tjlabsresource_sdk_android.ServerProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var jupiterService: JupiterServiceManager

    private var isAuthed = false
    private var isInitialized = false
    private var isServiceRunning = false
    private var isMockModeEnabled = false
    private var didInitialPermissionRequest = false

    // 샘플 기본값: 코엑스 섹터(20), 차량 모드
    private val sectorId = 20
    private val userMode = UserMode.MODE_VEHICLE
    private val region = JupiterRegion.KOREA.value

    // 샘플 앱에서는 userId를 고정으로 사용한다.
    // 실제 서비스에서는 로그인 사용자 식별자(공백 없는 고유값)를 사용하면 된다.
    private val userId = "sample_user_android"

    // local.properties 또는 Gradle property에서 주입받는 값
    private val accessKey: String by lazy { BuildConfig.AUTH_ACCESS_KEY }
    private val accessSecretKey: String by lazy { BuildConfig.AUTH_SECRET_ACCESS_KEY }

    private val jupiterCallback = object : JupiterServiceManager.JupiterServiceManagerDelegate {
        override fun onInitSuccess(isSuccess: Boolean, errorCode: InitErrorCode?) {
            runOnUiThread {
                isInitialized = isSuccess
                appendLog("INIT 결과: success=$isSuccess, errorCode=$errorCode")
            }
        }

        override fun onJupiterSuccess(isSuccess: Boolean, code: JupiterErrorCode?) {
            runOnUiThread {
                isServiceRunning = isSuccess
                appendLog("START 콜백: success=$isSuccess, errorCode=$code")
            }
        }

        override fun onJupiterReport(code: JupiterServiceCode, msg: String) {
            runOnUiThread {
                appendLog("Jupiter Report: code=$code, msg=$msg")
            }
        }

        override fun onJupiterResult(result: JupiterResult) {
            runOnUiThread {
                appendLog(
                    "Result: building=${result.building_name}, level=${result.level_name}, " +
                        "x=${"%.2f".format(result.jupiter_pos.x)}, y=${"%.2f".format(result.jupiter_pos.y)}"
                )
            }
        }

        override fun isJupiterInOutStateChanged(state: InOutState) {
            runOnUiThread {
                appendLog("InOut state changed: $state")
            }
        }

        override fun isUserGuidanceOut() {
            runOnUiThread {
                appendLog("User guidance out")
            }
        }

        override fun isNavigationRouteChanged(routes: List<JupiterNavigationRoute>) {
            runOnUiThread {
                appendLog("Route changed: points=${routes.size}")
            }
        }

        override fun isNavigationRouteFailed() {
            runOnUiThread {
                appendLog("Route failed")
            }
        }

        override fun isWaypointChanged(waypoints: List<List<Double>>) {
            runOnUiThread {
                appendLog("Waypoint changed: points=${waypoints.size}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        tvLog = findViewById(R.id.tvLog)

        //init
        jupiterService = JupiterServiceManager(application, userId)

        findViewById<Button>(R.id.btnAuth).setOnClickListener { authJupiter() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startJupiter() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopJupiter() }
        findViewById<Button>(R.id.btnMockToggle).setOnClickListener { toggleMockMode() }

        appendLog("앱 시작")
        appendLog("1) AUTH 버튼으로 인증")
        appendLog("2) START 버튼으로 서비스 시작")
        appendLog("3) STOP 버튼으로 서비스 중지")
    }

    override fun onStart() {
        super.onStart()
        if (didInitialPermissionRequest) return
        didInitialPermissionRequest = true
        tvLog.post {
            requestAllRequiredPermissions()
        }
    }

    private fun authJupiter() {
        // [Jupiter SDK 사용법 #1] 인증
        // auth(accessKey, accessSecretKey)를 먼저 호출한다.
        if (accessKey.isBlank() || accessSecretKey.isBlank()) {
            appendLog("AUTH 키가 비어있습니다. local.properties의 AUTH_ACCESS_KEY / AUTH_SECRET_ACCESS_KEY를 확인하세요.")
            return
        }

        appendLog("AUTH 요청...")
        isAuthed = false
        isInitialized = false
        JupiterServiceManager.setAuthServer(ServerProvider.AWS.value, region)
        JupiterServiceManager.auth(application, accessKey, accessSecretKey) { code, success ->
            runOnUiThread {
                isAuthed = success
                appendLog("AUTH 결과: success=$success, code=$code")
                if (success) {
                    appendLog("INIT 요청... provider=${ServerProvider.AWS.value}, region=$region, sectorId=$sectorId")
                    jupiterService.initialize(
                        sectorId,
                        jupiterCallback
                    )
                }
            }
        }
    }

    private fun startJupiter() {
        // [Jupiter SDK 사용법 #2] 시작
        // 2.0.5 기준: startService(mode, callback) 호출로 측위를 시작한다.
        if (isServiceRunning) {
            appendLog("이미 Jupiter 서비스가 실행 중입니다.")
            return
        }

        if (!isAuthed) {
            appendLog("START 전에 AUTH를 먼저 수행하세요.")
            return
        }
        if (!isInitialized) {
            appendLog("START 전에 INIT 성공이 필요합니다. AUTH 후 INIT 결과를 확인하세요.")
            return
        }

        if (!hasRequiredRuntimePermissions()) {
            val summary = missingPermissionsSummary()
            appendLog("필수 권한이 부족해 START를 중단했습니다: $summary")
            showToast("권한 필요: $summary")
            requestAllRequiredPermissions()
            return
        }

        appendLog("START 요청... mode=${userMode.value}")
        jupiterService.startService(userMode, jupiterCallback)
    }

    private fun stopJupiter() {
        // [Jupiter SDK 사용법 #3] 중지
        // stopService()로 서비스를 중지한다.
        if (!isServiceRunning) {
            appendLog("현재 실행 중인 Jupiter 서비스가 없습니다.")
            return
        }

        appendLog("STOP 요청...")
        jupiterService.stopService { success, message ->
            runOnUiThread {
                if (success) {
                    isServiceRunning = false
                }
                appendLog("STOP 결과: success=$success, message=$message")
            }
        }
    }

    private fun toggleMockMode() {
        // [Jupiter SDK 사용법 #4] Mock 데이터 모드 토글
        // setMockingMode(true/false)로 mock 모드를 켜고 끈다.
        isMockModeEnabled = !isMockModeEnabled
        jupiterService.setMockingMode(isMockModeEnabled)
        showToast("Mock mode: ${if (isMockModeEnabled) "ON" else "OFF"}")
    }

    private fun requestAllRequiredPermissions() {
        logPermissionState("requestAllRequiredPermissions")
        if (!hasLocationPermission()) {
            appendLog("위치 권한 요청 팝업 표시 시도")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                LOCATION_PERMISSIONS_CODE
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermissions()) {
            appendLog("블루투스 권한 요청 팝업 표시 시도")
            val missingBluetooth = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                missingBluetooth += Manifest.permission.BLUETOOTH_SCAN
            }
            if (missingBluetooth.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    missingBluetooth.toTypedArray(),
                    BLUETOOTH_PERMISSIONS_CODE
                )
            }
        }
    }

    private fun hasRequiredRuntimePermissions(): Boolean {
        val result = hasLocationPermission() && hasBluetoothPermissions()
        if (!result) {
            logPermissionState("hasRequiredRuntimePermissions=false")
        }
        return result
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun missingPermissionsSummary(): String {
        val missing = mutableListOf<String>()
        if (!hasLocationPermission()) {
            missing += "LOCATION"
        }
        if (!hasBluetoothPermissions() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            missing += "BLUETOOTH_SCAN"
        }
        return if (missing.isEmpty()) "없음" else missing.joinToString(", ")
    }

    private fun showManualPermissionGuide(permissionGroup: String) {
        appendLog("$permissionGroup 권한 팝업을 표시할 수 없습니다. 앱 설정에서 수동으로 허용해주세요.")
        showToast("$permissionGroup 권한: 앱 설정에서 허용 필요")
    }

    private fun logPermissionState(context: String) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val scan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        appendLog("perm[$context] fine=$fine scan=$scan")
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSIONS_CODE -> {
                if (hasLocationPermission()) {
                    appendLog("위치 권한 허용 완료")
                    requestAllRequiredPermissions()
                } else {
                    appendLog("위치 권한 미허용")
                    val canAskAgain =
                        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    if (!canAskAgain) {
                        showManualPermissionGuide("LOCATION")
                        openAppSettings()
                    }
                }
            }

            BLUETOOTH_PERMISSIONS_CODE -> {
                if (hasBluetoothPermissions()) {
                    appendLog("블루투스 권한 허용 완료")
                } else {
                    appendLog("블루투스 권한 미허용")
                    val canAskAgain =
                        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_SCAN)
                    if (!canAskAgain) {
                        showManualPermissionGuide("BLUETOOTH")
                        openAppSettings()
                    }
                }
            }
        }
    }

    private fun appendLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newText = "[$time] $message\n${tvLog.text}"

        // 로그가 너무 커지면 앞부분만 유지하여 UI가 흔들리지 않도록 한다.
        tvLog.text = if (newText.length > MAX_LOG_CHARS) newText.take(MAX_LOG_CHARS) else newText
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val LOCATION_PERMISSIONS_CODE = 1001
        private const val BLUETOOTH_PERMISSIONS_CODE = 1002
        private const val MAX_LOG_CHARS = 6000
    }
}
