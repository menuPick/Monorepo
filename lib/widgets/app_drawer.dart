import 'package:flutter/material.dart';

import 'package:user/pages/about_page.dart';
import 'package:user/pages/recommendation_input_page.dart';
import 'package:user/pages/recommendation_result_page.dart';
import 'package:user/theme/theme_controller.dart';

class AppDrawer extends StatelessWidget {
  const AppDrawer({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final backgroundColor = isDark ? Colors.black : Colors.white;
    final textColor = isDark ? const Color(0xFFBDBDBD) : const Color(0xFFB8B8B8);
    final dividerColor = isDark ? const Color(0xFF2A2A2A) : const Color(0xFFECECEC);

    return Drawer(
      backgroundColor: backgroundColor,
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 50,
                height: 50,
                decoration: BoxDecoration(
                  color: backgroundColor,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: dividerColor),
                ),
                child: const Icon(Icons.menu_rounded, size: 34),
              ),
              const SizedBox(height: 28),
              _DrawerItem(
                label: '메뉴픽',
                color: textColor,
                onTap: () => Navigator.of(context).popUntil((route) => route.isFirst),
              ),
              const SizedBox(height: 26),
              _DrawerItem(
                label: '메뉴추천',
                color: textColor,
                onTap: () => _openPage(context, const RecommendationInputPage()),
              ),
              const SizedBox(height: 26),
              _DrawerItem(
                label: '메뉴확인',
                color: textColor,
                onTap: () => _openPage(context, const RecommendationResultPage()),
              ),
              const SizedBox(height: 26),
              _DrawerItem(
                label: '문의',
                color: textColor,
                onTap: () => _openPage(context, const AboutPage()),
              ),
              const SizedBox(height: 26),
              _DrawerItem(
                label: '모드',
                color: textColor,
                onTap: () {
                  ThemeController.toggleTheme();
                  Navigator.pop(context);
                },
              ),
              const Spacer(),
              Container(
                width: double.infinity,
                height: 1,
                color: dividerColor,
              ),
              const SizedBox(height: 12),
              Text(
                '메뉴픽',
                style: TextStyle(
                  color: textColor,
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _openPage(BuildContext context, Widget page) {
    Navigator.pop(context);
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => page),
    );
  }
}

class _DrawerItem extends StatelessWidget {
  const _DrawerItem({
    required this.label,
    required this.color,
    required this.onTap,
  });

  final String label;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Text(
          label,
          style: TextStyle(
            color: color,
            fontSize: 16,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
    );
  }
}

