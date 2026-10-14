package com.nykiss.ghost;

import android.app.Activity;
import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

public class NykissConfig {
    private static final Object sync = new Object();
    private static boolean loaded;

    public static SharedPreferences prefs;
    public static SharedPreferences.Editor editor;

    // Ghost Mode flags
    public static boolean sendReadPackets;
    public static boolean sendOnlinePackets;
    public static boolean sendUploadProgress;
    public static boolean sendOfflineAfterOnline;

    // Anti-Recall flags
    public static boolean saveDeletedMessages;
    public static boolean saveEditedMessages;

    static {
        load();
    }

    public static void load() {
        synchronized (sync) {
            if (loaded) return;
            prefs = ApplicationLoader.applicationContext.getSharedPreferences("nykissconfig", Activity.MODE_PRIVATE);
            editor = prefs.edit();

            sendReadPackets    = prefs.getBoolean("sendReadPackets", true);
            sendOnlinePackets  = prefs.getBoolean("sendOnlinePackets", true);
            sendUploadProgress = prefs.getBoolean("sendUploadProgress", true);
            sendOfflineAfterOnline = prefs.getBoolean("sendOfflineAfterOnline", false);

            saveDeletedMessages = prefs.getBoolean("saveDeletedMessages", true);
            saveEditedMessages  = prefs.getBoolean("saveEditedMessages", true);

            loaded = true;
        }
    }

    public static boolean isGhostModeActive() {
        return !sendReadPackets && !sendOnlinePackets && !sendUploadProgress && sendOfflineAfterOnline;
    }

    public static void setGhostMode(boolean enabled) {
        sendReadPackets    = !enabled;
        sendOnlinePackets  = !enabled;
        sendUploadProgress = !enabled;
        sendOfflineAfterOnline = enabled;

        editor.putBoolean("sendReadPackets",    sendReadPackets).apply();
        editor.putBoolean("sendOnlinePackets",  sendOnlinePackets).apply();
        editor.putBoolean("sendUploadProgress", sendUploadProgress).apply();
        editor.putBoolean("sendOfflineAfterOnline", sendOfflineAfterOnline).apply();
    }

    public static void toggleGhostMode() {
        setGhostMode(!isGhostModeActive());
    }
}
