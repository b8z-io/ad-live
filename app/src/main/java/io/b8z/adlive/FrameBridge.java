package io.b8z.adlive;

import android.util.Base64;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Loopback-only WebSocket endpoint (127.0.0.1, random port) that receives binary messages from the page
 * and hands them to the NDI sender. Message layout, little-endian:
 *   'V' u8, pad[3], w i32, h i32, fpsN i32, fpsD i32, then w*h*2 bytes UYVY (top row first)
 *   'A' u8, pad[3], sampleRate i32, channels i32, samples i32, pad i32, then channels*samples float32 planar
 */
final class FrameBridge implements Runnable {
    private static final String TAG = "ADLive-Bridge";
    private static final int HEADER = 20;
    private final ServerSocket server;
    private volatile boolean running = true;
    volatile long videoFrames, audioFrames;

    private ByteBuffer msg = ByteBuffer.allocateDirect(4 << 20).order(ByteOrder.LITTLE_ENDIAN);
    private byte[] chunk = new byte[1 << 16];

    FrameBridge() throws IOException {
        server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(this, "ndi-bridge");
        t.setPriority(Thread.MAX_PRIORITY - 1);
        t.setDaemon(true);
        t.start();
    }

    int port() { return server.getLocalPort(); }

    void close() { running = false; try { server.close(); } catch (IOException ignored) {} }

    @Override public void run() {
        while (running) {
            try (Socket s = server.accept()) {
                s.setTcpNoDelay(true);
                s.setReceiveBufferSize(4 << 20);
                serve(s);
            } catch (IOException e) {
                if (running) Log.w(TAG, "client ended: " + e.getMessage());
            }
        }
    }

    private void serve(Socket s) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 1 << 16));
        OutputStream out = s.getOutputStream();
        // --- handshake
        String key = null, line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int c = line.indexOf(':');
            if (c > 0 && line.substring(0, c).trim().equalsIgnoreCase("Sec-WebSocket-Key")) key = line.substring(c + 1).trim();
        }
        if (key == null) return;
        String accept;
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            accept = Base64.encodeToString(sha1.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)), Base64.NO_WRAP);
        } catch (Exception e) { return; }
        out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        Log.i(TAG, "page connected");
        // --- frames
        int msgLen = 0, msgOp = 0;
        byte[] mask = new byte[4];
        while (running) {
            int b0 = in.readUnsignedByte(), b1 = in.readUnsignedByte();
            boolean fin = (b0 & 0x80) != 0;
            int op = b0 & 0x0F;
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) len = in.readUnsignedShort();
            else if (len == 127) len = in.readLong();
            if (masked) in.readFully(mask);
            if (op >= 8) { // control
                byte[] p = new byte[(int) len];
                in.readFully(p);
                if (masked) for (int i = 0; i < p.length; i++) p[i] ^= mask[i & 3];
                if (op == 8) { writeFrame(out, 8, p); return; }
                if (op == 9) writeFrame(out, 10, p);
                continue;
            }
            if (op != 0) { msgOp = op; msgLen = 0; }
            ensure(msgLen + (int) len);
            long left = len; int mi = 0;
            msg.position(msgLen);
            while (left > 0) {
                int n = (int) Math.min(chunk.length, left);
                in.readFully(chunk, 0, n);
                if (masked) for (int i = 0; i < n; i++, mi++) chunk[i] ^= mask[mi & 3];
                msg.put(chunk, 0, n);
                left -= n;
            }
            msgLen += (int) len;
            if (fin) { if (msgOp == 2) dispatch(msgLen); msgLen = 0; }
        }
    }

    private void dispatch(int len) {
        if (len < HEADER) return;
        byte type = msg.get(0);
        int a = msg.getInt(4), b = msg.getInt(8), c = msg.getInt(12), d = msg.getInt(16);
        if (type == 'V') {
            if (a <= 0 || b <= 0 || len < HEADER + a * b * 2) return;
            Ndi.sendVideo(msg, HEADER, a, b, c, d);
            videoFrames++;
        } else if (type == 'A') {
            if (b <= 0 || c <= 0 || len < HEADER + b * c * 4) return;
            Ndi.sendAudio(msg, HEADER, a, b, c);
            audioFrames++;
        }
    }

    private void ensure(int cap) {
        if (cap <= msg.capacity()) return;
        ByteBuffer n = ByteBuffer.allocateDirect(Math.max(cap, msg.capacity() * 2)).order(ByteOrder.LITTLE_ENDIAN);
        msg.position(0); msg.limit(msg.capacity()); n.put(msg); msg = n;
    }

    private static void writeFrame(OutputStream out, int op, byte[] p) throws IOException {
        synchronized (out) {
            out.write(0x80 | op);
            out.write(p.length); // control frames are < 126 bytes
            out.write(p);
            out.flush();
        }
    }

    private static String readLine(DataInputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) throw new IOException("header too long");
        }
        return c == -1 && sb.length() == 0 ? null : sb.toString();
    }
}
