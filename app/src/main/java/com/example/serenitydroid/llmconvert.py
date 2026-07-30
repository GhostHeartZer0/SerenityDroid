import gguf
import torch
from ai_edge_torch.generative.utilities import converter

# 1. Inspect GGUF model metadata
model_path = "gemma-2-2b-it.gguf"
output_litertlm_path = "gemma-2-2b-it.litertlm"

print(f"Loading GGUF file: {model_path}")
reader = gguf.GGUFReader(model_path)
print(f"GGUF architecture: {reader.fields.get('general.architecture')}")

# 2. Convert GGUF / PyTorch Gemma checkpoint to LiteRT-LM (.litertlm) format
print("Exporting model to LiteRT-LM format...")
converter.convert_to_litertlm(
    checkpoint_path=model_path,
    output_path=output_litertlm_path,
    quantization="int8",  # Options: int8, int4, fp16
    backend="npu"         # Options: npu, gpu, cpu
)

print(f"Conversion complete: {output_litertlm_path}")

# 3. Compile for Qualcomm AI Hub (Snapdragon 8 Gen 3 / SM8750)
import qai_hub as hub

print("Submitting Qualcomm AI Hub compilation job...")
qai_model = hub.load_model("gemma-2-2b-it")
compiled_model = hub.submit_compile_job(
    model=qai_model,
    device=hub.Device("Samsung Galaxy S24"),  # SM8650 / SM8750 Snapdragon target
    options="--target_runtime litert"         # Options: litert, qnn, genie
)

# 4. Profile performance & download compiled model file
print("Profiling model on device & downloading binary...")
profile_job = hub.submit_profile_job(model=compiled_model, device=hub.Device("Samsung Galaxy S24"))
downloaded_file = compiled_model.download()
print(f"Qualcomm AI Hub compilation finished: {downloaded_file}")


