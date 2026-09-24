# n8n Android Server Assets

Place the following payload files here for direct APK asset bundling:
1. `proot` - Precompiled PRoot static binary for `aarch64` (arm64-v8a).
2. `n8n-rootfs-arm64.tar.xz` - Compressed Alpine Linux rootfs containing Node.js v20 and global `n8n` package.

If these assets are omitted from the APK, the application will fallback to dynamic CDN download or local sandbox initialization on first launch.
