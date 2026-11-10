package menupick.web.dto;

public record LoginResponse(String token, String adminEmail, String expiresAt) {
}
