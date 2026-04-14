#include "napi/native_api.h"
#include <bundle/native_interface_bundle.h> 
#include <cstring>
#include <unistd.h>
#include "stdio.h"


EXTERN_C_START
static napi_value Init(napi_env env, napi_value exports)
{
    if (gettid() != getpid()) {
        return NULL;
    }

    auto so = libkmp_symbols();
    OH_NativeBundle_ApplicationInfo nativeApplicationInfo = OH_NativeBundle_GetCurrentApplicationInfo();
    auto harName = "@byte/kmp";
    char buffer[1000];
    sprintf(buffer, "%s%s", nativeApplicationInfo.bundleName, "/entry");
    so->kotlin.root.com.bytedance.kmp.ohos_ffi.init.init(env, exports, buffer, harName);
    return exports;
}
EXTERN_C_END

static napi_module demoModule = {
    .nm_version = 1,
    .nm_flags = 0,
    .nm_filename = nullptr,
    .nm_register_func = Init,
    .nm_modname = "kmp",
    .nm_priv = ((void*)0),
    .reserved = { 0 },
};

extern "C" __attribute__((constructor)) void RegisterKmpModule(void)
{
    napi_module_register(&demoModule);
}
