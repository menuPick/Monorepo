import 'package:flutter/material.dart';

/// 관리자 PWA 전용 테마 컨트롤러
/// (유저 앱과 분리된 상태를 유지하기 위해 별도 파일로 둡니다)
class AdminThemeController {
  static final ValueNotifier<ThemeMode> themeMode = ValueNotifier(ThemeMode.system);

  static void toggleTheme() {
    final current = themeMode.value;
    if (current == ThemeMode.dark) {
      themeMode.value = ThemeMode.light;
    } else {
      themeMode.value = ThemeMode.dark;
    }
  }
}

