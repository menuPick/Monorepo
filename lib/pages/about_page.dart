import 'package:flutter/material.dart';

import 'package:user/data/user_api.dart';
import 'package:user/widgets/app_drawer.dart';

class AboutPage extends StatefulWidget {
  const AboutPage({super.key});

  @override
  State<AboutPage> createState() => _AboutPageState();
}

class _AboutPageState extends State<AboutPage> {
  final TextEditingController _messageController = TextEditingController();
  final UserApi _api = UserApi();
  bool _saving = false;

  @override
  void dispose() {
    _messageController.dispose();
    super.dispose();
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
              Builder(
                builder: (context) => _MenuButton(
                  color: borderColor,
                  onTap: () => Scaffold.of(context).openDrawer(),
                ),
              ),
              const SizedBox(height: 24),
              Text(
                '문의',
                style: TextStyle(
                  color: textColor,
                  fontSize: 34,
                  height: 1.1,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 18),
              _InquiryBox(
                controller: _messageController,
                borderColor: borderColor,
                hintColor: hintColor,
              ),
              const SizedBox(height: 14),
              _InfoBox(
                title: '관리자 계정 이메일',
                subtitle: 'junsumon090608@dgsw.hs.kr',
                borderColor: borderColor,
                hintColor: hintColor,
              ),
              const SizedBox(height: 18),
              SizedBox(
                width: double.infinity,
                height: 60,
                child: ElevatedButton(
                  onPressed: _saving
                      ? null
                      : () async {
                          final messenger = ScaffoldMessenger.of(context);
                          final message = _messageController.text.trim();
                          if (message.isEmpty) {
                            messenger.showSnackBar(
                              const SnackBar(content: Text('문의 내용을 입력해주세요.')),
                            );
                            return;
                          }

                          setState(() => _saving = true);
                          try {
                            await _api.submitInquiry(message: message);
                            if (!mounted) return;
                            _messageController.clear();
                            messenger.showSnackBar(
                              const SnackBar(content: Text('문의가 서버에 저장되었습니다.')),
                            );
                          } on UserApiException catch (e) {
                            if (!mounted) return;
                            messenger.showSnackBar(
                              SnackBar(content: Text('서버 저장 실패: ${e.message}')),
                            );
                          } catch (_) {
                            if (!mounted) return;
                            messenger.showSnackBar(
                              const SnackBar(content: Text('문의 저장 중 오류가 발생했습니다.')),
                            );
                          } finally {
                            if (mounted) setState(() => _saving = false);
                          }
                        },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF0B18F1),
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(999),
                    ),
                  ),
                  child: const Text(
                    '문의 저장',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                  ),
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

class _InfoBox extends StatelessWidget {
  const _InfoBox({
    required this.title,
    required this.subtitle,
    required this.borderColor,
    required this.hintColor,
  });

  final String title;
  final String subtitle;
  final Color borderColor;
  final Color hintColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(32),
        border: Border.all(color: borderColor, width: 1),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            subtitle,
            style: TextStyle(
              fontSize: 16,
              height: 1.4,
              color: hintColor,
            ),
          ),
        ],
      ),
    );
  }
}

class _InquiryBox extends StatelessWidget {
  const _InquiryBox({
    required this.controller,
    required this.borderColor,
    required this.hintColor,
  });

  final TextEditingController controller;
  final Color borderColor;
  final Color hintColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      height: 220,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(32),
        border: Border.all(color: borderColor, width: 1),
      ),
      child: TextField(
        controller: controller,
        maxLines: null,
        expands: true,
        style: TextStyle(
          color: Theme.of(context).brightness == Brightness.dark ? Colors.white : Colors.black,
          fontSize: 16,
        ),
        decoration: InputDecoration(
          hintText: '원하는 내용을 입력해주세요.',
          hintStyle: TextStyle(color: hintColor, fontSize: 16),
          border: InputBorder.none,
          isCollapsed: true,
        ),
      ),
    );
  }
}

