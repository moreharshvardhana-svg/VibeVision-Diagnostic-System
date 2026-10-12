# VibeVision: Optical Vibration & Diagnostic System

**VibeVision** is a high-performance native Android application engineered for real-time optical vibration diagnostics and micro-motion inspection, utilizing **Posy's Motion Extraction technique** running entirely on the GPU via **OpenGL ES 2.0** shaders and **Android Jetpack CameraX**.

---

## ⚡ Technical Highlights

- **Pure GPU Real-Time Pipeline**: Camera frames stream directly into an OpenGL hardware `SurfaceTexture` (OES) and are buffered in an offscreen **FBO Texture Ring Buffer** (Δt = 1 to 5 frames). Zero CPU memory copies ensure rock-solid 30–60 FPS rendering.
- **Posy Motion Extraction**:
  $$\text{diff} = (\text{current}_{\text{rgb}} - \text{delayed}_{\text{rgb}}) \times \text{gain} + 0.5$$
  - Oscillations around the 50% neutral gray baseline make imperceptible mechanical vibrations and acoustic micro-movements visible to the naked eye.
- **High-Contrast Turbo Heatmap**:
  - Absolute differential amplitude is mapped onto Google's vibrant **Turbo colormap** polynomial for thermal-style vibration anomaly localization.
- **Real-Time Instrumentation & Diagnostics**:
  - Dual-stream CameraX `ImageAnalysis` computes the **Vibration Intensity Index (VII)** from sampled spatial luminance deltas across frames.
  - Automatic **Anomaly Detection** triggering `NORMAL / STABLE` vs. `VIBRATION ANOMALY DETECTED` alerts.
- **Dark Industrial HUD**:
  - Precision sliders for Amplification Gain ($1.0\times$ to $25.0\times$) and Temporal Delay ($\Delta t = 1$ to $5$ frames).
  - Ambient noise zero-calibration button to cancel out handheld device movement.
  - Optical target reticle crosshairs, flash/torch illumination, and front/rear camera switcher.

---

## 🔬 Core Shader Logic

The fragment shader (`ShaderUtils.kt`) executes the core mathematical processing on every pixel at native screen resolution:

```glsl
precision mediump float;
varying vec2 vTexCoord;

uniform sampler2D uCurrentFrame;
uniform sampler2D uDelayedFrame;
uniform float uGain;
uniform int uDisplayMode; // 0 = Posy 50% Gray, 1 = Turbo Heatmap, 2 = Raw Camera Feed

// Turbo colormap approximation
vec3 turboColormap(float x) {
    x = clamp(x, 0.0, 1.0);
    const vec4 kRedVec4   = vec4(0.13572138, 4.61539260, -42.66032258, 132.13108234);
    const vec4 kGreenVec4 = vec4(0.09140261, 2.19418839, 4.84296658, -14.18503333);
    const vec4 kBlueVec4  = vec4(0.10667330, 12.64194608, -60.58204836, 110.36276771);
    const vec2 kRedVec2   = vec2(-152.94239396, 59.28637943);
    const vec2 kGreenVec2 = vec2(4.27729857, 2.82956604);
    const vec2 kBlueVec2  = vec2(-89.90310912, 27.34824973);

    vec4 v4 = vec4(1.0, x, x * x, x * x * x);
    vec2 v2 = v4.zw * v4.z;

    return vec3(
        dot(v4, kRedVec4)   + dot(v2, kRedVec2),
        dot(v4, kGreenVec4) + dot(v2, kGreenVec2),
        dot(v4, kBlueVec4)  + dot(v2, kBlueVec2)
    );
}

void main() {
    vec4 current = texture2D(uCurrentFrame, vTexCoord);

    if (uDisplayMode == 2) {
        // Raw camera feed
        gl_FragColor = current;
        return;
    }

    vec4 delayed = texture2D(uDelayedFrame, vTexCoord);

    if (uDisplayMode == 0) {
        // Posy Motion Extraction baseline:
        vec3 diff = (current.rgb - delayed.rgb) * uGain + vec3(0.5);
        gl_FragColor = vec4(clamp(diff, 0.0, 1.0), 1.0);
    } else {
        // High-contrast Heatmap (Turbo colormap):
        vec3 absDiff = abs(current.rgb - delayed.rgb) * uGain;
        float motionMag = dot(absDiff, vec3(0.299, 0.587, 0.114));
        motionMag = clamp(motionMag, 0.0, 1.0);

        vec3 heatColor = turboColormap(motionMag);
        float ambientLuma = dot(current.rgb, vec3(0.299, 0.587, 0.114)) * 0.15;
        gl_FragColor = vec4(clamp(heatColor + vec3(ambientLuma), 0.0, 1.0), 1.0);
    }
}
```

