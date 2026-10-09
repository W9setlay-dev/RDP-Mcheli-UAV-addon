package net.vas.rdp.uav.client.vision;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PixelBufferPoolTest {
    @Test
    public void boundsOutstandingPixelArraysAndReusesReleasedStorage() {
        PixelBufferPool pool = new PixelBufferPool();
        byte[] first = pool.acquire(16);
        byte[] second = pool.acquire(16);
        byte[] third = pool.acquire(16);

        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);
        assertNull(pool.acquire(16));

        pool.release(second);
        assertSame(second, pool.acquire(16));
        pool.release(first);
        pool.release(second);
        pool.release(third);
        assertArrayEquals(new byte[16], pool.acquire(16));
    }
}
