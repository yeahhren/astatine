cmake_minimum_required(VERSION 3.24)
project(astatine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

if (NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release)
endif ()

set(ASTATINE_JAVA_HOME "$ENV{JAVA_HOME}" CACHE PATH "JDK whose jni.h the library is built against")
set(ASTATINE_OUTPUT_DIR "${CMAKE_BINARY_DIR}/out" CACHE PATH "Where the built library is copied for packaging")

find_package(Vulkan REQUIRED COMPONENTS glslc)

if (WIN32)
    set(ASTATINE_OS windows)
    set(JNI_PLATFORM_INCLUDE win32)
elseif (APPLE)
    set(ASTATINE_OS macos)
    set(JNI_PLATFORM_INCLUDE darwin)
else ()
    set(ASTATINE_OS linux)
    set(JNI_PLATFORM_INCLUDE linux)
endif ()

if (CMAKE_SYSTEM_PROCESSOR MATCHES "^(aarch64|arm64|ARM64)$")
    set(ASTATINE_ARCH arm64)
else ()
    set(ASTATINE_ARCH x64)
endif ()

if (NOT EXISTS "${ASTATINE_JAVA_HOME}/include/jni.h")
    message(FATAL_ERROR "jni.h not found, point ASTATINE_JAVA_HOME at a JDK (currently '${ASTATINE_JAVA_HOME}')")
endif ()

set(SHADER_DIR "${CMAKE_CURRENT_SOURCE_DIR}/shaders")
set(SHADER_OUT "${CMAKE_BINARY_DIR}/shaders")
file(MAKE_DIRECTORY "${SHADER_OUT}")
file(GLOB SHADER_HEADERS CONFIGURE_DEPENDS "${SHADER_DIR}/*.glsl")

set(SHADER_SPV)
set(SHADER_NAMES)

function(add_shader source stage name)
    set(output "${SHADER_OUT}/${name}.spv")
    add_custom_command(
        OUTPUT "${output}"
        COMMAND Vulkan::glslc -fshader-stage=${stage} --target-env=vulkan1.0 -O -I "${SHADER_DIR}" ${ARGN} -o "${output}" "${SHADER_DIR}/${source}"
        DEPENDS "${SHADER_DIR}/${source}" ${SHADER_HEADERS}
        COMMENT "Compiling shader ${name}"
        VERBATIM
    )
    list(APPEND SHADER_SPV "${output}")
    list(APPEND SHADER_NAMES "${name}")
    set(SHADER_SPV "${SHADER_SPV}" PARENT_SCOPE)
    set(SHADER_NAMES "${SHADER_NAMES}" PARENT_SCOPE)
endfunction()

foreach (mask RANGE 0 15)
    foreach (flat 0 1)
        set(defines)
        math(EXPR color "${mask} & 1")
        math(EXPR uv0 "${mask} & 2")
        math(EXPR uv1 "${mask} & 4")
        math(EXPR normal "${mask} & 8")
        if (color)
            list(APPEND defines -DHAS_COLOR)
        endif ()
        if (uv0)
            list(APPEND defines -DHAS_UV0)
        endif ()
        if (uv1)
            list(APPEND defines -DHAS_UV1)
        endif ()
        if (normal)
            list(APPEND defines -DHAS_NORMAL)
        endif ()
        if (flat)
            list(APPEND defines -DFLAT)
        endif ()
        add_shader(fixed_function.vert vert "fixed_vert_${mask}_${flat}" ${defines})
    endforeach ()
endforeach ()

foreach (flat 0 1)
    foreach (bindless 0 1)
        set(defines)
        if (flat)
            list(APPEND defines -DFLAT)
        endif ()
        if (bindless)
            list(APPEND defines -DBINDLESS)
        endif ()
        add_shader(fixed_function.frag frag "fixed_frag_${flat}_${bindless}" ${defines})
    endforeach ()
endforeach ()

add_shader(terrain.vert vert "terrain_vert")

set(SHADER_BLOB "${CMAKE_BINARY_DIR}/generated/shader_blob.inc")
set(SHADER_STAMP "${CMAKE_BINARY_DIR}/generated/shader_blob.stamp")

file(MAKE_DIRECTORY "${CMAKE_BINARY_DIR}/generated")

if (NOT EXISTS "${SHADER_BLOB}")
    file(WRITE "${SHADER_BLOB}" "// empty\n")
endif ()

add_custom_command(
        OUTPUT "${SHADER_STAMP}"
        BYPRODUCTS "${SHADER_BLOB}"
        COMMAND ${CMAKE_COMMAND}
        "-DOUTPUT=${SHADER_BLOB}"
        "-DDIRECTORY=${SHADER_OUT}"
        "-DNAMES=${SHADER_NAMES}"
        -P "${CMAKE_CURRENT_SOURCE_DIR}/cmake/embed_shaders.cmake"
        COMMAND ${CMAKE_COMMAND} -E touch "${SHADER_STAMP}"
        DEPENDS ${SHADER_SPV} "${CMAKE_CURRENT_SOURCE_DIR}/cmake/embed_shaders.cmake"
        COMMENT "Embedding SPIR-V"
        VERBATIM
)

add_custom_target(embed_shaders DEPENDS "${SHADER_STAMP}")

file(GLOB_RECURSE SOURCES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp")
file(GLOB_RECURSE HEADERS CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/src/*.h")

add_library(astatine SHARED ${SOURCES} ${HEADERS})
add_dependencies(astatine embed_shaders)
target_include_directories(astatine PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/src"
    "${CMAKE_BINARY_DIR}/generated"
    "${ASTATINE_JAVA_HOME}/include"
    "${ASTATINE_JAVA_HOME}/include/${JNI_PLATFORM_INCLUDE}"
)
target_link_libraries(astatine PRIVATE Vulkan::Headers)
target_compile_definitions(astatine PRIVATE VK_NO_PROTOTYPES NOMINMAX WIN32_LEAN_AND_MEAN)

if (MSVC)
    target_compile_options(astatine PRIVATE /W4 /permissive- /Zc:__cplusplus /utf-8 /EHsc /GR- /wd4100 /wd4201)
    target_compile_options(astatine PRIVATE $<$<CONFIG:Release>:/O2 /Ob3 /Oi /GS- /Gy>)
    target_link_options(astatine PRIVATE $<$<CONFIG:Release>:/OPT:REF /OPT:ICF>)
    set_property(TARGET astatine PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    target_link_libraries(astatine PRIVATE d3d12 dxgi)
else ()
    target_compile_options(astatine PRIVATE -Wall -Wextra -Wno-unused-parameter -fno-rtti)
    target_compile_options(astatine PRIVATE $<$<CONFIG:Release>:-O3 -fno-plt>)
    if (NOT APPLE)
        target_link_options(astatine PRIVATE -static-libstdc++ -static-libgcc -Wl,--gc-sections -Wl,-z,defs)
        target_link_libraries(astatine PRIVATE dl pthread)
    endif ()
endif ()

set(ASTATINE_PLATFORM "${ASTATINE_OS}-${ASTATINE_ARCH}")
add_custom_command(TARGET astatine POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory "${ASTATINE_OUTPUT_DIR}/${ASTATINE_PLATFORM}"
    COMMAND ${CMAKE_COMMAND} -E copy "$<TARGET_FILE:astatine>" "${ASTATINE_OUTPUT_DIR}/${ASTATINE_PLATFORM}/"
    VERBATIM
)
