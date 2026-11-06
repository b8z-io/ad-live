package io.b8z.adlive;

import android.util.Log;
import java.nio.ByteBuffer;

/** NDI® send, via libadndi.so -> libndi.so. NDI® is a registered trademark of Vizrt NDI AB. */
final class Ndi {
    static final boolean AVAILABLE;
    static {
        boolean ok = false;
        try { System.loadLibrary("ndi"); System.loadLibrary("adndi"); ok = true; }
        catch (Throwable t) { Log.e("ADLive-NDI", "NDI library not loaded", t); }
        AVAILABLE = ok;
    }
    static native String version();
    static native boolean start(String name);
    static native void stop();
    static native int connections();
    static native void sendVideo(ByteBuffer buf, int offset, int w, int h, int fpsN, int fpsD);
    static native void sendAudio(ByteBuffer buf, int offset, int sampleRate, int channels, int samples);
    private Ndi() {}
}
