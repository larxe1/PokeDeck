package com.pogooverlay.app.core.scan

import android.graphics.Bitmap
import com.pogooverlay.app.core.ocr.OcrLine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AppraisalIvParser {

    private data class LabelTriple(
        val atk: OcrLine,
        val def: OcrLine,
        val hp: OcrLine,
        val score: Float
    )

    private data class BarCandidateRow(
        val scanY: Int,
        val trackStartX: Int,
        val trackEndX: Int,
        val trackPixelCount: Int,
        val rowPixels: IntArray
    )

    fun parse(lines: List<OcrLine>, bitmap: Bitmap?): AppraisalIvParseResult? {
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return null
        }
        return parseFromPixels(bitmap.width, bitmap.height, lines, bitmap::getPixel)
    }

    fun parseFromPixels(
        width: Int,
        height: Int,
        lines: List<OcrLine>,
        pixelAt: (x: Int, y: Int) -> Int
    ): AppraisalIvParseResult? {
        if (width < 30 || height < 30 || lines.isEmpty()) return null

        val anchors = findAppraisalAnchors(lines) ?: return null

        val atkRow = scanBarBelowLabel(width, height, anchors.atk, pixelAt) ?: return null
        val defRow = scanBarBelowLabel(width, height, anchors.def, pixelAt) ?: return null
        val hpRow = scanBarBelowLabel(width, height, anchors.hp, pixelAt) ?: return null

        val unifiedStartX = medianOfThree(atkRow.trackStartX, defRow.trackStartX, hpRow.trackStartX)
        val unifiedEndX = medianOfThree(atkRow.trackEndX, defRow.trackEndX, hpRow.trackEndX)
        if (unifiedEndX - unifiedStartX < 15) return null

        val (atkIv, atkPink) = computeIvFromTrackPixels(atkRow.rowPixels, unifiedStartX, unifiedEndX)
        val (defIv, defPink) = computeIvFromTrackPixels(defRow.rowPixels, unifiedStartX, unifiedEndX)
        val (hpIv, hpPink) = computeIvFromTrackPixels(hpRow.rowPixels, unifiedStartX, unifiedEndX)

        val barDebugs = listOf(
            IvBarScanDebugInfo(
                label = "Attack",
                anchorLine = anchors.atk,
                scanY = atkRow.scanY,
                trackStartX = unifiedStartX,
                trackEndX = unifiedEndX,
                computedIv = atkIv,
                isMaxedPink = atkPink
            ),
            IvBarScanDebugInfo(
                label = "Defense",
                anchorLine = anchors.def,
                scanY = defRow.scanY,
                trackStartX = unifiedStartX,
                trackEndX = unifiedEndX,
                computedIv = defIv,
                isMaxedPink = defPink
            ),
            IvBarScanDebugInfo(
                label = "HP",
                anchorLine = anchors.hp,
                scanY = hpRow.scanY,
                trackStartX = unifiedStartX,
                trackEndX = unifiedEndX,
                computedIv = hpIv,
                isMaxedPink = hpPink
            )
        )

        val minLabelLeft = minOf(anchors.atk.normalizedLeft, anchors.def.normalizedLeft, anchors.hp.normalizedLeft)
        val cardLeft = min(minLabelLeft - 0.03f, (unifiedStartX.toFloat() / width) - 0.03f).coerceIn(0f, 1f)
        val cardRight = ((unifiedEndX.toFloat() / width) + 0.04f).coerceIn(cardLeft, 1f)
        val cardTop = (anchors.atk.normalizedTop - 0.05f).coerceIn(0f, 1f)
        val cardBottom = max(
            anchors.hp.normalizedBottom + 0.02f,
            (hpRow.scanY.toFloat() / height) + 0.03f
        ).coerceIn(cardTop, 1f)

        return AppraisalIvParseResult(
            ivSpread = IvSpread(atk = atkIv, def = defIv, sta = hpIv),
            attackAnchor = anchors.atk,
            defenseAnchor = anchors.def,
            hpAnchor = anchors.hp,
            barScanlines = barDebugs,
            appraisalCardBoundsNormalized = NormalizedRect(
                left = cardLeft,
                top = cardTop,
                right = cardRight,
                bottom = cardBottom
            )
        )
    }

    fun computeIvFromTrackPixels(
        rowArgb: IntArray,
        trackStartX: Int,
        trackEndX: Int
    ): Pair<Int, Boolean> {
        if (rowArgb.isEmpty()) return 0 to false
        val start = trackStartX.coerceIn(0, rowArgb.lastIndex)
        val end = trackEndX.coerceIn(start, rowArgb.lastIndex)
        if (end <= start) return 0 to false

        var filledPixels = 0
        var pinkCount = 0
        var unfilledGrayPixels = 0

        for (x in start..end) {
            when (classifyPixel(rowArgb[x])) {
                PixelKind.PINK -> {
                    pinkCount++
                    filledPixels++
                }
                PixelKind.ORANGE -> {
                    filledPixels++
                }
                PixelKind.GRAY_TRACK -> {
                    unfilledGrayPixels++
                }
                PixelKind.OTHER -> {
                    // White divider gaps and card background are excluded from effective track width.
                }
            }
        }

        val effectiveTrackPixels = filledPixels + unfilledGrayPixels
        if (effectiveTrackPixels <= 0) return 0 to false

        if (pinkCount >= 10 && pinkCount >= filledPixels / 2 && unfilledGrayPixels <= 2) {
            return 15 to true
        }

        val iv = ((filledPixels.toFloat() / effectiveTrackPixels.coerceAtLeast(1).toFloat()) * 15f)
            .roundToInt()
            .coerceIn(0, 15)
        return iv to false
    }

    private fun findAppraisalAnchors(lines: List<OcrLine>): LabelTriple? {
        val lowerLines = lines.filter { it.normalizedTop >= 0.40f }
        if (lowerLines.size < 3) return null

        val atkCandidates = lowerLines.filter { isAttackLabel(it.text) }
        val defCandidates = lowerLines.filter { isDefenseLabel(it.text) }
        val hpCandidates = lowerLines.filter { isHpLabel(it.text) }

        if (atkCandidates.isEmpty() || defCandidates.isEmpty() || hpCandidates.isEmpty()) {
            return null
        }

        var bestTriple: LabelTriple? = null
        for (atk in atkCandidates) {
            for (def in defCandidates) {
                if (def.normalizedTop <= atk.normalizedTop) continue
                if (abs(atk.normalizedLeft - def.normalizedLeft) > MAX_LEFT_DELTA) continue

                for (hp in hpCandidates) {
                    if (hp.normalizedTop <= def.normalizedTop) continue
                    if (abs(def.normalizedLeft - hp.normalizedLeft) > MAX_LEFT_DELTA) continue
                    if (abs(atk.normalizedLeft - hp.normalizedLeft) > MAX_LEFT_DELTA) continue

                    val leftSpread = abs(atk.normalizedLeft - def.normalizedLeft) +
                        abs(def.normalizedLeft - hp.normalizedLeft)
                    val exactBonus =
                        (if (atk.text.trim().equals("Attack", ignoreCase = true)) 1f else 0f) +
                            (if (def.text.trim().equals("Defense", ignoreCase = true)) 1f else 0f) +
                            (if (hp.text.trim().equals("HP", ignoreCase = true)) 1f else 0f)
                    val score = exactBonus - leftSpread * 5f

                    if (bestTriple == null || score > bestTriple.score) {
                        bestTriple = LabelTriple(atk = atk, def = def, hp = hp, score = score)
                    }
                }
            }
        }
        return bestTriple
    }

    private fun scanBarBelowLabel(
        width: Int,
        height: Int,
        labelLine: OcrLine,
        pixelAt: (x: Int, y: Int) -> Int
    ): BarCandidateRow? {
        val labelBottom = (labelLine.normalizedBottom * height).roundToInt().coerceIn(0, height - 1)
        val labelHeightPx = (labelLine.normalizedHeight * height).roundToInt().coerceAtLeast(1)
        val extraSearch = (labelHeightPx * 1.6f).roundToInt().coerceAtLeast((height * 0.025f).roundToInt())
        val yMax = (labelBottom + extraSearch).coerceIn(labelBottom, height - 1)

        val searchStartX = ((labelLine.normalizedLeft - 0.04f) * width).roundToInt().coerceIn(0, width - 1)
        val searchEndX = (width * 0.55f).roundToInt().coerceIn(min(searchStartX + 10, width - 1), width - 1)
        if (searchEndX - searchStartX < 15) return null

        val maxGapPx = (width * 0.025f).roundToInt().coerceAtLeast(8)
        val minTrackPixels = (width * 0.05f).roundToInt().coerceIn(10, 15)

        var bestRow: BarCandidateRow? = null

        for (y in labelBottom..yMax) {
            val rowBuffer = IntArray(width)
            var inSeg = false
            var segStart = -1
            var segTrackCount = 0
            val segments = mutableListOf<Triple<Int, Int, Int>>() // start, end, count

            for (x in searchStartX..searchEndX) {
                val pixel = pixelAt(x, y)
                rowBuffer[x] = pixel
                val isTrack = classifyPixel(pixel) != PixelKind.OTHER
                if (isTrack) {
                    if (!inSeg) {
                        inSeg = true
                        segStart = x
                        segTrackCount = 1
                    } else {
                        segTrackCount++
                    }
                } else if (inSeg) {
                    segments.add(Triple(segStart, x - 1, segTrackCount))
                    inSeg = false
                }
            }
            if (inSeg) {
                segments.add(Triple(segStart, searchEndX, segTrackCount))
            }
            if (segments.isEmpty()) continue

            // Merge segments separated only by the two white divider gaps.
            var curStart = segments[0].first
            var curEnd = segments[0].second
            var curCount = segments[0].third

            var bestRunStart = curStart
            var bestRunEnd = curEnd
            var bestRunCount = curCount

            for (i in 1 until segments.size) {
                val (nextStart, nextEnd, nextCount) = segments[i]
                if (nextStart - curEnd - 1 <= maxGapPx) {
                    curEnd = nextEnd
                    curCount += nextCount
                } else {
                    if (curCount > bestRunCount) {
                        bestRunStart = curStart
                        bestRunEnd = curEnd
                        bestRunCount = curCount
                    }
                    curStart = nextStart
                    curEnd = nextEnd
                    curCount = nextCount
                }
            }
            if (curCount > bestRunCount) {
                bestRunStart = curStart
                bestRunEnd = curEnd
                bestRunCount = curCount
            }

            if (bestRunCount >= minTrackPixels && bestRunEnd > bestRunStart) {
                if (bestRow == null || bestRunCount > bestRow.trackPixelCount) {
                    bestRow = BarCandidateRow(
                        scanY = y,
                        trackStartX = bestRunStart,
                        trackEndX = bestRunEnd,
                        trackPixelCount = bestRunCount,
                        rowPixels = rowBuffer
                    )
                }
            }
        }

        return bestRow
    }

    private enum class PixelKind {
        PINK,
        ORANGE,
        GRAY_TRACK,
        OTHER
    }

    private fun classifyPixel(pixel: Int): PixelKind {
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF

        val isPink = r >= 190 &&
            g in 50..165 &&
            b in 60..175 &&
            (r - g) >= 55 &&
            (r - b) >= 35 &&
            (g - b) <= 20
        if (isPink) return PixelKind.PINK

        val isOrange = r >= 195 &&
            g in 105..215 &&
            b <= 125 &&
            (r - b) >= 70 &&
            r > g
        if (isOrange) return PixelKind.ORANGE

        val isGrayTrack = r in 175..238 &&
            g in 175..238 &&
            b in 175..238 &&
            abs(r - g) <= 14 &&
            abs(g - b) <= 14 &&
            abs(r - b) <= 14
        if (isGrayTrack) return PixelKind.GRAY_TRACK

        return PixelKind.OTHER
    }

    private fun isAttackLabel(rawText: String): Boolean {
        val upper = rawText.trim().uppercase()
        if (upper.length > 16 || upper.contains("/") || upper.contains("NEW")) return false
        return ATTACK_REGEX.containsMatchIn(upper)
    }

    private fun isDefenseLabel(rawText: String): Boolean {
        val upper = rawText.trim().uppercase()
        if (upper.length > 16 || upper.contains("/")) return false
        return DEFENSE_REGEX.containsMatchIn(upper)
    }

    private fun isHpLabel(rawText: String): Boolean {
        val upper = rawText.trim().uppercase()
        if (upper.length > 10 || upper.contains("/") || DIGIT_REGEX.containsMatchIn(upper)) return false
        return HP_REGEX.containsMatchIn(upper)
    }

    private fun medianOfThree(a: Int, b: Int, c: Int): Int {
        val arr = intArrayOf(a, b, c)
        arr.sort()
        return arr[1]
    }

    companion object {
        private const val MAX_LEFT_DELTA = 0.10f
        private val ATTACK_REGEX = Regex("""\bATTACK\b""")
        private val DEFENSE_REGEX = Regex("""\bDEFEN[SC]E\b""")
        private val HP_REGEX = Regex("""\bHP\b""")
        private val DIGIT_REGEX = Regex("""\d""")
    }
}
