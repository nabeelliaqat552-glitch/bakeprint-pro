package com.bakeprint.pro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object BP {
    val Ivory = Color(0xFFF7F3EC)
    val Beige = Color(0xFFEFE7DB)
    val Beige2 = Color(0xFFE6DBCB)
    val Choc = Color(0xFF493629)
    val Grey = Color(0xFF897D70)
    val Gold = Color(0xFFB28B59)
    val White = Color(0xFFFFFEFC)
    val Line = Color(0x1F493629)
    val Ok = Color(0xFF5E7A4E)
    val Danger = Color(0xFFA2493A)
    val WarnBg = Color(0xFFF5EADB)
}

/** true when the app language is Urdu */
val LocalUr = staticCompositionLocalOf { false }

@Composable
fun tr(en: String, ur: String): String = if (LocalUr.current) ur else en

private val Sans = FontFamily.SansSerif

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, color = BP.Choc),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 30.sp, color = BP.Choc),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 17.sp, color = BP.Grey),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp),
)

@Composable
fun BakePrintTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BP.Choc, onPrimary = BP.White, secondary = BP.Gold, onSecondary = BP.White,
            background = BP.Ivory, onBackground = BP.Choc, surface = BP.White, onSurface = BP.Choc,
            surfaceVariant = BP.Beige, onSurfaceVariant = BP.Grey, outline = BP.Grey, error = BP.Danger,
            surfaceContainerLow = BP.Ivory, surfaceContainer = BP.Ivory, surfaceContainerHigh = BP.Ivory,
        ),
        typography = AppTypography,
        content = content,
    )
}

/* ---------------- shared components ---------------- */

fun categoryIcon(key: String): ImageVector = when (key) {
    "cake" -> Icons.Rounded.Cake
    "bread", "croissant" -> Icons.Rounded.BakeryDining
    "cookie" -> Icons.Rounded.BreakfastDining
    "donut" -> Icons.Rounded.Icecream
    "samosa" -> Icons.Rounded.Fastfood
    "milk" -> Icons.Rounded.LocalDrink
    "cup" -> Icons.Rounded.Coffee
    "pizza" -> Icons.Rounded.LocalPizza
    else -> Icons.Rounded.Inventory2
}
val categoryIconKeys = listOf("cake", "bread", "cookie", "donut", "samosa", "milk", "pizza", "cup", "box")

@Composable
fun SoftCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color = BP.White,
             padding: PaddingValues = PaddingValues(14.dp), content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, shadowElevation = 1.5.dp) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, shadowElevation = 1.5.dp) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

enum class BtnStyle { Choc, Gold, Soft, Outline, Danger }

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null,
              style: BtnStyle = BtnStyle.Choc, enabled: Boolean = true, height: Int = 56) {
    val (bg, fg) = when (style) {
        BtnStyle.Choc -> BP.Choc to BP.White
        BtnStyle.Gold -> BP.Gold to BP.White
        BtnStyle.Soft -> BP.Beige to BP.Choc
        BtnStyle.Outline -> BP.White to BP.Choc
        BtnStyle.Danger -> Color(0xFFF4E2DC) to BP.Danger
    }
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = height.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg,
            disabledContainerColor = bg.copy(alpha = .45f), disabledContentColor = fg.copy(alpha = .7f)),
        border = if (style == BtnStyle.Outline) BorderStroke(1.dp, BP.Line) else null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (style == BtnStyle.Choc) BP.Gold else fg)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = if (height >= 60) 17.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Eyebrow(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = BP.Gold)
}

@Composable
fun ScreenTitle(eyebrow: String, title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Eyebrow(eyebrow)
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        trailing?.invoke()
    }
}

@Composable
fun IconBadge(icon: ImageVector, size: Int = 44, bg: Color = BP.Beige, tint: Color = BP.Choc) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * .32f).dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * .5f).dp))
    }
}

@Composable
fun Pill(text: String, bg: Color = BP.Beige, fg: Color = BP.Grey) {
    Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 42.dp), shape = RoundedCornerShape(12.dp),
        color = if (selected) BP.Choc else BP.White, contentColor = if (selected) BP.White else BP.Choc,
        border = BorderStroke(1.dp, if (selected) BP.Choc else BP.Line)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = BP.Gold); Spacer(Modifier.width(6.dp)) }
            Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable
fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (sub != null) Text(sub, color = BP.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(
            checkedThumbColor = BP.White, checkedTrackColor = BP.Choc, uncheckedTrackColor = BP.Beige2,
            uncheckedThumbColor = BP.White, uncheckedBorderColor = BP.Beige2))
    }
}

@Composable
fun BPTextField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
                placeholder: String = "", numeric: Boolean = false, singleLine: Boolean = true, rtl: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(), singleLine = singleLine,
        label = { Text(label) }, placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = BP.Grey) },
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = if (numeric) androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal) else androidx.compose.foundation.text.KeyboardOptions.Default,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            textDirection = if (rtl) androidx.compose.ui.text.style.TextDirection.Rtl else androidx.compose.ui.text.style.TextDirection.Content),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BP.Gold, unfocusedBorderColor = BP.Line, focusedContainerColor = BP.White,
            unfocusedContainerColor = BP.Ivory, focusedLabelColor = BP.Gold, cursorColor = BP.Choc),
    )
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int = 1, max: Int = 999, step: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalIconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = BP.Beige)) {
            Icon(Icons.Rounded.Remove, "-")
        }
        Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp)).background(BP.Ivory), contentAlignment = Alignment.Center) {
            Text(value.toString(), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        FilledTonalIconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = BP.Beige)) {
            Icon(Icons.Rounded.Add, "+")
        }
    }
}
