"""Independent ZIP/CRC/XML validation, JVM semantic comparison, document reopening."""
import base64
import io
import json
import pathlib
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'build/test-python'))
from docx import Document
from openpyxl import load_workbook
from pptx import Presentation
from odf.opendocument import load as load_odf


def contents(path):
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None, f'CRC failure: {path}'
        assert len(set(archive.namelist())) == len(archive.namelist()), 'Duplicate names'
        for entry in archive.infolist():
            assert entry.header_offset < archive.start_dir, 'Invalid local offset'
            assert entry.compress_type in (0, 8), 'Unexpected compression'
            if any(ord(c) > 127 for c in entry.filename):
                assert entry.flag_bits & 0x800, 'Missing UTF8 flag'
            if entry.filename.endswith(('.xml', '.xhtml', '.opf', '.rels')):
                ET.fromstring(archive.read(entry))
        return {name: archive.read(name) for name in archive.namelist()}


def reopen(path):
    fmt = path.suffix[1:]
    if fmt == 'docx':
        document = Document(path)
        assert '頭髮發展' in ''.join(p.text for p in document.paragraphs)
        assert len(document.inline_shapes) == 1
    elif fmt == 'xlsx':
        workbook = load_workbook(path)
        assert '頭髮發展' in workbook.active['A1'].value
        assert workbook.active['A2'].value == 123
        assert workbook.active['A3'].value == '=SUM(1,2)'
        workbook.close()
    elif fmt == 'pptx':
        presentation = Presentation(path)
        assert '頭髮發展' in ''.join(s.text for s in presentation.slides[0].shapes if s.has_text_frame)
        assert any(s.shape_type == 13 for s in presentation.slides[0].shapes)
    elif fmt in ('odt', 'ods', 'odp'):
        package = load_odf(str(path))
        assert package.mimetype.endswith({'odt': 'text', 'ods': 'spreadsheet', 'odp': 'presentation'}[fmt])
    elif fmt == 'epub':
        with zipfile.ZipFile(path) as archive:
            first = archive.infolist()[0]
            assert first.filename == 'mimetype' and first.compress_type == zipfile.ZIP_STORED
            assert first.header_offset == 0 and archive.read(first) == b'application/epub+zip'
            container = ET.fromstring(archive.read('META-INF/container.xml'))
            rootfile = container.find('.//{*}rootfile').attrib['full-path']
            opf = ET.fromstring(archive.read(rootfile))
            for item in opf.findall('.//{*}manifest/{*}item'):
                assert str(pathlib.PurePosixPath(rootfile).parent / item.attrib['href']) in archive.namelist()


fixture = json.loads((ROOT / 'src/test/web/zip-fixture.json').read_text())
with zipfile.ZipFile(io.BytesIO(base64.b64decode(fixture['bytes']))) as z:
    expected_probe = {n: z.read(n) for n in z.namelist()}
for backend in ('js', 'wasm'):
    evidence = ROOT / 'build/browser-validation'
    assert contents(evidence / f'zip-{backend}/roundtrip.zip') == expected_probe
    packages = sorted((evidence / f'office-{backend}').glob('*'))
    assert len(packages) == 29, 'Missing browser evidence'
    for package in packages:
        actual = contents(package)
        expected = contents(ROOT / 'build/office-fixtures/jvm' / package.name)
        assert actual == expected, f'JVM semantic mismatch: {backend}/{package.name}'
        original = contents(ROOT / 'build/office-fixtures' / ('sample.' + package.suffix[1:]))
        assert set(actual) == set(original), f'Lost entries: {package.name}'
        main = {'docx': 'word/document.xml', 'xlsx': 'xl/worksheets/sheet1.xml',
                'pptx': 'ppt/slides/slide1.xml', 'epub': 'OEBPS/chapter.xhtml'}.get(package.suffix[1:], 'content.xml')
        xml = actual[main].decode('utf-8')
        assert '頭髮發展' in xml and '漢字轉換測試' in xml, 'Phrase pipeline did not run'
        for name in original:
            if not name.endswith(('.xml', '.xhtml', '.opf', '.rels')):
                assert actual[name] == original[name], f'Changed binary/unrelated entry: {name}'
        reopen(package)
    kept = contents(evidence / f'office-{backend}/docx-true-true.docx')['word/document.xml'].decode('utf-8')
    changed = contents(evidence / f'office-{backend}/docx-false-false.docx')['word/document.xml'].decode('utf-8')
    assert '汉字宋体' in kept and '漢字宋體' in changed, 'Font setting ineffective'
    assert '「' in kept and '“' in changed, 'Punctuation setting ineffective'
    for format, keep, punct in [('docx', 'true', 'true'), ('epub', 'false', 'false')]:
        name = f'{format}-{keep}-{punct}.{format}'
        expected = contents(ROOT / 'build/office-fixtures/jvm' / name)
        blob = evidence / f'integration-{backend}' / name
        downloaded = evidence / f'downloads-integration-{backend}' / f'sample_converted.{format}'
        assert blob.read_bytes() == downloaded.read_bytes(), 'Saved download differs from Blob'
        assert contents(downloaded) == expected, 'UI options or saved download mismatch'
        reopen(downloaded)
    print(f'{backend}: ZIP probe + 29 packages PASS: central directories, CRC, decompression, entry preservation, XML, JVM equivalence, independent document readers, EPUB packaging')
    entities = json.loads((ROOT / 'src/test/fixtures/entities/manifest.json').read_text(encoding='utf-8'))
    for case in entities:
        for keep in (False, True):
            name = f'{case["name"]}-{str(keep).lower()}.epub'
            # Malformed/entity-only XHTML is deliberately opaque text for this
            # preservation test; an XML parser must not repair or reject it here.
            with zipfile.ZipFile(evidence / f'entities-{backend}' / name) as browser, \
                    zipfile.ZipFile(ROOT / 'build/entity-jvm' / name) as jvm:
                assert browser.testzip() is None and jvm.testzip() is None
                assert {n: browser.read(n) for n in browser.namelist()} == {n: jvm.read(n) for n in jvm.namelist()}
                expected = case['expectedKept' if keep else 'expectedChanged']
                assert browser.read('OEBPS/chapter.xhtml').decode('utf-8') == expected
    print(f'{backend}: 16 entity/font packages match JVM and preserve all named, numeric, malformed and long references')
