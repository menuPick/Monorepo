package menupick.web;

/** 입력 정리 규칙(기존 서버와 동일): 앞뒤 공백 제거, 길이 초과·필수값 누락은 400. */
final class Inputs {
    private Inputs() {
    }

    static String required(String value, String field, int maxLength) {
        String cleaned = optional(value, maxLength);
        if (cleaned.isEmpty()) {
            throw ApiException.badRequest(field + " 값이 필요합니다.");
        }
        return cleaned;
    }

    static String optional(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw ApiException.badRequest("입력값이 너무 깁니다.");
        }
        return trimmed;
    }

    /** limit 쿼리: 기본 100, 1~200으로 제한, 숫자가 아니면 기본값. */
    static int limit(String raw) {
        if (raw == null) {
            return 100;
        }
        try {
            return Math.max(1, Math.min(Integer.parseInt(raw.trim()), 200));
        } catch (NumberFormatException ex) {
            return 100;
        }
    }
}
