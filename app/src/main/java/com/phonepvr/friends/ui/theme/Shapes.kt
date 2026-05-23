package com.phonepvr.friends.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Softer corner radii than the Material 3 defaults — the app should feel warm
 * and rounded rather than corporate-flat. Cards and chips pick up the medium
 * slot; bottom sheets and the cadence sheet use the extraLarge slot.
 */
val FriendsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
