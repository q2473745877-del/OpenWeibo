package com.openweibo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.GeistMono
import com.openweibo.app.ui.theme.monoStyle

val PanelShape = RoundedCornerShape(16.dp)
val SmallPanelShape = RoundedCornerShape(11.dp)

@Composable
fun Modifier.appPanel(
    color: Color = AppTheme.colors.surface,
    borderColor: Color = AppTheme.colors.border,
    shape: Shape = PanelShape
): Modifier = this
    .clip(shape)
    .background(color)
    .border(1.dp, borderColor, shape)

@Composable
fun BrandMark(size: Dp = 34.dp) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(colors.surfaceAlt)
            .border(1.dp, colors.border, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "W",
            color = colors.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Medium,
            fontSize = (size.value * 0.44f).sp,
            letterSpacing = (-1).sp
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 4.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.success)
        )
    }
}

@Composable
fun Avatar(
    url: String,
    name: String,
    size: Dp = 40.dp,
    shape: Shape = CircleShape,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colors.surfaceAlt)
            .border(1.dp, colors.borderSoft, shape),
        contentAlignment = Alignment.Center
    ) {
        Monogram(name, size)
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = "$name 的头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun Monogram(name: String, size: Dp) {
    Text(
        text = name.trim().take(1).ifBlank { "微" },
        color = AppTheme.colors.textDim,
        fontFamily = GeistMono,
        fontWeight = FontWeight.Medium,
        fontSize = (size.value * 0.34f).sp
    )
}

@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = AppTheme.colors.textDim,
    iconSize: Dp = 18.dp,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun MinimalSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val colors = AppTheme.colors
    val offset by animateDpAsState(
        targetValue = if (checked) 17.dp else 2.dp,
        animationSpec = tween(160),
        label = "switchOffset"
    )
    val background by animateColorAsState(
        targetValue = if (checked) colors.accent else colors.surfaceAlt,
        animationSpec = tween(160),
        label = "switchBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) colors.accent else colors.border,
        animationSpec = tween(160),
        label = "switchBorder"
    )
    Box(
        modifier = Modifier
            .width(37.dp)
            .height(22.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, borderColor, CircleShape)
            .clickable(enabled = enabled) { onChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset, y = 1.5.dp)
                .size(17.dp)
                .clip(CircleShape)
                .background(if (checked) colors.onAccent else colors.textFaint)
        )
    }
}

@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.surface)
            .border(1.dp, if (selected) colors.accent else colors.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) colors.onAccent else colors.textDim,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun MonoLabel(
    text: String,
    color: Color = AppTheme.colors.textFaint,
    modifier: Modifier = Modifier,
    size: Number = 9.5
) {
    Text(
        text = text,
        modifier = modifier,
        style = monoStyle(size, letterSpacing = 0.9),
        color = color,
        maxLines = 1
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = monoStyle(9.5, FontWeight.Medium, 1.1),
        color = AppTheme.colors.textFaint
    )
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 10.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                MonoLabel(subtitle.uppercase())
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

@Composable
fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.45f)
    ) {
        Text(
            label,
            color = colors.text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (enabled && !loading) colors.accent else colors.surfaceAlt)
            .then(
                if (enabled && !loading) Modifier.clickable(onClick = onClick) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = colors.textDim,
                    strokeWidth = 1.6.dp
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text = label,
                color = if (enabled && !loading) colors.onAccent else colors.textFaint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 42.dp, vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.surfaceAlt)
                .border(1.dp, colors.borderSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.textDim,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            color = colors.text,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = body,
            color = colors.textFaint,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            OutlineButton(label = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
    trailing: @Composable () -> Unit = {}
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (danger) colors.danger else colors.text,
                fontSize = 14.sp,
                lineHeight = 19.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    color = colors.textFaint,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.borderSoft
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

@Composable
fun SectionPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .appPanel(),
        content = content
    )
}

@Composable
fun AppSnackbar(data: SnackbarData) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = data.visuals.message,
            modifier = Modifier.weight(1f),
            color = colors.text,
            fontSize = 12.5.sp,
            lineHeight = 18.sp
        )
        data.visuals.actionLabel?.let { label ->
            Text(
                text = label,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { data.performAction() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = colors.accent,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
