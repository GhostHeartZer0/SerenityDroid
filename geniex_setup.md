# Qualcomm® AI Hub GenieX Android Integration Guide

Qualcomm® AI Hub GenieX is an on-device Gen AI inference runtime for Qualcomm devices supporting Hexagon NPU, Adreno GPU, and CPU execution via a unified Java/Kotlin Android SDK.

## Architecture

```
                      +-----------------------------+
                      |   GenieX Android Java SDK   |
                      +--------------+--------------+
                                     |
              +----------------------+----------------------+
              |                                             |
  +-----------v------------+                    +-----------v------------+
  |  GenieX - QAIRT Plugin |                    | GenieX - llama.cpp     |
  +-----------+------------+                    +-----------+------------+
              |                                             |
  +-----------v------------+                    +-----------v------------+
  | Qualcomm® AI Runtime   |                    | GGML Runtime (HTP /    |
  | (QAIRT) SDK            |                    | GPU / CPU Kernels)     |
  +-----------+------------+                    +-----------+------------+
              |                                             |
              +----------------------+----------------------+
                                     |
                          +----------v----------+
                          | CPU / GPU / Hex NPU |
                          +---------------------+
```

## Plugin Runtimes

1. **GenieX - QAIRT Plugin**
   - Target: Pre-compiled Qualcomm AI Hub bundles (`.zip`, extracted bundle directories, `.bin`, `.dlc`).
   - Execution: Direct Qualcomm AI Engine Direct / QAIRT NPU dispatch.

2. **GenieX - llama.cpp Plugin**
   - Target: Community GGUF models (`.gguf`).
   - Execution: GGML Hexagon Tensor Processor (HTP) NPU kernels, Adreno OpenCL GPU kernels, and ARM NEON CPU kernels.

## Model Registration & File Formats

- Local QAIRT Bundles: `.zip`, directory containing pre-compiled QAIRT assets, `.bin`, `.dlc`.
- Local GGUF Models: `.gguf` files (Q4_0 quantization recommended for Hexagon NPU execution).
