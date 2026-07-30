cmake_minimum_required(VERSION 3.22.1)

project("SerenityAI")

# Bring in llama.cpp
# We want it to build as a static library so we can bundle it into our JNI library,
# OR we can build it as shared and link to it.
# Static is often easier for Android to avoid multiple .so files.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_STATIC ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# Ensure OpenMP is linked correctly
find_package(OpenMP REQUIRED)

add_subdirectory(llama.cpp)

# OpenCL Support for Android (Snapdragon/Adreno)
if (GGML_OPENCL)
    find_library(OPENCL_LIB OpenCL)
    if (OPENCL_LIB)
        message(STATUS "Found OpenCL library: ${OPENCL_LIB}")
    else()
        message(WARNING "OpenCL library not found, but GGML_OPENCL is ON. Linking may fail unless provided by system.")
    endif()
endif()

# Vulkan Support
if (GGML_VULKAN)
    find_library(VULKAN_LIB vulkan)
    if (VULKAN_LIB)
        message(STATUS "Found Vulkan library: ${VULKAN_LIB}")
    else()
        message(WARNING "Vulkan library not found, but GGML_VULKAN is ON.")
    endif()
endif()

# Create the JNI library
add_library(serenity_llama SHARED
    llama-android.cpp
)

# Enable OpenMP
find_package(OpenMP REQUIRED)
if (OpenMP_CXX_FOUND)
    target_link_libraries(serenity_llama PUBLIC OpenMP::OpenMP_CXX)
    target_compile_options(serenity_llama PRIVATE ${OpenMP_CXX_FLAGS})
endif()

# Link libraries
# 'llama' is the target name from llama.cpp/src/CMakeLists.txt
target_link_libraries(serenity_llama PRIVATE
    llama
    android
    log
)

if (OPENCL_LIB)
    target_link_libraries(serenity_llama PRIVATE ${OPENCL_LIB})
endif()

if (VULKAN_LIB)
    target_link_libraries(serenity_llama PRIVATE ${VULKAN_LIB})
endif()

# Enable 16 KB page size alignment
target_link_options(serenity_llama PRIVATE "-Wl,-z,max-page-size=16384")

# Include directories
target_include_directories(serenity_llama PRIVATE
    llama.cpp/include
    llama.cpp/common
)
