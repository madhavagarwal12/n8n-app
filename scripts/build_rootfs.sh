#!/usr/bin/env bash
# ==============================================================================
# Alpine Linux ARM64 Rootfs Build Pipeline for Android n8n Server
# Run this on an ARM64 Linux machine or via Docker (with buildx emulation)
# ==============================================================================

set -euo pipefail

ROOTFS_DIR="./rootfs-arm64"
OUTPUT_TAR="n8n-rootfs-arm64.tar.xz"
ALPINE_VERSION="3.20.0"
ALPINE_MINI_URL="https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-${ALPINE_VERSION}-aarch64.tar.gz"

echo "==> Creating clean rootfs directory..."
rm -rf "$ROOTFS_DIR" "$OUTPUT_TAR"
mkdir -p "$ROOTFS_DIR"

echo "==> Downloading minimal Alpine Linux rootfs (aarch64)..."
curl -fsSL "$ALPINE_MINI_URL" | tar -xz -C "$ROOTFS_DIR"

echo "==> Configuring nameserver for internet access inside rootfs..."
echo "nameserver 1.1.1.1" > "$ROOTFS_DIR/etc/resolv.conf"

echo "==> Installing Node.js LTS, n8n, and native build dependencies..."
proot -0 -r "$ROOTFS_DIR" /bin/sh << 'EOF'
set -e
apk update
apk add --no-cache nodejs npm python3 make g++ sqlite ca-certificates bash
npm install -g n8n --omit=dev --foreground-scripts
rm -rf /var/cache/apk/* /root/.npm
EOF

echo "==> Compressing into distributable payload: $OUTPUT_TAR ..."
tar -cJvf "$OUTPUT_TAR" -C "$ROOTFS_DIR" .

echo "==> Done! Place $OUTPUT_TAR into app/src/main/assets/ or distribute via CDN."