---

## 🛠️ Project Structure

```
VibeVision_Prototype_Field_Project/
├── app/
│   ├── build.gradle.kts                     # App module dependencies (CameraX, Compose, Coroutines)
│   ├── proguard-rules.pro                   # R8 / ProGuard rules for CameraX and OpenGL
│   └── src/main/
│       ├── AndroidManifest.xml              # Permissions (CAMERA, Flash, GL ES 2.0)
│       ├── java/com/vibevision/app/
│       │   ├── MainActivity.kt              # Entry point Activity (Keeps screen on, edge-to-edge)
│       │   ├── model/
│       │   │   ├── DisplayMode.kt           # Modes: Posy Gray, Turbo Heatmap, Raw Feed
│       │   │   └── DiagnosticState.kt       # Real-time telemetry data state
│       │   ├── camera/
│       │   │   ├── CameraManager.kt         # CameraX 60 FPS setup, lifecycle, SurfaceProvider
│       │   │   └── VibrationAnalyzer.kt     # Subsampled Luminance Y-plane differential analysis
│       │   ├── gl/
│       │   │   ├── GLVibeSurfaceView.kt     # Custom GLSurfaceView wrapper
│       │   │   ├── VibeRenderer.kt          # OpenGL ES 2.0 two-pass renderer
│       │   │   ├── FboRingBuffer.kt         # Cyclical texture FBO ring buffer
│       │   │   └── ShaderUtils.kt           # GLSL shaders & quad buffer helpers
│       │   └── ui/
│       │       ├── VibeVisionScreen.kt      # Main Compose HUD & View interop
│       │       ├── components/
│       │       │   ├── TelemetryBadge.kt    # FPS meter, Vibration Index gauge, Health status
│       │       │   ├── ModeSelector.kt      # Posy / Heatmap / Raw mode toggle
│       │       │   ├── ControlsOverlay.kt   # Gain slider, delay slider, presets, zero-noise
│       │       │   └── ReticleOverlay.kt    # Optical targeting reticle & crosshairs
│       │       └── theme/
│       │           ├── Color.kt             # Dark industrial palette
│       │           ├── Type.kt              # Monospace telemetry typography
│       │           └── Theme.kt             # Material 3 dark theme
│       └── res/                             # Values, themes, strings, launcher icons
├── build.gradle.kts                         # Root Gradle build script
├── settings.gradle.kts                      # Module settings and repositories
├── gradle.properties                        # JVM & AndroidX properties
└── gradlew / gradlew.bat                    # Gradle wrapper scripts
```

---

## 🚀 Building & Running

1. Open **Android Studio** (Hedgehog, Iguana, Jellyfish, Koala, Ladybug or newer).
2. Select **Open** and select the `VibeVision_Prototype_Field_Project` directory.
3. Allow Gradle to synchronize dependencies.
4. Connect an Android device (Android 7.0 / API 24+) or launch an emulator with camera support.
5. Click **Run** (`Shift + F10`).

---

## 📱 In-App Controls Guide

| Control | Description |
|---|---|
| **Mode Selector** | Switch between Posy 50% Neutral Gray, Turbo Heatmap, and Raw Camera stream |
| **Gain Slider ($1.0\times - 25.0\times$)** | Amplifies micro-oscillations. Ideal for detecting subtle structural or motor vibrations |
| **Frame Delay ($\Delta t = 1 - 5$)** | Sets temporal distance. Low values ($1-2$) highlight high-frequency vibration; high values ($3-5$) capture slower mechanical sway |
| **Zero Ambient Noise** | Calibrates baseline sensor offset to nullify hand tremor or tripod movement |
| **Reticle Icon** | Toggles diagnostic crosshairs for precision targeting |
| **Torch / Flash** | Illuminates mechanical bays, engine compartments, or dark machine housings |
| **Tap to Focus** | Tap anywhere on the live video stream to focus camera optics on specific shafts or joints |
