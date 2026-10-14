package com.nykiss.ghost;

import java.util.concurrent.atomic.AtomicInteger;

public class GhostState {
    // One-shot: allow one read packet after sending a message
    private static final AtomicInteger allowReadPacketCount = new AtomicInteger(0);

    public static boolean consumeAllowReadPacket() {
        if (allowReadPacketCount.get() > 0) {
            allowReadPacketCount.decrementAndGet();
            return true;
        }
        return false;
    }

    public static void allowReadPacketOnce() {
        allowReadPacketCount.set(1);
    }

    public static boolean shouldSendReadPacket() {
        return NykissConfig.sendReadPackets || consumeAllowReadPacket();
    }
}
