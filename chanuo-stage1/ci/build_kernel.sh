#!/usr/bin/env bash
set -Eeuo pipefail
mkdir -p artifacts
KERNEL_URL="https://github.com/nokia-dev/android_kernel_nokia_mt6737m.git"
echo "[Stage 1] source: $KERNEL_URL"
git clone --depth=1 --single-branch --no-tags "$KERNEL_URL" kernel-src
git -C kernel-src rev-parse HEAD | tee artifacts/KERNEL_UPSTREAM_SHA.txt
# The 2018 MediaTek pinctrl .dws generator is Python 2; modern runner is Python 3.
# Convert all its Python sources in a temporary CI checkout (not upstream).
python3 -m lib2to3 -w -n kernel-src/tools/dct > artifacts/2to3-conversion.log 2>&1
# Python's removed cmp() built-in is not transformed by lib2to3.
sed -i '/sys.dont_write_bytecode = True/a\\ncmp = lambda a, b: (a > b) - (a < b)' kernel-src/tools/dct/DrvGen.py
test -f kernel-src/arch/arm/configs/FRT_defconfig
export ARCH=arm CROSS_COMPILE=arm-linux-gnueabi-
mkdir -p kernel-out
make -C kernel-src O="$PWD/kernel-out" FRT_defconfig
# Ensure ramdisk support and console for /init RAM stage, no persistent mount.
# Do not change board-specific drivers automatically: this is a candidate kernel only.
kernel-src/scripts/config --file kernel-out/.config   -e BLK_DEV_INITRD -e DEVTMPFS -e DEVTMPFS_MOUNT   -e PROC_FS -e SYSFS -e PRINTK
make -C kernel-src O="$PWD/kernel-out" olddefconfig
echo "[Stage 1] Compiling vendor-derived ARMv7 zImage"
make -C kernel-src O="$PWD/kernel-out" -j"$(nproc)" zImage 2>&1 | tee artifacts/build-kernel.log
test -s kernel-out/arch/arm/boot/zImage
cp kernel-out/arch/arm/boot/zImage artifacts/mt6737-nokia-reference-zImage
cp kernel-out/.config artifacts/mt6737-reference-config.txt
file artifacts/mt6737-nokia-reference-zImage | tee artifacts/verification.txt
sha256sum artifacts/mt6737-nokia-reference-zImage >> artifacts/verification.txt
cat > artifacts/IMPORTANT_README.txt <<'EOF'
EXPERIMENTAL REFERENCE KERNEL ONLY - NOT VALIDATED ON CHANUO A1!
Source: Nokia 1 MT6737M, NOT T8G-MB-V2.0. No projector Device Tree.
This file is NOT a complete boot image and cannot boot from USB alone.
DO NOT flash this kernel, DO NOT replace boot.img on the eMMC.
A future RAM-only boot test would require a safe loader with confirmed support.
EOF
