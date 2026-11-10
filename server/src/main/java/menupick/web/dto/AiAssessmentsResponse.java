package menupick.web.dto;

import menupick.domain.AiAssessment;

import java.util.List;

/** enabled=false면 AI 서버가 설정되지 않은 상태입니다(AI_BASE_URL). */
public record AiAssessmentsResponse(boolean enabled, List<AiAssessment> items) {
}
