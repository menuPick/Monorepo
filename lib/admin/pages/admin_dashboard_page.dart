import 'package:flutter/material.dart';

import '../admin_theme_controller.dart';
import '../models/admin_models.dart';
import '../services/admin_api.dart';
import '../services/admin_session_store.dart';

const String kAdminApiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://localhost:8080',
);

class AdminDashboardPage extends StatefulWidget {
  const AdminDashboardPage({
    super.key,
    required this.session,
    required this.onLogout,
  });

  final AdminSession session;
  final Future<void> Function() onLogout;

  @override
  State<AdminDashboardPage> createState() => _AdminDashboardPageState();
}

class _AdminDashboardPageState extends State<AdminDashboardPage> {
  int _index = 0;
  late final AdminApi _api;

  bool _loggingOutDueToUnauthorized = false;

  @override
  void initState() {
    super.initState();
    _api = AdminApi(baseUrl: AdminApi.normalizeBaseUrl(kAdminApiBaseUrl));
  }

  Future<void> _logout() async {
    await AdminSessionStore.clear();
    await widget.onLogout();
  }

  Future<void> _handleUnauthorized() async {
    if (_loggingOutDueToUnauthorized) return;
    _loggingOutDueToUnauthorized = true;
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('세션이 만료되었습니다. 다시 로그인해주세요.')),
      );
    }
    await _logout();
  }

  @override
  Widget build(BuildContext context) {
    final isWide = MediaQuery.sizeOf(context).width >= 900;

    final pages = [
      _RecommendationsPage(api: _api, session: widget.session, onUnauthorized: _handleUnauthorized),
      _InquiriesPage(api: _api, session: widget.session, onUnauthorized: _handleUnauthorized),
    ];

    final rail = NavigationRail(
      selectedIndex: _index,
      onDestinationSelected: (idx) => setState(() => _index = idx),
      labelType: NavigationRailLabelType.all,
      destinations: const [
        NavigationRailDestination(
          icon: Icon(Icons.restaurant_menu),
          label: Text('추천'),
        ),
        NavigationRailDestination(
          icon: Icon(Icons.support_agent),
          label: Text('문의'),
        ),
      ],
    );

    return Scaffold(
      appBar: AppBar(
        title: const Text('관리자 대시보드'),
        actions: [
          IconButton(
            tooltip: '모드 전환',
            onPressed: AdminThemeController.toggleTheme,
            icon: const Icon(Icons.brightness_6),
          ),
          IconButton(
            tooltip: '로그아웃',
            onPressed: _logout,
            icon: const Icon(Icons.logout),
          ),
          const SizedBox(width: 8),
        ],
      ),
      drawer: isWide
          ? null
          : Drawer(
              child: ListView(
                children: [
                  DrawerHeader(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('MenuPick Admin', style: TextStyle(fontWeight: FontWeight.w800)),
                        const SizedBox(height: 6),
                        Text(widget.session.adminEmail, style: const TextStyle(fontSize: 12)),
                      ],
                    ),
                  ),
                  ListTile(
                    leading: const Icon(Icons.restaurant_menu),
                    title: const Text('추천 목록'),
                    selected: _index == 0,
                    onTap: () {
                      setState(() => _index = 0);
                      Navigator.pop(context);
                    },
                  ),
                  ListTile(
                    leading: const Icon(Icons.support_agent),
                    title: const Text('문의 목록'),
                    selected: _index == 1,
                    onTap: () {
                      setState(() => _index = 1);
                      Navigator.pop(context);
                    },
                  ),
                ],
              ),
            ),
      body: Row(
        children: [
          if (isWide) rail,
          Expanded(child: pages[_index]),
        ],
      ),
    );
  }
}

class _RecommendationsPage extends StatefulWidget {
  const _RecommendationsPage({required this.api, required this.session, required this.onUnauthorized});

  final AdminApi api;
  final AdminSession session;
  final Future<void> Function() onUnauthorized;

  @override
  State<_RecommendationsPage> createState() => _RecommendationsPageState();
}

class _RecommendationsPageState extends State<_RecommendationsPage> {
  late Future<List<RecommendationItem>> _future;
  final Set<int> _selectedIds = <int>{};
  bool _isDeleting = false;

  @override
  void initState() {
    super.initState();
    _future = widget.api.fetchRecommendations(token: widget.session.token);
  }

