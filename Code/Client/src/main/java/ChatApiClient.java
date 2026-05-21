import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatApiClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final Pattern JSON_FIELD_PATTERN = Pattern.compile(
            "\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\""
    );
    private static final Pattern JSON_NUMBER_FIELD_PATTERN = Pattern.compile(
            "\"%s\"\\s*:\\s*(\\d+)"
    );

    private final HttpClient client;
    private final String baseUrl;

    // Base URL can be changed without editing code:
    // -Dchatapp.api.baseUrl=https://network-programming-project.onrender.com
    // or CHATAPP_API_BASE_URL=https://network-programming-project.onrender.com
    public ChatApiClient() {
        this(resolveBaseUrl());
    }

    public ChatApiClient(String baseUrl) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public ApiResponse register(String username, String password, String email)
            throws IOException, InterruptedException {
        String body = """
                {"username":"%s","password":"%s","email":"%s"}
                """.formatted(escapeJson(username), escapeJson(password), escapeJson(email));
        return send("POST", "/api/register", body);
    }

    public ApiResponse login(String username, String password)
            throws IOException, InterruptedException {
        String body = """
                {"username":"%s","password":"%s"}
                """.formatted(escapeJson(username), escapeJson(password));
        return send("POST", "/api/login", body);
    }

    public ApiResponse requestPasswordResetCode(String username)
            throws IOException, InterruptedException {
        String body = """
                {"username":"%s"}
                """.formatted(escapeJson(username));
        return send("POST", "/api/forgotpwd", body); // Wait, forgotpwd was GET in original code, I'll keep it as it was. Let's fix getConversations.
    }

    public ApiResponse getConversations(long userId) throws IOException, InterruptedException {
        return send("GET", "/api/user/conversations?userId=" + userId, "");
    }

    public ApiResponse getMessages(long conversationId) throws IOException, InterruptedException {
        return send("GET", "/api/messages?conversationId=" + conversationId, "");
    }

    /**
     * Upload avatar image as multipart/form-data to /api/change-avatar.
     * @param userId  ID của user cần đổi avatar
     * @param imageBytes  Dữ liệu ảnh đã crop (PNG)
     * @param filename  Tên file (vd: "avatar.png")
     */
    public ApiResponse uploadAvatar(long userId, byte[] imageBytes, String filename)
            throws IOException, InterruptedException {

        String boundary = "----AvatarBoundary" + System.currentTimeMillis();
        String CRLF = "\r\n";

        // Build multipart body
        StringBuilder sb = new StringBuilder();

        // Part 1: userId field
        sb.append("--").append(boundary).append(CRLF);
        sb.append("Content-Disposition: form-data; name=\"userId\"").append(CRLF);
        sb.append(CRLF);
        sb.append(userId).append(CRLF);

        // Part 2: avatar file header
        sb.append("--").append(boundary).append(CRLF);
        sb.append("Content-Disposition: form-data; name=\"avatar\"; filename=\"")
          .append(filename).append("\"").append(CRLF);
        sb.append("Content-Type: image/png").append(CRLF);
        sb.append(CRLF);

        byte[] headerBytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        // Closing boundary
        byte[] closingBytes = (CRLF + "--" + boundary + "--" + CRLF)
                .getBytes(StandardCharsets.UTF_8);

        // Combine: header + imageBytes + closing
        byte[] bodyBytes = new byte[headerBytes.length + imageBytes.length + closingBytes.length];
        System.arraycopy(headerBytes, 0, bodyBytes, 0, headerBytes.length);
        System.arraycopy(imageBytes, 0, bodyBytes, headerBytes.length, imageBytes.length);
        System.arraycopy(closingBytes, 0, bodyBytes, headerBytes.length + imageBytes.length, closingBytes.length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/change-avatar"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        String responseBody = response.body() == null ? "" : response.body();
        String status = readJsonString(responseBody, "status");
        String message = readJsonString(responseBody, "message");
        String avatarUrl = readJsonString(responseBody, "avatarUrl");

        if (message.isBlank()) {
            message = readJsonString(responseBody, "error");
        }
        if (message.isBlank()) {
            message = "HTTP " + response.statusCode();
        }

        return new ApiResponse(response.statusCode(), status, message, avatarUrl, null, responseBody);
    }

    public ApiResponse resetPassword(String code, String password)
            throws IOException, InterruptedException {
        String body = """
                {"code":"%s","password":"%s"}
                """.formatted(escapeJson(code), escapeJson(password));
        return send("POST", "/api/forgotpwd", body);
    }

    private ApiResponse send(String method, String path, String body)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        String responseBody = response.body() == null ? "" : response.body();

        // The current backend responses are small and predictable, so this
        // lightweight parser is enough for status/message/code fields.
        String status = readJsonString(responseBody, "status");
        String message = readJsonString(responseBody, "message");
        String code = readJsonString(responseBody, "code");
        Long userId = readJsonLong(responseBody, "userId");

        if (message.isBlank()) {
            message = readJsonString(responseBody, "error");
        }
        if (message.isBlank()) {
            message = "HTTP " + response.statusCode();
        }

        return new ApiResponse(response.statusCode(), status, message, code, userId, responseBody);
    }

    private static String resolveBaseUrl() {
        String propertyUrl = System.getProperty("chatapp.api.baseUrl");
        if (propertyUrl != null && !propertyUrl.isBlank()) {
            return propertyUrl;
        }

        String envUrl = System.getenv("CHATAPP_API_BASE_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            return envUrl;
        }

        return DEFAULT_BASE_URL;
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value == null ? DEFAULT_BASE_URL : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String readJsonString(String json, String fieldName) {
        Pattern fieldPattern = Pattern.compile(JSON_FIELD_PATTERN.pattern().formatted(fieldName));
        Matcher matcher = fieldPattern.matcher(json);
        if (!matcher.find()) {
            return "";
        }
        return unescapeJson(matcher.group(1));
    }

    private static Long readJsonLong(String json, String fieldName) {
        Pattern fieldPattern = Pattern.compile(JSON_NUMBER_FIELD_PATTERN.pattern().formatted(fieldName));
        Matcher matcher = fieldPattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String unescapeJson(String value) {
        StringBuilder result = new StringBuilder();
        boolean escaping = false;

        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!escaping) {
                if (current == '\\') {
                    escaping = true;
                } else {
                    result.append(current);
                }
                continue;
            }

            switch (current) {
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case '"', '\\', '/' -> result.append(current);
                default -> result.append(current);
            }
            escaping = false;
        }

        if (escaping) {
            result.append('\\');
        }

        return result.toString();
    }

    public record ApiResponse(
            int statusCode,
            String status,
            String message,
            String code,
            Long userId,
            String rawBody
    ) {
        public boolean isSuccess() {
            return statusCode >= 200
                    && statusCode < 300
                    && !"error".equalsIgnoreCase(status);
        }
    }
}
