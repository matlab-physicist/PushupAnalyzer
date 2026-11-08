#!/usr/bin/env python3
"""Seed Gradle's immutable artifact store when Java's Maven GET is redirected to a broken release URL.
Only used by CI. Normal local builds resolve dependencies through Gradle.
"""
import hashlib
import os
from pathlib import Path
import shutil
import time
import urllib.request

version = '2.1.20'  # Matches the Kotlin plugin in build.gradle.kts.
sha1 = '4ef56b3316798316bfac7a0ae443391c9e900ea1'  # Maven Central's published .sha1.
sha256 = 'c54a00718c8c0e3ee858bf42771c7f401c5e3c1738861e18e9536371042fa1b3'
name = f'kotlin-compiler-embeddable-{version}.jar'
base = f'https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/{version}/{name}'
cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home()/'.gradle')))
target = cache/'caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compiler-embeddable'/version/sha1/name

def valid(path):
    if not path.exists():
        return False
    with path.open('rb') as data:
        return hashlib.file_digest(data, 'sha256').hexdigest() == sha256

if not valid(target):
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix('.download')
    for attempt in range(4):
        try:
            print(f'Fetching verified Kotlin compiler, attempt {attempt+1}', flush=True)
            with urllib.request.urlopen(base+'?download=1', timeout=60) as response, temporary.open('wb') as output:
                shutil.copyfileobj(response, output)
            if not valid(temporary):
                raise ValueError('Kotlin compiler SHA-256 mismatch')
            temporary.replace(target)
            break
        except Exception:
            temporary.unlink(missing_ok=True)
            if attempt == 3:
                raise
            time.sleep(2 ** attempt)
print('Kotlin compiler SHA-256 verified')
