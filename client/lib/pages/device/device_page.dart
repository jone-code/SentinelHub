import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../providers/client_providers.dart';

class DevicePage extends ConsumerWidget {
  const DevicePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final localAsync = ref.watch(localStatusProvider);
    final assetsAsync = ref.watch(localAssetsProvider);
    final cloudAsync = ref.watch(cloudStatusProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('本机信息'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () {
              ref.invalidate(localStatusProvider);
              ref.invalidate(localAssetsProvider);
              ref.invalidate(cloudStatusProvider);
            },
          ),
        ],
      ),
      body: localAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => const Center(child: Text('加载失败')),
        data: (local) {
          final cloud = cloudAsync.valueOrNull;
          final assets = assetsAsync.valueOrNull;
          final hardware = assets?['hardware'] as Map<String, dynamic>?;

          final hostname = hardware?['hostname']?.toString()
              ?? cloud?['hostname']?.toString()
              ?? '—';
          final osType = hardware?['os_type']?.toString() ?? '—';
          final osVersion = hardware?['os_version']?.toString() ?? '—';
          final clientId = local?.clientId ?? cloud?['client_id']?.toString() ?? '—';
          final lastSync = cloud?['last_seen_at']?.toString()
              ?? assets?['collected_at']?.toString()
              ?? '—';
          final softwareCount = local?.softwareCount ?? 0;

          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              _InfoTile(label: '主机名', value: hostname),
              _InfoTile(label: '操作系统', value: '$osType $osVersion'.trim()),
              const _InfoTile(label: '客户端版本', value: '0.1.0'),
              _InfoTile(label: '客户端 ID', value: clientId),
              _InfoTile(label: '最近同步', value: lastSync),
              _InfoTile(label: '已安装软件', value: '$softwareCount 个'),
            ],
          );
        },
      ),
    );
  }
}

class _InfoTile extends StatelessWidget {
  const _InfoTile({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        title: Text(label),
        subtitle: Text(value, textAlign: TextAlign.end),
      ),
    );
  }
}
