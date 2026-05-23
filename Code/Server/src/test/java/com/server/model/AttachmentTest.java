package com.server.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentTest {

    @Test
    void testDefaultConstructor() {
        Attachment att = new Attachment();
        assertEquals(0, att.getId());
        assertEquals(0, att.getMessageId());
        assertNull(att.getFileUrl());
        assertNull(att.getFileName());
        assertEquals(0, att.getFileSize());
        assertNull(att.getMimeType());
    }

    @Test
    void testParameterizedConstructor() {
        Attachment att = new Attachment(1L, 100L, "http://files.com/doc.pdf",
                "document.pdf", 204800L, "application/pdf");

        assertEquals(1L, att.getId());
        assertEquals(100L, att.getMessageId());
        assertEquals("http://files.com/doc.pdf", att.getFileUrl());
        assertEquals("document.pdf", att.getFileName());
        assertEquals(204800L, att.getFileSize());
        assertEquals("application/pdf", att.getMimeType());
    }

    @Test
    void testSettersAndGetters() {
        Attachment att = new Attachment();
        att.setId(5L);
        att.setMessageId(50L);
        att.setFileUrl("/uploads/photo.jpg");
        att.setFileName("photo.jpg");
        att.setFileSize(1024000L);
        att.setMimeType("image/jpeg");

        assertEquals(5L, att.getId());
        assertEquals(50L, att.getMessageId());
        assertEquals("/uploads/photo.jpg", att.getFileUrl());
        assertEquals("photo.jpg", att.getFileName());
        assertEquals(1024000L, att.getFileSize());
        assertEquals("image/jpeg", att.getMimeType());
    }
}
