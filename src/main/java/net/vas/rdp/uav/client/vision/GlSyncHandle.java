package net.vas.rdp.uav.client.vision;

/** Tracks the lifetime of a native LWJGL 3 fence handle. */
final class GlSyncHandle {
    private long handle;

    boolean isPresent() {
        return handle != 0L;
    }

    long value() {
        return handle;
    }

    boolean setCreated(long createdHandle) {
        if (createdHandle == 0L) return false;
        if (handle != 0L) {
            throw new IllegalStateException("A GPU fence is already active for this slot");
        }
        handle = createdHandle;
        return true;
    }

    long take() {
        long releasedHandle = handle;
        handle = 0L;
        return releasedHandle;
    }
}
