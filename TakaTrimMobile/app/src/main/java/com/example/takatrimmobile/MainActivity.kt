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
import java.math.BigDecimal
import java.math.RoundingMode

private const val METHOD_NPSB = "NPSB (bKash to Bank)"
private const val METHOD_BANK = "Bank Transfer (bKash to Bank)"
private const val ROUND_NORMAL = "Round normally"
private const val ROUND_DOWN = "Cut extra decimals"

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

private data class CalculationResult(
    val transfer: BigDecimal,
    val fee: BigDecimal,
    val total: BigDecimal,
    val remaining: BigDecimal,
    val exact: Boolean,
)

private fun String.toMoney(): BigDecimal {
    val cleaned = trim()

    if (cleaned.isEmpty()) {
        throw IllegalArgumentException("Balance cannot be empty.")
    }

    return cleaned.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
}

private fun roundFee(value: BigDecimal, roundingMode: String): BigDecimal {
    val mode = if (roundingMode == ROUND_DOWN) {
        RoundingMode.DOWN
    } else {
        RoundingMode.HALF_UP
    }

    return value.setScale(2, mode)
}

private fun calculateFee(
    amount: BigDecimal,
    method: String,
    npsbPer1000: String,
    bankPercent: String,
    minFee: String,
    roundingMode: String,
): BigDecimal {
    return when (method) {
        METHOD_NPSB -> {
            val feePer1000 = npsbPer1000.toMoney()
            val rawFee = amount.multiply(feePer1000).divide(BigDecimal("1000"))
            roundFee(rawFee, roundingMode)
        }

        METHOD_BANK -> {
            val percent = bankPercent.toMoney()
            val minimum = minFee.toMoney()
            val rawFee = amount.multiply(percent).divide(BigDecimal("100"))
            val fee = roundFee(rawFee, roundingMode)

            if (fee < minimum) minimum else fee
        }

        else -> throw IllegalArgumentException("Invalid transfer type.")
    }
}

private fun findTransferAmount(
    balanceText: String,
    method: String,
    npsbPer1000: String,
    bankPercent: String,
    minFee: String,
    roundingMode: String,
): CalculationResult {
    val balance = balanceText.toMoney()

    if (balance <= BigDecimal.ZERO) {
        throw IllegalArgumentException("Balance must be greater than 0.")
    }

    val maxCents = balance.movePointRight(2).toLong()

    var low = 0L
    var high = maxCents

    var bestTransfer = BigDecimal("0.00")
    var bestFee = BigDecimal("0.00")
    var bestTotal = BigDecimal("0.00")

    while (low <= high) {
        val mid = (low + high) / 2L

        val transfer = BigDecimal(mid)
            .divide(BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP)

        val fee = calculateFee(
            amount = transfer,
            method = method,
            npsbPer1000 = npsbPer1000,
            bankPercent = bankPercent,
            minFee = minFee,
            roundingMode = roundingMode,
        )

        val total = transfer.add(fee).setScale(2, RoundingMode.HALF_UP)

        if (total <= balance) {
            bestTransfer = transfer
            bestFee = fee
            bestTotal = total
            low = mid + 1L
        } else {
            high = mid - 1L
        }
    }

    return CalculationResult(
        transfer = bestTransfer.setScale(2, RoundingMode.HALF_UP),
        fee = bestFee.setScale(2, RoundingMode.HALF_UP),
        total = bestTotal.setScale(2, RoundingMode.HALF_UP),
        remaining = balance.subtract(bestTotal).setScale(2, RoundingMode.HALF_UP),
        exact = bestTotal.compareTo(balance) == 0,
    )
}

@Composable
private fun TakaTrimTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Color(0xFFE2136E),
            secondary = Color(0xFF8B5CF6),
            background = Color(0xFFF4F6FB),
            surface = Color.White,
        ),
        content = content,
    )
}

