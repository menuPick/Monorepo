import 'package:flutter/material.dart';

import 'package:user/navigation/app_routes.dart';
import 'package:user/theme/theme_controller.dart';

const double kWideBreakpoint = 1024;

class ResponsiveScaffold extends StatelessWidget {
  const ResponsiveScaffold({
    super.key,
    required this.child,
    required this.currentIndex,
    this.backgroundColor,
  });

  final Widget child;
  final int currentIndex;
  final Color? backgroundColor;

  @override
  Widget build(BuildContext context) {
    final isWide = MediaQuery.sizeOf(context).width >= kWideBreakpoint;

    return Scaffold(
      backgroundColor: backgroundColor,
      body: isWide
          ? Row(
              children: [
                _SideNavigation(currentIndex: currentIndex),
                const VerticalDivider(width: 1),
                Expanded(child: child),
              ],
            )
          : child,
      bottomNavigationBar: isWide ? null : _BottomNavigation(currentIndex: currentIndex),
    );
  }
}

class _NavDestination {
  const _NavDestination({
    required this.label,
    required this.icon,
    this.routeName,
    this.isAction = false,
  });

  final String label;
  final IconData icon;
  final String? routeName;
  final bool isAction;
}

const List<_NavDestination> _destinations = [
  _NavDestination(label: '메뉴픽', icon: Icons.home_rounded, routeName: AppRoutes.home),
  _NavDestination(label: '메뉴추천', icon: Icons.edit_rounded, routeName: AppRoutes.recommend),
  _NavDestination(label: '메뉴확인', icon: Icons.verified_rounded, routeName: AppRoutes.result),
  _NavDestination(label: '문의', icon: Icons.help_outline_rounded, routeName: AppRoutes.about),
  _NavDestination(label: '모드', icon: Icons.brightness_6_rounded, isAction: true),
];

class _SideNavigation extends StatelessWidget {
  const _SideNavigation({required this.currentIndex});

  final int currentIndex;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final backgroundColor = isDark ? Colors.black : Colors.white;

    return SafeArea(
      child: Container(
        width: 240,
        color: backgroundColor,
        child: NavigationRail(
          extended: true,
          selectedIndex: currentIndex,
          backgroundColor: backgroundColor,
          onDestinationSelected: (index) => _handleDestination(context, index, currentIndex),
          leading: Padding(
            padding: const EdgeInsets.only(top: 8, bottom: 20),
            child: Text(
              '메뉴픽',
              style: TextStyle(
                color: isDark ? Colors.white : Colors.black,
                fontSize: 18,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          destinations: _destinations
              .map(
                (destination) => NavigationRailDestination(
                  icon: Icon(destination.icon),
                  label: Text(destination.label),
                ),
              )
              .toList(),
        ),
      ),
    );
  }
}

class _BottomNavigation extends StatelessWidget {
  const _BottomNavigation({required this.currentIndex});

  final int currentIndex;

  @override
  Widget build(BuildContext context) {
    return BottomNavigationBar(
      currentIndex: currentIndex,
      type: BottomNavigationBarType.fixed,
      onTap: (index) => _handleDestination(context, index, currentIndex),
      items: _destinations
          .map(
            (destination) => BottomNavigationBarItem(
              icon: Icon(destination.icon),
              label: destination.label,
            ),
          )
          .toList(),
    );
  }
}

void _handleDestination(BuildContext context, int index, int currentIndex) {
  final destination = _destinations[index];

  if (destination.isAction) {
    ThemeController.toggleTheme();
    return;
  }

  if (index == currentIndex) {
    return;
  }

  if (destination.routeName == AppRoutes.home) {
    Navigator.of(context).popUntil((route) => route.isFirst);
    return;
  }

  Navigator.of(context).pushNamedAndRemoveUntil(
    destination.routeName!,
    (route) => route.isFirst,
  );
}

