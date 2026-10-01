"""Generate small original EPUB 2/3 fixtures for manual reader smoke tests."""
from pathlib import Path
import struct
import zipfile
import zlib

ROOT = Path(__file__).parent

def png():
    def chunk(kind, data):
        return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data) & 0xffffffff)
    width, height = 240, 120
    pixels = b''.join(b'\0' + bytes((40, 110, 160) if y < 60 else (230, 190, 90)) * width for y in range(height))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!IIBBBBB', width, height, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b'')

def page(body):
    return '<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>EPUB reader test</title></head><body>' + body + '</body></html>'

chapter1 = page('''<h1 id="start">Chapter One: Typography</h1>
<p>Hello <em>world</em>! Book<strong>s</strong>, not book s.</p>
<p>This sentence has <strong>bold text</strong>, <em>italic text</em>, and <strong><em>both</em></strong>.</p>
<p>First line<br/>Second line<br/>Third line</p>
<ul><li>First bullet</li><li>Second bullet</li></ul><ol><li>First number</li><li>Second number</li></ol>
<h2 id="unicode">Unicode and punctuation</h2><p>“Curly quotes,” an em dash—and café.</p>
<p>日本語の文章。中文文字。한국어 문장.</p><p dir="rtl">مرحبا بالعالم</p><p>Emoji: 📖 🌙 ☀️</p>
<h2 id="image">Embedded image</h2><p>The image below should have a blue top half and a gold bottom half.</p>
<img src="../images/test%20image.png" alt="Two colour bands"/>
<p>Text following the image remains part of the book.</p>''')
paragraphs = ''.join(f'<p id="p{i}">Paragraph {i}. ' + ('Reading follows the words across each page. Changing font size should preserve the saved text location. ' * 7) + '</p>' for i in range(1, 61))
chapter2 = page('<h1 id="long">Chapter Two: Pagination</h1>' + paragraphs + '<h2 id="end">The end</h2><p>This is the final paragraph.</p>')
container = '''<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>'''
nav = '''<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol><li><a href="text/one.xhtml#start">Chapter One</a><ol><li><a href="text/one.xhtml#unicode">Unicode</a></li><li><a href="text/one.xhtml#image">Image</a></li></ol></li><li><a href="text/two.xhtml#long">Chapter Two</a></li><li><a href="text/two.xhtml#end">The end</a></li></ol></nav></body></html>'''
ncx = '''<?xml version="1.0"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head><meta name="dtb:uid" content="urn:mihon:epub-test-2"/></head><docTitle><text>EPUB 2 test</text></docTitle><navMap><navPoint id="one" playOrder="1"><navLabel><text>Chapter One</text></navLabel><content src="text/one.xhtml#start"/><navPoint id="unicode" playOrder="2"><navLabel><text>Unicode</text></navLabel><content src="text/one.xhtml#unicode"/></navPoint></navPoint><navPoint id="two" playOrder="3"><navLabel><text>Chapter Two</text></navLabel><content src="text/two.xhtml#long"/></navPoint><navPoint id="end" playOrder="4"><navLabel><text>The end</text></navLabel><content src="text/two.xhtml#end"/></navPoint></navMap></ncx>'''
for version in (2, 3):
    navigation = '<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>' if version == 3 else '<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>'
    modified = '<meta property="dcterms:modified">2026-09-29T00:00:00Z</meta>' if version == 3 else ''
    spine_attrs = '' if version == 3 else ' toc="ncx"'
    opf = f'''<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="{version}.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:mihon:epub-test-{version}</dc:identifier><dc:title>EPUB {version} Reader Test</dc:title><dc:language>en</dc:language>{modified}</metadata><manifest><item id="one" href="text/one.xhtml" media-type="application/xhtml+xml"/><item id="two" href="text/two.xhtml" media-type="application/xhtml+xml"/><item id="image" href="images/test%20image.png" media-type="image/png"/>{navigation}</manifest><spine{spine_attrs}><itemref idref="one"/><itemref idref="two"/></spine></package>'''
    entries = {'mimetype': 'application/epub+zip', 'META-INF/container.xml': container, 'OPS/book.opf': opf, 'OPS/text/one.xhtml': chapter1, 'OPS/text/two.xhtml': chapter2, 'OPS/images/test image.png': png()}
    entries['OPS/nav.xhtml' if version == 3 else 'OPS/toc.ncx'] = nav if version == 3 else ncx
    with zipfile.ZipFile(ROOT / f'EPUB-{version}-Reader-Test.epub', 'w') as archive:
        for name, content in entries.items():
            info = zipfile.ZipInfo(name, date_time=(2026, 9, 29, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED if name == 'mimetype' else zipfile.ZIP_DEFLATED
            archive.writestr(info, content)
