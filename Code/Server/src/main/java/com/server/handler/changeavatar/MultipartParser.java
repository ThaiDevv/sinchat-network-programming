package com.server.handler.changeavatar;

import java.util.*;

public class MultipartParser {

    public static class FilePart {
        public final String filename;
        public final String contentType;
        public final byte[] data;

        public FilePart(String filename, String contentType, byte[] data) {
            this.filename = filename;
            this.contentType = contentType;
            this.data = data;
        }
    }

    private final Map<String, String> fields = new HashMap<>();
    private final Map<String, FilePart> files = new HashMap<>();

    public MultipartParser(byte[] body, String boundary) {
        parse(body, ("--" + boundary).getBytes());
    }

    private void parse(byte[] body, byte[] delimiter) {
        List<byte[]> parts = splitBytes(body, delimiter);

        for (byte[] part : parts) {
            if (part.length == 0) continue;
            if (startsWith(part, "--".getBytes())) continue; // closing boundary

            // Tìm cuối headers (double CRLF)
            int headerEnd = indexOfSequence(part, "\r\n\r\n".getBytes());
            if (headerEnd < 0) continue;

            String headers = new String(part, 0, headerEnd);
            byte[] content = Arrays.copyOfRange(part, headerEnd + 4, part.length);

            // Bỏ CRLF cuối
            if (content.length >= 2
                    && content[content.length - 2] == '\r'
                    && content[content.length - 1] == '\n') {
                content = Arrays.copyOfRange(content, 0, content.length - 2);
            }

            String name = extractParam(headers, "name");
            String filename = extractParam(headers, "filename");

            if (name == null) continue;

            if (filename != null) {
                // Đây là file
                files.put(name, new FilePart(filename, extractContentType(headers), content));
            } else {
                // Field thông thường
                fields.put(name, new String(content).trim());
            }
        }
    }

    private String extractParam(String headers, String param) {
        String search = param + "=\"";
        int start = headers.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = headers.indexOf("\"", start);
        return (end < 0) ? null : headers.substring(start, end);
    }

    private String extractContentType(String headers) {
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-type:")) {
                return line.substring("content-type:".length()).trim();
            }
        }
        return "application/octet-stream";
    }

    private List<byte[]> splitBytes(byte[] data, byte[] delimiter) {
        List<byte[]> parts = new ArrayList<>();
        int start = 0;

        for (int i = 0; i <= data.length - delimiter.length; i++) {
            if (matches(data, i, delimiter)) {
                int partStart = start;
                // Bỏ CRLF đầu part
                if (partStart < i && data[partStart] == '\r') partStart++;
                if (partStart < i && data[partStart] == '\n') partStart++;

                if (i > partStart) {
                    parts.add(Arrays.copyOfRange(data, partStart, i));
                }
                start = i + delimiter.length;
                i += delimiter.length - 1;
            }
        }
        return parts;
    }

    private int indexOfSequence(byte[] data, byte[] seq) {
        for (int i = 0; i <= data.length - seq.length; i++) {
            if (matches(data, i, seq)) return i;
        }
        return -1;
    }

    private boolean matches(byte[] data, int offset, byte[] pattern) {
        for (int j = 0; j < pattern.length; j++) {
            if (offset + j >= data.length || data[offset + j] != pattern[j]) return false;
        }
        return true;
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        return matches(data, 0, prefix);
    }

    public String getField(String name) { return fields.get(name); }
    public FilePart getFile(String name) { return files.get(name); }
}
