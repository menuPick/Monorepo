import 'package:flutter/material.dart';

import 'package:user/data/user_api.dart';
import 'package:user/pages/recommendation_result_page.dart';
import 'package:user/widgets/app_drawer.dart';
import 'package:user/widgets/entrance_animations.dart';

class RecommendationInputPage extends StatefulWidget {
  const RecommendationInputPage({super.key});

  @override
  State<RecommendationInputPage> createState() => _RecommendationInputPageState();
}

class _RecommendationInputPageState extends State<RecommendationInputPage> {
  final TextEditingController _menuNameController = TextEditingController();
  final TextEditingController _reasonController = TextEditingController();
  bool _isSaving = false;

  final UserApi _api = UserApi();

  @override
  void dispose() {
    _menuNameController.dispose();
    _reasonController.dispose();
    super.dispose();
  }

  Future<void> _handleRecommend() async {
    final menuName = _menuNameController.text.trim();
    final reason = _reasonController.text.trim();

    if (menuName.isEmpty && reason.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('메뉴명 또는 이유를 입력해주세요.')),
      );
      return;
    }

    setState(() {
      _isSaving = true;
    });

    var isSaved = false;
    try {
      await _api.submitRecommendation(
        menuName: menuName.isEmpty ? '메뉴명 미입력' : menuName,
        reason: reason.isEmpty ? '이유 미입력' : reason,
      );
      isSaved = true;
    } on UserApiException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('서버 저장 실패: ${e.message}')),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('서버 저장 중 오류가 발생했습니다.')),
      );
    }

    if (!mounted) {
      return;
    }

    setState(() {
      _isSaving = false;
    });

    // 서버 DB 저장이 성공한 경우에만 결과 페이지로 이동합니다.
    if (!isSaved) {
      return;
    }

    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => const RecommendationResultPage()),
    );
  }


  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final backgroundColor = isDark ? Colors.black : Colors.white;
    final borderColor = isDark ? const Color(0xFFB5B5B5) : const Color(0xFFB8B8B8);
    final textColor = isDark ? Colors.white : Colors.black;
    final hintColor = isDark ? const Color(0xFF8F8F8F) : const Color(0xFFA8A8A8);

    return Scaffold(
      backgroundColor: backgroundColor,
      drawer: const AppDrawer(),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 18, 24, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              EntranceFadeSlide(
                fromYOffset: 10,
                duration: const Duration(milliseconds: 420),
                child: Builder(
                  builder: (context) => _MenuButton(
                    color: borderColor,
                    onTap: () => Scaffold.of(context).openDrawer(),
                  ),
                ),
              ),
              const SizedBox(height: 24),
              EntranceFadeSlide(
                delay: const Duration(milliseconds: 90),
                fromYOffset: 14,
                duration: const Duration(milliseconds: 520),
                child: Text(
                  '이 메뉴 먹고 싶어요!',
                  style: TextStyle(
                    color: textColor,
                    fontSize: 34,
                    height: 1.1,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
              const SizedBox(height: 26),
              EntranceFadeSlide(
                delay: const Duration(milliseconds: 160),
                fromYOffset: 18,
                duration: const Duration(milliseconds: 560),
                child: _OutlineField(
                  hint: '메뉴명',
                  borderColor: borderColor,
                  hintColor: hintColor,
                  height: 72,
                  controller: _menuNameController,
                ),
              ),
              const SizedBox(height: 24),
              EntranceFadeSlide(
                delay: const Duration(milliseconds: 220),
                fromYOffset: 18,
                duration: const Duration(milliseconds: 600),
                child: _OutlineField(
                  hint: '메뉴를 먹고 싶은 이유',
                  borderColor: borderColor,
                  hintColor: hintColor,
                  height: 260,
                  maxLines: 6,
                  controller: _reasonController,
                ),
              ),
              const SizedBox(height: 28),
              EntranceFadeSlide(
                delay: const Duration(milliseconds: 300),
                fromYOffset: 14,
                duration: const Duration(milliseconds: 560),
                child: _PrimaryButton(
                  label: '메뉴를 올려봅시다.',
                  backgroundColor: const Color(0xFF0B18F1),
                  textColor: Colors.white,
                  onTap: _isSaving ? () {} : _handleRecommend,
                ),
              ),
              const SizedBox(height: 20),
              EntranceFadeSlide(
                delay: const Duration(milliseconds: 340),
                fromYOffset: 14,
                duration: const Duration(milliseconds: 560),
                child: _PrimaryButton(
                  label: _isSaving ? '저장 중...' : 'AI에게 추천받기',
                  backgroundColor: isDark ? const Color(0xFFB6B8F3) : const Color(0xFFB6B8F3),
                  textColor: isDark ? Colors.white : Colors.white,
                  onTap: _isSaving
                      ? () {}
                      : () {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('현재는 "메뉴를 올려봅시다." 버튼으로 서버에 저장합니다.')),
                          );
                        },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MenuButton extends StatelessWidget {
  const _MenuButton({required this.onTap, required this.color});

  final VoidCallback onTap;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 50,
        height: 50,
        decoration: BoxDecoration(
          color: Theme.of(context).brightness == Brightness.dark ? Colors.black : Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: color, width: 1),
        ),
        child: Icon(
          Icons.menu_rounded,
          color: Theme.of(context).brightness == Brightness.dark ? Colors.white : Colors.black,
          size: 34,
        ),
      ),
    );
  }
}

class _OutlineField extends StatelessWidget {
  const _OutlineField({
    required this.hint,
    required this.borderColor,
    required this.hintColor,
    required this.height,
    required this.controller,
    this.maxLines = 1,
  });

  final String hint;
  final Color borderColor;
  final Color hintColor;
  final double height;
  final TextEditingController controller;
  final int maxLines;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(32),
        border: Border.all(color: borderColor, width: 1),
      ),
      child: Align(
        alignment: Alignment.topLeft,
        child: TextField(
          controller: controller,
          maxLines: maxLines,
          decoration: InputDecoration(
            hintText: hint,
            hintStyle: TextStyle(
              color: hintColor,
              fontSize: 18,
              fontWeight: FontWeight.w400,
            ),
            border: InputBorder.none,
            isCollapsed: true,
          ),
          style: TextStyle(
            color: Theme.of(context).brightness == Brightness.dark ? Colors.white : Colors.black,
            fontSize: 18,
          ),
        ),
      ),
    );
  }
}

class _PrimaryButton extends StatelessWidget {
  const _PrimaryButton({
    required this.label,
    required this.backgroundColor,
    required this.textColor,
    required this.onTap,
  });

  final String label;
  final Color backgroundColor;
  final Color textColor;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 70,
      child: ElevatedButton(
        onPressed: onTap,
        style: ElevatedButton.styleFrom(
          backgroundColor: backgroundColor,
          foregroundColor: textColor,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(999),
          ),
        ),
        child: Text(
          label,
          style: const TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
    );
  }
}

