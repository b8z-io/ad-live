// Another Distraction · Live — thin JNI wrapper around the NDI® send API.
// NDI® is a registered trademark of Vizrt NDI AB. https://ndi.video/
#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <cstdint>
#include <Processing.NDI.Lib.h>

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "ADLive-NDI", __VA_ARGS__)

static std::mutex g_mu;
static NDIlib_send_instance_t g_send = nullptr;
static bool g_init = false;

extern "C" {

JNIEXPORT jstring JNICALL Java_io_b8z_adlive_Ndi_version(JNIEnv* env, jclass) {
    return env->NewStringUTF(NDIlib_version());
}

JNIEXPORT jboolean JNICALL Java_io_b8z_adlive_Ndi_start(JNIEnv* env, jclass, jstring jname) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (!g_init) {
        g_init = NDIlib_initialize();
        if (!g_init) { LOG("NDIlib_initialize failed (CPU not supported?)"); return JNI_FALSE; }
    }
    if (g_send) return JNI_TRUE;
    const char* name = env->GetStringUTFChars(jname, nullptr);
    NDIlib_send_create_t c;
    c.p_ndi_name = name;
    c.p_groups = nullptr;
    c.clock_video = false;   // frames are paced by the renderer
    c.clock_audio = false;
    g_send = NDIlib_send_create(&c);
    env->ReleaseStringUTFChars(jname, name);
    LOG("send_create -> %p", (void*)g_send);
    return g_send ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_io_b8z_adlive_Ndi_stop(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (g_send) { NDIlib_send_destroy(g_send); g_send = nullptr; LOG("sender destroyed"); }
}

JNIEXPORT jint JNICALL Java_io_b8z_adlive_Ndi_connections(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lk(g_mu);
    return g_send ? NDIlib_send_get_no_connections(g_send, 0) : -1;
}

// UYVY 4:2:2, top row first, stride = w*2
JNIEXPORT void JNICALL Java_io_b8z_adlive_Ndi_sendVideo(JNIEnv* env, jclass, jobject buf, jint off,
                                                        jint w, jint h, jint fpsN, jint fpsD) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (!g_send) return;
    auto* base = static_cast<uint8_t*>(env->GetDirectBufferAddress(buf));
    if (!base) return;
    NDIlib_video_frame_v2_t f;
    f.xres = w; f.yres = h;
    f.FourCC = NDIlib_FourCC_video_type_UYVY;
    f.frame_rate_N = fpsN; f.frame_rate_D = fpsD;
    f.picture_aspect_ratio = (float)w / (float)h;
    f.frame_format_type = NDIlib_frame_format_type_progressive;
    f.timecode = NDIlib_send_timecode_synthesize;
    f.p_data = base + off;
    f.line_stride_in_bytes = w * 2;
    f.p_metadata = nullptr;
    f.timestamp = 0;
    NDIlib_send_send_video_v2(g_send, &f);   // synchronous: buffer is free again on return
}

// planar float: ch * ns floats
JNIEXPORT void JNICALL Java_io_b8z_adlive_Ndi_sendAudio(JNIEnv* env, jclass, jobject buf, jint off,
                                                        jint sr, jint ch, jint ns) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (!g_send) return;
    auto* base = static_cast<uint8_t*>(env->GetDirectBufferAddress(buf));
    if (!base) return;
    NDIlib_audio_frame_v3_t a;
    a.sample_rate = sr; a.no_channels = ch; a.no_samples = ns;
    a.timecode = NDIlib_send_timecode_synthesize;
    a.FourCC = NDIlib_FourCC_audio_type_FLTP;
    a.p_data = base + off;
    a.channel_stride_in_bytes = ns * 4;
    a.p_metadata = nullptr;
    a.timestamp = 0;
    NDIlib_send_send_audio_v3(g_send, &a);
}

}
