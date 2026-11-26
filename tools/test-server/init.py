"""Create local-only test secrets once; never print them or copy production data."""
from pathlib import Path
import secrets

root = Path(__file__).resolve().parents[2]
local = root / '.local'
local.mkdir(mode=0o700, exist_ok=True)
(local / 'fixtures').mkdir(exist_ok=True)
target = local / 'test-server.env'
if not target.exists():
    names = ('POSTGRES_PASSWORD', 'JWT_SECRET', 'SETUP_BOOTSTRAP_TOKEN',
             'AI_ENCRYPTION_KEY', 'BOOK_REQUEST_ENCRYPTION_KEY', 'EMAIL_ENCRYPTION_KEY', 'MIGRATION_ENCRYPTION_KEY')
    with target.open('x') as stream:
        target.chmod(0o600)
        stream.write(''.join(f'{name}={secrets.token_hex(32)}\n' for name in names))
    print('Created isolated test configuration. Secrets are stored only in .local/.')
else:
    print('Existing test configuration retained.')
