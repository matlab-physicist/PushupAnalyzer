#!/usr/bin/env python3
"""Install the exact wrapper distribution with a verified, retryable HTTP download.

Used by CI because Java's wrapper HTTP client can fail on release-asset redirects.
The normal developer entry point remains ./gradlew.
"""
import hashlib
import os
from pathlib import Path
import re
import shutil
import time
import urllib.request
import zipfile

root = Path(__file__).resolve().parents[1]
props = dict(line.split('=', 1) for line in
             (root / 'gradle/wrapper/gradle-wrapper.properties').read_text().splitlines() if '=' in line)
url = props['distributionUrl'].replace('\\:', ':')
expected = props['distributionSha256Sum']
version = re.search(r'gradle-([0-9.]+)-(?:bin|all)\.zip$', url).group(1)
target = Path(os.environ['RUNNER_TEMP']) / 'pushupanalyzer-gradle'
target.mkdir(parents=True, exist_ok=True)
archive = target / 'distribution.zip'
for attempt in range(4):
    try:
        print(f'Downloading verified Gradle {version}, attempt {attempt+1}', flush=True)
        with urllib.request.urlopen(url, timeout=60) as response, archive.open('wb') as output:
            shutil.copyfileobj(response, output)
        with archive.open('rb') as downloaded:
            actual = hashlib.file_digest(downloaded, 'sha256').hexdigest()
        if actual != expected:
            raise ValueError(f'Gradle checksum mismatch: {actual}')
        break
    except Exception:
        archive.unlink(missing_ok=True)
        if attempt == 3:
            raise
        time.sleep(2 ** attempt)
with zipfile.ZipFile(archive) as distribution:
    distribution.extractall(target)
executable = target / f'gradle-{version}' / 'bin' / 'gradle'
executable.chmod(0o755)
with open(os.environ['GITHUB_PATH'], 'a') as path:
    path.write(str(executable.parent) + '\n')
print(f'Installed verified Gradle {version}')
