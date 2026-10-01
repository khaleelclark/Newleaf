#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Signing credentials stay in the user's private application-data directory.
newleaf_signing_dir="${NEWLEAF_SIGNING_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/newleaf/signing}"
newleaf_sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$newleaf_sdk_dir" && -f local.properties ]]; then
    newleaf_sdk_dir="$(sed -n 's/^sdk.dir=//p' local.properties)"
fi
if [[ -z "$newleaf_sdk_dir" ]]; then
    printf '%s\n' 'Set ANDROID_SDK_ROOT or sdk.dir in local.properties.' >&2
    exit 1
fi
newleaf_tools_dir="${NEWLEAF_BUILD_TOOLS:-$newleaf_sdk_dir/build-tools/35.0.0}"
[[ -x "$newleaf_tools_dir/apksigner" && -x "$newleaf_tools_dir/zipalign" ]] || { printf '%s\n' 'Android apksigner/zipalign are required.' >&2; exit 1; }

umask 077
mkdir -p "$newleaf_signing_dir"
chmod 700 "$newleaf_signing_dir"
newleaf_key_file="$newleaf_signing_dir/newleaf-release.jks"
newleaf_pass_file="$newleaf_signing_dir/store-password"
if [[ ! -f "$newleaf_key_file" ]]; then
    [[ ! -e "$newleaf_pass_file" ]] || { printf '%s\n' 'A password exists without its key; recover the key before proceeding.' >&2; exit 1; }
    python3 - "$newleaf_pass_file" <<'PY'
import os,secrets,sys
with open(sys.argv[1],'x') as f:
    f.write(secrets.token_urlsafe(48))
os.chmod(sys.argv[1],0o600)
PY
    keytool -genkeypair -keystore "$newleaf_key_file" -alias newleaf -keyalg RSA -keysize 3072 -validity 10000 \
        -storepass:file "$newleaf_pass_file" -keypass:file "$newleaf_pass_file" \
        -dname 'CN=Newleaf Personal Android Release' -storetype JKS
fi
[[ -f "$newleaf_pass_file" ]] || { printf '%s\n' 'Signing password file is missing.' >&2; exit 1; }

./gradlew --no-daemon -Pnewleaf.testBuildType=release :app:assembleRelease :app:assembleReleaseAndroidTest :app:testReleaseUnitTest :app:lintRelease --console=plain
mkdir -p releases
cp app/build/outputs/mapping/release/mapping.txt releases/MAPPING.txt
newleaf_version="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts)"
newleaf_apk="releases/Newleaf-$newleaf_version.apk"
newleaf_aligned="$(mktemp --suffix=.apk)"
trap 'rm -f "$newleaf_aligned"' EXIT
"$newleaf_tools_dir/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk "$newleaf_aligned"
"$newleaf_tools_dir/apksigner" sign --ks "$newleaf_key_file" --ks-key-alias newleaf \
    --ks-pass "file:$newleaf_pass_file" --out "$newleaf_apk" "$newleaf_aligned"
chmod 644 "$newleaf_apk"
"$newleaf_tools_dir/apksigner" verify --verbose --print-certs "$newleaf_apk" > releases/SIGNATURE.txt
sha256sum "$newleaf_apk" > releases/CHECKSUMS.txt
printf 'Signed APK: %s\nPrivate signing key: %s\n' "$newleaf_apk" "$newleaf_key_file"
printf '%s\n' 'Back up the private signing directory securely; it is required for future in-place updates.'
