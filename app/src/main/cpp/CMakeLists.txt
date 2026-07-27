cmake_minimum_required(VERSION 3.22.1)

project("SerenityAI")

# Bring in llama.cpp
# We want it to build as a static library so we can bundle it into our JNI library,
# OR we can build it as shared and link to it.
# Static is often easier for Android to avoid multiple .so files.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

add_subdirectory(llama.cpp)

# Create the JNI library
add_library(serenity_llama SHARED
    llama-android.cpp
)

# Link libraries
# 'llama' is the target name from llama.cpp/src/CMakeLists.txt
target_link_libraries(serenity_llama
    llama
    android
    log
)

# Enable 16 KB page size alignment
target_link_options(serenity_llama PRIVATE "-Wl,-z,max-page-size=16384")

# Include directories
target_include_directories(serenity_llama PRIVATE
    llama.cpp/include
    llama.cpp/common
)
