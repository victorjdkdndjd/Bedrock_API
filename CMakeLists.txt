cmake_minimum_required(VERSION 3.22)
project(Bedrock_API VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
option(BEDROCK_API_BUILD_TESTS "Build host core tests" ON)

add_library(bedrock_api_core STATIC
    src/core/Module.cpp
    src/core/Signature.cpp
    src/core/Resolver.cpp
    src/profiles/CurrentBuild.cpp
)
target_include_directories(bedrock_api_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_link_libraries(bedrock_api_core PUBLIC ${CMAKE_DL_LIBS})

if(ANDROID)
    if(NOT CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
        message(FATAL_ERROR "Bedrock_API currently profiles arm64-v8a only")
    endif()

    include(FetchContent)
    FetchContent_Declare(
        preloader_android
        GIT_REPOSITORY https://github.com/LiteLDev/preloader-android.git
        GIT_TAG 0.2.2
        SOURCE_SUBDIR cmake/preloader-sdk
    )
    FetchContent_MakeAvailable(preloader_android)
    FetchContent_GetProperties(preloader_android)
    set(BEDROCK_PRELOADER_INCLUDE_DIR "${preloader_android_SOURCE_DIR}/include")

    if(NOT TARGET preloader)
        add_subdirectory("${preloader_android_SOURCE_DIR}" "${CMAKE_BINARY_DIR}/_preloader_android")
    endif()

    add_library(bedrock_api SHARED
        src/main.cpp
        src/Runtime.cpp
        src/SelfTest.cpp
        src/Hook.cpp
        src/modules/Minecraft.cpp
        src/mod/BedrockApiMod.cpp
    )
    target_include_directories(bedrock_api
        PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include
        PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src ${BEDROCK_PRELOADER_INCLUDE_DIR}
    )
    target_link_libraries(bedrock_api PRIVATE bedrock_api_core preloader log dl)
    target_compile_options(bedrock_api PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
    target_link_options(bedrock_api PRIVATE "-Wl,-z,max-page-size=16384")

    configure_file(manifest.json.in ${CMAKE_CURRENT_BINARY_DIR}/manifest.json @ONLY)
    set(PACKAGE_ROOT ${CMAKE_CURRENT_BINARY_DIR}/package)
    set(PACKAGE_DIR ${PACKAGE_ROOT}/bedrock_api)
    set(PACKAGE_FILE ${CMAKE_CURRENT_BINARY_DIR}/bedrock_api-${PROJECT_VERSION}-arm64-v8a.levipack)
    add_custom_target(bedrock_api_package
        COMMAND ${CMAKE_COMMAND} -E rm -rf ${PACKAGE_ROOT}
        COMMAND ${CMAKE_COMMAND} -E make_directory ${PACKAGE_DIR}
        COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:bedrock_api> ${PACKAGE_DIR}/libbedrock_api.so
        COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_BINARY_DIR}/manifest.json ${PACKAGE_DIR}/manifest.json
        COMMAND ${CMAKE_COMMAND} -E chdir ${PACKAGE_ROOT} ${CMAKE_COMMAND} -E tar cf ${PACKAGE_FILE} --format=zip bedrock_api
        DEPENDS bedrock_api
        VERBATIM
    )
elseif(BEDROCK_API_BUILD_TESTS)
    enable_testing()
    add_executable(bedrock_api_core_tests tests/core_tests.cpp)
    target_link_libraries(bedrock_api_core_tests PRIVATE bedrock_api_core)
    add_test(NAME bedrock_api_core_tests COMMAND bedrock_api_core_tests)
endif()
