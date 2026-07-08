#!/usr/bin/env bash
# Generate Flutter platform projects (requires Flutter SDK 3.24+).
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v flutter >/dev/null 2>&1; then
  echo "Flutter SDK not found. Install from https://flutter.dev/docs/get-started"
  exit 1
fi

flutter create --platforms=android,ios,windows,linux,macos .

echo "Platform projects created. Run: flutter pub get && flutter run -d linux"
