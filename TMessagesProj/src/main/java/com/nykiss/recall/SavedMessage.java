package com.nykiss.recall;

/**
 * Plain data holder for a saved (deleted or edited) message.
 * Not using Room — we piggyback on Telegram's existing SQLite (MessagesStorage)
 * to avoid adding a heavy dependency. The table is created lazily.
 */
public class SavedMessage {
    public long   userId;
    public long   dialogId;
    public int    messageId;
    public long   topicId;
    public String text;
    public int    date;
    public int    fromId;
    public String mediaPath;  // local path to saved attachment
    public String mimeType;
    public boolean isEdited;
    public long   savedAt;

    public SavedMessage() {}
}
