package com.nykiss.recall;

import android.os.Environment;
import android.text.TextUtils;
import com.nykiss.ghost.NykissConfig;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class RecallStorage {

    private static final String TABLE = "nykiss_saved_messages";
    // saved attachments folder
    public static final File attachmentsDir = new File(
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NYKISS"),
            "SavedAttachments"
    );

    private static volatile boolean tableReady = false;

    private static void ensureTable(SQLiteDatabase db) {
        if (tableReady) return;
        try {
            db.executeFast(
                "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER," +
                "dialog_id INTEGER," +
                "message_id INTEGER," +
                "topic_id INTEGER," +
                "text TEXT," +
                "date INTEGER," +
                "from_id INTEGER," +
                "media_path TEXT," +
                "mime_type TEXT," +
                "is_edited INTEGER DEFAULT 0," +
                "saved_at INTEGER" +
                ")"
            ).dispose();
            db.executeFast(
                "CREATE INDEX IF NOT EXISTS nykiss_idx ON " + TABLE + "(dialog_id, message_id)"
            ).dispose();
            tableReady = true;
            if (!attachmentsDir.exists()) {
                attachmentsDir.mkdirs();
                try { new File(attachmentsDir, ".nomedia").createNewFile(); } catch (IOException ignored) {}
            }
        } catch (SQLiteException e) {
            FileLog.e("RecallStorage ensureTable", e);
        }
    }

    public static void saveDeletedMessage(int accountId, TLRPC.Message msg, long dialogId) {
        if (!NykissConfig.saveDeletedMessages || msg == null) return;
        saveMessage(accountId, msg, dialogId, false);
    }

    public static void saveEditedMessage(int accountId, TLRPC.Message oldMsg, long dialogId) {
        if (!NykissConfig.saveEditedMessages || oldMsg == null) return;
        saveMessage(accountId, oldMsg, dialogId, true);
    }

    private static void saveMessage(int accountId, TLRPC.Message msg, long dialogId, boolean isEdited) {
        AccountInstance.getInstance(accountId).getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                SQLiteDatabase db = AccountInstance.getInstance(accountId).getMessagesStorage().getDatabase();
                if (db == null) return;
                ensureTable(db);

                // Check duplicate
                Integer count = db.executeInt(
                    "SELECT COUNT(*) FROM " + TABLE + " WHERE dialog_id=? AND message_id=? AND is_edited=?",
                    dialogId, msg.id, isEdited ? 1 : 0
                );
                if (count != null && count > 0) return;

                String mediaPath = null;
                String mimeType  = null;
                if (msg.media != null) {
                    mediaPath = copyMediaToSavedDir(accountId, msg);
                    mimeType  = getMimeType(msg);
                }

                long now = System.currentTimeMillis() / 1000L;
                long fromId = 0;
                if (msg.from_id instanceof TLRPC.TL_peerUser) {
                    fromId = ((TLRPC.TL_peerUser) msg.from_id).user_id;
                }

                var st = db.executeFast(
                    "INSERT INTO " + TABLE +
                    " (user_id, dialog_id, message_id, topic_id, text, date, from_id, media_path, mime_type, is_edited, saved_at)" +
                    " VALUES(?,?,?,?,?,?,?,?,?,?,?)"
                );
                st.bindLong(1, AccountInstance.getInstance(accountId).getUserConfig().getClientUserId());
                st.bindLong(2, dialogId);
                st.bindInteger(3, msg.id);
                st.bindLong(4, msg.reply_to != null ? msg.reply_to.reply_to_top_id : 0);
                st.bindString(5, msg.message != null ? msg.message : "");
                st.bindInteger(6, msg.date);
                st.bindLong(7, fromId);
                st.bindString(8, mediaPath != null ? mediaPath : "");
                st.bindString(9, mimeType  != null ? mimeType  : "");
                st.bindInteger(10, isEdited ? 1 : 0);
                st.bindLong(11, now);
                st.step();
                st.dispose();
            } catch (Exception e) {
                FileLog.e("RecallStorage.saveMessage", e);
            }
        });
    }

    private static String copyMediaToSavedDir(int accountId, TLRPC.Message msg) {
        try {
            File src = findCachedFile(accountId, msg);
            if (src == null || !src.exists()) return null;

            if (!attachmentsDir.exists()) attachmentsDir.mkdirs();
            String name = src.getName();
            File dst = new File(attachmentsDir, msg.id + "_" + name);
            if (!dst.exists()) {
                try (FileChannel in  = new FileInputStream(src).getChannel();
                     FileChannel out = new FileOutputStream(dst).getChannel()) {
                    out.transferFrom(in, 0, in.size());
                }
            }
            return dst.getAbsolutePath();
        } catch (Exception e) {
            FileLog.e("RecallStorage.copyMedia", e);
            return null;
        }
    }

    private static File findCachedFile(int accountId, TLRPC.Message msg) {
        // Telegram caches files in getCacheDir/files/
        // MessagesStorage doesn't expose cache path directly, use FileLoader
        try {
            File f = org.telegram.messenger.FileLoader.getInstance(accountId).getPathToMessage(msg);
            if (f != null && f.exists()) return f;
        } catch (Exception ignored) {}
        return null;
    }

    private static String getMimeType(TLRPC.Message msg) {
        if (msg.media instanceof TLRPC.TL_messageMediaPhoto) return "image/jpeg";
        if (msg.media instanceof TLRPC.TL_messageMediaDocument && msg.media.document != null) {
            for (TLRPC.DocumentAttribute attr : msg.media.document.attributes) {
                if (attr instanceof TLRPC.TL_documentAttributeVideo) return "video/mp4";
                if (attr instanceof TLRPC.TL_documentAttributeAudio) return "audio/ogg";
            }
            if (!TextUtils.isEmpty(msg.media.document.mime_type)) return msg.media.document.mime_type;
        }
        if (msg.media instanceof TLRPC.TL_messageMediaGeo ||
            msg.media instanceof TLRPC.TL_messageMediaGeoLive) return "geo";
        return null;
    }
}
