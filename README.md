# VibeVision: Non-Contact Optical Vibration & Diagnostic System

**Mobile Computer Vision & Industrial Predictive Maintenance Platform**  
**Build Version:** v1.0.0 (Field Evaluation Build)

---

## ⚡ System Overview & Engineering Concept

**VibeVision** transforms commercial mobile hardware into a precision, non-contact optical vibrometer. By evaluating temporal frame differentials against a calibrated null baseline and amplifying sub-pixel displacement, the system visualizes microscopic structural flex, mechanical unbalance, and bearing chatter in real-time—eliminating the hazards and capital expenditure of physical contact accelerometers.

---

## 👥 Project Team & Division of Engineering

- **Harshvardhan**: Backend Architecture, Core Frame Differencing Pipelines & System Stability
- **ShivaGanesh**: Diagnostic Algorithms, Spectral Frequency Modeling & Signal Processing
- **Pritesh**: UI/UX Architecture, Field Interaction Design & Mobile Experience
- **Darshan**: Commercial Feasibility, Industrial Cost-Benefit Analysis & Project Strategy

---

## 🔬 Core Shader Engine: Sub-Pixel Motion Amplifier

The GLSL fragment shader (`ShaderUtils.kt`) executes real-time differential frame processing directly on the GPU across four visualization modes:

```glsl
precision mediump float;
varying vec2 vTexCoord;

uniform sampler2D uCurrentFrame;
uniform sampler2D uDelayedFrame;
uniform float uGain;
uniform int uDisplayMode; // 0 = Null Baseline, 1 = Spectral Heatmap, 2 = Dynamic Edge Accent, 3 = Raw Optical Feed

// Google's Turbo Colormap polynomial approximation
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

    if (uDisplayMode == 3) {
        // Mode 3: Raw Optical Feed (Unprocessed direct sensor feed)
        gl_FragColor = current;
        return;
    }

    vec4 delayed = texture2D(uDelayedFrame, vTexCoord);

    if (uDisplayMode == 0) {
        // Mode 0: Differential Null Baseline (50% neutral gray baseline difference)
        vec3 diff = (current.rgb - delayed.rgb) * uGain + vec3(0.5);
        gl_FragColor = vec4(clamp(diff, 0.0, 1.0), 1.0);
    } else if (uDisplayMode == 1) {
        // Mode 1: Spectral Turbo Heatmap (Color-mapped absolute motion gradients)
        vec3 absDiff = abs(current.rgb - delayed.rgb) * uGain;
        float motionMag = dot(absDiff, vec3(0.299, 0.587, 0.114));
        motionMag = clamp(motionMag, 0.0, 1.0);

        vec3 heatColor = turboColormap(motionMag);
        float ambientLuma = dot(current.rgb, vec3(0.299, 0.587, 0.114)) * 0.15;
        vec3 finalColor = heatColor + vec3(ambientLuma);
        gl_FragColor = vec4(clamp(finalColor, 0.0, 1.0), 1.0);
    } else {
        // Mode 2: Dynamic Edge Accent (Darkened raw background with glowing motion fringes)
        vec3 absDiff = abs(current.rgb - delayed.rgb) * uGain;
        float motionMag = dot(absDiff, vec3(0.299, 0.587, 0.114));
        vec3 fringeColor = vec3(0.0, 0.898, 1.0) * clamp(motionMag * 2.5, 0.0, 1.0);
        vec3 composite = current.rgb * 0.25 + fringeColor;
        gl_FragColor = vec4(clamp(composite, 0.0, 1.0), 1.0);
    }
}
```

---

## 🛠️ Architectural Components

```
VibeVision_Prototype_Field_Project/
├── app/
│   ├── build.gradle.kts                     # App module dependencies (CameraX, Compose, Coroutines)
│   ├── proguard-rules.pro                   # R8 ProGuard rules
│   └── src/main/
│       ├── AndroidManifest.xml              # Hardware permissions (CAMERA, RECORD_AUDIO, GLES 2.0)
│       ├── java/com/vibevision/app/
│       │   ├── MainActivity.kt              # App entry point (Edge-to-edge layout, wake-lock)
│       │   ├── model/
│       │   │   ├── DisplayMode.kt           # 4 Industrial Modes
│       │   │   └── DiagnosticState.kt       # Real-time state & telemetry model
│       │   ├── camera/
│       │   │   ├── CameraManager.kt         # Locked rear camera, continuous AF, target 60 FPS
│       │   │   └── VibrationAnalyzer.kt     # Sub-pixel Y-plane differential signal analysis
│       │   ├── gl/
│       │   │   ├── GLVibeSurfaceView.kt     # Custom GLSurfaceView wrapper
│       │   │   ├── VibeRenderer.kt          # OpenGL ES 2.0 two-pass renderer with EGL recording
│       │   │   ├── FboRingBuffer.kt         # Cyclical FBO texture ring buffer (Δt = 1 to 8 frames)
│       │   │   └── ShaderUtils.kt           # GLSL Shaders & quad buffers
│       │   ├── media/
│       │   │   ├── SnapshotManager.kt       # Processed frame capture -> MediaStore (Pictures/VibeVision)
│       │   │   └── VideoRecorder.kt         # Real-time H.264 MP4 recording -> MediaStore (Movies/VibeVision)
│       │   └── ui/
│       │       ├── VibeVisionScreen.kt      # Main clean inspection viewport
│       │       ├── components/
│       │       │   ├── TopHudBar.kt         # Live FPS, mode pill, active REC timer pill, settings icon
│       │       │   ├── BottomCaptureBar.kt  # Snapshot shutter, video record toggle, zero-noise pill
│       │       │   ├── SettingsBottomSheet.kt # Section A (Config) & Section B (About VibeVision)
│       │       │   └── ReticleOverlay.kt    # Optical inspection reticle & target rings
│       │       └── theme/
│       │           ├── Color.kt             # Dark industrial palette
│       │           ├── Type.kt              # Monospace telemetry typography
│       │           └── Theme.kt             # Dark Material 3 theme
│       └── res/                             # Values, strings, colors, adaptive launcher icons
├── build.gradle.kts                         # Root Gradle build script
├── settings.gradle.kts                      # Module settings and repositories
├── gradle.properties                        # JVM & AndroidX properties
└── gradlew / gradlew.bat                    # Gradle wrapper scripts
```

---

## 📱 Field Operations Guide

| Inspection Action | Description |
|---|---|
| **Inspection Viewport** | 100% clean, unobstructed optical stream with zero on-screen slider clutter |
| **Top Telemetry Pill** | Displays Live FPS and active engine mode (`[NULL BASELINE]`, `[SPECTRAL HEATMAP]`, etc.) |
| **REC Timer Pill** | When filming, pulses red indicator: `REC [00:15]` |
| **Snapshot Shutter** | Captures the processed shader frame directly from the OpenGL frame buffer and saves high-res JPEG to Gallery |
| **Video Record Toggle** | Hardware-encodes real-time diagnostic feed to H.264 MP4 and saves to Gallery |
| **Calibrate Null Baseline**| Zeroes out background handheld camera jitter or tripod tremor |
| **Settings Modal Sheet** | Opened via Gear icon: adjust Sub-Pixel Gain ($1.0\times - 30.0\times$), Frame Delay ($\Delta t = 1 - 8$), Target 60 FPS, and view engineering specifications |
| **Tap to Focus** | Tap anywhere on the viewport to trigger spot autofocus and auto-exposure lock |
