package com.server.model;

public class Attachment {
    private long id;            // bigint NOT NULL AUTO_INCREMENT
    private long messageId;     // bigint - FK den messages.id
    private String fileUrl;     // text - duong dan file
    private String fileName;    // varchar(255) - ten file goc
    private long fileSize;      // bigint - kich thuoc tinh bang byte
    private String mimeType;    // varchar(100) - loai file

    public Attachment() {}

    public Attachment(long id, long messageId, String fileUrl, String fileName,
                      long fileSize, String mimeType) {
        this.id = id;
        this.messageId = messageId;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getMessageId() { return messageId; }
    public void setMessageId(long messageId) { this.messageId = messageId; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
}
