import 'package:flutter/material.dart';

import '../admin_theme_controller.dart';
import '../models/admin_models.dart';
import '../services/admin_api.dart';
import '../services/admin_session_store.dart';
import 'package:excel/excel.dart' hide Border;

import '../utils/category_classifier.dart';
import '../utils/file_download.dart';

const String kAdminApiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'https://api.54.116.164.162.nip.io',
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
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('세션이 만료되었습니다. 다시 로그인해주세요.')));
    }
    await _logout();
  }

  @override
  Widget build(BuildContext context) {
    final isWide = MediaQuery.sizeOf(context).width >= 900;
    final destinations = const [
      _AdminDestination(
        icon: Icons.restaurant_menu_rounded,
        label: '추천 관리',
        description: '추천 요청과 결정 메뉴',
      ),
      _AdminDestination(
        icon: Icons.support_agent_rounded,
        label: '문의 관리',
        description: '사용자 문의 내역',
      ),
    ];

    final pages = [
      _RecommendationsPage(
        api: _api,
        session: widget.session,
        onUnauthorized: _handleUnauthorized,
      ),
      _InquiriesPage(
        api: _api,
        session: widget.session,
        onUnauthorized: _handleUnauthorized,
      ),
    ];

    return Scaffold(
      appBar: isWide
          ? null
          : AppBar(
              title: Text(destinations[_index].label),
              actions: [
                IconButton(
                  tooltip: '모드 전환',
                  onPressed: AdminThemeController.toggleTheme,
                  icon: const Icon(Icons.brightness_6_rounded),
                ),
                IconButton(
                  tooltip: '로그아웃',
                  onPressed: _logout,
                  icon: const Icon(Icons.logout_rounded),
                ),
                const SizedBox(width: 8),
              ],
            ),
      drawer: isWide
          ? null
          : _AdminDrawer(
              email: widget.session.adminEmail,
              selectedIndex: _index,
              destinations: destinations,
              onSelected: (idx) {
                setState(() => _index = idx);
                Navigator.pop(context);
              },
            ),
      body: SafeArea(
        child: Row(
          children: [
            if (isWide)
              _AdminSidebar(
                email: widget.session.adminEmail,
                selectedIndex: _index,
                destinations: destinations,
                onSelected: (idx) => setState(() => _index = idx),
              ),
            Expanded(
              child: Column(
                children: [
                  if (isWide)
                    _AdminTopBar(
                      title: destinations[_index].label,
                      description: destinations[_index].description,
                      onToggleTheme: AdminThemeController.toggleTheme,
                      onLogout: _logout,
                    ),
                  Expanded(child: pages[_index]),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AdminDestination {
  const _AdminDestination({
    required this.icon,
    required this.label,
    required this.description,
  });

  final IconData icon;
  final String label;
  final String description;
}

class _AdminSidebar extends StatelessWidget {
  const _AdminSidebar({
    required this.email,
    required this.selectedIndex,
    required this.destinations,
    required this.onSelected,
  });

  final String email;
  final int selectedIndex;
  final List<_AdminDestination> destinations;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Container(
      width: 280,
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: scheme.surface,
        borderRadius: BorderRadius.circular(26),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.55),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const _AdminBrand(),
          const SizedBox(height: 28),
          for (var i = 0; i < destinations.length; i++)
            _AdminNavTile(
              destination: destinations[i],
              selected: selectedIndex == i,
              onTap: () => onSelected(i),
            ),
          const Spacer(),
          _AdminProfile(email: email),
        ],
      ),
    );
  }
}

class _AdminDrawer extends StatelessWidget {
  const _AdminDrawer({
    required this.email,
    required this.selectedIndex,
    required this.destinations,
    required this.onSelected,
  });

  final String email;
  final int selectedIndex;
  final List<_AdminDestination> destinations;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) {
    return Drawer(
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const _AdminBrand(),
              const SizedBox(height: 24),
              for (var i = 0; i < destinations.length; i++)
                _AdminNavTile(
                  destination: destinations[i],
                  selected: selectedIndex == i,
                  onTap: () => onSelected(i),
                ),
              const Spacer(),
              _AdminProfile(email: email),
            ],
          ),
        ),
      ),
    );
  }
}

