package com.tjlabs.tjjupiterdemo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tjlabs.tjlabsjupiter_sdk_android.InOutState
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterErrorCode
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterMockMode
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterNavigationRoute
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterServiceCode
import com.tjlabs.tjlabscommon_sdk_android.uvd.UserMode
import com.tjlabs.tjlabsjupiter_sdk_android.InitErrorCode
import com.tjlabs.tjlabsjupiter_sdk_android.JupiterServiceManager
import com.tjlabs.tjlabsjupiter_sdk_android.TJJupiterAuth
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
    private var selectedMockMode = JupiterMockMode.VEHICLE_INDOOR_OUTDOOR
    private var isMockModeApplied = false
    private var didInitialPermissionRequest = false

    // 서버 · 데이터 옵션 (기본 PROD, save/upload 모두 OFF).
    // save/upload 는 useDevServer=true 인 경우에만 켤 수 있다 — PROD 사용자에게 개발용
    // 진단 데이터가 저장 · 업로드되지 않도록 방지.
    private var useDevServer = false
    private var saveDataEnabled = false
    private var uploadDataEnabled = false
    private lateinit var switchDevServer: SwitchCompat
    private lateinit var switchSaveData: SwitchCompat
    private lateinit var switchUploadData: SwitchCompat

    // 샘플 기본값: 송도 컨벤시아 섹터(20), 차량 모드
    private val sectorId = 111
    private val userMode = UserMode.MODE_VEHICLE
    private val region = JupiterRegion.SAUDI.value

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

        override fun isNavigationRouteChanged(
            routeId: String?,
            totalDistance: Int?,
            routes: List<JupiterNavigationRoute>
        ) {
            runOnUiThread {
                appendLog("Route changed: routeId=$routeId, totalDistance=$totalDistance, points=${routes.size}")
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
        val spinnerMockMode = findViewById<Spinner>(R.id.spinnerMockMode)

        findViewById<Button>(R.id.btnAuth).setOnClickListener { authJupiter() }
        findViewById<Button>(R.id.btnInit).setOnClickListener { initJupiter() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startJupiter() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopJupiter() }
        findViewById<Button>(R.id.btnMockToggle).setOnClickListener { applyMockMode() }

        // 서버 · 데이터 옵션 스위치
        switchDevServer = findViewById(R.id.switchDevServer)
        switchSaveData = findViewById(R.id.switchSaveData)
        switchUploadData = findViewById(R.id.switchUploadData)

        switchDevServer.setOnCheckedChangeListener { _, checked ->
            useDevServer = checked
            appendLog(
                if (checked) "서버 설정: DEV (.jupiter.tjlabs.dev) — 다음 AUTH 부터 적용"
                else "서버 설정: PROD (.jupiter.tjlabscorp.com) — 다음 AUTH 부터 적용"
            )
            // PROD 로 되돌리면 save/upload 도 자동 OFF (PROD 에서 켜져있으면 안 되므로).
            if (!checked) {
                if (saveDataEnabled) {
                    switchSaveData.isChecked = false
                }
                if (uploadDataEnabled) {
                    switchUploadData.isChecked = false
                }
            }
        }

        switchSaveData.setOnCheckedChangeListener { _, checked ->
            if (checked && !useDevServer) {
                showToast("Save 는 Test Server (DEV) 사용 시에만 가능합니다.")
                appendLog("Save toggle 거부: DEV 서버 미선택 상태")
                switchSaveData.isChecked = false
                return@setOnCheckedChangeListener
            }
            saveDataEnabled = checked
            jupiterService.setSaveDataFlag(checked)
            appendLog("Save data: ${if (checked) "ON" else "OFF"}")
        }

        switchUploadData.setOnCheckedChangeListener { _, checked ->
            if (checked && !useDevServer) {
                showToast("Upload 는 Test Server (DEV) 사용 시에만 가능합니다.")
                appendLog("Upload toggle 거부: DEV 서버 미선택 상태")
                switchUploadData.isChecked = false
                return@setOnCheckedChangeListener
            }
            uploadDataEnabled = checked
            jupiterService.setTelemetryUploadEnabled(checked)
            appendLog("Upload telemetry: ${if (checked) "ON" else "OFF"}")
        }

        val mockModes = JupiterMockMode.values().toList()
        val mockLabels = mockModes.map { it.name }
        spinnerMockMode.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            mockLabels
        )
        spinnerMockMode.setSelection(mockModes.indexOf(selectedMockMode).coerceAtLeast(0))
        spinnerMockMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedMockMode = mockModes[position]
                appendLog("선택된 Mock item: ${selectedMockMode.name}")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        appendLog("앱 시작")
        appendLog("(선택) Test Server 스위치 → DEV 서버 사용. Save/Upload 는 DEV 일 때만 활성.")
        appendLog("1) AUTH 버튼으로 인증")
        appendLog("2) INIT 버튼으로 초기화")
        appendLog("3) Mock item 선택 후 APPLY MOCK ITEM(선택)")
        appendLog("4) START 버튼으로 서비스 시작")
        appendLog("5) STOP 버튼으로 서비스 중지")
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
        // [Jupiter SDK 2.0.14 사용법 #1] 서버 설정 후 인증
        // 서버 설정은 TJJupiterAuth.setServerConfig(provider, region)에서 관리한다.
        if (accessKey.isBlank() || accessSecretKey.isBlank()) {
            appendLog("AUTH 키가 비어있습니다. local.properties의 AUTH_ACCESS_KEY / AUTH_SECRET_ACCESS_KEY를 확인하세요.")
            return
        }

        val envLabel = if (useDevServer) "DEV (.jupiter.tjlabs.dev)" else "PROD (.jupiter.tjlabscorp.com)"
        appendLog("AUTH 요청... env=$envLabel")
        isAuthed = false
        isInitialized = false
        if (useDevServer) {
            // ⚠ 내부 QA · 개발 서버 (SLA 없음). release 빌드에서 호출 시 SDK 가 경고 로그 출력.
            TJJupiterAuth.setServerConfigForDevelopment(this, ServerProvider.GCP.value, region)
        } else {
            TJJupiterAuth.setServerConfig(ServerProvider.GCP.value, region)
        }
        TJJupiterAuth.auth(application, accessKey, accessSecretKey) { code, success ->
            runOnUiThread {
                isAuthed = success
                appendLog("AUTH 결과: success=$success, code=$code")
                if (success) {
                    appendLog("AUTH 성공 — INIT 버튼을 눌러 초기화하세요.")
                }
            }
        }
    }

    private fun initJupiter() {
        // [Jupiter SDK 2.0.14 사용법 #2] 초기화
        // AUTH 성공 후 sectorId 로 서비스를 초기화한다.
        if (!isAuthed) {
            appendLog("INIT 전에 AUTH 를 먼저 수행하세요.")
            return
        }
        if (isInitialized) {
            appendLog("이미 INIT 완료 상태입니다.")
            return
        }

        val envLabel = if (useDevServer) "DEV (.jupiter.tjlabs.dev)" else "PROD (.jupiter.tjlabscorp.com)"
        appendLog("INIT 요청... provider=${ServerProvider.GCP.value}, region=$region, sectorId=$sectorId, env=$envLabel")
        jupiterService.setDebugOption(true)
        // INIT 전에 현재 스위치 상태를 SDK 에 반영.
        jupiterService.setSaveDataFlag(saveDataEnabled)
        jupiterService.setTelemetryUploadEnabled(uploadDataEnabled)
        jupiterService.initialize(
            sectorId,
            jupiterCallback
        )
    }

    private fun startJupiter() {
        // [Jupiter SDK 사용법 #2] 시작
        // 2.0.5 기준: startService(mode, callback) 호출로 측위를 시작한다.
        if (isServiceRunning) {
            appendLog("이미 Jupiter 서비스가 실행 중입니다.")
        }

        if (!isAuthed) {
            appendLog("START 전에 AUTH를 먼저 수행하세요.")
        }
        if (!isInitialized) {
            appendLog("START 전에 INIT 성공이 필요합니다. AUTH 후 INIT 결과를 확인하세요.")
        }

        if (!hasRequiredRuntimePermissions()) {
            val summary = missingPermissionsSummary()
            appendLog("필수 권한이 부족해 START를 중단했습니다: $summary")
            showToast("권한 필요: $summary")
            requestAllRequiredPermissions()
        }

        appendLog(
            if (isMockModeApplied) {
                "START 요청... mode=${userMode.value}, mock=${selectedMockMode.name}"
            } else {
                "START 요청... mode=${userMode.value}, mock=OFF"
            }
        )
        jupiterService.startService(userMode, jupiterCallback)
    }

    private fun stopJupiter() {
        // [Jupiter SDK 사용법 #3] 중지
        // stopService()로 서비스를 중지한다.
        if (!isServiceRunning) {
            appendLog("현재 실행 중인 Jupiter 서비스가 없습니다.")
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

    private fun applyMockMode() {
        // [Jupiter SDK 2.0.14 사용법 #4] Mock 데이터 item 적용
        // 선택한 mock timeline을 로드한 뒤 START로 실행한다.
        jupiterService.setMockMode(selectedMockMode)
        isMockModeApplied = true
        appendLog("Mock item 적용 완료: ${selectedMockMode.name}")
        showToast("Mock item applied: ${selectedMockMode.name}")
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
