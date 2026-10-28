#!/usr/bin/env bash
set -euo pipefail

BASE_URL="https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP_BASE="$(mktemp -d)"
trap 'rm -rf "$TMP_BASE"' EXIT

PACK200_CMD="${PACK200_BIN:-}"
if [[ -z "$PACK200_CMD" ]] && command -v pack200 >/dev/null 2>&1; then
  PACK200_CMD="$(command -v pack200)"
fi

download_set() {
  local ver="$1"
  local tag="download_jre${ver}"
  local dl_dir="$TMP_BASE/dl_${ver}"
  mkdir -p "$dl_dir"
  echo "=== Downloading OpenJDK ${ver} release tarballs ==="
  for arch in arm arm64 x86 x86_64; do
    curl -fsSL --retry 4 --retry-delay 2 \
      -o "$dl_dir/jre${ver}-android-${arch}.tar.xz" \
      "${BASE_URL}/${tag}/jre${ver}-android-${arch}.tar.xz" &
  done
  wait
}

repack_jre8() {
  local in_dir="$TMP_BASE/dl_8"
  local out_dir="$REPO_ROOT/app_pojavlauncher/src/main/assets/components/jre"
  local work="$TMP_BASE/w8"
  local work1="$TMP_BASE/w8_out"
  mkdir -p "$work" "$work1" "$out_dir"

  echo "=== Repacking JRE 8 universal ==="
  rm -rf "$work"/*
  tar -xf "$in_dir/jre8-android-arm64.tar.xz" -C "$work"
  (
    cd "$work"
    rm -rf bin lib/aarch64 lib/jexec release lib/jfr man
    if [[ -n "$PACK200_CMD" ]]; then
      find ./ -name '*.jar' -execdir "$PACK200_CMD" -S-1 -g -G -E9 {}.pack {} \;
      find ./ -name '*.jar' -execdir rm -f {} \;
    fi
    XZ_OPT="-6 --threads=0" tar -cJf "$out_dir/universal.tar.xz" ./*
  )

  repack_jre8_arch() {
    local lib_arch="$1"
    local name_arch="$2"
    echo "=== Repacking JRE 8 bin-${name_arch} ==="
    rm -rf "$work"/* "$work1"/*
    tar -xf "$in_dir/jre8-android-${name_arch}.tar.xz" -C "$work"
    (
      cd "$work"
      rm -f bin/rmid bin/keytool bin/rmiregistry bin/tnameserv bin/policytool bin/orbd bin/servertool
      mv release "$work1"/
      mv bin "$work1"/
      mkdir -p "$work1/lib"
      if [[ -d "lib/$lib_arch" ]]; then
        mv "lib/$lib_arch" "$work1/lib/"
      fi
      if [[ -f "lib/jexec" ]]; then
        mv lib/jexec "$work1/lib/"
      fi
      XZ_OPT="-6 --threads=0" tar -cJf "$out_dir/bin-${name_arch}.tar.xz" -C "$work1" .
    )
  }

  repack_jre8_arch aarch32 arm
  repack_jre8_arch aarch64 arm64
  repack_jre8_arch i386 x86
  repack_jre8_arch amd64 x86_64

  echo "${GITHUB_SHA:-$(date +%Y%m%d)}-jre8" > "$out_dir/version"
}

repack_modern_jre() {
  local ver="$1"
  local dest_sub="$2"
  local in_dir="$TMP_BASE/dl_${ver}"
  local out_dir="$REPO_ROOT/app_pojavlauncher/src/main/assets/components/${dest_sub}"
  local work="$TMP_BASE/w${ver}"
  local work1="$TMP_BASE/w${ver}_out"
  mkdir -p "$work" "$work1" "$out_dir"

  echo "=== Repacking JRE ${ver} universal ==="
  rm -rf "$work"/*
  tar -xf "$in_dir/jre${ver}-android-arm64.tar.xz" -C "$work"
  (
    cd "$work"
    rm -rf bin lib/server lib/client
    rm -f lib/jexec lib/jvm.cfg release
    find ./ -name '*.so' -exec rm -f {} +
    XZ_OPT="-6 --threads=0" tar -cJf "$out_dir/universal.tar.xz" ./*
  )

  for name_arch in arm arm64 x86 x86_64; do
    echo "=== Repacking JRE ${ver} bin-${name_arch} ==="
    rm -rf "$work"/* "$work1"/*
    tar -xf "$in_dir/jre${ver}-android-${name_arch}.tar.xz" -C "$work"
    (
      cd "$work"
      mv bin "$work1"/
      mkdir -p "$work1/lib"
      [[ -f lib/jexec ]] && mv lib/jexec "$work1/lib/"
      [[ -f lib/jvm.cfg ]] && mv lib/jvm.cfg "$work1/lib/"
      [[ -d lib/server ]] && mv lib/server "$work1/lib/"
      [[ -d lib/client ]] && mv lib/client "$work1/lib/"
      find ./ -name '*.so' -exec mv {} "$work1/lib/" \;
      mv release "$work1/release"
      XZ_OPT="-6 --threads=0" tar -cJf "$out_dir/bin-${name_arch}.tar.xz" -C "$work1" .
    )
  done

  echo "${GITHUB_SHA:-$(date +%Y%m%d)}-jre${ver}" > "$out_dir/version"
}

download_set 8
download_set 17
download_set 21

repack_jre8
repack_modern_jre 17 "jre-new"
repack_modern_jre 21 "jre-21"

echo "=== All JRE components repacked successfully ==="
ls -lh "$REPO_ROOT/app_pojavlauncher/src/main/assets/components/jre" \
       "$REPO_ROOT/app_pojavlauncher/src/main/assets/components/jre-new" \
       "$REPO_ROOT/app_pojavlauncher/src/main/assets/components/jre-21"
