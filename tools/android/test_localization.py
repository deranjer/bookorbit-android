"""Keep Russian string/plural coverage and formatting complete without hiding lint checks."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

RES = Path(__file__).resolve().parents[2] / 'app/src/main/res'


def resources(directory):
    result = {}
    for file in directory.glob('*.xml'):
        for element in ET.parse(file).getroot():
            if element.tag not in ('string', 'plurals') or element.get('translatable') == 'false':
                continue
            key = element.get('name')
            assert key not in result, f'Duplicate translation: {key}'
            result[key] = element
    return result


def placeholders(text):
    return sorted(re.findall(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]', text.replace('%%', '')))


class RussianLocalizationTest(unittest.TestCase):
    def test_every_string_and_plural_has_a_russian_translation(self):
        self.assertEqual(set(resources(RES / 'values')), set(resources(RES / 'values-ru')))

    def test_placeholders_and_russian_plural_forms(self):
        russian = resources(RES / 'values-ru')
        for key, base in resources(RES / 'values').items():
            translated = russian[key]
            self.assertEqual(base.tag, translated.tag, key)
            if base.tag == 'plurals':
                self.assertTrue({'one', 'few', 'many', 'other'} <= {item.get('quantity') for item in translated}, key)
                expected = placeholders(''.join(base.find("item[@quantity='other']").itertext()))
                values = [''.join(item.itertext()) for item in translated]
            else:
                expected = placeholders(''.join(base.itertext()))
                values = [''.join(translated.itertext())]
            for value in values:
                self.assertTrue(value.strip(), key)
                self.assertEqual(expected, placeholders(value), key)


if __name__ == '__main__':
    unittest.main()
