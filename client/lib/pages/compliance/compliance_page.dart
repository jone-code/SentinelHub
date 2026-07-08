import 'package:flutter/material.dart';

class CompliancePage extends StatelessWidget {
  const CompliancePage({super.key});

  static const _items = [
    ('操作系统补丁', '待扫描'),
    ('杀毒软件', '待扫描'),
    ('防火墙', '待扫描'),
    ('磁盘加密', '待扫描'),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('合规状态')),
      body: ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: _items.length,
        separatorBuilder: (_, __) => const SizedBox(height: 8),
        itemBuilder: (context, i) {
          final (title, status) = _items[i];
          return Card(
            child: ListTile(
              title: Text(title),
              trailing: Chip(label: Text(status)),
            ),
          );
        },
      ),
    );
  }
}
