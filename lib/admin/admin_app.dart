import 'package:flutter/material.dart';

import 'admin_theme_controller.dart';
import 'pages/admin_auth_gate.dart';

const _adminLightScheme = ColorScheme.light(
  primary: Color(0xFF4F56D7),
  onPrimary: Colors.white,
  primaryContainer: Color(0xFFE1E4FF),
  onPrimaryContainer: Color(0xFF10165B),
  secondary: Color(0xFF006C5F),
  onSecondary: Colors.white,
  secondaryContainer: Color(0xFFA7F2E6),
  onSecondaryContainer: Color(0xFF00201B),
  surface: Colors.white,
  onSurface: Color(0xFF191B23),
  surfaceContainerHighest: Color(0xFFE8EAF4),
  onSurfaceVariant: Color(0xFF444754),
  outline: Color(0xFF747785),
  outlineVariant: Color(0xFFC5C7D3),
  error: Color(0xFFBA1A1A),
  onError: Colors.white,
);

const _adminDarkScheme = ColorScheme.dark(
  primary: Color(0xFFBEC2FF),
  onPrimary: Color(0xFF222A78),
  primaryContainer: Color(0xFF373F9F),
  onPrimaryContainer: Color(0xFFE1E4FF),
  secondary: Color(0xFF82D6CB),
  onSecondary: Color(0xFF003731),
  secondaryContainer: Color(0xFF005047),
  onSecondaryContainer: Color(0xFFA7F2E6),
  surface: Color(0xFF101116),
  onSurface: Color(0xFFE4E5EE),
  surfaceContainerHighest: Color(0xFF454653),
  onSurfaceVariant: Color(0xFFC7C8D4),
  outline: Color(0xFF9193A0),
  outlineVariant: Color(0xFF454653),
  error: Color(0xFFFFB4AB),
  onError: Color(0xFF690005),
);

class AdminApp extends StatelessWidget {
  const AdminApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<ThemeMode>(
      valueListenable: AdminThemeController.themeMode,
      builder: (context, mode, child) {
        return MaterialApp(
          debugShowCheckedModeBanner: false,
          title: 'MenuPick Admin',
          themeMode: mode,
          theme: ThemeData(
            useMaterial3: true,
            brightness: Brightness.light,
            colorScheme: _adminLightScheme,
            scaffoldBackgroundColor: const Color(0xFFF6F7FB),
            fontFamily: 'Pretendard',
            cardTheme: CardThemeData(
              elevation: 0,
              color: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(18),
              ),
            ),
            inputDecorationTheme: InputDecorationTheme(
              filled: true,
              fillColor: const Color(0xFFF7F8FC),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
                borderSide: BorderSide.none,
              ),
            ),
          ),
          darkTheme: ThemeData(
            useMaterial3: true,
            brightness: Brightness.dark,
            colorScheme: _adminDarkScheme,
            scaffoldBackgroundColor: const Color(0xFF101116),
            fontFamily: 'Pretendard',
            cardTheme: CardThemeData(
              elevation: 0,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(18),
              ),
            ),
          ),
          home: const AdminAuthGate(),
        );
      },
    );
  }
}
