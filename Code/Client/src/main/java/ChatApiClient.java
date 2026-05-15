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
        return send("GET", "/api/forgotpwd", body);
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

        if (message.isBlank()) {
            message = readJsonString(responseBody, "error");
        }
        if (message.isBlank()) {
            message = "HTTP " + response.statusCode();
        }

        return new ApiResponse(response.statusCode(), status, message, code, responseBody);
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
            String rawBody
    ) {
        public boolean isSuccess() {
            return statusCode >= 200
                    && statusCode < 300
                    && !"error".equalsIgnoreCase(status);
        }
    }
}
