package com.example.ui.sheets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.NotepadSheet
import com.example.data.model.ShareFormat
import com.example.data.model.SheetType
import com.example.data.model.Track
import com.example.ui.components.BlueprintBottomSheet
import com.example.ui.components.formatTimeMs
import com.example.ui.theme.GaeguFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalBlueprintTokens

private enum class ShareBackdrop(val label: String) {
    PAPER("PAPER GRID"),
    NAVY("DEEP NAVY"),
    DRAWING("SAVED SKETCH"),
    PHOTO("PHOTO")
}

@Composable
fun ShareToSocialsSheet(
    track: Track,
    defaultFormat: ShareFormat,
    savedDrawings: List<NotepadSheet>,
    noteExcerpt: String,
    onCopyCaption: (String) -> Unit,
    onDownloadPng: (Bitmap) -> Unit,
    onSharePng: (Bitmap, String) -> Unit,
    onExportAudioClip: (Track) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    val context = LocalContext.current

    var format by remember { mutableStateOf(defaultFormat) }
    var backdrop by remember { mutableStateOf(ShareBackdrop.PAPER) }
    var selectedDrawingId by remember { mutableStateOf(savedDrawings.firstOrNull()?.id) }
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoDarken by remember { mutableFloatStateOf(0.45f) }

    val lyricLines = remember(track.lyrics) {
        track.lyrics.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }
    val selectedLines = remember(track.id) {
        mutableStateListOf<Int>().apply {
            if (lyricLines.isNotEmpty()) {
                add(0)
                if (lyricLines.size > 1) add(1)
            }
        }
    }

    val totalSec = (track.durationMs / 1000f).coerceAtLeast(1f)
    var timeRange by remember(track.id) { mutableStateOf(0f..minOf(totalSec, 15f)) }
    var includeTimeRange by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()?.let { bmp ->
                photoBitmap = bmp
                backdrop = ShareBackdrop.PHOTO
            }
        }
    }

    val activeDrawingBitmap = remember(backdrop, selectedDrawingId, savedDrawings) {
        if (backdrop == ShareBackdrop.DRAWING) {
            val sheet = savedDrawings.firstOrNull { it.id == selectedDrawingId } ?: savedDrawings.firstOrNull()
            sheet?.imageBase64?.takeIf { it.isNotBlank() }?.let { b64 ->
                runCatching {
                    val bytes = Base64.decode(b64, Base64.NO_WRAP)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
        } else null
    }

    val selectedSnippet = remember(selectedLines.toList(), lyricLines, noteExcerpt) {
        if (selectedLines.isNotEmpty()) {
            selectedLines.sorted().mapNotNull { lyricLines.getOrNull(it) }.joinToString("\n")
        } else {
            noteExcerpt.trim()
        }
    }

    val captionText = buildString {
        append("${track.title} — ${track.artist.ifBlank { "Ensis" }}")
        if (selectedSnippet.isNotBlank()) {
            append("\n\n\"$selectedSnippet\"")
        }
        if (track.tags.isNotEmpty()) {
            append("\n\n#${track.tags.joinToString(" #")}")
        }
    }

    fun buildRenderBitmap(): Bitmap {
        val bgImage = when (backdrop) {
            ShareBackdrop.PHOTO -> photoBitmap
            ShareBackdrop.DRAWING -> activeDrawingBitmap
            else -> null
        }
        return renderShareCardBitmap(
            context = context,
            format = format,
            isNavyBackdrop = backdrop == ShareBackdrop.NAVY,
            backdropImage = bgImage,
            darkenAlpha = if (backdrop == ShareBackdrop.PHOTO) photoDarken else if (backdrop == ShareBackdrop.DRAWING) 0.15f else 0f,
            track = track,
            snippet = selectedSnippet,
            timeRangeText = if (includeTimeRange) {
                "${formatTimeMs((timeRange.start * 1000).toLong())} — ${formatTimeMs((timeRange.endInclusive * 1000).toLong())}"
            } else null
        )
    }

    BlueprintBottomSheet(
        title = "SHARE TO SOCIALS",
        subtitle = "Song info plus a notepad or lyric section",
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Live Preview Card
            val previewRatio = format.width.toFloat() / format.height.toFloat()
            val isDarkCard = backdrop == ShareBackdrop.NAVY || (backdrop == ShareBackdrop.PHOTO && photoBitmap != null)
            val cardBg = if (backdrop == ShareBackdrop.NAVY) Color(0xFF14213D) else Color(0xFFF8F6F0)
            val cardInk = if (isDarkCard) Color(0xFFF4F1EA) else Color(0xFF14213D)
            val cardSub = if (isDarkCard) Color(0xFFB5C4DF) else Color(0xFF6B7280)
            val gold = Color(0xFFC9A227)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.paperBg)
                    .padding(horizontal = if (format == ShareFormat.STORY_9_16) 56.dp else 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(previewRatio)
                        .background(cardBg)
                        .border(2.dp, tokens.border)
                        .clipToBounds()
                ) {
                    val previewPhoto = when (backdrop) {
                        ShareBackdrop.PHOTO -> photoBitmap
                        ShareBackdrop.DRAWING -> activeDrawingBitmap
                        else -> null
                    }
                    if (previewPhoto != null) {
                        Image(
                            bitmap = previewPhoto.asImageBitmap(),
                            contentDescription = "Card background",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (backdrop == ShareBackdrop.PHOTO) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = photoDarken))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFF8F6F0).copy(alpha = 0.52f))
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "DAUDIO BY ENSIS",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    letterSpacing = 2.sp,
                                    color = cardSub
                                )
                                if (includeTimeRange) {
                                    Text(
                                        text = "${formatTimeMs((timeRange.start * 1000).toLong())}–${formatTimeMs((timeRange.endInclusive * 1000).toLong())}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = gold
                                    )
                                }
                            }
                            Text(
                                text = track.title,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = cardInk,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist.ifBlank { "Studio Session" },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = cardSub
                            )
                        }

                        if (selectedSnippet.isNotBlank()) {
                            Text(
                                text = selectedSnippet,
                                fontFamily = GaeguFontFamily,
                                fontSize = 17.sp,
                                lineHeight = 21.sp,
                                color = cardInk,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            HorizontalDivider(color = cardSub.copy(alpha = 0.4f))
                            Text(
                                text = "ALBUM: ${track.album.ifBlank { "Single" }}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 9.sp,
                                color = cardSub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (track.tags.isNotEmpty()) {
                                Text(
                                    text = "TAGS: ${track.tags.joinToString(", ")}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    color = if (isDarkCard) gold else tokens.tagBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Format Picker
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("FORMAT", fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, letterSpacing = 1.6.sp, color = tokens.inkMuted)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShareFormat.entries.forEach { fmt ->
                        val selected = fmt == format
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .background(if (selected) tokens.inkPrimary else tokens.cardBg)
                                .border(1.5.dp, tokens.border)
                                .clickable { format = fmt },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${fmt.label} ${fmt.ratioLabel}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = if (selected) tokens.cardBg else tokens.inkPrimary
                            )
                        }
                    }
                }
            }

            // Background Picker
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("BACKGROUND", fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, letterSpacing = 1.6.sp, color = tokens.inkMuted)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShareBackdrop.entries.forEach { option ->
                        val selected = option == backdrop
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .background(if (selected) tokens.inkPrimary else tokens.cardBg)
                                .border(1.dp, tokens.border)
                                .clickable {
                                    if (option == ShareBackdrop.PHOTO) {
                                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    } else {
                                        backdrop = option
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (option) {
                                    ShareBackdrop.PAPER -> "PAPER"
                                    ShareBackdrop.NAVY -> "NAVY"
                                    ShareBackdrop.DRAWING -> "SKETCH"
                                    ShareBackdrop.PHOTO -> "PHOTO"
                                },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = if (selected) tokens.cardBg else tokens.inkPrimary
                            )
                        }
                    }
                }

                if (backdrop == ShareBackdrop.PHOTO && photoBitmap != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("DARKEN", fontFamily = JetBrainsMonoFontFamily, fontSize = 9.sp, color = tokens.inkMuted)
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = photoDarken,
                            onValueChange = { photoDarken = it },
                            valueRange = 0f..0.8f,
                            colors = SliderDefaults.colors(thumbColor = tokens.gold, activeTrackColor = tokens.inkPrimary),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (backdrop == ShareBackdrop.DRAWING && savedDrawings.isEmpty()) {
                    Text(
                        text = "Save a sketch in DRAW mode first to use it as a backdrop.",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = tokens.inkMuted
                    )
                }
            }

            // Song Section Lyric Line Picker
            if (lyricLines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SONG SECTION (TAP LYRIC LINES)", fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, letterSpacing = 1.4.sp, color = tokens.inkMuted)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        lyricLines.take(10).forEachIndexed { idx, line ->
                            val isSelected = selectedLines.contains(idx)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) tokens.gold.copy(alpha = 0.2f) else tokens.cardBg)
                                    .border(1.dp, if (isSelected) tokens.gold else tokens.gridMajor)
                                    .clickable {
                                        if (isSelected) selectedLines.remove(idx) else selectedLines.add(idx)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = line,
                                    fontFamily = GaeguFontFamily,
                                    fontSize = 16.sp,
                                    color = tokens.inkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = tokens.gold, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Optional Time Range Stamp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.cardBg)
                    .border(1.dp, tokens.border)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeTimeRange = !includeTimeRange },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("INCLUDE TIME RANGE STAMP", fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, color = tokens.inkPrimary)
                    Text(
                        text = if (includeTimeRange) "ON" else "OFF",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (includeTimeRange) tokens.gold else tokens.inkMuted
                    )
                }
                if (includeTimeRange && totalSec > 1f) {
                    RangeSlider(
                        value = timeRange,
                        onValueChange = { timeRange = it },
                        valueRange = 0f..totalSec,
                        colors = SliderDefaults.colors(thumbColor = tokens.gold, activeTrackColor = tokens.inkPrimary)
                    )
                }
            }

            // Action Buttons: COPY | DOWNLOAD | SHARE | AUDIO CLIP
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onCopyCaption(captionText) },
                    shape = RectangleShape,
                    border = BorderStroke(1.5.dp, tokens.border),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = tokens.inkPrimary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("COPY", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = tokens.inkPrimary)
                }
                OutlinedButton(
                    onClick = { onDownloadPng(buildRenderBitmap()) },
                    shape = RectangleShape,
                    border = BorderStroke(1.5.dp, tokens.border),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = tokens.inkPrimary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SAVE PNG", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = tokens.inkPrimary)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onExportAudioClip(track) },
                    shape = RectangleShape,
                    border = BorderStroke(1.5.dp, tokens.border),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(0.85f).height(48.dp)
                ) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = tokens.inkPrimary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AUDIO", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = tokens.inkPrimary)
                }
                Button(
                    onClick = { onSharePng(buildRenderBitmap(), captionText) },
                    shape = RectangleShape,
                    border = BorderStroke(1.5.dp, tokens.border),
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.gold, contentColor = tokens.goldText),
                    modifier = Modifier
                        .weight(1.35f)
                        .height(48.dp)
                        .testTag("share_social_png_button")
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SHARE CARD", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
                }
            }
        }
    }
}

