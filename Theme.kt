package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.model.MarchingFigureStyle

enum class BlueprintThemeStyle(
    val title: String,
    val shortLabel: String,
    val subtitle: String,
    val isDarkSurface: Boolean
) {
    CREAM_DRAFT(
        title = "CREAM STUDIO",
        shortLabel = "CREAM",
        subtitle = "Warm drafting paper & Prussian navy",
        isDarkSurface = false
    ),
    LANTERN_GLADE(
        title = "LANTERN GLADE",
        shortLabel = "GLADE",
        subtitle = "Twilight grove, amber lantern & teal pond",
        isDarkSurface = true
    ),
    NIGHT_BLUEPRINT(
        title = "NIGHT BLUEPRINT",
        shortLabel = "NIGHT",
        subtitle = "Deep navy sheet & chalk lines",
        isDarkSurface = true
    ),
    ROYAL_CYANOTYPE(
        title = "CYANOTYPE 1842",
        shortLabel = "CYANO",
        subtitle = "Cobalt engineering print & copper gold",
        isDarkSurface = true
    ),
    SEPIA_VELLUM(
        title = "SEPIA VELLUM",
        shortLabel = "SEPIA",
        subtitle = "Archival manuscript & espresso ink",
        isDarkSurface = false
    ),
    GRAPHITE_SLATE(
        title = "GRAPHITE LAB",
        shortLabel = "SLATE",
        subtitle = "Architectural steel pad & drafting amber",
        isDarkSurface = false
    )
}

fun MarchingFigureStyle.pairedThemeStyle(): BlueprintThemeStyle = when (this) {
    MarchingFigureStyle.FLAG_BEARER -> BlueprintThemeStyle.CREAM_DRAFT
    MarchingFigureStyle.DANCER -> BlueprintThemeStyle.LANTERN_GLADE
    MarchingFigureStyle.SOLDIER -> BlueprintThemeStyle.GRAPHITE_SLATE
    MarchingFigureStyle.DRUMMER -> BlueprintThemeStyle.SEPIA_VELLUM
    MarchingFigureStyle.CONDUCTOR -> BlueprintThemeStyle.ROYAL_CYANOTYPE
}

@Immutable
data class BlueprintTokens(
    val style: BlueprintThemeStyle,
    val isDark: Boolean,
    val paperBg: Color,
    val cardBg: Color,
    val transportBg: Color,
    val gridMinor: Color,
    val gridMajor: Color,
    val hatchLine: Color,
    val border: Color,
    val shadow: Color,
    val inkPrimary: Color,
    val inkMuted: Color,
    val gold: Color,
    val goldText: Color,
    val libraryBg: Color,
    val libraryLine: Color,
    val tagBlue: Color,
    val danger: Color,
    val emeraldAccent: Color,
    val tealAccent: Color,
    val plumAccent: Color,
    val coralAccent: Color
)

val LightBlueprintTokens = BlueprintTokens(
    style = BlueprintThemeStyle.CREAM_DRAFT,
    isDark = false,
    paperBg = CreamPaper,
    cardBg = CreamCard,
    transportBg = CreamTransportBg,
    gridMinor = CreamGridMinor,
    gridMajor = CreamGridMajor,
    hatchLine = CreamHatch,
    border = BlueprintNavy,
    shadow = BlueprintNavy,
    inkPrimary = BlueprintNavy,
    inkMuted = MutedInk,
    gold = DraftingGold,
    goldText = BlueprintNavy,
    libraryBg = LibraryNavy,
    libraryLine = Color(0x28FFFFFF),
    tagBlue = TagBlue,
    danger = CrimsonAlert,
    emeraldAccent = CreamEmerald,
    tealAccent = CreamTeal,
    plumAccent = CreamPlum,
    coralAccent = CreamCoral
)

val LanternGladeTokens = BlueprintTokens(
    style = BlueprintThemeStyle.LANTERN_GLADE,
    isDark = true,
    paperBg = GladePaper,
    cardBg = GladeCard,
    transportBg = GladeTransportBg,
    gridMinor = GladeGridMinor,
    gridMajor = GladeGridMajor,
    hatchLine = GladeHatch,
    border = GladeBorder,
    shadow = GladeShadow,
    inkPrimary = GladeTextPrimary,
    inkMuted = GladeTextMuted,
    gold = GladeGold,
    goldText = GladeShadow,
    libraryBg = GladeLibraryBg,
    libraryLine = Color(0x3CF2B134),
    tagBlue = GladeTagBlue,
    danger = Color(0xFFFF7A88),
    emeraldAccent = GladeEmerald,
    tealAccent = GladeTeal,
    plumAccent = GladePlum,
    coralAccent = GladeCoral
)