@Composable
private fun TakaTrimApp() {
    val clipboard = LocalClipboardManager.current

    var balance by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(METHOD_NPSB) }
    var roundingMode by remember { mutableStateOf(ROUND_NORMAL) }

    var npsbFee by remember { mutableStateOf("8.50") }
    var bankPercent by remember { mutableStateOf("1.25") }
    var minFee by remember { mutableStateOf("10.00") }
    var settingsOpen by remember { mutableStateOf(false) }

    var result by remember {
        mutableStateOf(
            CalculationResult(
                transfer = BigDecimal("0.00"),
                fee = BigDecimal("0.00"),
                total = BigDecimal("0.00"),
                remaining = BigDecimal("0.00"),
                exact = false,
            )
        )
    }

    var status by remember { mutableStateOf("Ready") }
    var error by remember { mutableStateOf<String?>(null) }

    if (settingsOpen) {
        FeeSettingsDialog(
            npsbFee = npsbFee,
            onNpsbFeeChange = { npsbFee = it },
            roundingMode = roundingMode,
            onRoundingModeChange = { roundingMode = it },
            bankPercent = bankPercent,
            onBankPercentChange = { bankPercent = it },
            minFee = minFee,
            onMinFeeChange = { minFee = it },
            onDismiss = { settingsOpen = false },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF4F6FB),
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
                HeaderCompact(
                    onSettingsClick = { settingsOpen = true },
                )

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
                            options = listOf(METHOD_NPSB, METHOD_BANK),
                            onSelected = { method = it },
                        )

                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE2136E),
                            ),
                            onClick = {
                                try {
                                    error = null

                                    result = findTransferAmount(
                                        balanceText = balance,
                                        method = method,
                                        npsbPer1000 = npsbFee,
                                        bankPercent = bankPercent,
                                        minFee = minFee,
                                        roundingMode = roundingMode,
                                    )

                                    status = if (result.exact) {
                                        "Exact zero match"
                                    } else {
                                        "Closest safe amount"
                                    }
                                } catch (e: Exception) {
                                    error = e.message ?: "Invalid input."
                                }
                            },
                        ) {
                            Text(
                                text = "Calculate",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }

                ResultCompact(
                    status = status,
                    result = result,
                )

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
                        onClick = {
                            clipboard.setText(AnnotatedString(result.transfer.toPlainString()))
                            status = "Copied ${result.transfer.toPlainString()} Tk"
                        },
                    ) {
                        Text(
                            text = "Copy amount",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }

                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(13.dp),
                        onClick = {
                            balance = ""
                            status = "Ready"
                            error = null

                            result = CalculationResult(
                                transfer = BigDecimal("0.00"),
                                fee = BigDecimal("0.00"),
                                total = BigDecimal("0.00"),
                                remaining = BigDecimal("0.00"),
                                exact = false,
                            )
                        },
                    ) {
                        Text(
                            text = "Clear",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }

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
            }
        }
    }
}

@Composable
private fun HeaderCompact(
    onSettingsClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFE2136E), Color(0xFF8B5CF6)),
                )
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
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
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = "⚙",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ResultCompact(
    status: String,
    result: CalculationResult,
) {
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
                Text(
                    text = "Result",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                )

                Text(
                    text = status,
                    color = if (result.exact) {
                        Color(0xFF027A48)
                    } else {
                        Color(0xFF344054)
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
                    color = Color(0xFF667085),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )

                Text(
                    text = "${result.transfer.toPlainString()} Tk",
                    color = Color(0xFFE2136E),
                    fontSize = 31.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiniStat(
                    label = "Fee",
                    value = "${result.fee.toPlainString()} Tk",
                    modifier = Modifier.weight(1f),
                )

                MiniStat(
                    label = "Total",
                    value = "${result.total.toPlainString()} Tk",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF9FAFB))
            .padding(10.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = Color(0xFF667085),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
        )

        Text(
            text = value,
            color = Color(0xFF101828),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun FeeSettingsDialog(
    npsbFee: String,
    onNpsbFeeChange: (String) -> Unit,
    roundingMode: String,
    onRoundingModeChange: (String) -> Unit,
    bankPercent: String,
    onBankPercentChange: (String) -> Unit,
    minFee: String,
    onMinFeeChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Fee settings",
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CompactTextField(
                    value = npsbFee,
                    onValueChange = onNpsbFeeChange,
                    label = "NPSB fee per 1000 Tk",
                    placeholder = "8.50",
                )

                CompactDropdown(
                    label = "Fee rounding",
                    selected = roundingMode,
                    options = listOf(ROUND_NORMAL, ROUND_DOWN),
                    onSelected = onRoundingModeChange,
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
                    label = "Minimum fee",
                    placeholder = "10.00",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Done",
                    color = Color(0xFFE2136E),
                    fontWeight = FontWeight.Bold,
                )
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
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                fontSize = 11.sp,
            )
        },
        placeholder = {
            Text(
                text = placeholder,
                fontSize = 12.sp,
            )
        },
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
        ),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color(0xFFE2136E),
            unfocusedIndicatorColor = Color(0xFFD0D5DD),
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
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
                .fillMaxWidth()
                .height(56.dp),
            readOnly = true,
            value = selected,
            onValueChange = {},
            label = {
                Text(
                    text = label,
                    fontSize = 11.sp,
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                )
            },
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp),
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color(0xFFE2136E),
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
                    text = {
                        Text(
                            text = option,
                            fontSize = 13.sp,
                        )
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}