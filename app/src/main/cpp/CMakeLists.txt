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
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)

# Ensure OpenMP is linked correctly
find_package(OpenMP REQUIRED)

# OpenCL Support for Android (Snapdragon/Adreno)
if (GGML_OPENCL)
    # Pre-emptively find OpenCL or set hints for llama.cpp's find_package(OpenCL)
    set(OpenCL_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/opencl/include" CACHE PATH "OpenCL include dir")

    find_library(OPENCL_LIB OpenCL)
    if (OPENCL_LIB)
        message(STATUS "Found OpenCL library: ${OPENCL_LIB}")
        set(OpenCL_LIBRARY "${OPENCL_LIB}" CACHE FILEPATH "OpenCL library" FORCE)
        set(OpenCL_LIBRARIES "${OPENCL_LIB}" CACHE STRING "OpenCL libraries" FORCE)
    else()
        message(WARNING "OpenCL library not found in NDK, creating a stub for build-time linking.")
        # Create a stub library so the linker is happy at build time.
        # We name it OpenCL_stub but set its output name to OpenCL.
        add_library(OpenCL_stub SHARED third_party/opencl/libOpenCL_stub.c)
        set_target_properties(OpenCL_stub PROPERTIES OUTPUT_NAME OpenCL)
        set(OpenCL_LIBRARY OpenCL_stub CACHE FILEPATH "OpenCL library" FORCE)
        set(OpenCL_LIBRARIES OpenCL_stub CACHE STRING "OpenCL libraries" FORCE)
        set(OpenCL_INCLUDE_DIRS "${OpenCL_INCLUDE_DIR}" CACHE STRING "OpenCL include dirs" FORCE)
        set(OPENCL_LIB OpenCL_stub)
    endif()

    # Force OpenCL_FOUND to TRUE to satisfy llama.cpp if headers exist
    if (EXISTS "${OpenCL_INCLUDE_DIR}/CL/cl.h")
        set(OpenCL_FOUND TRUE CACHE BOOL "OpenCL found" FORCE)
        set(OpenCL_INCLUDE_DIRS "${OpenCL_INCLUDE_DIR}" CACHE STRING "OpenCL include dirs" FORCE)
    endif()
endif()

add_subdirectory(llama.cpp)

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

# Enable 16 KB page size alignment
target_link_options(serenity_llama PRIVATE "-Wl,-z,max-page-size=16384")

# Include directories
target_include_directories(serenity_llama PRIVATE
    llama.cpp/include
    llama.cpp/common
    llama.cpp/ggml/include
)
