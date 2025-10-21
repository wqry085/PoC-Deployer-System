
LOCAL_PATH := $(call my-dir)

# ===================================================================
# 定义所有模块共享的通用编译标志
# ===================================================================

# 对应 add_compile_options(...)
common_cflags := -Werror=format -fdata-sections -ffunction-sections -fno-rtti -fno-threadsafe-statics

# 对应 CMAKE_CXX_STANDARD 17
common_cpp_standard := c++17

# Debug 模式下的标志 (对应 DDEBUG)
common_cflags_debug := -DDEBUG

# Release 模式下的标志 (对应 -Os, -flto, -fvisibility 等)
common_cflags_release := -Os -flto -fvisibility=hidden -fvisibility-inlines-hidden
common_ldflags_release := -flto -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--strip-all

# ===================================================================
# 模块 1: libzygote_nc.so (来自 add_executable)
# ===================================================================
include $(CLEAR_VARS)

LOCAL_MODULE := zygote_nc_exec
LOCAL_SRC_FILES := zygote_nc.c

# 对应 target_link_libraries(... log)
LOCAL_LDLIBS := -llog

# 应用通用标志
LOCAL_CFLAGS += $(common_cflags)
LOCAL_CFLAGS_DEBUG += $(common_cflags_debug)
LOCAL_CFLAGS_RELEASE += $(common_cflags_release)
LOCAL_LDFLAGS_RELEASE += $(common_ldflags_release)
LOCAL_CPP_STANDARD := $(common_cpp_standard)

# 重命名可执行文件以匹配 CMake 的 add_executable(libzygote_nc.so ...)
LOCAL_MODULE_FILENAME := libzygote_nc.so

include $(BUILD_EXECUTABLE)

# ===================================================================
# 模块 2: libzygote_term.so (来自 add_executable)
# ===================================================================
include $(CLEAR_VARS)

LOCAL_MODULE := zygote_term_exec
LOCAL_SRC_FILES := \
    zygote_term.c \
    app_process_launcher.c \
    socket_sender.c

# 对应 target_link_libraries(... log)
LOCAL_LDLIBS := -llog

# 应用通用标志
LOCAL_CFLAGS += $(common_cflags)
LOCAL_CFLAGS_DEBUG += $(common_cflags_debug)
LOCAL_CFLAGS_RELEASE += $(common_cflags_release)
LOCAL_LDFLAGS_RELEASE += $(common_ldflags_release)
LOCAL_CPP_STANDARD := $(common_cpp_standard)

# 重命名可执行文件以匹配 CMake 的 add_executable(libzygote_term.so ...)
LOCAL_MODULE_FILENAME := libzygote_term.so

include $(BUILD_EXECUTABLE)

# ===================================================================
# 模块 3: libterminal-wqry.so (来自 add_library SHARED)
# ===================================================================
include $(CLEAR_VARS)

# 对应 add_library(terminal-wqry SHARED ...)
# ndk-build 会自动添加 "lib" 前缀和 ".so" 后缀
LOCAL_MODULE := terminal-wqry
LOCAL_SRC_FILES := terminal-wqry.c

# 对应 target_link_libraries(... log)
LOCAL_LDLIBS := -llog

# 应用通用标志
LOCAL_CFLAGS += $(common_cflags)
LOCAL_CFLAGS_DEBUG += $(common_cflags_debug)
LOCAL_CFLAGS_RELEASE += $(common_cflags_release)
LOCAL_LDFLAGS_RELEASE += $(common_ldflags_release)
LOCAL_CPP_STANDARD := $(common_cpp_standard)

include $(BUILD_SHARED_LIBRARY)