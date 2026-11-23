// JNI bridge between Kotlin (audio.Mp3Encoder) and libmp3lame.
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include "lame.h"

#define JNI_FN(name) Java_com_faradaymicrosystems_nobodygenerator_audio_Mp3Encoder_##name

static jbyteArray to_array(JNIEnv *env, const unsigned char *buf, int n) {
    if (n < 0) n = 0;
    jbyteArray out = (*env)->NewByteArray(env, n);
    if (out != NULL && n > 0) (*env)->SetByteArrayRegion(env, out, 0, n, (const jbyte *) buf);
    return out;
}

JNIEXPORT jlong JNICALL
JNI_FN(nativeInit)(JNIEnv *env, jclass clazz, jint sampleRate, jint kbps, jstring title, jstring artist) {
    (void) clazz;
    lame_t l = lame_init();
    if (l == NULL) return 0;
    lame_set_in_samplerate(l, sampleRate);
    lame_set_out_samplerate(l, sampleRate);
    lame_set_num_channels(l, 1);
    lame_set_mode(l, MONO);
    lame_set_brate(l, kbps);
    lame_set_quality(l, 5);

    id3tag_init(l);
    id3tag_add_v2(l);
    if (title != NULL) {
        const char *t = (*env)->GetStringUTFChars(env, title, NULL);
        if (t != NULL) { id3tag_set_title(l, t); (*env)->ReleaseStringUTFChars(env, title, t); }
    }
    if (artist != NULL) {
        const char *a = (*env)->GetStringUTFChars(env, artist, NULL);
        if (a != NULL) { id3tag_set_artist(l, a); (*env)->ReleaseStringUTFChars(env, artist, a); }
    }

    if (lame_init_params(l) < 0) {
        lame_close(l);
        return 0;
    }
    return (jlong) (intptr_t) l;
}

JNIEXPORT jbyteArray JNICALL
JNI_FN(nativeEncode)(JNIEnv *env, jclass clazz, jlong handle, jshortArray pcm, jint count) {
    (void) clazz;
    lame_t l = (lame_t) (intptr_t) handle;
    if (l == NULL || count <= 0) return to_array(env, NULL, 0);
    int cap = (int) (1.25 * count) + 7200;
    unsigned char *buf = (unsigned char *) malloc((size_t) cap);
    if (buf == NULL) return to_array(env, NULL, 0);
    jshort *samples = (*env)->GetShortArrayElements(env, pcm, NULL);
    int n = lame_encode_buffer(l, samples, samples, count, buf, cap);
    (*env)->ReleaseShortArrayElements(env, pcm, samples, JNI_ABORT);
    jbyteArray out = to_array(env, buf, n);
    free(buf);
    return out;
}

JNIEXPORT jbyteArray JNICALL
JNI_FN(nativeFlush)(JNIEnv *env, jclass clazz, jlong handle) {
    (void) clazz;
    lame_t l = (lame_t) (intptr_t) handle;
    if (l == NULL) return to_array(env, NULL, 0);
    unsigned char buf[16384];
    int n = lame_encode_flush(l, buf, (int) sizeof(buf));
    return to_array(env, buf, n);
}

JNIEXPORT void JNICALL
JNI_FN(nativeClose)(JNIEnv *env, jclass clazz, jlong handle) {
    (void) env; (void) clazz;
    lame_t l = (lame_t) (intptr_t) handle;
    if (l != NULL) lame_close(l);
}
