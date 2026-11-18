package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.model.LocationItem
import com.example.data.model.WeatherCondition
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream

private const val TAG = "DioramaGenerator"

data class DioramaResult(
    val bitmap: Bitmap,
    val promptUsed: String,
    val fromCache: Boolean
)

class DioramaGenerator(private val context: Context) {

    private val imageModel by lazy {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(AspectRatio.LANDSCAPE_16x9)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = config
        )
    }

    private val cacheDir: File by lazy {
        File(context.cacheDir, "dioramas").apply { mkdirs() }
    }

    suspend fun generateLandmarkCluster(
        location: LocationItem,
        condition: WeatherCondition,
        isDay: Boolean,
        forceRegenerate: Boolean = false
    ): DioramaResult {
        val dayPhase = if (isDay) "day" else "night"
        val cacheKey = "${location.cacheSlug}_${condition.name.lowercase()}_$dayPhase.png"
        val cacheFile = File(cacheDir, cacheKey)

        if (!forceRegenerate && cacheFile.exists()) {
            val cached = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(cacheFile.absolutePath)
            }
            if (cached != null) {
                return DioramaResult(
                    bitmap = cached,
                    promptUsed = buildPrompt(location, condition, isDay),
                    fromCache = true
                )
            }
        }

        val prompt = buildPrompt(location, condition, isDay)
        val bitmap = withTimeout(60_000L) {
            val response = imageModel.generateContent(prompt)
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            imagePart?.image
                ?: throw IllegalStateException("Gemini returned no 3D diorama image for ${location.name}.")
        }

        withContext(Dispatchers.IO) {
            try {
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Unable to cache diorama bitmap locally", e)
            }
        }

        return DioramaResult(
            bitmap = bitmap,
            promptUsed = prompt,
            fromCache = false
        )
    }

    private fun buildPrompt(
        location: LocationItem,
        condition: WeatherCondition,
        isDay: Boolean
    ): String {
        val placeLabel = listOfNotNull(
            location.name,
            location.admin1?.takeIf { it.isNotBlank() && it != location.name },
            location.country.takeIf { it.isNotBlank() }
        ).joinToString(", ")

        val lightingPhase = if (isDay) {
            "natural daytime illumination with soft architectural shadows"
        } else {
            "nighttime architectural diorama with warm glowing miniature building windows, tiny streetlamps, and cool moonlight"
        }

        return buildString {
            append("A wide 3D miniature horizontal cluster of iconic landmarks, monuments, and characteristic local buildings representing ")
            append(placeLabel)
            append(". ")
            append("Stylized tilt-shift architectural scale model arranged as a compact, elongated horizontal island skyline strip across the center of the frame, crafted from matte clay, fine plaster, brushed metal, and translucent glass textures. ")
            append("Weather and lighting: ")
            append(condition.promptAtmosphere)
            append(", ")
            append(lightingPhase)
            append(". ")
            append("Seamless expansive ")
            append(condition.promptSkyTone)
            append(" background surrounding the miniature cluster so it blends smoothly as a weather app backdrop. ")
            append("Ultra-clean architectural render, crisp depth of field, centered horizontal composition, no text, no labels, no UI elements, no watermarks.")
        }
    }
}
