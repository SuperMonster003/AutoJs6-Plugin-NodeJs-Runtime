cmake_minimum_required(VERSION 3.10.2)

project(autojs6_node LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(AUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE OFF CACHE BOOL "Enable direct Node embedded lifecycle probe calls")
set(AUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION OFF CACHE BOOL "Enable embedded Node script execution")
set(AUTOJS6_NODE_RUNTIME_SLOT "node24_21" CACHE STRING "Build-only Embedded Node runtime slot")
set(AUTOJS6_NODE_INCLUDE_DIR "" CACHE PATH "Build-selected Node include/node directory")
set(AUTOJS6_NODE_LIBRARY_ROOT "" CACHE PATH "Build-selected Node jniLibs root")

if (AUTOJS6_NODE_RUNTIME_SLOT STREQUAL "node24_21")
    set(NODE_HEADER_VERSION "24.21.0")
    set(NODE_ADAPTER_SOURCE "node_runtime_adapter_24_21.cpp")
elseif (AUTOJS6_NODE_RUNTIME_SLOT STREQUAL "node24_20")
    set(NODE_HEADER_VERSION "24.20.0")
    set(NODE_ADAPTER_SOURCE "node_runtime_adapter_24_20.cpp")
elseif (AUTOJS6_NODE_RUNTIME_SLOT STREQUAL "node24_5")
    set(NODE_HEADER_VERSION "24.5.0")
    set(NODE_ADAPTER_SOURCE "node_runtime_adapter_24_5.cpp")
else ()
    message(FATAL_ERROR "Unsupported Node runtime slot: ${AUTOJS6_NODE_RUNTIME_SLOT}")
endif ()

if ("${AUTOJS6_NODE_INCLUDE_DIR}" STREQUAL "")
    set(NODE_INCLUDE_DIR "${CMAKE_CURRENT_LIST_DIR}/node-v${NODE_HEADER_VERSION}/include/node")
else ()
    set(NODE_INCLUDE_DIR "${AUTOJS6_NODE_INCLUDE_DIR}")
endif ()

if ("${AUTOJS6_NODE_LIBRARY_ROOT}" STREQUAL "")
    set(NODE_LIBRARY_PATH "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libnode.so")
else ()
    set(NODE_LIBRARY_PATH "${AUTOJS6_NODE_LIBRARY_ROOT}/${ANDROID_ABI}/libnode.so")
endif ()

if (NOT EXISTS "${NODE_INCLUDE_DIR}/node.h")
    message(FATAL_ERROR "Node.js headers are missing for runtime slot ${AUTOJS6_NODE_RUNTIME_SLOT}: ${NODE_INCLUDE_DIR}")
endif ()

if (NOT EXISTS "${NODE_LIBRARY_PATH}")
    message(FATAL_ERROR "libnode.so is missing for runtime slot ${AUTOJS6_NODE_RUNTIME_SLOT}, ABI ${ANDROID_ABI}: ${NODE_LIBRARY_PATH}")
endif ()

add_library(node SHARED IMPORTED GLOBAL)
set_target_properties(node PROPERTIES IMPORTED_LOCATION "${NODE_LIBRARY_PATH}")

add_library(
        autojs6-node
        SHARED
        node_bridge.cpp
        node_bridge_sources.cpp
        node_bridge_core.cpp
        node_bridge_process_runtime.cpp
        node_bridge_payload.cpp
        node_bridge_lifecycle.cpp
        node_bridge_native_transport.cpp
        node_bridge_jni.cpp
        "${NODE_ADAPTER_SOURCE}"
        node_bridge/payload_utils.cpp
)

target_include_directories(
        autojs6-node
        PRIVATE
        "${NODE_INCLUDE_DIR}"
)

target_compile_definitions(
        autojs6-node
        PRIVATE
        AUTOJS6_NODE_ANDROID_ABI="${ANDROID_ABI}"
        AUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE=$<BOOL:${AUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE}>
        AUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION=$<BOOL:${AUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION}>
        AUTOJS6_NODE_DEBUG_BUILD=$<CONFIG:Debug>
        AUTOJS6_NODE_BRIDGE_LINKS_LIBNODE=1
)

if (CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo")
    target_compile_options(autojs6-node PRIVATE -O0)
endif ()

target_link_libraries(
        autojs6-node
        PRIVATE
        node
        log
        dl
)

# Keep 16 KB alignment explicit when changing toolchains.
target_link_options(autojs6-node PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

# Multi-call terminal launcher (node / npm / npx / corepack / yarn / pnpm), see docs/nodejs/TERMINAL.md.
# AGP only packages shared-library targets, so the PIE executable is written to a Gradle-owned
# jniLibs directory under the `libnodexe.so` name and merged from there.
set(AUTOJS6_NODE_CLI_OUTPUT_ROOT "" CACHE PATH "Directory receiving <abi>/libnodexe.so for jniLibs packaging")

if (NOT "${AUTOJS6_NODE_CLI_OUTPUT_ROOT}" STREQUAL "")
    # The Android platform module defines no runtime-path flag, so CMake's RPATH properties
    # would be silent; target_link_options cannot be used because CMake 3.22 double-escapes
    # `$ORIGIN` for Ninja (the ELF ends up with a literal `$$ORIGIN`).
    set(CMAKE_EXECUTABLE_RUNTIME_CXX_FLAG "-Wl,-rpath,")
    set(CMAKE_EXECUTABLE_RUNTIME_CXX_FLAG_SEP ":")

    add_executable(nodexe node_cli_main.cpp)

    target_include_directories(nodexe PRIVATE "${NODE_INCLUDE_DIR}")

    target_link_libraries(nodexe PRIVATE node log)

    set_target_properties(
            nodexe
            PROPERTIES
            OUTPUT_NAME "libnodexe.so"
            PREFIX ""
            SUFFIX ""
            RUNTIME_OUTPUT_DIRECTORY "${AUTOJS6_NODE_CLI_OUTPUT_ROOT}/${ANDROID_ABI}"
            # libnode.so and libc++_shared.so sit next to the launcher in the installed lib dir.
            SKIP_BUILD_RPATH TRUE
            BUILD_WITH_INSTALL_RPATH TRUE
            INSTALL_RPATH "$ORIGIN"
    )

    target_link_options(nodexe PRIVATE
        "-Wl,--enable-new-dtags"
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
    )
endif ()
