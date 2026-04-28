package menupick.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MenuDecisionSetting {
    private final long recommendationId;
    private final String publishAt;
    private final String updatedAt;

    public MenuDecisionSetting(long recommendationId, String publishAt, String updatedAt) {
        this.recommendationId = recommendationId;
        this.publishAt = publishAt;
        this.updatedAt = updatedAt;
    }

    public long recommendationId() {
        return recommendationId;
    }

    public String publishAt() {
        return publishAt;
    }

    public String updatedAt() {
        return updatedAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("recommendationId", recommendationId);
        map.put("publishAt", publishAt);
        map.put("updatedAt", updatedAt);
        return map;
    }
}

