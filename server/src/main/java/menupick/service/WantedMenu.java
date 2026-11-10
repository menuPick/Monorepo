package menupick.service;

import java.util.Set;
import java.util.regex.Pattern;

/** 사용자가 입력한 "원하는 메뉴"가 실제 메뉴 이름인지 판단합니다. */
final class WantedMenu {
    /** 한글·영문·숫자로 시작하고 공백과 ( ) · , & - 정도만 허용, 40자 이하. */
    private static final Pattern MENU_NAME = Pattern.compile("[가-힣A-Za-z0-9][가-힣A-Za-z0-9 ()·,&\\-]{0,39}");

    /** 메뉴가 아니라 "골라 달라"는 뜻의 입력. 사용자 앱은 빈 칸이면 "메뉴명 미입력"을 보냅니다. */
    private static final Set<String> NOT_A_MENU = Set.of(
            "메뉴명 미입력", "미입력", "없음", "없어요", "아무거나", "아무거나요", "아무", "상관없음", "상관없어요",
            "몰라", "모름", "모르겠어요", "추천", "추천해줘", "추천해주세요", "아무거나 추천");

    private WantedMenu() {
    }

    static boolean isMenuName(String value) {
        if (value == null) {
            return false;
        }
        String menu = value.strip();
        return MENU_NAME.matcher(menu).matches() && !NOT_A_MENU.contains(menu);
    }
}
