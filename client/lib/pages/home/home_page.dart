import 'package:flutter/material.dart';
import '../../api/local_service_client.dart';
import '../../platform/platform_info.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final _localClient = LocalServiceClient();
  LocalServiceStatus? _localStatus;
  bool _loadingLocal = false;

  @override
  void initState() {
    super.initState();
    _refreshLocalStatus();
  }

  Future<void> _refreshLocalStatus() async {
    if (!PlatformInfo.isDesktop) return;
    setState(() => _loadingLocal = true);
    final status = await _localClient.getStatus();
    if (mounted) {
      setState(() {
        _localStatus = status;
        _loadingLocal = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final local = _localStatus;
    final serviceOk = !PlatformInfo.isDesktop || (local?.running == true);
    final cloudOk = !PlatformInfo.isDesktop || (local?.cloudConnected == true);

    return Scaffold(
      appBar: AppBar(
        title: const Text('安全状态'),
        actions: [
          if (PlatformInfo.isDesktop)
            IconButton(
              icon: const Icon(Icons.refresh),
              onPressed: _loadingLocal ? null : _refreshLocalStatus,
            ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('概览', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
            const SizedBox(height: 16),
            Row(
              children: [
                _StatCard(title: '合规评分', value: '—'),
                const SizedBox(width: 12),
                _StatCard(title: '待处理', value: '0'),
                const SizedBox(width: 12),
                _StatCard(title: '通知', value: '0'),
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
                subtitle: Text(_serviceSubtitle(local, serviceOk)),
              ),
            ),
            if (PlatformInfo.isDesktop && local?.nativeAvailable == true) ...[
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
        ),
      ),
    );
  }

  String _serviceSubtitle(LocalServiceStatus? local, bool serviceOk) {
    if (!PlatformInfo.isDesktop) return '移动端无需后台服务';
    if (_loadingLocal) return '正在检测本地服务…';
    if (!serviceOk) return '后台服务未运行，请启动 client/service';
    final parts = <String>['后台服务运行正常'];
    if (local != null && local.softwareCount > 0) {
      parts.add('已采集 ${local.softwareCount} 个软件');
    }
    if (local?.clientId != null) parts.add('ID: ${local!.clientId}');
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
