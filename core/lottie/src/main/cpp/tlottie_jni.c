#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include "tlottie.h"

#define MAX_DATA (8 * 1024 * 1024)
#define MAX_SIDE 4096
#define MAX_REPLACEMENTS 32

JNIEXPORT jlong JNICALL
Java_cn_ellerotta_core_lottie_LottieNative_create(JNIEnv* env, jclass cls, jbyteArray data, jint fitz, jobjectArray layers, jintArray colors) {
    (void) cls;
    if (data == NULL || layers == NULL || colors == NULL) return 0;
    jsize size = (*env)->GetArrayLength(env, data);
    jsize count = (*env)->GetArrayLength(env, layers);
    if (size <= 0 || size > MAX_DATA || count > MAX_REPLACEMENTS || count != (*env)->GetArrayLength(env, colors)) return 0;

    TLottieLayerColorReplacement replacements[MAX_REPLACEMENTS];
    jstring names[MAX_REPLACEMENTS];
    jint argb[MAX_REPLACEMENTS];
    (*env)->GetIntArrayRegion(env, colors, 0, count, argb);
    jsize ready = 0;
    int ok = 1;
    for (; ready < count; ready++) {
        names[ready] = (jstring) (*env)->GetObjectArrayElement(env, layers, ready);
        const char* chars = names[ready] == NULL ? NULL : (*env)->GetStringUTFChars(env, names[ready], NULL);
        if (chars == NULL) {
            ok = 0;
            break;
        }
        replacements[ready].layer_name_prefix = (const uint8_t*) chars;
        replacements[ready].layer_name_prefix_len = (size_t) (*env)->GetStringUTFLength(env, names[ready]);
        replacements[ready].color = (uint32_t) argb[ready];
    }

    TLottieInstance* instance = NULL;
    jbyte* bytes = ok ? (*env)->GetByteArrayElements(env, data, NULL) : NULL;
    if (bytes != NULL) {
        instance = tlottie_new_with_options((const uint8_t*) bytes, (size_t) size, (uint32_t) fitz, replacements, (size_t) count, NULL, 0, TLOTTIE_CHANNEL_RGBA);
        (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    }
    for (jsize i = 0; i < ready; i++) {
        (*env)->ReleaseStringUTFChars(env, names[i], (const char*) replacements[i].layer_name_prefix);
        (*env)->DeleteLocalRef(env, names[i]);
    }
    return (jlong) (intptr_t) instance;
}

static TLottieInstance* from(jlong handle) { return (TLottieInstance*) (intptr_t) handle; }

JNIEXPORT jint JNICALL
Java_cn_ellerotta_core_lottie_LottieNative_width(JNIEnv* env, jclass cls, jlong handle) {
    (void) env, (void) cls;
    return (jint) tlottie_width(from(handle));
}

JNIEXPORT jint JNICALL
Java_cn_ellerotta_core_lottie_LottieNative_height(JNIEnv* env, jclass cls, jlong handle) {
    (void) env, (void) cls;
    return (jint) tlottie_height(from(handle));
}

JNIEXPORT jfloat JNICALL
Java_cn_ellerotta_core_lottie_LottieNative_frameRate(JNIEnv* env, jclass cls, jlong handle) {
    (void) env, (void) cls;
    return tlottie_frame_rate(from(handle));
}

JNIEXPORT jint JNICALL
Java_cn_ellerotta_core_lottie_LottieNative_frameCount(JNIEnv* env, jclass cls, jlong handle) {
    (void) env, (void) cls;
    return (jint) tlottie_frame_count(from(handle));
}

JNIEXPORT jboolean JNICALL
Java_cn_ellerotta_core_lottie_LottieNative_render(JNIEnv* env, jclass cls, jlong handle, jfloat frame, jint width, jint height, jobject buffer) {
    (void) cls;
    if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE) return JNI_FALSE;
    uint32_t* pixels = (uint32_t*) (*env)->GetDirectBufferAddress(env, buffer);
    jlong capacity = (*env)->GetDirectBufferCapacity(env, buffer);
    if (pixels == NULL || capacity < 0) return JNI_FALSE;
    int32_t status = tlottie_render(from(handle), frame, (uint32_t) width, (uint32_t) height, pixels, (size_t) capacity / 4, 1);
    return status == TLOTTIE_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_cn_ellerotta_core_lottie_LottieNative_destroy(JNIEnv* env, jclass cls, jlong handle) {
    (void) env, (void) cls;
    tlottie_drop(from(handle));
}
