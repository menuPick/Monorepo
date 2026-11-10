package menupick.web;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 삭제 id 파싱과 입력 정리 규칙(앱과의 계약)을 고정합니다. */
class InputParsingTest {
    private final ObjectMapper json = JsonMapper.builder().build();

    @Test
    void parsesIdsInEverySupportedShape() {
        assertEquals(List.of(1L, 2L, 3L), IdsParser.parse("{\"ids\":[1,2,3]}", json), "Flutter app shape");
        assertEquals(List.of(1L, 2L), IdsParser.parse("{\"ids\":\"1,2\"}", json));
        assertEquals(List.of(4L, 5L), IdsParser.parse("[4,5]", json));
        assertEquals(List.of(7L, 8L), IdsParser.parse("7, 8", json), "plain text");
        assertEquals(List.of(1L, 2L), IdsParser.parse("{\"ids\":[1,1,2,0]}", json), "dedupe and drop 0");
        assertEquals(List.of(), IdsParser.parse("{\"other\":[1]}", json));
        assertEquals(List.of(), IdsParser.parse("   ", json));
        assertEquals(List.of(), IdsParser.parse(null, json));
    }

    @Test
    void capsIdsAt500() {
        StringBuilder many = new StringBuilder("[");
        for (int i = 1; i <= 600; i++) {
            many.append(i).append(i < 600 ? "," : "]");
        }
        assertEquals(500, IdsParser.parse(many.toString(), json).size());
    }

    @Test
    void trimsAndValidatesText() {
        assertEquals("떡볶이", Inputs.required("  떡볶이 ", "menuName", 255));
        assertEquals("", Inputs.optional(null, 10));
        ApiException blank = assertThrows(ApiException.class, () -> Inputs.required("   ", "menuName", 255));
        assertEquals("bad_request", blank.code());
        assertThrows(ApiException.class, () -> Inputs.optional("가".repeat(11), 10));
        assertEquals("가".repeat(10), Inputs.optional("가".repeat(10), 10), "exactly max length is ok");
    }

    @Test
    void clampsLimit() {
        assertEquals(100, Inputs.limit(null));
        assertEquals(100, Inputs.limit("abc"));
        assertEquals(1, Inputs.limit("0"));
        assertEquals(200, Inputs.limit("999"));
        assertEquals(50, Inputs.limit(" 50 "));
    }
}
