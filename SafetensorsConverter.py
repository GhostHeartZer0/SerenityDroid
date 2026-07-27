"""
        Safetensors to LiteRT-LM Converter
This script converts a standard HuggingFace Safetensors model to the .litertlm format
optimized for Android NPU/GPU execution using Google's ai-edge-torch toolchain.

Prerequisites:
    pip install ai-edge-torch torch safetensors
"""

import os
import argparse
import torch
import ai_edge_torch
from ai_edge_torch.generative.utilities import loader
from ai_edge_torch.generative.utilities import converter

def convert(input_dir, output_path, model_type="gemma2", quant="int8"):
    print(f"--- Serenity AI Model Converter ---")
    print(f"Source: {input_dir}")
    print(f"Architecture: {model_type}")
    print(f"Target Quantization: {quant}")

    try:
        # 1. Load the model from the directory containing safetensors + config.json
        print("Loading weights...")
        if model_type == "gemma2":
            model = loader.load_gemma2_2b(input_dir)
        elif model_type == "phi3":
            model = loader.load_phi3_mini(input_dir)
        else:
            print(f"Unsupported architecture: {model_type}")
            return

        # 2. Convert and Export to .litertlm
        # This handles the heavy lifting of mapping to LiteRT ops and quantizing
        print(f"Converting to LiteRT-LM (this may take a few minutes)...")
        
        # Example using the newer high-level generative API
        converter.convert_to_litertlm(
            checkpoint_path=input_dir,
            output_path=output_path,
            quantization=quant,
            backend="npu" # NPU target for sm8750/sm8650
        )

        print(f"\nSUCCESS: Model saved to {output_path}")
        print("Move this file to your device's 'Models' folder to use in Serenity AI.")

    except Exception as e:
        print(f"\nCONVERSION FAILED: {e}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Serenity AI Safetensors Converter")
    parser.add_argument("--input", required=True, help="Directory containing safetensors and config.json")
    parser.add_argument("--output", required=True, help="Path for the resulting .litertlm file")
    parser.add_argument("--type", default="gemma2", choices=["gemma2", "phi3"], help="Model architecture")
    parser.add_argument("--quant", default="int8", choices=["int8", "int4", "fp16"], help="Quantization level")

    args = parser.parse_args()
    
    # Check if dependencies are installed
    try:
        import torch
        import ai_edge_torch
    except ImportError:
        print("Error: Missing dependencies. Please run:")
        print("pip install ai-edge-torch torch safetensors")
        exit(1)

    convert(args.input, args.output, args.type, args.quant)
