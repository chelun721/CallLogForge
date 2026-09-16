package com.example.calllogforge

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CallLogForgeScreen()
            }
        }
    }
}

@Composable
fun CallLogForgeScreen() {
    val context = LocalContext.current

    // ---- 权限状态 ----
    var hasWriteCallLog by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasReadPhoneState by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val writeCallLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasWriteCallLog = granted }

    val readPhoneStateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasReadPhoneState = granted }

    LaunchedEffect(Unit) {
        if (!hasWriteCallLog) {
            writeCallLogLauncher.launch(Manifest.permission.WRITE_CALL_LOG)
        }
        if (!hasReadPhoneState) {
            readPhoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    // ---- 表单状态 ----
    var phoneNumber by remember { mutableStateOf("") }
    var callType by remember { mutableStateOf(CallLog.Calls.INCOMING_TYPE) }
    var selectedYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var selectedDay by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }

    // 秒数：字符串状态，允许为空
    var secondInput by remember {
        mutableStateOf(Calendar.getInstance().get(Calendar.SECOND).toString())
    }
    // 通话时长：字符串状态，允许为空
    var durationInput by remember { mutableStateOf("30") }

    // SIM 卡槽：1 或 2，始终可选
    var selectedSimSlot by remember { mutableIntStateOf(1) }
    var simAccounts by remember { mutableStateOf<List<PhoneAccountHandle>>(emptyList()) }

    // ---- 加载真实 SIM 卡账户 ----
    LaunchedEffect(hasReadPhoneState) {
        if (hasReadPhoneState) {
            try {
                val telecomManager = context.getSystemService(TelecomManager::class.java)
                simAccounts = telecomManager.callCapablePhoneAccounts ?: emptyList()
            } catch (e: Exception) {
                simAccounts = emptyList()
            }
        } else {
            simAccounts = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "通话记录生成器", style = MaterialTheme.typography.headlineMedium)

        // ---- 权限状态卡片 ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (hasWriteCallLog) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hasWriteCallLog) "已获得通话记录写入权限" else "未获得通话记录写入权限",
                    modifier = Modifier.weight(1f)
                )
                if (!hasWriteCallLog) {
                    Button(onClick = {
                        writeCallLogLauncher.launch(Manifest.permission.WRITE_CALL_LOG)
                    }) { Text("申请") }
                }
            }
        }

        // ---- 手机号 ----
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("手机号") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // ---- 通话类型 ----
        Text("通话类型")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = callType == CallLog.Calls.INCOMING_TYPE,
                onClick = { callType = CallLog.Calls.INCOMING_TYPE },
                label = { Text("呼入") }
            )
            FilterChip(
                selected = callType == CallLog.Calls.OUTGOING_TYPE,
                onClick = { callType = CallLog.Calls.OUTGOING_TYPE },
                label = { Text("呼出") }
            )
        }

        // ---- 呼叫时间 ----
        Text("呼叫时间")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        selectedYear = year
                        selectedMonth = month
                        selectedDay = dayOfMonth
                    },
                    selectedYear, selectedMonth, selectedDay
                ).show()
            }) {
                Text("$selectedYear-${selectedMonth + 1}-$selectedDay")
            }
            OutlinedButton(onClick = {
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        selectedHour = hourOfDay
                        selectedMinute = minute
                    },
                    selectedHour, selectedMinute, true
                ).show()
            }) {
                Text(String.format("%02d:%02d", selectedHour, selectedMinute))
            }
        }

        // ---- 秒数：可留空 ----
        OutlinedTextField(
            value = secondInput,
            onValueChange = { input ->
                if (input.isEmpty()) {
                    secondInput = ""
                } else if (input.all { it.isDigit() } &&
                    (input.toIntOrNull() ?: 0) in 0..59
                ) {
                    secondInput = input
                }
            },
            label = { Text("秒 (0-59，可留空)") },
            placeholder = { Text("留空按 0 处理") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(200.dp),
            singleLine = true
        )

        // ---- 通话时长：可留空 ----
        OutlinedTextField(
            value = durationInput,
            onValueChange = { input ->
                if (input.isEmpty() || input.all { it.isDigit() }) {
                    durationInput = input
                }
            },
            label = { Text("通话时长（秒，可留空）") },
            placeholder = { Text("留空按 0 处理") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(220.dp),
            singleLine = true
        )

        // ---- SIM 卡选择：始终显示 ----
        Text("选择 SIM 卡")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedSimSlot == 1,
                onClick = { selectedSimSlot = 1 },
                label = { Text("卡1") }
            )
            FilterChip(
                selected = selectedSimSlot == 2,
                onClick = { selectedSimSlot = 2 },
                label = { Text("卡2") }
            )
        }
        val simStatus = when {
            !hasReadPhoneState -> "未获得电话状态权限，将使用占位标识"
            simAccounts.isEmpty() -> "未检测到 SIM 卡账户，将使用占位标识"
            simAccounts.size == 1 -> "检测到 1 张 SIM 卡，卡2 将使用占位标识"
            else -> "检测到 ${simAccounts.size} 张 SIM 卡"
        }
        Text(simStatus, style = MaterialTheme.typography.bodySmall)

        // ---- 生成按钮 ----
        Button(
            onClick = {
                if (!hasWriteCallLog) {
                    writeCallLogLauncher.launch(Manifest.permission.WRITE_CALL_LOG)
                    return@Button
                }
                if (phoneNumber.isBlank()) {
                    Toast.makeText(context, "请输入手机号", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                // 空字符串 → 0
                val second = secondInput.toIntOrNull() ?: 0
                val durationSeconds = durationInput.toIntOrNull() ?: 0

                val calendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, selectedDay)
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                    set(Calendar.SECOND, second)
                    set(Calendar.MILLISECOND, 0)
                }
                val timestamp = calendar.timeInMillis

                val accountHandle = simAccounts.getOrNull(selectedSimSlot - 1)

                val values = ContentValues().apply {
                    put(CallLog.Calls.NUMBER, phoneNumber)
                    put(CallLog.Calls.TYPE, callType)
                    put(CallLog.Calls.DATE, timestamp)
                    put(CallLog.Calls.DURATION, durationSeconds)
                    put(CallLog.Calls.NEW, 1)

                    if (accountHandle != null) {
                        // 真实 SIM 卡：ID + ComponentName 两个都要写
                        put(CallLog.Calls.PHONE_ACCOUNT_ID, accountHandle.id)
                        try {
                            put(
                                CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
                                accountHandle.componentName.flattenToString()
                            )
                        } catch (_: Exception) {
                        }
                    } else {
                        // 未检测到账户时写入占位标识
                        put(CallLog.Calls.PHONE_ACCOUNT_ID, "sim_slot_$selectedSimSlot")
                    }
                }

                try {
                    val uri = context.contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
                    if (uri != null) {
                        val formatted = String.format(
                            "%04d-%02d-%02d %02d:%02d:%02d",
                            selectedYear, selectedMonth + 1, selectedDay,
                            selectedHour, selectedMinute, second
                        )
                        Toast.makeText(
                            context,
                            "已生成：$formatted，时长 ${durationSeconds} 秒，卡$selectedSimSlot",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "写入失败：返回空 URI", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "写入异常：${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("生成通话记录")
        }
    }
}
