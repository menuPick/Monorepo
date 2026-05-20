                import 'package:flutter/material.dart';

                import 'package:user/pages/recommendation_input_page.dart';
                import 'package:user/pages/recommendation_result_page.dart';
                import 'package:user/theme/theme_controller.dart';
                import 'package:user/widgets/app_drawer.dart';
                import 'package:user/widgets/model_banner.dart';
                 import 'package:user/widgets/entrance_animations.dart';

    void main() {
      runApp(const MyApp());
    }

    class MyApp extends StatelessWidget {
      const MyApp({super.key});

      @override
      Widget build(BuildContext context) {
        return ValueListenableBuilder<ThemeMode>(
          valueListenable: ThemeController.themeMode,
          builder: (context, mode, child) {
            return MaterialApp(
              debugShowCheckedModeBanner: false,
              title: '메뉴픽',
              themeMode: mode,
              theme: ThemeData(
                useMaterial3: true,
                brightness: Brightness.light,
                scaffoldBackgroundColor: Colors.white,
                fontFamily: 'Pretendard',
              ),
              darkTheme: ThemeData(
                useMaterial3: true,
                brightness: Brightness.dark,
                scaffoldBackgroundColor: Colors.black,
                fontFamily: 'Pretendard',
              ),
              home: const HomeScreen(),
            );
          },
        );
      }
    }

    class HomeScreen extends StatelessWidget {
      const HomeScreen({super.key});

      @override
      Widget build(BuildContext context) {
        return Scaffold(
              drawer: const AppDrawer(),
          body: SafeArea(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(30, 0, 30, 40),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                   children: [
                     EntranceFadeSlide(
                       fromYOffset: 10,
                       duration: const Duration(milliseconds: 420),
                       child: Builder(
                         builder: (context) => _MenuButton(
                           onTap: () => Scaffold.of(context).openDrawer(),
                         ),
                       ),
                     ),
                     const SizedBox(height: 16),
                     const SizedBox(height: 8),
                     EntranceFadeSlide(
                       delay: Duration(milliseconds: 80),
                       fromYOffset: 14,
                       duration: Duration(milliseconds: 520),
                       child: Row(
                         crossAxisAlignment: CrossAxisAlignment.start,
                         children: [
                           const Expanded(child: _HeaderTitle()),
                           const SizedBox(width: 12),
                           const SizedBox(
                             width: 140,
                             height: 120,
                             child: ModelBanner(height: 120),
                           ),
                         ],
                       ),
                     ),
                   SizedBox(height: 24),
                     EntranceBlurSlide(
                       delay: const Duration(milliseconds: 140),
                       duration: const Duration(milliseconds: 720),
                       fromYOffset: 54,
                       maxBlurSigma: 16,
                       child: _PromoCard(
                      onTap: () {
                        Navigator.of(context).push(
                          MaterialPageRoute(
                            builder: (_) => const RecommendationInputPage(),
                          ),
                        );
                      },
                    gradient: LinearGradient(
                        begin: Alignment.center,
                        end: Alignment.bottomCenter,
                      colors: [Color(0xff5F66BA), Color(0xffC9CAFE)],
                    ),
                    title: '메뉴를 추천 받아요.',
                      subtitle: '여러분이 주신 결과로 식단이 바뀝니다.',
                    icon: Icons.image_outlined,
                      iconColor: Color(0xFFF7F8FF),
                      assetPath: 'student-assets/Icon/Edit Column.png',
                  ),
                     ),
                  SizedBox(height: 54),
                     EntranceBlurSlide(
                       delay: const Duration(milliseconds: 260),
                       duration: const Duration(milliseconds: 760),
                       fromYOffset: 54,
                       maxBlurSigma: 16,
                       child: _PromoCard(
                      onTap: () {
                        Navigator.of(context).push(
                          MaterialPageRoute(
                            builder: (_) => const RecommendationResultPage(),
                          ),
                        );
                      },
                    gradient: LinearGradient(
                        begin: Alignment.center,
                        end: Alignment.bottomCenter,
                      colors: [Color(0xff01037B), Color(0xFF1400F7)],
                    ),
                    title: '추천메뉴를 확인하세요!',
                    subtitle: '여러분의 식단 결과를 확인해봅시다.',
                    icon: Icons.verified,
                    iconColor: Colors.white,
                    assetPath: 'student-assets/Icon/Approval.png',
                  ),
                     ),
                ],
              ),
            ),
          ),
        );
      }
    }

    class _HeaderTitle extends StatelessWidget {
      const _HeaderTitle();

      static const List<Shadow> _shadows = [
        Shadow(
          color: Color(0x22000000),
          blurRadius: 8,
          offset: Offset(0, 3),
        ),
      ];

      @override
      Widget build(BuildContext context) {
        final isDark = Theme.of(context).brightness == Brightness.dark;
        final textColor = isDark ? Colors.white : Colors.black;

        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '환영합니다',
              style: TextStyle(
                fontSize: 30,
                height: 1.05,
                fontWeight: FontWeight.w900,
                color: textColor,
                shadows: _shadows,
              ),
            ),
            const SizedBox(height: 14),
            Text(
              '식단 추천 앱',
              style: TextStyle(
                fontSize: 30,
                height: 1.05,
                fontWeight: FontWeight.w900,
                color: textColor,
                shadows: _shadows,
              ),
            ),
            const SizedBox(height: 14),
            Text(
              '메뉴픽',
              style: TextStyle(
                fontSize: 30,
                height: 1.05,
                fontWeight: FontWeight.w900,
                color: Color(0xFF666ED8),
                shadows: _shadows,
              ),
            ),
          ],
        );
      }
    }

    class _MenuButton extends StatelessWidget {
                  const _MenuButton({this.onTap});

          final VoidCallback? onTap;

      @override
      Widget build(BuildContext context) {
            final isDark = Theme.of(context).brightness == Brightness.dark;

            return GestureDetector(
              onTap: onTap,
              child: Container(
                width: 50,
                height: 50,
                decoration: BoxDecoration(
                  color: isDark ? Colors.black : Colors.white,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: isDark ? const Color(0xFF5B5B5B) : const Color(0xFFDBDBDB),
                    width: 1,
                  ),
                ),
                child: Icon(
                  Icons.menu_rounded,
                  color: isDark ? Colors.white : Colors.black,
                  size: 34,
                ),
              ),
        );
      }
    }

    class _PromoCard extends StatelessWidget {
      const _PromoCard({
            this.onTap,
                this.assetPath,
        required this.gradient,
        required this.title,
        required this.subtitle,
        required this.icon,
        required this.iconColor,
      });

          final VoidCallback? onTap;
              final String? assetPath;
      final Gradient gradient;
      final String title;
      final String subtitle;
      final IconData icon;
      final Color iconColor;

      @override
      Widget build(BuildContext context) {
        return GestureDetector(
              onTap: onTap,
          child: Container(
            width: double.infinity,
            constraints: const BoxConstraints(minHeight: 180),
            padding: const EdgeInsets.fromLTRB(18, 22, 18, 18),
            decoration: BoxDecoration(
              gradient: gradient,
                  borderRadius: BorderRadius.circular(12),
            ),
            child: Stack(
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 26,
                        height: 1.1,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      subtitle,
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.92),
                        fontSize: 16,
                        height: 1.25,
                        fontWeight: FontWeight.w400,
                      ),
                    ),
                  ],
                ),
                Align(
                  alignment: Alignment.topRight,
                  child: Padding(
                    padding: EdgeInsets.only(right: 10, top : 120),
                    child: _CardIcon(
                      icon: icon,
                      color: iconColor,
                      assetPath: assetPath,
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      }
    }

    class _CardIcon extends StatelessWidget {
      const _CardIcon({
            required this.icon,
            required this.color,
                    this.assetPath,
      });

      final IconData icon;
      final Color color;
              final String? assetPath;

      @override
      Widget build(BuildContext context) {
                if (assetPath != null) {
                  return Image.asset(
                    assetPath!,
                    width: 80,
                    height: 80,
                    fit: BoxFit.contain,
                  );
                }
        return Icon(icon, size: 40, color: color);
      }
    }







