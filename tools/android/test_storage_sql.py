"""Exercise production download SQL on SQLite, without an emulator or production database.

This verifies statements and migration preservation, not Room's Android runtime/schema validation.
"""
from pathlib import Path
import json
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[2]
DB = ROOT / 'app/src/main/java/com/bookorbit/core/db'


def query(method):
    source = (DB / 'DownloadDao.kt').read_text()
    match = re.search(r'@Query\("([^"\n]+)"\)\s+suspend fun ' + method + r'\(', source)
    assert match, f'Production query not found: {method}'
    return match[1]


class DownloadSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.addCleanup(self.db.close)
        # Version 10: preserve this fixture when later versions add fields.
        self.db.execute('''CREATE TABLE downloads (
            bookId INTEGER NOT NULL PRIMARY KEY, title TEXT, authors TEXT NOT NULL,
            narrators TEXT NOT NULL, isAudiobook INTEGER NOT NULL, format TEXT,
            sizeBytes INTEGER NOT NULL, downloadedAt INTEGER NOT NULL, coverLocalPath TEXT,
            bookJson TEXT NOT NULL, filesJson TEXT NOT NULL, status TEXT NOT NULL,
            progress REAL NOT NULL, lastError TEXT, selectedFileIdsJson TEXT)''')
        self.files = json.dumps([{'id': 11, 'localPath': '/existing/book.epub'}])
        self.db.execute('''INSERT INTO downloads VALUES
            (1, 'Book', 'Author', '', 0, 'epub', 50, 123, '/cover.jpg', '{}', ?, 'COMPLETE', 1.0, NULL, '[11]')''', (self.files,))
        source = (DB / 'BookOrbitDatabase.kt').read_text()
        migration = source.split('val MIGRATION_10_11 =', 1)[1].split('\n}\n', 1)[0]
        for statement in re.findall(r'db\.execSQL\("([^"\n]+)"\)', migration):
            self.db.execute(statement)

    def test_migration_retains_content_and_adds_legacy_defaults(self):
        row = self.db.execute('SELECT filesJson, sizeBytes, downloadedAt, coverLocalPath, planId, storageId FROM downloads').fetchone()
        self.assertEqual(row, (self.files, 50, 123, '/cover.jpg', '', ''))

    def new_plan(self):
        self.db.execute("UPDATE downloads SET planId = 'new', status = 'DOWNLOADING'")

    def test_obsolete_worker_cannot_checkpoint_complete_or_fail_new_plan(self):
        self.new_plan()
        for method, params in (
            ('checkpoint', dict(filesJson='[]', sizeBytes=0, progress=0.5)),
            ('finishPlan', dict(status='COMPLETE', cover=None)),
            ('failPlan', dict(status='FAILED', error='old error')),
            ('progressForPlan', dict(progress=0.9)),
        ):
            cursor = self.db.execute(query(method), dict(bookId=1, planId='old', **params))
            self.assertEqual(cursor.rowcount, 0)
        self.assertEqual(self.db.execute('SELECT filesJson, status FROM downloads').fetchone(), (self.files, 'DOWNLOADING'))

    def test_deleted_plan_cannot_be_resurrected_by_finishing_worker(self):
        self.db.execute('DELETE FROM downloads WHERE bookId = 1')
        self.assertEqual(self.db.execute(query('finishPlan'), dict(bookId=1, planId='', status='COMPLETE', cover=None)).rowcount, 0)
        self.assertEqual(self.db.execute('SELECT count(*) FROM downloads').fetchone()[0], 0)

    def test_failure_keeps_previously_published_files(self):
        self.new_plan()
        self.db.execute(query('failPlan'), dict(bookId=1, planId='new', status='FAILED', error='offline'))
        self.assertEqual(self.db.execute('SELECT filesJson, sizeBytes, coverLocalPath FROM downloads').fetchone(), (self.files, 50, '/cover.jpg'))

    def test_checkpoint_publishes_completed_files_before_whole_plan_finishes(self):
        self.new_plan()
        files = json.dumps([{'id': 11, 'localPath': '/existing/book.epub'}, {'id': 12, 'localPath': '/new/audio.mp3'}])
        self.assertEqual(self.db.execute(query('checkpoint'), dict(bookId=1, planId='new', filesJson=files, sizeBytes=150, progress=0.5)).rowcount, 1)
        self.assertEqual(self.db.execute('SELECT filesJson, status FROM downloads').fetchone(), (files, 'DOWNLOADING'))
        self.db.execute(query('finishPlan'), dict(bookId=1, planId='new', status='COMPLETE', cover='/cover.jpg'))
        self.assertEqual(self.db.execute('SELECT filesJson, progress, status FROM downloads').fetchone(), (files, 1.0, 'COMPLETE'))


if __name__ == '__main__':
    unittest.main()
