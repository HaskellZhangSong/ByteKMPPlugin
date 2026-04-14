#ifndef FFI_OPT_H
#define FFI_OPT_H
#include "napi/native_api.h"
#include "js_native_api.h"
#include "js_native_api_types.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * system
 */
int get_tid();
int get_pid();

/**
 * basic types
 */
// int
int napi_get_value_int32_opt(void* env, void* value);
void* napi_create_int32_opt(void* env, int value);
// unsigned int
unsigned int napi_get_value_uint32_opt(void* env, void* value);
void* napi_create_uint32_opt(void* env, unsigned int value);
// long
long napi_get_value_int64_opt(void* env, void* value);
void* napi_create_int64_opt(void* env, long value);
// bool
bool napi_get_value_bool_opt(void* env, void* value);
void* napi_create_bool_opt(void* env, bool value);
// double
double napi_get_value_double_opt(void* env, void* value);
void* napi_create_double_opt(void* env, double value);
// string
const char* napi_get_value_string_utf8_opt(void* env, void* value);
void* napi_create_string_utf8_opt(void* env, const char* value);
// undefined
void* napi_get_undefined_opt(void* env);
bool  isUndefined(void* env, void* value);
// TODO: need to merge
char *toKString(napi_env env, napi_value value);
int toInt(napi_env env, napi_value value);
long toLong(napi_env env, napi_value value);
napi_value convertStringToNapiValue(napi_env env, const char *value);
napi_value convertIntToNapiValue(napi_env env, int value);
napi_value convertLongToNapiValue(napi_env env, long value);
bool toBoolean(napi_env env, napi_value value);
napi_value convertBooleanToNapiValue(napi_env env, bool value);
double toDouble(napi_env env, napi_value value);
napi_value convertDoubleToNapiValue(napi_env env, double value);
napi_valuetype typeOf(napi_env env, napi_value value);

/**
 * array
 */
void* napi_create_array_opt(void* env);
void napi_set_element_opt(void* env, void* array, int index, void* value);
int napi_get_array_length_opt(void* env, void* value);
void* napi_get_element_opt(void* env, void* array, int index);
bool napi_is_array_opt(void* env, void* value);
void* createNapiValueArray(void* env, int size);

/**
 * arraybuffer
 */
bool isArrayBuffer(napi_env env, napi_value value);
bool isTypedArray(napi_env env, napi_value value);
size_t getArrayBufferLength(napi_env env, napi_value value);
uint8_t *getArrayBufferValue(napi_env env, napi_value value);
uint8_t *getTypeArrayValue(napi_env env, napi_value value);
size_t getTypeArrayLength(napi_env env, napi_value value);
napi_typedarray_type getTypeArrayType(napi_env env, napi_value value);
napi_value createArrayBuffer(napi_env env, uint8_t *inputBuffer, long length);
napi_value createTypedArray(napi_env env, uint8_t *data, long count, napi_typedarray_type type);
int getTypedArrayItemSize(napi_typedarray_type typed);

/**
 * property
 */
void* napi_get_named_property_opt(void* env, void* obj, const char* name);
napi_value getPropertyNames(napi_env env, napi_value value);
napi_value getPropertyValue(napi_env env, napi_value obj, const char *key);
void setPropertyValue(napi_env env, napi_value obj, const char *key, napi_value value); // TODO: 不需要

/**
 * module
 */
void* napi_load_module_with_info_opt(void* env, const char* path, const char* module_info);
void* napi_get_global_opt(void* env);
napi_status loadModuleWithInfo(napi_env env, const char *path, const char *module_info, napi_value *result);
napi_status loadModule(napi_env env, const char *path, napi_value *result);

/**
 * object
 */
void* napi_create_object_opt(void* env);
void* napi_new_instance_opt(void* env, void* constructor, int size, void* args);
napi_value createObject(napi_env env);
napi_value getGlobal(napi_env env);
napi_value getUndefined(napi_env env);
void wrap(napi_env env, napi_value js_object, void *data, void *finalize_cb);
void *unwrap(napi_env env, napi_value js_object);
bool isEquals(napi_env env, napi_value a, napi_value b);
bool isInstanceOf(napi_env env, napi_value constructor, napi_value object);
void* napi_unwrap_opt(void* env, void* value);

/**
 * function
 */
void* napi_call_function_opt(void* env, void* recev, void* func, int size, void* args);
napi_value callFunction(napi_env env, napi_value recv, napi_value func, int size, const napi_value *argv, napi_value* exception_str);
napi_value createFunction(napi_env env, const char *name, napi_callback callback, void *release);
void callThreadSafeFunctionWithData(napi_threadsafe_function tsfn, void *data);
napi_threadsafe_function createThreadSafeFunctionWithCallback(napi_env env, const char *workName, void *callback);
napi_threadsafe_function
createThreadSafeFunctionWithSync(napi_env env, const char *workName);
void* callThreadSafeFunction(napi_threadsafe_function tsfn, void* callback, void* data, bool sync, int tsfnOriginTid);

/**
 * reference
 */
void* napi_create_reference_opt(void* env, void* value, int initRefCount);
void* napi_get_reference_value_opt(void* env, void* ref);
void napi_delete_reference_opt(void* env, void* ref);
napi_ref createReference(napi_env env, napi_value value);
void deleteReference(napi_env env, napi_ref ref);
napi_value getReferenceValue(napi_env env, napi_ref ref);

/**
 * params
 */
void* getThisArg(void* env, void* cbinfo);
void* getParams(void* env, void* cbinfo, int size);
int getIntByCB(void* env, void* cbinfo, int index);
long getLongByCB(void* env, void* cbinfo, int index);
double getDoubleByCB(void* env, void* cbinfo, int index);
bool getBooleanByCB(void* env, void* cbinfo, int index);
const char* getStringByCB(void* env, void* cbinfo, int index);
int getCallbackInfoParamsSize(napi_env env, napi_callback_info info);
void *getCbInfoData(napi_env env, napi_callback_info info);
napi_value *getCbInfoWithSize(napi_env env, napi_callback_info info, int size);
napi_value getCbInfoThis(napi_env env, napi_callback_info info);

/**
 * sendable
 */
bool napi_is_sendable_opt(napi_env env, napi_value value);

#ifdef __cplusplus
}
#endif
#endif // FFI_OPT_H
