package com.example.serenitydroid.model

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes

data class Persona(
    val level: Int,
    val name: String,
    val description: String,
    @param:DrawableRes val imageResId: Int,
    @param:DrawableRes val backgroundResId: Int,
    @param:ColorRes val thermoColorRes: Int,
    @param:ColorRes val fgColorRes: Int,
    val gpuLayers: Int,
    val modelAssetFileName: String, // Added field for the model asset file name
    val visionProjectorFileName: String? = null, // New field for mmproj
    val systemPrompt: String = ""
)
