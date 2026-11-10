package menupick.web.dto;

/** publishAt은 ISO-8601 UTC 문자열(예: 2026-11-10T03:00:00Z). */
public record DecisionRequest(Long recommendationId, String publishAt) {
}
