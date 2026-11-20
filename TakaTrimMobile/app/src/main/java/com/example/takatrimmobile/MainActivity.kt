package com.example.takatrimmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Pink = Color(0xFFE2136E)
private val Purple = Color(0xFF8B5CF6)
private val PageBackground = Color(0xFFF4F6FB)
private val TextDark = Color(0xFF101828)
private val TextMuted = Color(0xFF667085)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TakaTrimTheme {
                TakaTrimApp()
            }
        }
    }
}

@Composable
private fun TakaTrimTheme(content: @Composable () -> Unit) {
    // Light-only design: avoids unreadable text when the phone is in dark mode.
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Pink,
            secondary = Purple,
            background = PageBackground,
            surface = Color.White,
            onSurface = TextDark,
            onBackground = TextDark,
        ),
        content = content,
    )
}

@Composable
private fun TakaTrimApp() {
    val clipboard = LocalClipboardManager.current

    // rememberSaveable keeps typed values across screen rotation.
    var balance by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(TransferMethod.NPSB) }
    var rounding by rememberSaveable { mutableStateOf(FeeRounding.NORMAL) }
    var npsbFee by rememberSaveable { mutableStateOf("8.50") }
    var bankPercent by rememberSaveable { mutableStateOf("1.25") }
    var minFee by rememberSaveable { mutableStateOf("10.00") }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    var result by remember { mutableStateOf<CalculationResult?>(null) }
    var status by remember { mutableStateOf("Ready") }
    var error by remember { mutableStateOf<String?>(null) }

    fun clearResult(newStatus: String = "Ready") {
        result = null
        status = newStatus
    }

    fun calculate() {
        try {
            error = null
            val calculated = TakaTrimCalculator.findTransferAmount(
                balanceText = balance,
                method = method,
                settings = FeeSettings(
                    npsbPer1000 = npsbFee,
                    bankPercent = bankPercent,
                    minFee = minFee,
                    rounding = rounding,
                ),
            )
            if (calculated.transfer.signum() == 0) {
                clearResult("Balance too low for this fee")
            } else {
                result = calculated
                status = if (calculated.exact) "Exact zero match" else "Closest safe amount"
            }
        } catch (e: CalculatorException) {
            clearResult()
            error = e.message
        }
    }

    if (settingsOpen) {
        FeeSettingsDialog(
            npsbFee = npsbFee,
            onNpsbFeeChange = { npsbFee = it },
            rounding = rounding,
            onRoundingChange = { rounding = it },
            bankPercent = bankPercent,
            onBankPercentChange = { bankPercent = it },
            minFee = minFee,
            onMinFeeChange = { minFee = it },
            onDismiss = { settingsOpen = false },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PageBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 430.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeaderCompact(onSettingsClick = { settingsOpen = true })

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Transfer details",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextDark,
                        )

                        CompactTextField(
                            value = balance,
                            onValueChange = { balance = it },
                            label = "Current balance",
                            placeholder = "940.39",
                        )

                        CompactDropdown(
                            label = "Transfer type",
                            selected = method,
                            options = TransferMethod.entries,
                            optionLabel = { it.label },
                            onSelected = { method = it },
                        )

                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Pink),
                            onClick = { calculate() },
                        ) {
                            Text(text = "Calculate", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }

                ResultCompact(status = status, result = result)

                error?.let {
                    Text(
                        text = it,
                        color = Color(0xFFB42318),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFF1F3))
                            .padding(8.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(13.dp),
                        enabled = result != null,
                        onClick = {
                            result?.let {
                                val amount = it.transfer.toPlainString()
                                clipboard.setText(AnnotatedString(amount))
                                status = "Copied $amount Tk"
                            }
                        },
                    ) {
                        Text(text = "Copy amount", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(13.dp),
                        onClick = {
                            balance = ""
                            error = null
                            clearResult()
                        },
                    ) {
                        Text(text = "Clear", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCompact(onSettingsClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Pink, Purple)))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text(
                text = "TakaTrim",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Exact transfer calculator",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 12.sp,
            )
        }

        OutlinedButton(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(38.dp),
            shape = RoundedCornerShape(20.dp),
            onClick = onSettingsClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            Text(text = "⚙ Fees", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultCompact(status: String, result: CalculationResult?) {
    val zero = "0.00 Tk"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Result", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                Text(
                    text = status,
                    color = when {
                        result == null -> Color(0xFF344054)
                        result.exact -> Color(0xFF027A48)
                        else -> Color(0xFFB45309)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFF1F7))
                    .padding(12.dp),
            ) {
                Text(
                    text = "TRANSFER AMOUNT",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = result?.let { "${it.transfer.toPlainString()} Tk" } ?: zero,
                    color = Pink,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat(
                    label = "Fee",
                    value = result?.let { "${it.fee.toPlainString()} Tk" } ?: zero,
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    label = "Total",
                    value = result?.let { "${it.total.toPlainString()} Tk" } ?: zero,
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    label = "Left",
                    value = result?.let { "${it.remaining.toPlainString()} Tk" } ?: zero,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF9FAFB))
            .padding(10.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = value,
            color = TextDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun FeeSettingsDialog(
    npsbFee: String,
    onNpsbFeeChange: (String) -> Unit,
    rounding: FeeRounding,
    onRoundingChange: (FeeRounding) -> Unit,
    bankPercent: String,
    onBankPercentChange: (String) -> Unit,
    minFee: String,
    onMinFeeChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Fee settings", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactTextField(
                    value = npsbFee,
                    onValueChange = onNpsbFeeChange,
                    label = "NPSB fee per 1000 Tk",
                    placeholder = "8.50",
                )
                CompactDropdown(
                    label = "Fee rounding",
                    selected = rounding,
                    options = FeeRounding.entries,
                    optionLabel = { it.label },
                    onSelected = onRoundingChange,
                )
                CompactTextField(
                    value = bankPercent,
                    onValueChange = onBankPercentChange,
                    label = "Bank transfer fee %",
                    placeholder = "1.25",
                )
                CompactTextField(
                    value = minFee,
                    onValueChange = onMinFeeChange,
                    label = "Bank transfer minimum fee",
                    placeholder = "10.00",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Done", color = Pink, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(22.dp),
        containerColor = Color.White,
    )
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, fontSize = 11.sp) },
        placeholder = { Text(text = placeholder, fontSize = 12.sp) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = TextDark),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Pink,
            unfocusedIndicatorColor = Color(0xFFD0D5DD),
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> CompactDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            readOnly = true,
            value = optionLabel(selected),
            onValueChange = {},
            label = { Text(text = label, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = TextDark),
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Pink,
                unfocusedIndicatorColor = Color(0xFFD0D5DD),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = optionLabel(option), fontSize = 13.sp) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