  void _toggleSelected(int id) {
    setState(() {
      if (_selectedIds.contains(id)) {
        _selectedIds.remove(id);
      } else {
        _selectedIds.add(id);
      }
    });
  }

  void _selectAll(List<RecommendationItem> items) {
    setState(() {
      _selectedIds
        ..clear()
        ..addAll(items.map((e) => e.id));
    });
  }

  void _clearSelection() {
    setState(_selectedIds.clear);
  }

  Future<void> _refresh() async {
    setState(() {
      _selectedIds.clear();
      _future = widget.api.fetchRecommendations(token: widget.session.token);
    });
    await _future;
  }

  Future<void> _confirmAndDeleteSelected({required List<RecommendationItem> items}) async {
    if (_isDeleting) return;
    final ids = _selectedIds.toList(growable: false);
    if (ids.isEmpty) return;

    final ok = await showDialog<bool>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('삭제 확인'),
          content: Text('${ids.length}개 항목을 삭제할까요?\n(삭제하면 복구할 수 없습니다.)'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('취소'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('삭제'),
            ),
          ],
        );
      },
    );

    if (ok != true) return;

    setState(() => _isDeleting = true);

    try {
      final deleted = await widget.api.deleteRecommendations(
        token: widget.session.token,
        ids: ids,
      );

      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('삭제 완료: $deleted개')),
      );
      setState(() {
        _selectedIds.clear();
        _future = widget.api.fetchRecommendations(token: widget.session.token);
      });
      await _future;
    } catch (e) {
      if (e is AdminUnauthorizedException) {
        await widget.onUnauthorized();
        return;
      }
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('삭제 실패: $e')),
      );
    } finally {
      if (mounted) {
        setState(() => _isDeleting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<RecommendationItem>>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          final err = snapshot.error;
          if (err is AdminUnauthorizedException) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              widget.onUnauthorized();
            });
            return const Center(child: Text('세션 만료. 다시 로그인해주세요.'));
          }
          return Center(
            child: Text('불러오기 실패: ${snapshot.error}'),
          );
        }

        final items = snapshot.data ?? const [];
        if (items.isEmpty) {
          return const Center(child: Text('추천 데이터가 없습니다.'));
        }

        final allSelected = _selectedIds.isNotEmpty && _selectedIds.length == items.length;
        final selectionText = _selectedIds.isEmpty
            ? '항목을 선택(체크)해서 삭제할 수 있어요.'
            : '${_selectedIds.length}개 선택됨';

        return RefreshIndicator(
          onRefresh: _refresh,
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                  child: Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Theme.of(context).colorScheme.surfaceContainerHighest,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: LayoutBuilder(
                      builder: (context, constraints) {
                        final isNarrow = constraints.maxWidth < 520;

                        final selectAllButton = TextButton(
                          onPressed: _isDeleting
                              ? null
                              : () {
                                  if (allSelected) {
                                    _clearSelection();
                                  } else {
                                    _selectAll(items);
                                  }
                                },
                          child: Text(allSelected ? '전체 해제' : '전체 선택'),
                        );

                        final deleteButton = FilledButton.tonalIcon(
                          onPressed: (_selectedIds.isEmpty || _isDeleting)
                              ? null
                              : () => _confirmAndDeleteSelected(items: items),
                          icon: _isDeleting
                              ? const SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(strokeWidth: 2),
                                )
                              : const Icon(Icons.delete),
                          label: const Text('삭제'),
                        );

                        if (isNarrow) {
                          return Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                selectionText,
                                style: const TextStyle(fontWeight: FontWeight.w700),
                              ),
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 8,
                                runSpacing: 8,
                                children: [
                                  selectAllButton,
                                  deleteButton,
                                ],
                              ),
                            ],
                          );
                        }

                        return Row(
                          children: [
                            Expanded(
                              child: Text(
                                selectionText,
                                style: const TextStyle(fontWeight: FontWeight.w700),
                              ),
                            ),
                            selectAllButton,
                            const SizedBox(width: 8),
                            deleteButton,
                          ],
                        );
                      },
                    ),
                  ),
                ),
              ),
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                sliver: SliverList(
                  delegate: SliverChildBuilderDelegate(
                    (context, index) {
                      final item = items[index];
                      final isSelected = _selectedIds.contains(item.id);
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: Card(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(vertical: 6),
                            child: ListTile(
                              selected: isSelected,
                              leading: Checkbox(
                                value: isSelected,
                                onChanged: _isDeleting ? null : (_) => _toggleSelected(item.id),
                              ),
                              title: Text(
                                item.recommendedMenu,
                                style: const TextStyle(fontWeight: FontWeight.w800),
                              ),
                              subtitle: Text(
                                '원한 메뉴: ${item.menuName}\n이유: ${item.reason}\n시간: ${item.createdAt.toLocal()}',
                              ),
                              isThreeLine: true,
                              onTap: _isDeleting ? null : () => _toggleSelected(item.id),
                              trailing: _selectedIds.isNotEmpty
                                  ? null
                                  : SizedBox(
                                      width: 110,
                                      child: FilledButton.tonal(
                                        style: FilledButton.styleFrom(
                                          padding: const EdgeInsets.symmetric(horizontal: 8),
                                          minimumSize: const Size(0, 40),
                                        ),
                                        onPressed: () async {
                                          final messenger = ScaffoldMessenger.of(context);

                                          // 공개 시간 선택
                                          final now = DateTime.now();
                                          final date = await showDatePicker(
                                            context: context,
                                            firstDate: DateTime(now.year, now.month, now.day),
                                            lastDate: DateTime(now.year + 1),
                                            initialDate: now,
                                          );
                                          if (date == null) return;

                                          if (!context.mounted) return;

                                          final time = await showTimePicker(
                                            context: context,
                                            initialTime: TimeOfDay.fromDateTime(
                                              now.add(const Duration(minutes: 5)),
                                            ),
                                          );
                                          if (time == null) return;

                                          final publishAt = DateTime(
                                            date.year,
                                            date.month,
                                            date.day,
                                            time.hour,
                                            time.minute,
                                          );

                                          try {
                                            await widget.api.setDecision(
                                              token: widget.session.token,
                                              recommendationId: item.id,
                                              publishAt: publishAt,
                                            );

                                            if (!context.mounted) return;

                                            messenger.showSnackBar(
                                              SnackBar(
                                                content: Text('결정 메뉴 설정 완료 (공개: $publishAt)'),
                                              ),
                                            );
                                          } catch (e) {
                                            if (!context.mounted) return;

                                            messenger.showSnackBar(
                                              SnackBar(content: Text('설정 실패: $e')),
                                            );
                                          }
                                        },
                                        child: const FittedBox(child: Text('결정 설정')),
                                      ),
                                    ),
                            ),
                          ),
                        ),
                      );
                    },
                    childCount: items.length,
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _InquiriesPage extends StatefulWidget {
  const _InquiriesPage({required this.api, required this.session, required this.onUnauthorized});

  final AdminApi api;
  final AdminSession session;
  final Future<void> Function() onUnauthorized;

  @override
  State<_InquiriesPage> createState() => _InquiriesPageState();
}