val NightBlueprintTokens = BlueprintTokens(
    style = BlueprintThemeStyle.NIGHT_BLUEPRINT,
    isDark = true,
    paperBg = NightPaper,
    cardBg = NightCard,
    transportBg = NightTransportBg,
    gridMinor = NightGridMinor,
    gridMajor = NightGridMajor,
    hatchLine = NightHatch,
    border = NightBorder,
    shadow = NightShadow,
    inkPrimary = NightTextPrimary,
    inkMuted = NightTextMuted,
    gold = NightGold,
    goldText = NightPaper,
    libraryBg = NightLibraryNavy,
    libraryLine = Color(0x38D8B23A),
    tagBlue = NightTagBlue,
    danger = Color(0xFFF87171),
    emeraldAccent = NightEmerald,
    tealAccent = NightTeal,
    plumAccent = NightPlum,
    coralAccent = NightCoral
)

val CyanotypeBlueprintTokens = BlueprintTokens(
    style = BlueprintThemeStyle.ROYAL_CYANOTYPE,
    isDark = true,
    paperBg = CyanoPaper,
    cardBg = CyanoCard,
    transportBg = CyanoTransportBg,
    gridMinor = CyanoGridMinor,
    gridMajor = CyanoGridMajor,
    hatchLine = CyanoHatch,
    border = CyanoBorder,
    shadow = CyanoShadow,
    inkPrimary = CyanoTextPrimary,
    inkMuted = CyanoTextMuted,
    gold = CyanoGold,
    goldText = CyanoShadow,
    libraryBg = CyanoLibraryBg,
    libraryLine = Color(0x3CEAF3FF),
    tagBlue = CyanoTagBlue,
    danger = Color(0xFFFF9EA6),
    emeraldAccent = CyanoEmerald,
    tealAccent = CyanoTeal,
    plumAccent = CyanoPlum,
    coralAccent = CyanoCoral
)

val SepiaBlueprintTokens = BlueprintTokens(
    style = BlueprintThemeStyle.SEPIA_VELLUM,
    isDark = false,
    paperBg = SepiaPaper,
    cardBg = SepiaCard,
    transportBg = SepiaTransportBg,
    gridMinor = SepiaGridMinor,
    gridMajor = SepiaGridMajor,
    hatchLine = SepiaHatch,
    border = SepiaBorder,
    shadow = SepiaShadow,
    inkPrimary = SepiaTextPrimary,
    inkMuted = SepiaTextMuted,
    gold = SepiaGold,
    goldText = Color(0xFFFFFBF2),
    libraryBg = SepiaLibraryBg,
    libraryLine = Color(0x34EDE2CE),
    tagBlue = SepiaTagBlue,
    danger = Color(0xFFC1272D),
    emeraldAccent = SepiaEmerald,
    tealAccent = SepiaTeal,
    plumAccent = SepiaPlum,
    coralAccent = SepiaCoral
)

val GraphiteBlueprintTokens = BlueprintTokens(
    style = BlueprintThemeStyle.GRAPHITE_SLATE,
    isDark = false,
    paperBg = GraphitePaper,
    cardBg = GraphiteCard,
    transportBg = GraphiteTransportBg,
    gridMinor = GraphiteGridMinor,
    gridMajor = GraphiteGridMajor,
    hatchLine = GraphiteHatch,
    border = GraphiteBorder,
    shadow = GraphiteShadow,
    inkPrimary = GraphiteTextPrimary,
    inkMuted = GraphiteTextMuted,
    gold = GraphiteGold,
    goldText = GraphiteBorder,
    libraryBg = GraphiteLibraryBg,
    libraryLine = Color(0x2EFFFFFF),
    tagBlue = GraphiteTagBlue,
    danger = Color(0xFFD02935),
    emeraldAccent = GraphiteEmerald,
    tealAccent = GraphiteTeal,
    plumAccent = GraphitePlum,
    coralAccent = GraphiteCoral
)

fun tokensForStyle(style: BlueprintThemeStyle): BlueprintTokens = when (style) {
    BlueprintThemeStyle.CREAM_DRAFT -> LightBlueprintTokens
    BlueprintThemeStyle.LANTERN_GLADE -> LanternGladeTokens
    BlueprintThemeStyle.NIGHT_BLUEPRINT -> NightBlueprintTokens
    BlueprintThemeStyle.ROYAL_CYANOTYPE -> CyanotypeBlueprintTokens
    BlueprintThemeStyle.SEPIA_VELLUM -> SepiaBlueprintTokens
    BlueprintThemeStyle.GRAPHITE_SLATE -> GraphiteBlueprintTokens
}

