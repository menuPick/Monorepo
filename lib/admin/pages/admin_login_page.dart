import 'package:flutter/material.dart';

import '../services/admin_api.dart';
import '../services/admin_session_store.dart';

const String kAdminApiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://localhost:8080',
);

class AdminLoginPage extends StatefulWidget {
  const AdminLoginPage({super.key, required this.onLoginSuccess});

  final VoidCallback onLoginSuccess;

  @override
  State<AdminLoginPage> createState() => _AdminLoginPageState();
}

class _AdminLoginPageState extends State<AdminLoginPage> {
  final _idController = TextEditingController();
  bool _loading = false;
  bool _showAdminId = false;

  @override
  void dispose() {
    _idController.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    final id = _idController.text.trim();
    if (id.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('관리자 ID를 입력해주세요.')));
      return;
    }

    setState(() => _loading = true);
    try {
      final api = AdminApi(
        baseUrl: AdminApi.normalizeBaseUrl(kAdminApiBaseUrl),
      );
      final session = await api.login(id: id);
      await AdminSessionStore.save(session);

      if (!mounted) return;
      widget.onLoginSuccess();
    } on AdminApiException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(e.message)));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('로그인 중 오류가 발생했습니다.')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Text(
                      'MenuPick Admin',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: _idController,
                      obscureText: !_showAdminId,
                      decoration: InputDecoration(
                        labelText: '관리자 ID',
                        border: OutlineInputBorder(),
                        suffixIcon: IconButton(
                          tooltip: _showAdminId ? '관리자 ID 숨기기' : '관리자 ID 보기',
                          onPressed: _loading
                              ? null
                              : () {
                                  setState(() => _showAdminId = !_showAdminId);
                                },
                          icon: Text(_showAdminId ? '<1>' : '<0>'),
                        ),
                      ),
                      onSubmitted: (_) => _login(),
                      enabled: !_loading,
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      height: 48,
                      child: FilledButton(
                        onPressed: _loading ? null : _login,
                        child: Text(_loading ? '로그인 중...' : '로그인'),
                      ),
                    ),
                    const SizedBox(height: 10),
                    Text(
                      '서버: ${AdminApi.normalizeBaseUrl(kAdminApiBaseUrl)}',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
