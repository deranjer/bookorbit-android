"""Generate a separate original OCR/paging comic. Requires Pillow; touches synthetic fixtures only."""
from pathlib import Path
import io
import os
import zipfile
import subprocess
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[2] / '.local/fixtures/Orbit OCR Comic'
root.mkdir(parents=True, exist_ok=True)
font_path = subprocess.check_output(['fc-match', '-f', '%{file}', 'sans'], text=True).strip()
font = ImageFont.truetype(font_path, 52)
with zipfile.ZipFile(root / 'Orbit OCR Comic.cbz', 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    for page in (1, 2, 10):
        image = Image.new('RGB', (1000, 1400), 'white')
        draw = ImageDraw.Draw(image)
        draw.rectangle((30, 30, 970, 1370), outline='black', width=5)
        draw.rounded_rectangle((60, 100, 940, 420), 50, fill='white', outline='black', width=3)
        draw.text((100, 150), f'Page {page}. Hello world!', font=font, fill='black')
        draw.text((100, 250), 'Read this sentence.', font=font, fill='black')
        draw.rounded_rectangle((60, 600, 940, 900), 50, fill='white', outline='black', width=3)
        draw.text((100, 650), 'Привет, читатель!', font=font, fill='black')
        draw.text((100, 750), 'Это тестовая страница.', font=font, fill='black')
        data = io.BytesIO()
        image.save(data, 'PNG')
        archive.writestr(f'{page}.png', data.getvalue())
    archive.writestr('ComicInfo.xml', '<ComicInfo><Title>Orbit OCR Comic</Title><Writer>Client Test Author</Writer><LanguageISO>en</LanguageISO><Summary>Original OCR and numeric page-order fixture.</Summary></ComicInfo>')
os.utime(root, None)
print('Created synthetic Orbit OCR Comic, pages 1, 2, 10.')
