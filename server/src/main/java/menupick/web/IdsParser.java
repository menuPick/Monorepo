package menupick.web;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 삭제 요청의 id 목록 파싱. {"ids":[1,2]}(Flutter 앱), {"ids":"1,2"}, [1,2], "1,2" 형태를 모두 받습니다.
 * (기존 서버는 {"ids":[1,2,3]}처럼 id가 2개 이상이면 파싱에 실패해 0건 삭제되던 문제가 있었습니다.)
 */
final class IdsParser {
    private static final Pattern NUMBER = Pattern.compile("\\d{1,18}");
    private static final int MAX_IDS = 500;

    private IdsParser() {
    }

    static List<Long> parse(String body, ObjectMapper json) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        String source = body;
        try {
            JsonNode root = json.readTree(body);
            if (root != null && root.isObject()) {
                JsonNode ids = root.get("ids");
                if (ids == null || ids.isNull()) {
                    return List.of();
                }
                source = ids.toString();
            }
        } catch (JacksonException ignored) {
            // JSON이 아니면 원문에서 숫자를 찾습니다(예: "1,2,3").
        }
        Set<Long> ids = new LinkedHashSet<>();
        Matcher matcher = NUMBER.matcher(source);
        while (matcher.find() && ids.size() < MAX_IDS) {
            long id = Long.parseLong(matcher.group());
            if (id > 0) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }
}
