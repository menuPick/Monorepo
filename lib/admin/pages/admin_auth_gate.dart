import 'package:flutter/material.dart';

import '../models/admin_models.dart';
import '../services/admin_session_store.dart';
import 'admin_dashboard_page.dart';
import 'admin_login_page.dart';

class AdminAuthGate extends StatefulWidget {
  const AdminAuthGate({super.key});

  @override
  State<AdminAuthGate> createState() => _AdminAuthGateState();
}

class _AdminAuthGateState extends State<AdminAuthGate> {
  late Future<AdminSession?> _sessionFuture;

  @override
  void initState() {
    super.initState();
    _sessionFuture = AdminSessionStore.load();
  }

  void _reload() {
    setState(() {
      _sessionFuture = AdminSessionStore.load();
    });
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<AdminSession?>(
      future: _sessionFuture,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Scaffold(
            body: Center(child: CircularProgressIndicator()),
          );
        }

        final session = snapshot.data;
        if (session == null) {
          return AdminLoginPage(onLoginSuccess: _reload);
        }

        return AdminDashboardPage(
          session: session,
          onLogout: () async {
            await AdminSessionStore.clear();
            _reload();
          },
        );
      },
    );
  }
}

