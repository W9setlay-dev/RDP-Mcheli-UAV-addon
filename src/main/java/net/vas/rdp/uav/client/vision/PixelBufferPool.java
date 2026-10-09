package net.vas.rdp.uav.client.vision;

import java.util.ArrayDeque;
import java.util.Iterator;

/** Small fixed-capacity pool; outstanding worker frames count against its bound. */
final class PixelBufferPool {
    private static final int MAX_BUFFERS = 3;
    private final ArrayDeque<byte[]> available = new ArrayDeque<>();
    private int allocated;

    synchronized byte[] acquire(int size) {
        Iterator<byte[]> iterator = available.iterator();
        while (iterator.hasNext()) {
            byte[] buffer = iterator.next();
            iterator.remove();
            if (buffer.length == size) return buffer;
            allocated--;
        }
        if (allocated >= MAX_BUFFERS) return null;
        allocated++;
        return new byte[size];
    }

    synchronized void release(byte[] buffer) {
        if (buffer == null) return;
        if (available.size() < MAX_BUFFERS) {
            available.addLast(buffer);
        } else {
            allocated--;
        }
    }
}
