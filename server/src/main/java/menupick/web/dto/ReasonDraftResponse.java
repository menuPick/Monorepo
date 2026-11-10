package menupick.web.dto;

/** 이유 자세히 쓰기 결과. reason = 사용자가 쓴 말 + added(AI가 덧붙인 문장). */
public record ReasonDraftResponse(String reason, String added, String engine) {
}
