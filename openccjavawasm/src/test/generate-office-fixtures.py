"""Generate real small packages with independent libraries; no runtime app dependency.
Install the pinned fixture tools into build/test-python as described in README.
"""
import base64
import io
import json
import pathlib
import re
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'build/test-python'))
from docx import Document
from docx.shared import Inches
from openpyxl import Workbook
from openpyxl.styles import Font
from pptx import Presentation
from pptx.util import Inches as PptInches
from odf.opendocument import OpenDocumentText, OpenDocumentSpreadsheet, OpenDocumentPresentation
from odf import text, style, table, draw

out = ROOT / 'src/test/fixtures'
out.mkdir(parents=True, exist_ok=True)
phrase = '“头发发展，计算机软件，汉字转换测试。”🙂'
png = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1ZkAAAAASUVORK5CYII=')
doc = Document()
run = doc.add_paragraph().add_run(phrase)
run.font.name = '汉字宋体'
doc.add_picture(io.BytesIO(png), width=Inches(.1))
doc.save(out / 'sample.docx')
corrupt = bytearray((out / 'sample.docx').read_bytes())
with zipfile.ZipFile(io.BytesIO(corrupt)) as archive:
    entry = archive.getinfo('word/document.xml')
    pos = entry.header_offset
    name_length = int.from_bytes(corrupt[pos + 26:pos + 28], 'little')
    extra_length = int.from_bytes(corrupt[pos + 28:pos + 30], 'little')
    corrupt[pos + 30 + name_length + extra_length + entry.compress_size // 2] ^= 0x80
(out / 'corrupt.docx').write_bytes(corrupt)
wb = Workbook()
wb.active['A1'] = phrase
wb.active['A1'].font = Font(name='汉字宋体')
wb.active['A2'] = 123
wb.active['A3'] = '=SUM(1,2)'
wb.save(out / 'sample.xlsx')
# openpyxl emits numeric references; OfficeHelper intentionally converts literal
# XML text, so serialize the same valid Unicode scalars literally for this fixture.
xlsx_source = io.BytesIO((out / 'sample.xlsx').read_bytes())
with zipfile.ZipFile(xlsx_source) as source, zipfile.ZipFile(out / 'sample.xlsx', 'w', zipfile.ZIP_DEFLATED) as target:
    for entry in source.infolist():
        data = source.read(entry)
        if entry.filename.endswith('.xml'):
            data = re.sub(r'&#(\d+);', lambda m: chr(int(m[1])) if int(m[1]) > 127 else m[0], data.decode('utf-8')).encode('utf-8')
        target.writestr(entry, data)
presentation = Presentation()
slide = presentation.slides.add_slide(presentation.slide_layouts[6])
box = slide.shapes.add_textbox(PptInches(1), PptInches(1), PptInches(8), PptInches(2))
run = box.text_frame.paragraphs[0].add_run()
run.text = phrase
run.font.name = '汉字宋体'
slide.shapes.add_picture(io.BytesIO(png), PptInches(1), PptInches(3), width=PptInches(.1))
presentation.save(out / 'sample.pptx')
for fmt, factory in [('odt', OpenDocumentText), ('ods', OpenDocumentSpreadsheet), ('odp', OpenDocumentPresentation)]:
    package = factory()
    paragraph_style = style.Style(name='汉字样式', family='paragraph')
    paragraph_style.addElement(style.TextProperties(fontname='汉字宋体'))
    package.styles.addElement(paragraph_style)
    paragraph = text.P(stylename=paragraph_style, text=phrase)
    if fmt == 'odt':
        package.text.addElement(paragraph)
    elif fmt == 'ods':
        sheet = table.Table(name='Sheet1')
        row = table.TableRow()
        cell = table.TableCell(valuetype='string')
        cell.addElement(paragraph); row.addElement(cell); sheet.addElement(row)
        package.spreadsheet.addElement(sheet)
    else:
        layout = style.PageLayout(name='Layout')
        layout.addElement(style.PageLayoutProperties(pagewidth='28cm', pageheight='21cm', printorientation='landscape'))
        package.automaticstyles.addElement(layout)
        master = style.MasterPage(name='Default', pagelayoutname=layout)
        package.masterstyles.addElement(master)
        page = draw.Page(name='page1', masterpagename=master)
        frame = draw.Frame(x='1cm', y='1cm', width='20cm', height='5cm')
        textbox = draw.TextBox(); textbox.addElement(paragraph); frame.addElement(textbox)
        page.addElement(frame); package.presentation.addElement(page)
    package.save(str(out / ('sample.' + fmt)), addsuffix=False)
with zipfile.ZipFile(out / 'sample.epub', 'w', zipfile.ZIP_DEFLATED) as archive:
    archive.writestr('mimetype', 'application/epub+zip', compress_type=zipfile.ZIP_STORED)
    archive.writestr('META-INF/container.xml', '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>')
    archive.writestr('OEBPS/content.opf', '<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="bookid">urn:uuid:openccjava-fixture</dc:identifier><dc:title>汉字测试</dc:title><dc:language>zh</dc:language><meta property="dcterms:modified">2026-11-01T00:00:00Z</meta></metadata><manifest><item id="chapter" href="章节.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="image" href="image.png" media-type="image/png"/></manifest><spine><itemref idref="chapter"/></spine></package>')
    archive.writestr('OEBPS/章节.xhtml', '<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>汉字</title></head><body><p style="font-family:汉字宋体;">' + phrase + '</p><img src="image.png" alt="image"/></body></html>')
    archive.writestr('OEBPS/nav.xhtml', '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol><li><a href="章节.xhtml">汉字</a></li></ol></nav></body></html>')
    archive.writestr('OEBPS/image.png', png)
# Keep the non-ASCII-path regression as an explicit rejection fixture.
(out / 'nonascii.epub').write_bytes((out / 'sample.epub').read_bytes())
with zipfile.ZipFile(out / 'nonascii.epub') as source, zipfile.ZipFile(out / 'sample.epub', 'w', zipfile.ZIP_DEFLATED) as target:
    for entry in source.infolist():
        data = source.read(entry)
        if entry.filename.endswith(('.opf', '.xhtml')):
            data = data.decode('utf-8').replace('章节', 'chapter').encode('utf-8')
        target.writestr(entry.filename.replace('章节', 'chapter'), data, compress_type=entry.compress_type)
cases = [{'format': fmt, 'keepFont': keep, 'punctuation': punct,
          'name': f'{fmt}-{str(keep).lower()}-{str(punct).lower()}.{fmt}'}
         for fmt in ['docx', 'xlsx', 'pptx', 'odt', 'ods', 'odp', 'epub']
         for keep in [False, True] for punct in [False, True]]
(out / 'manifest.json').write_text(json.dumps(cases), encoding='utf-8')
print(f'Generated 7 independent document fixtures and {len(cases)} option combinations in {out}')