val LocalBlueprintTokens = staticCompositionLocalOf { LightBlueprintTokens }

@Composable
fun EnsisViTheme(
    darkTheme: Boolean = false,
    themeStyle: BlueprintThemeStyle = if (darkTheme) BlueprintThemeStyle.NIGHT_BLUEPRINT else BlueprintThemeStyle.CREAM_DRAFT,
    content: @Composable () -> Unit
) {
    val target = tokensForStyle(themeStyle)
    val spec = tween<Color>(durationMillis = 380)

    val paperBg by animateColorAsState(target.paperBg, spec, label = "paperBg")
    val cardBg by animateColorAsState(target.cardBg, spec, label = "cardBg")
    val transportBg by animateColorAsState(target.transportBg, spec, label = "transportBg")
    val gridMinor by animateColorAsState(target.gridMinor, spec, label = "gridMinor")
    val gridMajor by animateColorAsState(target.gridMajor, spec, label = "gridMajor")
    val hatchLine by animateColorAsState(target.hatchLine, spec, label = "hatchLine")
    val border by animateColorAsState(target.border, spec, label = "border")
    val shadow by animateColorAsState(target.shadow, spec, label = "shadow")
    val inkPrimary by animateColorAsState(target.inkPrimary, spec, label = "inkPrimary")
    val inkMuted by animateColorAsState(target.inkMuted, spec, label = "inkMuted")
    val gold by animateColorAsState(target.gold, spec, label = "gold")
    val goldText by animateColorAsState(target.goldText, spec, label = "goldText")
    val libraryBg by animateColorAsState(target.libraryBg, spec, label = "libraryBg")
    val libraryLine by animateColorAsState(target.libraryLine, spec, label = "libraryLine")
    val tagBlue by animateColorAsState(target.tagBlue, spec, label = "tagBlue")
    val danger by animateColorAsState(target.danger, spec, label = "danger")
    val emeraldAccent by animateColorAsState(target.emeraldAccent, spec, label = "emeraldAccent")
    val tealAccent by animateColorAsState(target.tealAccent, spec, label = "tealAccent")
    val plumAccent by animateColorAsState(target.plumAccent, spec, label = "plumAccent")
    val coralAccent by animateColorAsState(target.coralAccent, spec, label = "coralAccent")

    val tokens = target.copy(
        paperBg = paperBg,
        cardBg = cardBg,
        transportBg = transportBg,
        gridMinor = gridMinor,
        gridMajor = gridMajor,
        hatchLine = hatchLine,
        border = border,
        shadow = shadow,
        inkPrimary = inkPrimary,
        inkMuted = inkMuted,
        gold = gold,
        goldText = goldText,
        libraryBg = libraryBg,
        libraryLine = libraryLine,
        tagBlue = tagBlue,
        danger = danger,
        emeraldAccent = emeraldAccent,
        tealAccent = tealAccent,
        plumAccent = plumAccent,
        coralAccent = coralAccent
    )

    val colorScheme = if (tokens.isDark) {
        darkColorScheme(
            primary = tokens.gold,
            onPrimary = tokens.goldText,
            secondary = tokens.libraryBg,
            onSecondary = tokens.inkPrimary,
            tertiary = tokens.tealAccent,
            background = tokens.paperBg,
            onBackground = tokens.inkPrimary,
            surface = tokens.cardBg,
            onSurface = tokens.inkPrimary,
            surfaceVariant = tokens.gridMinor,
            onSurfaceVariant = tokens.inkMuted,
            outline = tokens.border,
            error = tokens.danger
        )
    } else {
        lightColorScheme(
            primary = tokens.gold,
            onPrimary = tokens.goldText,
            secondary = tokens.libraryBg,
            onSecondary = tokens.paperBg,
            tertiary = tokens.tealAccent,
            background = tokens.paperBg,
            onBackground = tokens.inkPrimary,
            surface = tokens.cardBg,
            onSurface = tokens.inkPrimary,
            surfaceVariant = tokens.gridMinor,
            onSurfaceVariant = tokens.inkMuted,
            outline = tokens.border,
            error = tokens.danger
        )
    }

    CompositionLocalProvider(LocalBlueprintTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StudioTypography,
            content = content
        )
    }
}
