import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../providers/client_providers.dart';

class CompliancePage extends ConsumerWidget {
  const CompliancePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final complianceAsync = ref.watch(complianceProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('合规状态'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(complianceProvider),
          ),
        ],
      ),
      body: complianceAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => const Center(child: Text('无法获取合规数据，请确认后台服务已连接')),
        data: (data) {
          if (data == null || (data['items'] as List?)?.isEmpty != false && (data['score'] ?? 0) == 0) {
            return const Center(child: Text('等待首次合规扫描…'));
          }
          final score = data['score']?.toString() ?? '0';
          final items = data['items'] as List<dynamic>? ?? [];

          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Card(
                child: ListTile(
                  title: const Text('合规评分'),
                  trailing: Text(
                    score,
                    style: TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.bold,
                      color: _scoreColor(int.tryParse(score) ?? 0),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              ...items.map((item) {
                final map = Map<String, dynamic>.from(item as Map);
                final name = map['name']?.toString() ?? '';
                final status = map['status']?.toString() ?? 'pending';
                final detail = map['detail']?.toString() ?? '';
                return Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Card(
                    child: ListTile(
                      title: Text(name),
                      subtitle: detail.isNotEmpty ? Text(detail) : null,
                      trailing: Chip(
                        label: Text(_statusLabel(status)),
                        backgroundColor: _statusBg(status),
                      ),
                    ),
                  ),
                );
              }),
            ],
          );
        },
      ),
    );
  }

  Color _scoreColor(int score) {
    if (score >= 80) return Colors.green;
    if (score >= 60) return Colors.orange;
    return Colors.red;
  }

  String _statusLabel(String status) {
    switch (status) {
      case 'pass':
        return '通过';
      case 'fail':
        return '未通过';
      default:
        return '待扫描';
    }
  }

  Color? _statusBg(String status) {
    switch (status) {
      case 'pass':
        return Colors.green.shade50;
      case 'fail':
        return Colors.red.shade50;
      default:
        return null;
    }
  }
}
