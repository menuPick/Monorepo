package menupick.domain;

import java.time.Instant;

/** 관리자가 지정한 결정 메뉴와 공개 시각. */
public record MenuDecision(long recommendationId, String publishAt, String updatedAt) {
    public boolean isPublishedAt(Instant now) {
        return !now.isBefore(Instant.parse(publishAt));
    }
}
