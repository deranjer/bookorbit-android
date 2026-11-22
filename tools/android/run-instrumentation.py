"""Run an explicit test class only on the isolated Docker emulator, preserving app data."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CONTAINER = 'bookorbit-client-emulator'
RUNNER = 'io.github.monkecode.bookorbitclient.debug.test/androidx.test.runner.AndroidJUnitRunner'


def passed(output, returncode):
    # `adb shell am instrument` can exit zero even when JUnit failed.
    if returncode or any(marker in output for marker in ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed', 'INSTRUMENTATION_ABORTED')):
        return False
    result = re.search(r'^OK \((\d+) tests?\)\s*$', output, re.MULTILINE)
    return result is not None and int(result.group(1)) > 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--class', dest='test_class', required=True)
    parser.add_argument('--repeat', type=int, default=1)
    parser.add_argument('--install', action='store_true', help='Install both existing fdroid debug APKs with -r; never clear app data.')
    args = parser.parse_args()
    if not re.fullmatch(r'com\.bookorbit\.[A-Za-z0-9_.$]+(?:#[A-Za-z0-9_]+)?', args.test_class) or not 1 <= args.repeat <= 20:
        parser.error('An explicit BookOrbit test class and repeat count 1..20 are required')
    if args.install:
        for apk in ('app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk',
                    'app/build/outputs/apk/androidTest/fdroid/debug/app-fdroid-debug-androidTest.apk'):
            if not (ROOT / apk).is_file():
                parser.error(f'Build the APK first: {apk}')
            subprocess.run(['docker', 'exec', CONTAINER, 'adb', 'install', '-r', f'/workspace/{apk}'], check=True, timeout=120)
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    out = ROOT / '.local' / 'instrumentation' / stamp
    out.mkdir(parents=True, exist_ok=False)
    results = []
    for index in range(args.repeat):
        result = subprocess.run(['docker', 'exec', CONTAINER, 'adb', 'shell', 'am', 'instrument', '-w',
                                 '-e', 'class', args.test_class, RUNNER], text=True, capture_output=True, timeout=300)
        output = result.stdout + result.stderr
        (out / f'run-{index + 1}.txt').write_text(output)
        success = passed(output, result.returncode)
        results.append({'run': index + 1, 'passed': success, 'adbReturnCode': result.returncode})
        (out / 'results.json').write_text(json.dumps({'class': args.test_class, 'results': results}, indent=2) + '\n')
        print(output)
        if not success:
            print(f'FAILED; evidence: {out}')
            return 1
    print(f'PASS: {len(results)} run(s); evidence: {out}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
