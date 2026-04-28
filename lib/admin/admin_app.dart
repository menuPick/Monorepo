import 'package:flutter/material.dart';

import 'admin_theme_controller.dart';
import 'pages/admin_auth_gate.dart';

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
            colorSchemeSeed: const Color(0xFF0B18F1),
            fontFamily: 'Pretendard',
          ),
          darkTheme: ThemeData(
            useMaterial3: true,
            brightness: Brightness.dark,
            colorSchemeSeed: const Color(0xFF0B18F1),
            fontFamily: 'Pretendard',
          ),
          home: const AdminAuthGate(),
        );
      },
    );
  }
}

