package com.server.handler.changeavatar;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MultipartParserTest {

    private byte[] buildMultipartBody(String boundary, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append("--").append(boundary).append("\r\n");
            sb.append(part);
        }
        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void testParseSimpleField() {
        String boundary = "----TestBoundary123";
        byte[] body = buildMultipartBody(boundary,
                "Content-Disposition: form-data; name=\"userId\"\r\n\r\n42\r\n"
        );

        MultipartParser parser = new MultipartParser(body, boundary);
        assertEquals("42", parser.getField("userId"));
    }

    @Test
    void testParseMultipleFields() {
        String boundary = "----TestBoundary456";
        byte[] body = buildMultipartBody(boundary,
                "Content-Disposition: form-data; name=\"field1\"\r\n\r\nvalue1\r\n",
                "Content-Disposition: form-data; name=\"field2\"\r\n\r\nvalue2\r\n"
        );

        MultipartParser parser = new MultipartParser(body, boundary);
        assertEquals("value1", parser.getField("field1"));
        assertEquals("value2", parser.getField("field2"));
    }

    @Test
    void testParseFilePart() {
        String boundary = "----TestBoundary789";
        String fileContent = "fake image data";
        byte[] body = buildMultipartBody(boundary,
                "Content-Disposition: form-data; name=\"avatar\"; filename=\"photo.png\"\r\n" +
                "Content-Type: image/png\r\n\r\n" + fileContent + "\r\n"
        );

        MultipartParser parser = new MultipartParser(body, boundary);
        MultipartParser.FilePart filePart = parser.getFile("avatar");
        assertNotNull(filePart);
        assertEquals("photo.png", filePart.filename);
        assertEquals("image/png", filePart.contentType);
        assertEquals(fileContent, new String(filePart.data, StandardCharsets.UTF_8));
    }

    @Test
    void testParseFieldAndFile() {
        String boundary = "----Boundary000";
        byte[] body = buildMultipartBody(boundary,
                "Content-Disposition: form-data; name=\"userId\"\r\n\r\n7\r\n",
                "Content-Disposition: form-data; name=\"avatar\"; filename=\"img.jpg\"\r\n" +
                "Content-Type: image/jpeg\r\n\r\nJPEG_DATA\r\n"
        );

        MultipartParser parser = new MultipartParser(body, boundary);
        assertEquals("7", parser.getField("userId"));

        MultipartParser.FilePart file = parser.getFile("avatar");
        assertNotNull(file);
        assertEquals("img.jpg", file.filename);
        assertEquals("image/jpeg", file.contentType);
    }

    @Test
    void testMissingField() {
        String boundary = "----BoundaryXYZ";
        byte[] body = buildMultipartBody(boundary,
                "Content-Disposition: form-data; name=\"userId\"\r\n\r\n1\r\n"
        );

        MultipartParser parser = new MultipartParser(body, boundary);
        assertNull(parser.getField("nonexistent"));
        assertNull(parser.getFile("nonexistent"));
    }

    @Test
    void testFilePartDataIntegrity() {
        String boundary = "----BoundaryData";
        String binaryLikeData = "This\thas\ttabs\nand\nnewlines";
        byte[] body = buildMultipartBody(boundary,
                "Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n" +
                "Content-Type: text/plain\r\n\r\n" + binaryLikeData + "\r\n"
        );

        MultipartParser parser = new MultipartParser(body, boundary);
        MultipartParser.FilePart file = parser.getFile("file");
        assertNotNull(file);
        assertEquals(binaryLikeData, new String(file.data, StandardCharsets.UTF_8));
    }
}
