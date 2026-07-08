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
          if (data == null) {
            return const Center(child: Text('暂无数据'));
          }
          final score = data['score']?.toString() ?? '0';
          final items = data['items'] as List<dynamic>? ?? [];

          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Card(
                child: ListTile(
                  title: const Text('合规评分'),
                  trailing: Text(score, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
                ),
              ),
              const SizedBox(height: 16),
              ...items.map((item) {
                final map = Map<String, dynamic>.from(item as Map);
                final name = map['name']?.toString() ?? '';
                final status = map['status']?.toString() ?? 'pending';
                final label = status == 'pending' ? '待扫描' : status;
                return Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Card(
                    child: ListTile(
                      title: Text(name),
                      trailing: Chip(label: Text(label)),
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
}
