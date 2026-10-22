#!/usr/bin/env python3
"""Package reviewed Newleaf source and verify the named handoff artifacts."""
import hashlib
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parent.parent
version_match = re.search(r'versionName\s*=\s*"([^"]+)"', (ROOT / 'app/build.gradle.kts').read_text())
if not version_match:
    raise SystemExit('Cannot determine release version')
VERSION = version_match.group(1)
required = [f'releases/Newleaf-{VERSION}.apk', 'releases/INSTALL.md', 'releases/CHANGELOG.md',
            'releases/VALIDATION.md', 'releases/SIGNATURE.txt', 'BUILD.md', 'THIRD_PARTY_NOTICES.md',
            'releases/MAPPING.txt', 'AGENTS.md', 'docs/UX_PRINCIPLES.md', 'docs/REPOSITORY_REVIEW.md',
            'docs/references/ponytail-AGENTS.md', 'docs/references/ponytail-LICENSE.txt',
            'gateway/README.md', 'gateway/.env.example', 'gateway/server.py',
            'releases/samples/example-journal.json', 'releases/samples/example-report.md',
            'releases/samples/example-spreadsheets.zip', 'releases/samples/example-backup.zip']
missing = [name for name in required if not (ROOT / name).is_file() or (ROOT / name).stat().st_size == 0]
if missing:
    raise SystemExit('Handoff is incomplete: ' + ', '.join(missing))

top_files = ['AGENTS.md', 'README.md', 'BUILD.md', 'THIRD_PARTY_NOTICES.md', '.gitignore', 'settings.gradle.kts',
             'build.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat',
             'app/build.gradle.kts', 'app/proguard-rules.pro']
files = {ROOT / name for name in top_files}
for directory in ['docs', 'gradle/wrapper', 'app/src', 'app/schemas', 'gateway', 'scripts', 'releases/samples']:
    path = ROOT / directory
    if path.exists():
        files.update(p for p in path.rglob('*') if p.is_file())
files.update((ROOT / 'releases').glob('*.md'))
files.add(ROOT / 'releases/SIGNATURE.txt')
files.add(ROOT / 'releases/MAPPING.txt')

def safe(path):
    relative = path.relative_to(ROOT)
    return not any(part in {'build', '.gradle', '.git', '__pycache__', '.venv'} for part in relative.parts) and \
        path.name not in {'local.properties', '.env', '.newleaf-quota.json', '.newleaf-quota.json.lock', 'store-password'} and \
        path.suffix not in {'.pyc', '.jks', '.keystore', '.lock'}

files = sorted(p for p in files if p.is_file() and safe(p))
destination = ROOT / f'releases/Newleaf-{VERSION}-source.zip'
with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    for path in files:
        archive.write(path, 'Newleaf/' + str(path.relative_to(ROOT)))
with zipfile.ZipFile(destination) as archive:
    if archive.testzip() is not None:
        raise SystemExit('Source archive integrity check failed')
    if any(name.endswith(('.jks', '.keystore', '/.env', '/local.properties')) for name in archive.namelist()):
        raise SystemExit('Private configuration entered source archive')

artifacts = [ROOT / f'releases/Newleaf-{VERSION}.apk', destination]
artifacts += sorted((ROOT / 'releases/samples').glob('*'))
checksums = ''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(ROOT)}\n' for p in artifacts if p.is_file())
(ROOT / 'releases/CHECKSUMS.txt').write_text(checksums)
for name in required + ['releases/CHECKSUMS.txt', str(destination.relative_to(ROOT))]:
    path = ROOT / name
    print(f'Verified {name} ({path.stat().st_size} bytes)')
