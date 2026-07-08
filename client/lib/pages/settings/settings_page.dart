import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../config/app_config.dart';
import '../../providers/client_providers.dart';

class SettingsPage extends ConsumerStatefulWidget {
  const SettingsPage({super.key});

  @override
  ConsumerState<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends ConsumerState<SettingsPage> {
  final _controller = TextEditingController();
  bool _loaded = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final url = await AppConfig.getServerUrl();
    _controller.text = url;
    setState(() => _loaded = true);
  }

  Future<void> _save() async {
    await AppConfig.setServerUrl(_controller.text.trim());
    ref.invalidate(serverUrlProvider);
    ref.invalidate(clientApiProvider);
    ref.invalidate(cloudStatusProvider);
    ref.invalidate(complianceProvider);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已保存')));
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_loaded) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    return Scaffold(
      appBar: AppBar(title: const Text('设置')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            controller: _controller,
            decoration: const InputDecoration(
              labelText: '服务器地址',
              hintText: 'http://localhost:8080',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
          FilledButton(onPressed: _save, child: const Text('保存')),
          const SizedBox(height: 24),
          const SwitchListTile(
            title: Text('开机自启动'),
            subtitle: Text('仅桌面端（P1）'),
            value: false,
            onChanged: null,
          ),
          const SwitchListTile(
            title: Text('关闭窗口后后台运行'),
            subtitle: Text('配合 Node 后台服务'),
            value: true,
            onChanged: null,
          ),
        ],
      ),
    );
  }
}
