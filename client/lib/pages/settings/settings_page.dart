import 'package:flutter/material.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('设置')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            decoration: const InputDecoration(
              labelText: '服务器地址',
              hintText: 'https://api.sentinel.example.com',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 16),
          SwitchListTile(
            title: const Text('开机自启动'),
            subtitle: const Text('仅桌面端'),
            value: true,
            onChanged: (_) {},
          ),
          SwitchListTile(
            title: const Text('关闭窗口后后台运行'),
            subtitle: const Text('仅桌面端，需配合后台服务'),
            value: true,
            onChanged: (_) {},
          ),
        ],
      ),
    );
  }
}
