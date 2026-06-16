import 'package:flutter/material.dart';

import 'package:user/data/user_api.dart';
import 'package:user/navigation/app_routes.dart';
import 'package:user/widgets/entrance_animations.dart';
import 'package:user/widgets/model_banner.dart';
import 'package:user/widgets/responsive_scaffold.dart';

class RecommendationInputPage extends StatefulWidget {
  const RecommendationInputPage({super.key});

  @override
  State<RecommendationInputPage> createState() =>
      _RecommendationInputPageState();
}

class _RecommendationInputPageState extends State<RecommendationInputPage> {
  static const List<String> _categories = [
    '주식',
    '국/찌개',
    '주찬(메인 반찬)',
    '부찬(보조 반찬)',
    '김치류',
    '후식(디저트)',
    '미분류',
  ];

  final TextEditingController _menuNameController = TextEditingController();
  final TextEditingController _reasonController = TextEditingController();
  bool _isSaving = false;
  String _selectedCategory = '미분류';

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
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('메뉴명 또는 이유를 입력해주세요.')));
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
        category: _selectedCategory,
      );
      isSaved = true;
    } on MonthlyRecommendationLimitException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(e.message)));
    } on UserApiException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('서버 저장 실패: ${e.message}')));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('서버 저장 중 오류가 발생했습니다.')));
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

    Navigator.of(context).pushNamed(AppRoutes.result);
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final backgroundColor = isDark ? Colors.black : Colors.white;
    final borderColor = isDark
        ? const Color(0xFFB5B5B5)
        : const Color(0xFFB8B8B8);
    final textColor = isDark ? Colors.white : Colors.black;
    final hintColor = isDark
        ? const Color(0xFF8F8F8F)
        : const Color(0xFFA8A8A8);

    return ResponsiveScaffold(
      backgroundColor: backgroundColor,
      currentIndex: 1,
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 18, 24, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 16),
              const ModelBanner(),
              const SizedBox(height: 12),
              const SizedBox(height: 18),
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
                delay: const Duration(milliseconds: 190),
                fromYOffset: 16,
                duration: const Duration(milliseconds: 540),
                child: _CategoryScroller(
                  categories: _categories,
                  selectedCategory: _selectedCategory,
                  textColor: textColor,
                  borderColor: borderColor,
                  onSelected: _isSaving
                      ? null
                      : (category) {
                          setState(() => _selectedCategory = category);
                        },
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
            ],
          ),
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
            color: Theme.of(context).brightness == Brightness.dark
                ? Colors.white
                : Colors.black,
            fontSize: 18,
          ),
        ),
      ),
    );
  }
}

class _CategoryScroller extends StatelessWidget {
  const _CategoryScroller({
    required this.categories,
    required this.selectedCategory,
    required this.textColor,
    required this.borderColor,
    required this.onSelected,
  });

  final List<String> categories;
  final String selectedCategory;
  final Color textColor;
  final Color borderColor;
  final ValueChanged<String>? onSelected;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final selectedColor = isDark
        ? const Color(0xFFBFC4FF)
        : const Color(0xFF0B18F1);
    final selectedTextColor = isDark ? Colors.black : Colors.white;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: 12),
          child: Text(
            '카테고리',
            style: TextStyle(
              color: textColor,
              fontSize: 18,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
        SizedBox(
          height: 46,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: categories.length,
            separatorBuilder: (_, __) => const SizedBox(width: 10),
            itemBuilder: (context, index) {
              final category = categories[index];
              final selected = category == selectedCategory;
              return ChoiceChip(
                label: Text(category),
                selected: selected,
                onSelected: onSelected == null
                    ? null
                    : (_) => onSelected!(category),
                showCheckmark: false,
                labelStyle: TextStyle(
                  color: selected ? selectedTextColor : textColor,
                  fontSize: 15,
                  fontWeight: FontWeight.w800,
                ),
                selectedColor: selectedColor,
                backgroundColor: Colors.transparent,
                disabledColor: Colors.transparent,
                shape: StadiumBorder(
                  side: BorderSide(
                    color: selected ? selectedColor : borderColor,
                    width: selected ? 0 : 1,
                  ),
                ),
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 10,
                ),
              );
            },
          ),
        ),
      ],
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
          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
        ),
      ),
    );
  }
}
