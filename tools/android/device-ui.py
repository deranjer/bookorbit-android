"""Small ADB UI harness for the isolated Docker emulator, never a user's physical phone."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
CONTAINER = 'bookorbit-client-emulator'
PACKAGE = 'io.github.monkecode.bookorbitclient.debug'


def adb(*args):
    result = subprocess.run(['docker', 'exec', CONTAINER, 'adb', *args], capture_output=True)
    if result.returncode:
        raise RuntimeError('Emulator ADB operation failed')  # Do not include credential-bearing arguments.
    return result.stdout


def hierarchy():
    adb('shell', 'uiautomator', 'dump', '/sdcard/bookorbit-ui.xml')
    return adb('exec-out', 'cat', '/sdcard/bookorbit-ui.xml')


def tap_node(node):
    bounds = [int(value) for value in re.findall(r'\d+', node.attrib['bounds'])]
    adb('shell', 'input', 'tap', str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['snapshot', 'tap', 'back', 'launch', 'server', 'credentials'])
    parser.add_argument('--text', default='')
    parser.add_argument('--name', default='screen')
    args = parser.parse_args()
    if args.action == 'snapshot':
        assert re.fullmatch(r'[a-zA-Z0-9_-]+', args.name)
        out = ROOT / '.local/device-screenshots'
        out.mkdir(parents=True, exist_ok=True)
        xml = hierarchy()
        (out / f'{args.name}.xml').write_bytes(xml)
        (out / f'{args.name}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
        for node in ET.fromstring(xml).iter('node'):
            if node.get('password') == 'true':
                continue
            text = node.get('text') or node.get('content-desc')
            if text:
                print(node.get('bounds'), text)
        print(out / f'{args.name}.png')
    elif args.action == 'tap':
        nodes = [node for node in ET.fromstring(hierarchy()).iter('node') if args.text in (node.get('text'), node.get('content-desc'))]
        if not nodes:
            raise RuntimeError('Requested UI label not found')
        tap_node(nodes[-1])
    elif args.action == 'back':
        adb('shell', 'input', 'keyevent', '4')
    elif args.action == 'launch':
        adb('shell', 'am', 'start', '-n', f'{PACKAGE}/com.bookorbit.app.MainActivity')
    elif args.action == 'server':
        info = json.loads(subprocess.check_output(['docker', 'inspect', 'bookorbit-client-test-app-1']))[0]
        ip = info['NetworkSettings']['Networks']['bookorbit-client-test_default']['IPAddress']
        fields = [node for node in ET.fromstring(hierarchy()).iter('node') if node.get('class') == 'android.widget.EditText']
        assert fields
        tap_node(fields[0])
        adb('shell', 'input', 'keycombination', '113', '29')
        adb('shell', 'input', 'text', f'http://{ip}:3000')
        adb('shell', 'input', 'keyevent', '4')
    elif args.action == 'credentials':
        account = json.loads((ROOT / '.local/test-account.json').read_text())
        for index, value in enumerate((account['username'], account['password'])):
            fields = [node for node in ET.fromstring(hierarchy()).iter('node') if node.get('class') == 'android.widget.EditText']
            assert len(fields) >= 2
            assert re.fullmatch(r'[a-zA-Z0-9_-]+', value), 'Unsupported synthetic credential characters'
            tap_node(fields[index])
            adb('shell', 'input', 'keycombination', '113', '29')
            adb('shell', 'input', 'text', value)
        adb('shell', 'input', 'keyevent', '4')
        print('Entered isolated test credentials (not printed).')


if __name__ == '__main__':
    main()