class _InquiriesPageState extends State<_InquiriesPage> {
  late Future<List<InquiryItem>> _future;

  @override
  void initState() {
    super.initState();
    _future = widget.api.fetchInquiries(token: widget.session.token);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<InquiryItem>>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          final err = snapshot.error;
          if (err is AdminUnauthorizedException) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              widget.onUnauthorized();
            });
            return const Center(child: Text('세션 만료. 다시 로그인해주세요.'));
          }
          return Center(
            child: Text('불러오기 실패: ${snapshot.error}'),
          );
        }

        final items = snapshot.data ?? const [];
        if (items.isEmpty) {
          return const Center(child: Text('문의 데이터가 없습니다.'));
        }

        return RefreshIndicator(
          onRefresh: () async {
            setState(() {
              _future = widget.api.fetchInquiries(token: widget.session.token);
            });
            await _future;
          },
          child: ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: items.length,
            separatorBuilder: (context, index) => const SizedBox(height: 12),
            itemBuilder: (context, index) {
              final item = items[index];
              return Card(
                child: ListTile(
                  title: Text('문의 #${item.id}', style: const TextStyle(fontWeight: FontWeight.w800)),
                  subtitle: Text('${item.message}\n시간: ${item.createdAt.toLocal()}'),
                  isThreeLine: true,
                ),
              );
            },
          ),
        );
      },
    );
  }
}

