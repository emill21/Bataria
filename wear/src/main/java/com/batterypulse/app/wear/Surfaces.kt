package com.batterypulse.app.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.batterypulse.app.data.BatteryReader
import com.batterypulse.app.data.Prefs
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Tile: the level ring, the percentage, and how long it will last (or until full). */
class BatteryTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val s = BatteryReader.read(this, Prefs(this))
        val store = WearStore(this)
        store.record(s)
        val (label, ms) = TimeLeft.estimate(s, store.history())
        val line2 = when {
            s.isFull -> "Full"
            ms != null -> "${Fmt.duration(ms)} $label"
            else -> Fmt.status(s)
        }
        val charging = s.isCharging || s.isPlugged
        val accent = when {
            charging -> GREEN
            s.level <= 15 -> RED
            s.level <= 30 -> AMBER
            else -> GREEN
        }

        val track = LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setLength(degrees(359.9f)).setThickness(dp(7f)).setColor(argb(TRACK)).build()
            ).build()
        val ring = LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setLength(degrees((s.level.coerceIn(1, 100) * 3.6f).coerceAtMost(359.9f)))
                    .setThickness(dp(7f)).setColor(argb(accent)).build()
            ).build()

        val center = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(if (charging) "Charging" else "Bataria", 13f, MUTED, bold = false))
            .addContent(text("${s.level}%", 44f, 0xFFFFFFFF.toInt(), bold = true))
            .addContent(text(line2, 14f, accent, bold = false))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
            .addContent(text("${Fmt.temp(s.temperatureC, store.fahrenheit)} · ${Fmt.power(s)}", 12f, MUTED, bold = false))
            .build()

        val open = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build()
                    ).build()
            ).build()

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(open)
                    .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(6f)).build())
                    .build()
            )
            .addContent(track)
            .addContent(ring)
            .addContent(center)
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(10 * 60_000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build())

    private fun text(value: String, size: Float, color: Int, bold: Boolean) =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(1)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .setColor(argb(color))
                    .build()
            ).build()

    companion object {
        private const val RES_VERSION = "1"
        const val GREEN = 0xFF93D69F.toInt()
        const val AMBER = 0xFFF3C46B.toInt()
        const val RED = 0xFFFFB4AB.toInt()
        const val TRACK = 0x33AEF2BA
        const val MUTED = 0xFFC1C9BF.toInt()
    }
}

/** Watch-face complication: level as a ring (ranged) or "82%" / time left as short text. */
class BatteryComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val s = BatteryReader.read(this, Prefs(this))
        val store = WearStore(this)
        val (_, ms) = TimeLeft.estimate(s, store.history())
        val title = ms?.let { Fmt.shortDuration(it) }
        return build(request.complicationType, s.level, title)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, 82, "14h")

    private fun build(type: ComplicationType, level: Int, title: String?): ComplicationData? {
        val pct = PlainComplicationText.Builder("$level%").build()
        val desc = PlainComplicationText.Builder("Battery $level%").build()
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_stat_battery)).build()
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(level.toFloat(), 0f, 100f, desc)
                    .setText(pct)
                    .setMonochromaticImage(icon)
                    .setTapAction(tap)
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(pct, desc)
                    .apply { title?.let { setTitle(PlainComplicationText.Builder(it).build()) } }
                    .setMonochromaticImage(icon)
                    .setTapAction(tap)
                    .build()
            else -> null
        }
    }
}
