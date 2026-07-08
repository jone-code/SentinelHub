import { collectAssetsJs } from './asset.js';
import { collectViaNative } from '../native-bridge.js';

/**
 * Collect assets: prefer native sidecar, fallback to Node collectors.
 * @param {string | null} nativeBin
 */
export async function collectAssets(nativeBin) {
  const native = await collectViaNative(nativeBin);
  if (native) {
    return { ...native, source: native.source ?? 'native' };
  }
  return collectAssetsJs();
}
