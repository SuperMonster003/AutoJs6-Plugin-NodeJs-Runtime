#include <node_api.h>

static napi_value hello(napi_env env, napi_callback_info info) {
    (void)info;
    napi_value result;
    if (napi_create_string_utf8(env, "Hello from Node-API", NAPI_AUTO_LENGTH, &result) != napi_ok) return NULL;
    return result;
}

static napi_value add(napi_env env, napi_callback_info info) {
    size_t argc = 2;
    napi_value argv[2];
    double left, right;
    if (napi_get_cb_info(env, info, &argc, argv, NULL, NULL) != napi_ok) return NULL;
    if (argc != 2 || napi_get_value_double(env, argv[0], &left) != napi_ok ||
        napi_get_value_double(env, argv[1], &right) != napi_ok) {
        napi_throw_type_error(env, "ERR_INVALID_ARG_TYPE", "add requires two numbers");
        return NULL;
    }
    napi_value result;
    if (napi_create_double(env, left + right, &result) != napi_ok) return NULL;
    return result;
}

static napi_value initialize(napi_env env, napi_value exports) {
    const napi_property_descriptor methods[] = {
        {"hello", NULL, hello, NULL, NULL, NULL, napi_default_method, NULL},
        {"add", NULL, add, NULL, NULL, NULL, napi_default_method, NULL}
    };
    if (napi_define_properties(env, exports, 2, methods) != napi_ok) return NULL;
    napi_value abi;
    if (napi_create_string_utf8(env, AUTOJS6_ADDON_ABI, NAPI_AUTO_LENGTH, &abi) != napi_ok) return NULL;
    if (napi_set_named_property(env, exports, "abi", abi) != napi_ok) return NULL;
    return exports;
}

// No process-global napi_env or JS handles: each Worker/environment initializes its own exports.
NAPI_MODULE(NODE_GYP_MODULE_NAME, initialize)
