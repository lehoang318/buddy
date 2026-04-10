package com.example.buddy.ui.theme

import androidx.compose.ui.graphics.Color

// Vintage Library Color Scheme from design/look-and-feel.md
val VintageBackground = Color(0xFFF2E8CF)  // Main Background: #F2E8CF
val UserBubble = Color(0xFF829377)         // User Bubble: #829377
val SendButton = Color(0xFFBC6C4D)         // Send Button: #BC6C4D
val TextColor = Color(0xFF3D3631)          // Text Color: #3D3631
val SecondaryIcons = Color(0xFFD1C7B0)     // Secondary Icons: #D1C7B0

// Additional colors for UI elements
val SurfaceVariant = Color(0xFFE8DCC3)     // Lighter variant for surfaces
val OnSurfaceVariant = Color(0xFF5D5144)   // Darker text for surfaces
val Outline = Color(0xFFCCC2B3)            // Outline/border color

// Material Theme color mappings
val Background = VintageBackground
val OnBackground = TextColor
val Surface = VintageBackground
val OnSurface = TextColor
val Primary = SendButton
val OnPrimary = Color.White
val Secondary = UserBubble
val OnSecondary = Color.White