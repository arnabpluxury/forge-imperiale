package com.forge.imperiale.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forge.imperiale.ui.theme.ForgeMonoFont
import com.forge.imperiale.ui.theme.ForgePalette
import com.forge.imperiale.ui.theme.LocalForgeColors

/**
 * Full-bleed color-blocked hero header modeled directly after Linktree Dump & Aurelia:
 * Zero sloppy boxes behind text — bold uppercase condensed display headline, DM Mono eyebrow,
 * inline pill highlight badge, and full-rounded primary pill CTA.
 */
@Composable
fun LinktreeColorBlockHero(
    eyebrow: String,
    headline: String,
    badgeText: String? = null,
    subtitle: String? = null,
    backgroundColor: Color = LocalForgeColors.current.heroBannerBg,
    contentColor: Color = LocalForgeColors.current.heroBannerInk,
    badgeBgColor: Color = ForgePalette.TrueBlack,
    badgeTextColor: Color = ForgePalette.Chartreuse,
    modifier: Modifier = Modifier,
    bottomAction: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(backgroundColor)
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.82f)
            )
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeBgColor)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = badgeText.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeTextColor
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = headline.uppercase(),
            style = MaterialTheme.typography.displayMedium,
            color = contentColor
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor.copy(alpha = 0.88f)
            )
        }
        if (bottomAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            bottomAction()
        }
    }
}

/**
 * Linktree Dump Bento Card: 24dp rounded geometry, crisp 1dp ring border, zero muddy shadows.
 */
@Composable
fun LinktreeBentoCard(
    modifier: Modifier = Modifier,
    containerColor: Color = LocalForgeColors.current.cardSurface,
    borderColor: Color = LocalForgeColors.current.cardBorder,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .clickable(role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

/**
 * Tactile 48dp+ Linktree Pill Button (CircleShape) with haptic feedback.
 */
@Composable
fun LinktreePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = LocalForgeColors.current.primaryPillBg,
    contentColor: Color = LocalForgeColors.current.primaryPillInk,
    trailingArrow: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.38f),
            disabledContentColor = contentColor.copy(alpha = 0.55f)
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        modifier = modifier.defaultMinSize(minHeight = 50.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingArrow) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun LinktreeOutlinePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    leadingIcon: ImageVector? = null
) {
    val haptics = LocalHapticFeedback.current
    OutlinedButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.5.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LinktreeMonoChip(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    locked: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (locked) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked block",
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = text.uppercase(),
            fontFamily = ForgeMonoFont,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

data class ForgeNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Floating Linktree Capsule Bottom Navigation Bar (`rounded-full`, pill-selected active state, 48dp+ touch targets).
 */
@Composable
fun LinktreeFloatingBottomBar(
    destinations: List<ForgeNavDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalForgeColors.current
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(colors.navPillBg)
                .border(1.dp, colors.navPillBorder, CircleShape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { dest ->
                val active = currentRoute == dest.route
                val itemBg by animateColorAsState(
                    targetValue = if (active) colors.navActiveBg else Color.Transparent,
                    label = "navBg"
                )
                val itemInk by animateColorAsState(
                    targetValue = if (active) colors.navActiveInk else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    label = "navInk"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(CircleShape)
                        .background(itemBg)
                        .clickable(role = Role.Tab) {
                            if (!active) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigate(dest.route)
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = dest.label,
                            tint = itemInk,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dest.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = itemInk,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
