import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'pages/home/home_page.dart';
import 'pages/compliance/compliance_page.dart';
import 'pages/device/device_page.dart';
import 'pages/notifications/notifications_page.dart';
import 'pages/settings/settings_page.dart';
import 'platform/platform_info.dart';

class SentinelHubClientApp extends StatelessWidget {
  const SentinelHubClientApp({super.key});

  @override
  Widget build(BuildContext context) {
    final router = GoRouter(
      routes: [
        ShellRoute(
          builder: (context, state, child) => _ClientShell(child: child),
          routes: [
            GoRoute(path: '/', builder: (_, __) => const HomePage()),
            GoRoute(path: '/compliance', builder: (_, __) => const CompliancePage()),
            GoRoute(path: '/device', builder: (_, __) => const DevicePage()),
            GoRoute(path: '/notifications', builder: (_, __) => const NotificationsPage()),
            GoRoute(path: '/settings', builder: (_, __) => const SettingsPage()),
          ],
        ),
      ],
    );

    return MaterialApp.router(
      title: 'SentinelHub',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1677FF)),
        useMaterial3: true,
      ),
      routerConfig: router,
    );
  }
}

class _ClientShell extends StatelessWidget {
  const _ClientShell({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final isDesktop = PlatformInfo.isDesktop;
    final location = GoRouterState.of(context).uri.path;

    final navItems = [
      _NavItem('/', Icons.home_outlined, '首页'),
      _NavItem('/compliance', Icons.verified_user_outlined, '合规'),
      _NavItem('/device', Icons.devices_outlined, '本机'),
      _NavItem('/notifications', Icons.notifications_outlined, '通知'),
      _NavItem('/settings', Icons.settings_outlined, '设置'),
    ];

    if (isDesktop) {
      return Scaffold(
        body: Row(
          children: [
            NavigationRail(
              selectedIndex: navItems.indexWhere((e) => e.path == location).clamp(0, navItems.length - 1),
              onDestinationSelected: (i) => context.go(navItems[i].path),
              labelType: NavigationRailLabelType.all,
              destinations: [
                for (final item in navItems)
                  NavigationRailDestination(
                    icon: Icon(item.icon),
                    label: Text(item.label),
                  ),
              ],
            ),
            const VerticalDivider(width: 1),
            Expanded(child: child),
          ],
        ),
      );
    }

    return Scaffold(
      body: child,
      bottomNavigationBar: NavigationBar(
        selectedIndex: navItems.indexWhere((e) => e.path == location).clamp(0, navItems.length - 1),
        onDestinationSelected: (i) => context.go(navItems[i].path),
        destinations: [
          for (final item in navItems)
            NavigationDestination(icon: Icon(item.icon), label: item.label),
        ],
      ),
    );
  }
}

class _NavItem {
  const _NavItem(this.path, this.icon, this.label);
  final String path;
  final IconData icon;
  final String label;
}
