import 'package:flutter/material.dart';

class DevicePage extends StatelessWidget {
  const DevicePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('本机信息')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: const [
          _InfoTile(label: '主机名', value: '—'),
          _InfoTile(label: '操作系统', value: '—'),
          _InfoTile(label: '客户端版本', value: '0.1.0'),
          _InfoTile(label: '客户端 ID', value: '—'),
          _InfoTile(label: '最近同步', value: '—'),
        ],
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
      child: ListTile(title: Text(label), trailing: Text(value)),
    );
  }
}
