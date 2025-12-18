cmake_minimum_required(VERSION 3.18.1)

project("poc-31314")

set(CMAKE_CXX_STANDARD 17)
add_compile_options(-Werror=format -fdata-sections -ffunction-sections -fno-rtti -fno-threadsafe-statics)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    message("Builing Release...")

    add_compile_options(-Os -flto -fvisibility=hidden -fvisibility-inlines-hidden)
    add_link_options(-flto -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--strip-all)
else ()
    message("Builing Debug...")

    add_definitions(-DDEBUG)
endif ()
include_directories(${OPENSSL_INCLUDE_DIRS})

find_library(OPENSSL_LIB crypto)

add_executable(libzygote_nc.so
        zygote_nc.c)
        
target_link_libraries(libzygote_nc.so log ${OPENSSL_LIB})

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET libzygote_nc.so POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libzygote_nc.so")
endif ()

add_executable(libzygote_term.so
        zygote_term.c app_process_launcher.c socket_sender.c policy_client.c)
        
target_link_libraries(libzygote_term.so log ${OPENSSL_LIB})

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET libzygote_term.so POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libzygote_term.so")
endif ()

add_executable(libbinder.so
        binder.c)
        
target_link_libraries(libbinder.so log ${OPENSSL_LIB})

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET libbinder.so POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libbinder.so")
endif ()

add_executable(libpolicy_daemon.so
        policy_daemon.c)
        
target_link_libraries(libpolicy_daemon.so log ${OPENSSL_LIB})

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET libpolicy_daemon.so POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libpolicy_daemon.so")
endif ()