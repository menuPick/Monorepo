package menupick.domain;

public record Inquiry(long id, String message, String adminEmail, String createdAt) {
    public Inquiry {
        message = message == null ? "" : message;
        adminEmail = adminEmail == null ? "" : adminEmail;
        createdAt = createdAt == null ? "" : createdAt;
    }
}
