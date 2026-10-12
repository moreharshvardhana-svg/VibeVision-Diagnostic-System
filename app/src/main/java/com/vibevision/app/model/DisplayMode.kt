package com.vibevision.app.model

/**
 * Optical motion visualization modes supported by the OpenGL ES fragment shader.
 */
enum class DisplayMode(
    val shaderModeId: Int,
    val title: String,
    val shortName: String,
    val description: String
) {
    POSY_GRAY(
        shaderModeId = 0,
        title = "Posy 50% Neutral Gray",
        shortName = "Posy Gray",
        description = "Bidirectional motion extraction around 50% gray baseline"
    ),
    HEATMAP(
        shaderModeId = 1,
        title = "High-Contrast Turbo Heatmap",
        shortName = "Heatmap",
        description = "Vibration amplitude mapped onto high-contrast Turbo colormap"
    ),
    RAW_FEED(
        shaderModeId = 2,
        title = "Raw Optical Stream",
        shortName = "Raw Feed",
        description = "Direct unamplified camera sensor passthrough"
    )
}
