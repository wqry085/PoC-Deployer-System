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

add_executable(libzygote_nc.so
        zygote_nc.c)
        
target_link_libraries(libzygote_nc.so log)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET libzygote_nc.so POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libzygote_nc.so")
endif ()

add_executable(libzygote_term.so
        zygote_term.c app_process_launcher.c socket_sender.c)
        
target_link_libraries(libzygote_term.so log)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET libzygote_term.so POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libzygote_term.so")
endif ()

# 添加真正的SO库 - terminal-wqry.c
add_library(terminal-wqry SHARED
        terminal-wqry.c)
        
target_link_libraries(terminal-wqry log)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET terminal-wqry POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "$<TARGET_FILE:terminal-wqry>")
endif ()

# 设置输出属性，确保生成正确的SO文件名
set_target_properties(terminal-wqry PROPERTIES
        PREFIX "lib"
        OUTPUT_NAME "terminal-wqry"
        SUFFIX ".so")