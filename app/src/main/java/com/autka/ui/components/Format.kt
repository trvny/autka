package com.autka.ui.components

import com.autka.core.model.Money
import java.text.NumberFormat
import java.util.Locale

fun Money.formatted(): String {
    val nf = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 0 }
    return "${nf.format(amount)} ${currency.symbol}"
}

fun Int?.kmOrDash(): String =
    if (this == null) "--" else "${NumberFormat.getNumberInstance(Locale.getDefault()).format(this)} km"
