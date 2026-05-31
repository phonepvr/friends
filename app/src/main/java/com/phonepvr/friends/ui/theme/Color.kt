package com.phonepvr.friends.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Light palette ──────────────────────────────────────────────────────────
// Warm terracotta primary on a cream surface; peach secondary; sage-olive
// tertiary. All foreground / background pairings here meet WCAG AA on body
// text (≥ 4.5:1) — checked manually against the chosen hex values.

val LightPrimary = Color(0xFFBF5039)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFFFDAD0)
val LightOnPrimaryContainer = Color(0xFF3E0900)

val LightSecondary = Color(0xFF765A4F)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFFFDBCF)
val LightOnSecondaryContainer = Color(0xFF2C160D)

val LightTertiary = Color(0xFF6E5C2F)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF8E0A8)
val LightOnTertiaryContainer = Color(0xFF251A00)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFFFFFBFF)
val LightOnBackground = Color(0xFF221A16)
val LightSurface = Color(0xFFFFFBFF)
val LightOnSurface = Color(0xFF221A16)
val LightSurfaceVariant = Color(0xFFF5DDD5)
val LightOnSurfaceVariant = Color(0xFF53433E)
val LightOutline = Color(0xFF85736D)
val LightOutlineVariant = Color(0xFFD8C2BB)

// ─── Dark palette ───────────────────────────────────────────────────────────
// Warm-charcoal background, peach primary and a warm gold tertiary so the
// vibe stays warm in low light.

val DarkPrimary = Color(0xFFFFB59E)
val DarkOnPrimary = Color(0xFF5C1800)
val DarkPrimaryContainer = Color(0xFF802C12)
val DarkOnPrimaryContainer = Color(0xFFFFDAD0)

val DarkSecondary = Color(0xFFE6BDAF)
val DarkOnSecondary = Color(0xFF432A21)
val DarkSecondaryContainer = Color(0xFF5C3F36)
val DarkOnSecondaryContainer = Color(0xFFFFDBCF)

val DarkTertiary = Color(0xFFDAC68E)
val DarkOnTertiary = Color(0xFF3A2F06)
val DarkTertiaryContainer = Color(0xFF534619)
val DarkOnTertiaryContainer = Color(0xFFF8E0A8)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF1A120F)
val DarkOnBackground = Color(0xFFF0E0DA)
val DarkSurface = Color(0xFF1A120F)
val DarkOnSurface = Color(0xFFF0E0DA)
val DarkSurfaceVariant = Color(0xFF53433E)
val DarkOnSurfaceVariant = Color(0xFFD8C2BB)
val DarkOutline = Color(0xFFA18C86)
val DarkOutlineVariant = Color(0xFF53433E)

// ─── Call-control accents ───────────────────────────────────────────────────
// Telephony convention is green = accept, red = decline. Decline reuses the
// M3 `error` role; "accept" has no equivalent positive role, so it gets its
// own token here. Intentionally NOT derived from dynamic colour — an accept
// button should stay green even under Material You.
val LightCallAccept = Color(0xFF1B873A)
val LightOnCallAccept = Color(0xFFFFFFFF)
val DarkCallAccept = Color(0xFF6FD58A)
val DarkOnCallAccept = Color(0xFF00391A)
