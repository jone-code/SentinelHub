import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../platform/platform_info.dart';
import '../../providers/client_providers.dart';

class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final localAsync = ref.watch(localStatusProvider);
    final cloudAsync = ref.watch(cloudStatusProvider);
    final isDesktop = PlatformInfo.isDesktop;

    return Scaffold(
      appBar: AppBar(
        title: const Text('安全状态'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () {
              ref.invalidate(localStatusProvider);
              ref.invalidate(cloudStatusProvider);
            },
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: localAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (_, __) => _buildContent(context, null, cloudAsync.valueOrNull, isDesktop),
          data: (local) => _buildContent(context, local, cloudAsync.valueOrNull, isDesktop),
        ),
      ),
    );
  }

  Widget _buildContent(BuildContext context, local, Map<String, dynamic>? cloud, bool isDesktop) {
    final compliance = cloud?['compliance_score']?.toString() ?? '—';
    final pending = cloud?['pending_items']?.toString() ?? '0';
    final notifications = cloud?['unread_notifications']?.toString() ?? '0';
    final cloudOk = isDesktop ? (local?.cloudConnected == true) : cloud != null;
    final serviceOk = !isDesktop || (local?.running == true);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('概览', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
        const SizedBox(height: 16),
        Row(
          children: [
            _StatCard(title: '合规评分', value: compliance),
            const SizedBox(width: 12),
            _StatCard(title: '待处理', value: pending),
            const SizedBox(width: 12),
            _StatCard(title: '通知', value: notifications),
          ],
        ),
        const SizedBox(height: 24),
        Card(
          child: ListTile(
            leading: Icon(
              cloudOk ? Icons.cloud_done_outlined : Icons.cloud_off_outlined,
              color: cloudOk ? Colors.green : Colors.orange,
            ),
            title: Text(cloudOk ? '已连接企业管理平台' : '未连接企业管理平台'),
            subtitle: Text(_subtitle(local, serviceOk, isDesktop)),
          ),
        ),
        if (isDesktop && local?.nativeAvailable == true) ...[
          const SizedBox(height: 8),
          const Card(
            child: ListTile(
              leading: Icon(Icons.memory_outlined, color: Colors.blue),
              title: Text('Native 扩展已加载'),
              subtitle: Text('深度采集由 sentinel-native sidecar 执行'),
            ),
          ),
        ],
      ],
    );
  }

  String _subtitle(local, bool serviceOk, bool isDesktop) {
    if (!isDesktop) return '移动端客户端';
    if (local == null) return '后台服务未运行，请启动 client/service';
    if (!serviceOk) return '后台服务未运行';
    final parts = <String>['后台服务运行正常'];
    if (local.softwareCount > 0) parts.add('已采集 ${local.softwareCount} 个软件');
    if (local.clientId != null) parts.add('ID: ${local.clientId}');
    return parts.join(' · ');
  }
}

class _StatCard extends StatelessWidget {
  const _StatCard({required this.title, required this.value});
  final String title;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 8),
              Text(value, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
            ],
          ),
        ),
      ),
    );
  }
}
