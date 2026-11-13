package com.example.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object DemoAudioSynthesizer {

    private const val SAMPLE_RATE = 11025
    private const val LUT_SIZE = 4096
    private const val LUT_MASK = LUT_SIZE - 1

    private val SIN_LUT = FloatArray(LUT_SIZE) { idx ->
        sin(2.0 * PI * idx / LUT_SIZE).toFloat()
    }

    private fun fastSin(cycles: Double): Double {
        val index = ((cycles - kotlin.math.floor(cycles)) * LUT_SIZE).toInt() and LUT_MASK
        return SIN_LUT[index].toDouble()
    }

    suspend fun getOrCreateDemoFile(context: Context, demoKey: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "demos").apply { mkdirs() }
        val file = File(dir, "${demoKey}_v2.wav")
        if (file.exists() && file.length() > 1024) {
            return@withContext file
        }
        val durationSec = when (demoKey) {
            "ensis_vi" -> 26
            "drafting_room" -> 32
            "prussian_gold" -> 28
            else -> 26
        }
        val samples = synthesizeTrack(demoKey, durationSec)
        writeWavFile(file, samples, SAMPLE_RATE)
        file
    }

    private fun synthesizeTrack(demoKey: String, durationSec: Int): ShortArray {
        val totalSamples = durationSec * SAMPLE_RATE
        val buffer = ShortArray(totalSamples)

        val chords: List<DoubleArray>
        val bpm: Double
        val bassRoots: DoubleArray

        when (demoKey) {
            "ensis_vi" -> {
                bpm = 96.0
                chords = listOf(
                    doubleArrayOf(293.66, 349.23, 440.00, 523.25),
                    doubleArrayOf(233.08, 293.66, 349.23, 466.16),
                    doubleArrayOf(261.63, 349.23, 440.00, 523.25),
                    doubleArrayOf(261.63, 329.63, 392.00, 523.25)
                )
                bassRoots = doubleArrayOf(73.42, 58.27, 87.31, 65.41)
            }
            "drafting_room" -> {
                bpm = 84.0
                chords = listOf(
                    doubleArrayOf(196.00, 246.94, 293.66, 392.00),
                    doubleArrayOf(164.81, 246.94, 293.66, 392.00),
                    doubleArrayOf(261.63, 329.63, 392.00, 493.88),
                    doubleArrayOf(293.66, 369.99, 440.00, 587.33)
                )
                bassRoots = doubleArrayOf(98.00, 82.41, 65.41, 73.42)
            }
            else -> {
                bpm = 72.0
                chords = listOf(
                    doubleArrayOf(174.61, 220.00, 261.63, 329.63),
                    doubleArrayOf(146.83, 220.00, 261.63, 329.63),
                    doubleArrayOf(220.00, 261.63, 329.63, 392.00),
                    doubleArrayOf(196.00, 246.94, 329.63, 392.00)
                )
                bassRoots = doubleArrayOf(87.31, 73.42, 110.00, 98.00)
            }
        }

        val beatDuration = 60.0 / bpm
        val barDuration = beatDuration * 4.0
        val invSampleRate = 1.0 / SAMPLE_RATE

        for (i in 0 until totalSamples) {
            val t = i * invSampleRate
            val barIdx = ((t / barDuration).toInt()) % chords.size
            val barTime = t % barDuration
            val beatIdx = ((barTime / beatDuration).toInt()) % 4
            val beatTime = barTime % beatDuration

            val chord = chords[barIdx]
            val bass = bassRoots[barIdx]

            val padAttack = (barTime * 6.0).coerceAtMost(1.0)
            val padDecay = 1.0 / (1.0 + barTime * 0.65)
            val padEnv = padAttack * padDecay
            var pad = 0.0
            for (n in chord.indices) {
                val f = chord[n]
                pad += fastSin(f * t) * 0.14 + fastSin(f * 2.0 * t) * 0.03
            }

            val arpFreq = chord[beatIdx] * 2.0
            val arpEnv = if (beatTime < 0.025) beatTime * 40.0 else exp(-beatTime * 4.2)
            val arp = (fastSin(arpFreq * t) + 0.25 * fastSin(arpFreq * 2.0 * t)) * arpEnv * 0.18

            val bassEnv = if (beatTime < 0.04) beatTime * 25.0 else exp(-beatTime * 2.0)
            val bassTone = (fastSin(bass * t) + 0.3 * fastSin(bass * 2.0 * t)) * bassEnv * 0.28

            val clickEnv = if (beatTime < 0.06) {
                if (beatIdx % 2 == 0) exp(-beatTime * 60.0) else exp(-beatTime * 95.0) * 0.45
            } else 0.0
            val click = if (clickEnv > 0.001) fastSin((if (beatIdx == 0) 140.0 else 95.0) * t) * clickEnv * 0.16 else 0.0

            val masterFadeIn = (t / 0.4).coerceIn(0.0, 1.0)
            val masterFadeOut = ((durationSec - t) / 0.8).coerceIn(0.0, 1.0)
            val sampleVal = (pad * padEnv + arp + bassTone + click) * masterFadeIn * masterFadeOut

            buffer[i] = (sampleVal * Short.MAX_VALUE * 0.72).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return buffer
    }

    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int) {
        val dataSize = samples.size * 2
        val totalSize = 36 + dataSize
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(dataSize)
        }.array()

        val pcmBytes = ByteArray(dataSize)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)

        FileOutputStream(file).use { out ->
            out.write(header)
            out.write(pcmBytes)
        }
    }
}