private fun renderShareCardBitmap(
    context: Context,
    format: ShareFormat,
    isNavyBackdrop: Boolean,
    backdropImage: Bitmap?,
    darkenAlpha: Float,
    track: Track,
    snippet: String,
    timeRangeText: String?
): Bitmap {
    val w = format.width
    val h = format.height
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)

    val paperColor = if (isNavyBackdrop) 0xFF14213D.toInt() else 0xFFF4F1EA.toInt()
    val gridColor = if (isNavyBackdrop) 0xFF223863.toInt() else 0xFFE3DDD0.toInt()
    val borderColor = if (isNavyBackdrop) 0xFFE8E2D0.toInt() else 0xFF14213D.toInt()
    val inkColor = if (isNavyBackdrop || backdropImage != null && darkenAlpha > 0.25f) 0xFFF4F1EA.toInt() else 0xFF14213D.toInt()
    val subColor = if (isNavyBackdrop || backdropImage != null && darkenAlpha > 0.25f) 0xFFB5C4DF.toInt() else 0xFF6B7280.toInt()
    val goldColor = 0xFFC9A227.toInt()
    val tagColor = if (isNavyBackdrop || backdropImage != null && darkenAlpha > 0.25f) goldColor else 0xFF1B3A8A.toInt()

    canvas.drawColor(paperColor)

    if (backdropImage != null) {
        val srcRatio = backdropImage.width.toFloat() / backdropImage.height.toFloat()
        val dstRatio = w.toFloat() / h.toFloat()
        val srcRect = if (srcRatio > dstRatio) {
            val cropW = (backdropImage.height * dstRatio).toInt()
            val left = (backdropImage.width - cropW) / 2
            Rect(left, 0, left + cropW, backdropImage.height)
        } else {
            val cropH = (backdropImage.width / dstRatio).toInt()
            val top = (backdropImage.height - cropH) / 2
            Rect(0, top, backdropImage.width, top + cropH)
        }
        canvas.drawBitmap(backdropImage, srcRect, Rect(0, 0, w, h), null)
        val overlayColor = if (darkenAlpha > 0.2f) android.graphics.Color.BLACK else 0xFFF8F6F0.toInt()
        val overlayAlpha = if (darkenAlpha > 0.2f) (darkenAlpha * 255).toInt() else 130
        val overlayPaint = Paint().apply {
            color = overlayColor
            alpha = overlayAlpha
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), overlayPaint)
    } else {
        // Drafting grid lines
        val gridPaint = Paint().apply {
            color = gridColor
            strokeWidth = 2f
        }
        val step = 54f
        var x = step
        while (x < w) {
            canvas.drawLine(x, 0f, x, h.toFloat(), gridPaint)
            x += step
        }
        var y = step
        while (y < h) {
            canvas.drawLine(0f, y, w.toFloat(), y, gridPaint)
            y += step
        }
    }

    // Outer drafting frame + corner ticks
    val margin = 64f
    val framePaint = Paint().apply {
        color = borderColor
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    canvas.drawRect(margin, margin, w - margin, h - margin, framePaint)

    val monoTypeface = runCatching { ResourcesCompat.getFont(context, R.font.jetbrains_mono) }.getOrNull() ?: Typeface.MONOSPACE
    val handTypeface = runCatching { ResourcesCompat.getFont(context, R.font.gaegu) }.getOrNull() ?: Typeface.SERIF

    val pad = margin + 56f
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = monoTypeface
        color = subColor
        textSize = 30f
        letterSpacing = 0.2f
    }
    canvas.drawText("DAUDIO BY ENSIS", pad, pad + 32f, labelPaint)

    if (timeRangeText != null) {
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(monoTypeface, Typeface.BOLD)
            color = goldColor
            textSize = 28f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(timeRangeText, w - pad, pad + 32f, timePaint)
    }

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(monoTypeface, Typeface.BOLD)
        color = inkColor
        textSize = 68f
    }
    canvas.drawText(track.title.take(24), pad, pad + 126f, titlePaint)

    val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = monoTypeface
        color = subColor
        textSize = 36f
    }
    canvas.drawText(track.artist.ifBlank { "daudio by Ensis" }, pad, pad + 184f, artistPaint)

    // Snippet lyrics
    if (snippet.isNotBlank()) {
        val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = handTypeface
            color = inkColor
            textSize = 54f
        }
        var ly = h * 0.44f
        snippet.lines().take(6).forEach { line ->
            canvas.drawText(line.take(40), pad, ly, lyricPaint)
            ly += 70f
        }
    }

    // Bottom metadata
    val footerY = h - pad - 88f
    val divPaint = Paint().apply {
        color = subColor
        alpha = 110
        strokeWidth = 2.5f
    }
    canvas.drawLine(pad, footerY, w - pad, footerY, divPaint)

    val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = monoTypeface
        color = subColor
        textSize = 28f
    }
    canvas.drawText("ALBUM: ${track.album.ifBlank { "Single" }}", pad, footerY + 48f, metaPaint)

    if (track.tags.isNotEmpty()) {
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = monoTypeface
            color = tagColor
            textSize = 28f
        }
        canvas.drawText("TAGS: ${track.tags.joinToString(", ")}", pad, footerY + 92f, tagPaint)
    }

    val legalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = monoTypeface
        color = subColor
        textSize = 22f
        textAlign = Paint.Align.RIGHT
    }
    canvas.drawText("© Ensis Vi. Dauid", w - pad, footerY + 48f, legalPaint)

    return bmp
}
