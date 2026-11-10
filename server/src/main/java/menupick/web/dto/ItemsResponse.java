package menupick.web.dto;

import java.util.List;

/** 목록 응답은 {"items": [...]}로 감쌉니다. */
public record ItemsResponse<T>(List<T> items) {
}
