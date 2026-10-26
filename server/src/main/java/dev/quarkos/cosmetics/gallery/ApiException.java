package dev.quarkos.cosmetics.gallery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<Violation> violations;

    public ApiException(int status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(int status, String code, String message, List<Violation> violations) {
        super(message);
        this.status = status;
        this.code = code;
        this.violations = List.copyOf(violations);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<Violation> violations() {
        return violations;
    }

    public Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", getMessage());
        if (!violations.isEmpty()) {
            body.put("violations", violations);
        }
        return body;
    }

    public static ApiException validation(List<Violation> violations) {
        return new ApiException(400, "validation_failed", "The request is not valid.", violations);
    }

    public record Violation(String path, String message) {
    }
}
