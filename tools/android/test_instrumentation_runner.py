import runpy
from pathlib import Path
import unittest

passed = runpy.run_path(str(Path(__file__).with_name('run-instrumentation.py')))['passed']


class InstrumentationResultTest(unittest.TestCase):
    def test_success_requires_actual_tests(self):
        self.assertTrue(passed('\nOK (1 test)\n', 0))
        self.assertTrue(passed('\nOK (12 tests)\n\n', 0))
        self.assertFalse(passed('OK (0 tests)', 0))
        self.assertFalse(passed('No test output', 0))

    def test_junit_failure_is_not_an_adb_success(self):
        self.assertFalse(passed('There was 1 failure:\nFAILURES!!!\nTests run: 1, Failures: 1', 0))

    def test_crashed_aborted_or_failed_adb_cannot_pass(self):
        for marker in ('INSTRUMENTATION_FAILED', 'Process crashed', 'INSTRUMENTATION_ABORTED'):
            self.assertFalse(passed(f'OK (1 test)\n{marker}', 0))
        self.assertFalse(passed('OK (1 test)', 1))


if __name__ == '__main__':
    unittest.main()