class _AdminBrand extends StatelessWidget {
  const _AdminBrand();

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Row(
      children: [
        Container(
          width: 48,
          height: 48,
          decoration: BoxDecoration(
            color: scheme.primary,
            borderRadius: BorderRadius.circular(16),
          ),
          child: Icon(Icons.restaurant_rounded, color: scheme.onPrimary),
        ),
        const SizedBox(width: 12),
        const Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'MenuPick',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900),
              ),
              SizedBox(height: 2),
              Text('Admin Console', style: TextStyle(fontSize: 12)),
            ],
          ),
        ),
      ],
    );
  }
}

class _AdminNavTile extends StatelessWidget {
  const _AdminNavTile({
    required this.destination,
    required this.selected,
    required this.onTap,
  });

  final _AdminDestination destination;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Material(
        color: selected ? scheme.primaryContainer : Colors.transparent,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            child: Row(
              children: [
                Icon(
                  destination.icon,
                  color: selected
                      ? scheme.onPrimaryContainer
                      : scheme.onSurfaceVariant,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    destination.label,
                    style: TextStyle(
                      fontWeight: selected ? FontWeight.w800 : FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _AdminProfile extends StatelessWidget {
  const _AdminProfile({required this.email});

  final String email;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest.withValues(alpha: 0.68),
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        children: [
          CircleAvatar(
            backgroundColor: scheme.primary,
            child: Text(
              email.isEmpty ? 'A' : email.characters.first.toUpperCase(),
              style: TextStyle(
                color: scheme.onPrimary,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '관리자',
                  style: TextStyle(fontWeight: FontWeight.w800),
                ),
                Text(
                  email,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 12,
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AdminTopBar extends StatelessWidget {
  const _AdminTopBar({
    required this.title,
    required this.description,
    required this.onToggleTheme,
    required this.onLogout,
  });

  final String title;
  final String description;
  final VoidCallback onToggleTheme;
  final VoidCallback onLogout;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Padding(
      padding: const EdgeInsets.fromLTRB(0, 18, 18, 8),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  description,
                  style: TextStyle(color: scheme.onSurfaceVariant),
                ),
              ],
            ),
          ),
          IconButton.filledTonal(
            tooltip: '모드 전환',
            onPressed: onToggleTheme,
            icon: const Icon(Icons.brightness_6_rounded),
          ),
          const SizedBox(width: 10),
          IconButton.filledTonal(
            tooltip: '로그아웃',
            onPressed: onLogout,
            icon: const Icon(Icons.logout_rounded),
          ),
        ],
      ),
    );
  }
}

class _RecommendationsPage extends StatefulWidget {
  const _RecommendationsPage({
    required this.api,
    required this.session,
    required this.onUnauthorized,
  });

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
  String _selectedCategory = '전체';

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

  List<String> _categoryOptions() {
    return ['전체', ...CategoryClassifier.categoryOrder];
  }

  String _categoryForItem(RecommendationItem item) {
    if (item.category.trim().isNotEmpty) {
      return item.category.trim();
    }
    return CategoryClassifier.classify(item.menuName, item.recommendedMenu);
  }

  Future<void> _exportXlsx(List<RecommendationItem> items) async {
    final excel = Excel.createExcel();
    final sheet = excel['추천목록'];
    sheet.appendRow([
      TextCellValue('추천ID'),
      TextCellValue('원한메뉴'),
      TextCellValue('추천메뉴'),
      TextCellValue('이유'),
      TextCellValue('카테고리'),
      TextCellValue('작성시간'),
    ]);
    for (final item in items) {
      sheet.appendRow([
        IntCellValue(item.id),
        TextCellValue(item.menuName),
        TextCellValue(item.recommendedMenu),
        TextCellValue(item.reason),
        TextCellValue(_categoryForItem(item)),
        TextCellValue(item.createdAt.toLocal().toString()),
      ]);
    }

    final categorySheet = excel['카테고리목록'];
    categorySheet.appendRow([TextCellValue('카테고리'), TextCellValue('개수')]);
    final counts = CategoryClassifier.summarize(items);
    for (final category in CategoryClassifier.categoryOrder) {
      categorySheet.appendRow([
        TextCellValue(category),
        IntCellValue(counts[category] ?? 0),
      ]);
    }

    excel.delete('Sheet1');
    final bytes = excel.encode();
    if (bytes == null) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('엑셀 생성에 실패했습니다.')));
      }
      return;
    }

    final now = DateTime.now();
    final filename =
        "추천목록_${now.year}${now.month.toString().padLeft(2, '0')}${now.day.toString().padLeft(2, '0')}.xlsx";

    try {
      await downloadBytes(
        filename: filename,
        bytes: bytes,
        mimeType:
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('다운로드 실패: $e')));
      }
    }
  }

  Future<void> _confirmAndDeleteSelected({
    required List<RecommendationItem> items,
  }) async {
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

      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('삭제 완료: $deleted개')));
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
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('삭제 실패: $e')));
    } finally {
      if (mounted) {
        setState(() => _isDeleting = false);
      }
    }
  }

  Future<void> _setDecision(RecommendationItem item) async {
    final messenger = ScaffoldMessenger.of(context);
    final now = DateTime.now();
    final date = await showDatePicker(
      context: context,
      firstDate: DateTime(now.year, now.month, now.day),
      lastDate: DateTime(now.year + 1),
      initialDate: now,
    );
    if (date == null) return;

    if (!mounted) return;

    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(now.add(const Duration(minutes: 5))),
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

      if (!mounted) return;

      messenger.showSnackBar(
        SnackBar(content: Text('결정 메뉴 설정 완료 (공개: $publishAt)')),
      );
    } catch (e) {
      if (!mounted) return;

      messenger.showSnackBar(SnackBar(content: Text('설정 실패: $e')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<RecommendationItem>>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const _LoadingState();
        }
        if (snapshot.hasError) {
          final err = snapshot.error;
          if (err is AdminUnauthorizedException) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              widget.onUnauthorized();
            });
            return const Center(child: Text('세션 만료. 다시 로그인해주세요.'));
          }
          return Center(child: Text('불러오기 실패: ${snapshot.error}'));
        }

        final items = snapshot.data ?? const [];
        if (items.isEmpty) {
          return const _EmptyState(
            icon: Icons.restaurant_menu_rounded,
            title: '추천 데이터가 없습니다.',
            message: '사용자가 메뉴 추천을 요청하면 이곳에 표시됩니다.',
          );
        }

        final categoryOptions = _categoryOptions();
        final filteredItems = _selectedCategory == '전체'
            ? items
            : items
                  .where((item) => _categoryForItem(item) == _selectedCategory)
                  .toList();
        final categoryCounts = CategoryClassifier.summarize(items);
        final recentText = items.isEmpty
            ? '-'
            : _formatDateTime(items.first.createdAt);

        final allSelected =
            _selectedIds.isNotEmpty &&
            _selectedIds.length == filteredItems.length;
        final selectionText = _selectedIds.isEmpty
            ? '항목을 선택해서 삭제할 수 있어요.'
            : '${_selectedIds.length}개 선택됨';

        return RefreshIndicator(
          onRefresh: _refresh,
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(18, 18, 18, 8),
                  child: _MetricGrid(
                    metrics: [
                      _MetricData(
                        icon: Icons.fact_check_rounded,
                        label: '전체 추천',
                        value: '${items.length}',
                        accent: const Color(0xFF4F56D7),
                      ),
                      _MetricData(
                        icon: Icons.filter_alt_rounded,
                        label: '현재 보기',
                        value: '${filteredItems.length}',
                        accent: const Color(0xFF00A88F),
                      ),
                      _MetricData(
                        icon: Icons.local_fire_department_rounded,
                        label: '한식 분류',
                        value: '${categoryCounts['한식'] ?? 0}',
                        accent: const Color(0xFFF05A28),
                      ),
                      _MetricData(
                        icon: Icons.schedule_rounded,
                        label: '최근 요청',
                        value: recentText,
                        accent: const Color(0xFF7B61FF),
                      ),
                    ],
                  ),
                ),
              ),
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(18, 8, 18, 8),
                  child: _AdminPanel(
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
                                    _selectAll(filteredItems);
                                  }
                                },
                          child: Text(allSelected ? '전체 해제' : '전체 선택'),
                        );

                        final categoryDropdown = DropdownButton<String>(
                          value: _selectedCategory,
                          borderRadius: BorderRadius.circular(14),
                          underline: const SizedBox.shrink(),
                          items: categoryOptions
                              .map(
                                (category) => DropdownMenuItem(
                                  value: category,
                                  child: Text(category),
                                ),
                              )
                              .toList(growable: false),
                          onChanged: (value) {
                            if (value == null) return;
                            setState(() {
                              _selectedCategory = value;
                              _selectedIds.clear();
                            });
                          },
                        );

                        final exportButton = FilledButton.tonalIcon(
                          onPressed: filteredItems.isEmpty
                              ? null
                              : () => _exportXlsx(filteredItems),
                          icon: const Icon(Icons.download_rounded),
                          label: const Text('엑셀 다운로드'),
                        );

                        final deleteButton = FilledButton.tonalIcon(
                          onPressed: (_selectedIds.isEmpty || _isDeleting)
                              ? null
                              : () => _confirmAndDeleteSelected(
                                  items: filteredItems,
                                ),
                          icon: _isDeleting
                              ? const SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                  ),
                                )
                              : const Icon(Icons.delete_rounded),
                          label: const Text('삭제'),
                        );

                        if (isNarrow) {
                          return Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                selectionText,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 8,
                                runSpacing: 8,
                                children: [
                                  categoryDropdown,
                                  exportButton,
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
                                style: const TextStyle(
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                            categoryDropdown,
                            const SizedBox(width: 8),
                            exportButton,
                            const SizedBox(width: 8),
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
                padding: const EdgeInsets.fromLTRB(18, 8, 18, 22),
                sliver: SliverList(
                  delegate: SliverChildBuilderDelegate((context, index) {
                    final item = filteredItems[index];
                    final isSelected = _selectedIds.contains(item.id);
                    final category = _categoryForItem(item);
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: _RecommendationCard(
                        item: item,
                        category: category,
                        selected: isSelected,
                        deleting: _isDeleting,
                        selectionMode: _selectedIds.isNotEmpty,
                        onToggle: () => _toggleSelected(item.id),
                        onSetDecision: () => _setDecision(item),
                      ),
                    );
                  }, childCount: filteredItems.length),
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
  const _InquiriesPage({
    required this.api,
    required this.session,
    required this.onUnauthorized,
  });

  final AdminApi api;
  final AdminSession session;
  final Future<void> Function() onUnauthorized;

  @override
  State<_InquiriesPage> createState() => _InquiriesPageState();
}

class _InquiriesPageState extends State<_InquiriesPage> {
  late Future<List<InquiryItem>> _future;
  final Set<int> _selectedIds = <int>{};
  bool _isDeleting = false;

  @override
  void initState() {
    super.initState();
    _future = widget.api.fetchInquiries(token: widget.session.token);
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

  void _selectAll(List<InquiryItem> items) {
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
      _future = widget.api.fetchInquiries(token: widget.session.token);
    });
    await _future;
  }

  Future<void> _confirmAndDeleteSelected() async {
    if (_isDeleting) return;
    final ids = _selectedIds.toList(growable: false);
    if (ids.isEmpty) return;

    final ok = await showDialog<bool>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('삭제 확인'),
          content: Text('${ids.length}개 문의를 삭제할까요?\n(삭제하면 복구할 수 없습니다.)'),
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
      final deleted = await widget.api.deleteInquiries(
        token: widget.session.token,
        ids: ids,
      );

      if (!mounted) return;

      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('삭제 완료: $deleted개')));
      setState(() {
        _selectedIds.clear();
        _future = widget.api.fetchInquiries(token: widget.session.token);
      });
      await _future;
    } catch (e) {
      if (e is AdminUnauthorizedException) {
        await widget.onUnauthorized();
        return;
      }
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('삭제 실패: $e')));
    } finally {
      if (mounted) {
        setState(() => _isDeleting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<InquiryItem>>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const _LoadingState();
        }
        if (snapshot.hasError) {
          final err = snapshot.error;
          if (err is AdminUnauthorizedException) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              widget.onUnauthorized();
            });
            return const Center(child: Text('세션 만료. 다시 로그인해주세요.'));
          }
          return Center(child: Text('불러오기 실패: ${snapshot.error}'));
        }

        final items = snapshot.data ?? const [];
        if (items.isEmpty) {
          return const _EmptyState(
            icon: Icons.support_agent_rounded,
            title: '문의 데이터가 없습니다.',
            message: '새로운 문의가 등록되면 이곳에서 확인할 수 있습니다.',
          );
        }

        final allSelected =
            _selectedIds.isNotEmpty && _selectedIds.length == items.length;
        final selectionText = _selectedIds.isEmpty
            ? '항목을 선택해서 삭제할 수 있어요.'
            : '${_selectedIds.length}개 선택됨';
        final recentText = items.isEmpty
            ? '-'
            : _formatDateTime(items.first.createdAt);

        return RefreshIndicator(
          onRefresh: _refresh,
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(18, 18, 18, 8),
                  child: _MetricGrid(
                    metrics: [
                      _MetricData(
                        icon: Icons.mark_email_unread_rounded,
                        label: '전체 문의',
                        value: '${items.length}',
                        accent: const Color(0xFF4F56D7),
                      ),
                      _MetricData(
                        icon: Icons.checklist_rounded,
                        label: '선택 항목',
                        value: '${_selectedIds.length}',
                        accent: const Color(0xFF00A88F),
                      ),
                      _MetricData(
                        icon: Icons.schedule_rounded,
                        label: '최근 문의',
                        value: recentText,
                        accent: const Color(0xFF7B61FF),
                      ),
                    ],
                  ),
                ),
              ),
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(18, 8, 18, 8),
                  child: _AdminPanel(
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
                              : () => _confirmAndDeleteSelected(),
                          icon: _isDeleting
                              ? const SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                  ),
                                )
                              : const Icon(Icons.delete_rounded),
                          label: const Text('삭제'),
                        );

                        if (isNarrow) {
                          return Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                selectionText,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 8,
                                runSpacing: 8,
                                children: [selectAllButton, deleteButton],
                              ),
                            ],
                          );
                        }

                        return Row(
                          children: [
                            Expanded(
                              child: Text(
                                selectionText,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w700,
                                ),
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
                padding: const EdgeInsets.fromLTRB(18, 8, 18, 22),
                sliver: SliverList(
                  delegate: SliverChildBuilderDelegate((context, index) {
                    final item = items[index];
                    final isSelected = _selectedIds.contains(item.id);
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: _InquiryCard(
                        item: item,
                        selected: isSelected,
                        deleting: _isDeleting,
                        onToggle: () => _toggleSelected(item.id),
                      ),
                    );
                  }, childCount: items.length),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _MetricData {
  const _MetricData({
    required this.icon,
    required this.label,
    required this.value,
    required this.accent,
  });

  final IconData icon;
  final String label;
  final String value;
  final Color accent;
}

class _MetricGrid extends StatelessWidget {
  const _MetricGrid({required this.metrics});

  final List<_MetricData> metrics;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final columns = constraints.maxWidth >= 980
            ? metrics.length
            : (constraints.maxWidth >= 620 ? 2 : 1);
        final itemWidth =
            (constraints.maxWidth - ((columns - 1) * 12)) / columns;

        return Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            for (final metric in metrics)
              SizedBox(
                width: itemWidth,
                child: _MetricCard(metric: metric),
              ),
          ],
        );
      },
    );
  }
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({required this.metric});

  final _MetricData metric;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Row(
          children: [
            Container(
              width: 46,
              height: 46,
              decoration: BoxDecoration(
                color: metric.accent.withValues(alpha: 0.13),
                borderRadius: BorderRadius.circular(15),
              ),
              child: Icon(metric.icon, color: metric.accent),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    metric.label,
                    style: TextStyle(
                      color: scheme.onSurfaceVariant,
                      fontSize: 12,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    metric.value,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AdminPanel extends StatelessWidget {
  const _AdminPanel({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: scheme.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.55),
        ),
      ),
      child: child,
    );
  }
}

class _RecommendationCard extends StatelessWidget {
  const _RecommendationCard({
    required this.item,
    required this.category,
    required this.selected,
    required this.deleting,
    required this.selectionMode,
    required this.onToggle,
    required this.onSetDecision,
  });

  final RecommendationItem item;
  final String category;
  final bool selected;
  final bool deleting;
  final bool selectionMode;
  final VoidCallback onToggle;
  final VoidCallback onSetDecision;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Card(
      color: selected ? scheme.primaryContainer.withValues(alpha: 0.45) : null,
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: deleting ? null : onToggle,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final isNarrow = constraints.maxWidth < 620;
              final action = selectionMode
                  ? const SizedBox.shrink()
                  : FilledButton.tonalIcon(
                      onPressed: onSetDecision,
                      icon: const Icon(Icons.event_available_rounded, size: 18),
                      label: const Text('결정 설정'),
                    );

              final content = Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      _StatusPill(
                        label: category,
                        icon: Icons.category_rounded,
                      ),
                      _StatusPill(
                        label: '#${item.id}',
                        icon: Icons.tag_rounded,
                      ),
                      _StatusPill(
                        label: _formatDateTime(item.createdAt),
                        icon: Icons.schedule_rounded,
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text(
                    item.recommendedMenu,
                    style: const TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    '원한 메뉴: ${item.menuName}',
                    style: TextStyle(
                      fontWeight: FontWeight.w700,
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    item.reason,
                    maxLines: 3,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: scheme.onSurfaceVariant,
                      height: 1.45,
                    ),
                  ),
                ],
              );

              return Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Checkbox(
                    value: selected,
                    onChanged: deleting ? null : (_) => onToggle(),
                  ),
                  const SizedBox(width: 8),
                  Expanded(child: content),
                  if (!isNarrow) ...[const SizedBox(width: 14), action],
                  if (isNarrow && !selectionMode) ...[
                    const SizedBox(width: 8),
                    IconButton.filledTonal(
                      tooltip: '결정 설정',
                      onPressed: onSetDecision,
                      icon: const Icon(Icons.event_available_rounded),
                    ),
                  ],
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

class _InquiryCard extends StatelessWidget {
  const _InquiryCard({
    required this.item,
    required this.selected,
    required this.deleting,
    required this.onToggle,
  });

  final InquiryItem item;
  final bool selected;
  final bool deleting;
  final VoidCallback onToggle;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Card(
      color: selected ? scheme.primaryContainer.withValues(alpha: 0.45) : null,
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: deleting ? null : onToggle,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Checkbox(
                value: selected,
                onChanged: deleting ? null : (_) => onToggle(),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        _StatusPill(
                          label: '문의 #${item.id}',
                          icon: Icons.confirmation_number_rounded,
                        ),
                        _StatusPill(
                          label: _formatDateTime(item.createdAt),
                          icon: Icons.schedule_rounded,
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Text(
                      item.message,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        height: 1.45,
                      ),
                    ),
                    if (item.adminEmail.isNotEmpty) ...[
                      const SizedBox(height: 10),
                      Text(
                        item.adminEmail,
                        style: TextStyle(
                          color: scheme.onSurfaceVariant,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.label, required this.icon});

  final String label;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest.withValues(alpha: 0.78),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: scheme.onSurfaceVariant),
          const SizedBox(width: 5),
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w700,
              color: scheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

class _LoadingState extends StatelessWidget {
  const _LoadingState();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: SizedBox(
        width: 36,
        height: 36,
        child: CircularProgressIndicator(strokeWidth: 3),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({
    required this.icon,
    required this.title,
    required this.message,
  });

  final IconData icon;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(28),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 64,
                  height: 64,
                  decoration: BoxDecoration(
                    color: scheme.primaryContainer,
                    borderRadius: BorderRadius.circular(22),
                  ),
                  child: Icon(icon, color: scheme.onPrimaryContainer),
                ),
                const SizedBox(height: 18),
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  message,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: scheme.onSurfaceVariant,
                    height: 1.45,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

String _formatDateTime(DateTime value) {
  final local = value.toLocal();
  final month = local.month.toString().padLeft(2, '0');
  final day = local.day.toString().padLeft(2, '0');
  final hour = local.hour.toString().padLeft(2, '0');
  final minute = local.minute.toString().padLeft(2, '0');
  return '$month.$day $hour:$minute';
}
