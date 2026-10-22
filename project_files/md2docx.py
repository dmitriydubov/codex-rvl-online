"""Собирает Word-файл (.docx) из Markdown-исходника ТЗ для переноса в Confluence.

Запуск: python3 project_files/md2docx.py "<путь к .md>" — рядом с исходником появится .docx с тем же именем.
Нужен Python-пакет markdown, остальное — стандартная библиотека. Заголовки — встроенные стили Word «Heading 1…3»
(по ним Confluence строит структуру страницы), таблицы — с фиксированной шириной колонок, код — моноширинным шрифтом.
Вывод воспроизводимый: одинаковый исходник даёт побайтно одинаковый .docx.
"""

import html.parser
import pathlib
import re
import sys
import zipfile
from xml.sax.saxutils import escape

import markdown

TEXT_WIDTH = 9638
MIN_COLUMN = 900
CHAR_WIDTH = 110
CELL_PADDING = 220
BLOCKS = {"h1", "h2", "h3", "h4", "p", "ul", "ol", "pre", "table", "hr", "blockquote"}
HEADINGS = {"h1": "Heading1", "h2": "Heading2", "h3": "Heading3", "h4": "Heading3"}
W = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"'
ZIP_TIME = (1980, 1, 1, 0, 0, 0)


class Node:
    def __init__(self, tag, parent=None):
        self.tag = tag
        self.parent = parent
        self.children = []

    def text(self):
        return "".join(child if isinstance(child, str) else child.text() for child in self.children)


class TreeBuilder(html.parser.HTMLParser):
    VOID = {"br", "hr", "img"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.root = Node("root")
        self.current = self.root

    def handle_starttag(self, tag, attrs):
        node = Node(tag, self.current)
        self.current.children.append(node)
        if tag not in self.VOID:
            self.current = node

    def handle_endtag(self, tag):
        node = self.current
        while node is not self.root and node.tag != tag:
            node = node.parent
        if node is not self.root:
            self.current = node.parent

    def handle_data(self, data):
        self.current.children.append(data)


class Document:
    def __init__(self):
        self.body = []
        self.ordered_lists = 0

    def paragraph(self, runs, style=None, numbering=None):
        props = ""
        if style:
            props += f'<w:pStyle w:val="{style}"/>'
        if numbering:
            num_id, level = numbering
            props += f'<w:numPr><w:ilvl w:val="{level}"/><w:numId w:val="{num_id}"/></w:numPr>'
        return f"<w:p><w:pPr>{props}</w:pPr>{''.join(runs)}</w:p>"

    def add(self, xml):
        self.body.append(xml)

    def new_ordered_list(self):
        self.ordered_lists += 1
        return self.ordered_lists + 1


def run(text, bold=False, italic=False, code=False):
    props = '<w:rFonts w:ascii="Courier New" w:hAnsi="Courier New" w:cs="Courier New"/>' if code else ""
    if bold:
        props += "<w:b/>"
    if italic:
        props += "<w:i/>"
    if code:
        props += '<w:sz w:val="18"/><w:shd w:val="clear" w:color="auto" w:fill="EFEFEF"/>'
    return f'<w:r><w:rPr>{props}</w:rPr><w:t xml:space="preserve">{escape(text)}</w:t></w:r>'


def inline_runs(node, bold=False, italic=False, code=False):
    runs = []
    for child in node.children:
        if isinstance(child, str):
            text = child if code else re.sub(r"\s+", " ", child)
            if text:
                runs.append((text, bold, italic, code))
        elif child.tag == "br":
            runs.append(("\n", bold, italic, code))
        elif child.tag not in BLOCKS:
            runs.extend(inline_runs(child, bold or child.tag in ("strong", "b", "th"),
                                    italic or child.tag in ("em", "i"), code or child.tag == "code"))
    return runs


def render_runs(node, bold=False):
    runs = inline_runs(node, bold=bold)
    if runs:
        first, last = runs[0], runs[-1]
        runs[0] = (first[0].lstrip(),) + first[1:]
        runs[-1] = (runs[-1][0].rstrip(),) + last[1:]
    rendered = []
    for text, is_bold, is_italic, is_code in runs:
        if text == "\n":
            rendered.append("<w:r><w:br/></w:r>")
        elif text:
            rendered.append(run(text, is_bold, is_italic, is_code))
    return rendered


def render_list(doc, node, level, numbering_id=None):
    num_id = numbering_id or (doc.new_ordered_list() if node.tag == "ol" else 1)
    for item in (child for child in node.children if isinstance(child, Node) and child.tag == "li"):
        nested = [child for child in item.children if isinstance(child, Node) and child.tag in ("ul", "ol")]
        paragraphs = [child for child in item.children if isinstance(child, Node) and child.tag == "p"]
        head = paragraphs[0] if paragraphs else item
        doc.add(doc.paragraph(render_runs(head), "ListParagraph", (num_id, level)))
        for extra in paragraphs[1:]:
            doc.add(doc.paragraph(render_runs(extra), "ListContinue"))
        for sublist in nested:
            render_list(doc, sublist, level + 1)


def render_code(doc, node):
    lines = node.text().rstrip("\n").split("\n")
    for line in lines:
        doc.add(doc.paragraph([run(line)] if line else [], "Code"))
    doc.add(doc.paragraph([], "TableGap"))


def table_rows(node):
    rows = []
    for child in node.children:
        if isinstance(child, Node):
            if child.tag == "tr":
                rows.append(child)
            elif child.tag in ("thead", "tbody"):
                rows.extend(table_rows(child))
    return rows


def column_widths(rows):
    columns = max(len([c for c in row.children if isinstance(c, Node)]) for row in rows)
    weights = [6] * columns
    minimums = [MIN_COLUMN] * columns
    for row in rows:
        for index, cell in enumerate(c for c in row.children if isinstance(c, Node)):
            text = cell.text().strip()
            weights[index] = max(weights[index], min(60, len(text)))
            longest_word = max((len(word) for word in text.split()), default=0)
            minimums[index] = max(minimums[index], longest_word * CHAR_WIDTH + CELL_PADDING)
    spare = max(0, TEXT_WIDTH - sum(minimums))
    widths = [minimum + spare * weight // sum(weights) for minimum, weight in zip(minimums, weights)]
    widths[-1] += TEXT_WIDTH - sum(widths)
    return widths


def render_table(doc, node):
    rows = table_rows(node)
    widths = column_widths(rows)
    grid = "".join(f'<w:gridCol w:w="{width}"/>' for width in widths)
    xml = [f'<w:tbl><w:tblPr><w:tblStyle w:val="TableGrid"/><w:tblW w:w="{TEXT_WIDTH}" w:type="dxa"/>'
           f'<w:tblLayout w:type="fixed"/></w:tblPr><w:tblGrid>{grid}</w:tblGrid>']
    for row in rows:
        cells = [c for c in row.children if isinstance(c, Node)]
        header = all(cell.tag == "th" for cell in cells)
        xml.append("<w:tr>" + ('<w:trPr><w:tblHeader/></w:trPr>' if header else ""))
        for width, cell in zip(widths, cells):
            shading = '<w:shd w:val="clear" w:color="auto" w:fill="E8E8E8"/>' if header else ""
            xml.append(f'<w:tc><w:tcPr><w:tcW w:w="{width}" w:type="dxa"/>{shading}</w:tcPr>'
                       f'{doc.paragraph(render_runs(cell, bold=header), "TableText")}</w:tc>')
        xml.append("</w:tr>")
    xml.append("</w:tbl>")
    doc.add("".join(xml))
    doc.add(doc.paragraph([], "TableGap"))


def render_blocks(doc, node):
    for child in node.children:
        if not isinstance(child, Node):
            continue
        if child.tag in HEADINGS:
            doc.add(doc.paragraph(render_runs(child), HEADINGS[child.tag]))
        elif child.tag == "p":
            doc.add(doc.paragraph(render_runs(child)))
        elif child.tag in ("ul", "ol"):
            render_list(doc, child, 0)
        elif child.tag == "pre":
            render_code(doc, child)
        elif child.tag == "table":
            render_table(doc, child)
        elif child.tag == "blockquote":
            render_blocks(doc, child)


def heading_style(style_id, name, size, level):
    return (f'<w:style w:type="paragraph" w:styleId="{style_id}"><w:name w:val="{name}"/>'
            f'<w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:uiPriority w:val="9"/><w:qFormat/>'
            f'<w:pPr><w:keepNext/><w:spacing w:before="{240 if level else 0}" w:after="120"/>'
            f'<w:outlineLvl w:val="{level}"/></w:pPr><w:rPr><w:b/><w:sz w:val="{size}"/></w:rPr></w:style>')


STYLES = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles {W}>
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:eastAsia="Arial" w:cs="Arial"/>
<w:sz w:val="21"/><w:lang w:val="ru-RU"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="264" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>
{heading_style("Heading1", "heading 1", 36, 0)}
{heading_style("Heading2", "heading 2", 28, 1)}
{heading_style("Heading3", "heading 3", 24, 2)}
<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/>
<w:pPr><w:spacing w:after="60"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="ListContinue"><w:name w:val="List Continue"/><w:basedOn w:val="Normal"/>
<w:pPr><w:spacing w:after="60"/><w:ind w:left="720"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Code"><w:name w:val="Code"/><w:basedOn w:val="Normal"/>
<w:pPr><w:shd w:val="clear" w:color="auto" w:fill="F2F2F2"/><w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr>
<w:rPr><w:rFonts w:ascii="Courier New" w:hAnsi="Courier New" w:cs="Courier New"/><w:sz w:val="17"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="TableText"><w:name w:val="Table Text"/><w:basedOn w:val="Normal"/>
<w:pPr><w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr><w:rPr><w:sz w:val="19"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="TableGap"><w:name w:val="Table Gap"/><w:basedOn w:val="Normal"/>
<w:pPr><w:spacing w:after="0"/></w:pPr><w:rPr><w:sz w:val="12"/></w:rPr></w:style>
<w:style w:type="table" w:styleId="TableGrid"><w:name w:val="Table Grid"/><w:tblPr>
<w:tblBorders><w:top w:val="single" w:sz="4" w:space="0" w:color="808080"/>
<w:left w:val="single" w:sz="4" w:space="0" w:color="808080"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="808080"/>
<w:right w:val="single" w:sz="4" w:space="0" w:color="808080"/><w:insideH w:val="single" w:sz="4" w:space="0" w:color="808080"/>
<w:insideV w:val="single" w:sz="4" w:space="0" w:color="808080"/></w:tblBorders>
<w:tblCellMar><w:top w:w="40" w:type="dxa"/><w:left w:w="80" w:type="dxa"/><w:bottom w:w="40" w:type="dxa"/>
<w:right w:w="80" w:type="dxa"/></w:tblCellMar></w:tblPr></w:style>
</w:styles>"""


def numbering_level(level, kind):
    indent = 360 + 360 * level
    text = ("•", "◦", "▪")[level % 3] if kind == "bullet" else f"%{level + 1}."
    fmt = "bullet" if kind == "bullet" else "decimal"
    return (f'<w:lvl w:ilvl="{level}"><w:start w:val="1"/><w:numFmt w:val="{fmt}"/><w:lvlText w:val="{text}"/>'
            f'<w:lvlJc w:val="left"/><w:pPr><w:ind w:left="{indent}" w:hanging="360"/></w:pPr></w:lvl>')


def numbering(ordered_lists):
    bullets = "".join(numbering_level(level, "bullet") for level in range(3))
    decimals = "".join(numbering_level(level, "decimal") for level in range(3))
    restarts = "".join(
        f'<w:num w:numId="{num_id}"><w:abstractNumId w:val="1"/>'
        + "".join(f'<w:lvlOverride w:ilvl="{level}"><w:startOverride w:val="1"/></w:lvlOverride>'
                  for level in range(3))
        + "</w:num>"
        for num_id in range(2, ordered_lists + 2))
    return (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:numbering {W}>'
            f'<w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="hybridMultilevel"/>{bullets}</w:abstractNum>'
            f'<w:abstractNum w:abstractNumId="1"><w:multiLevelType w:val="hybridMultilevel"/>{decimals}</w:abstractNum>'
            f'<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>{restarts}</w:numbering>')


def document_xml(body):
    section = ('<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>'
               '<w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="567" w:footer="567"'
               ' w:gutter="0"/></w:sectPr>')
    return (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document {W}>'
            f'<w:body>{"".join(body)}{section}</w:body></w:document>')


def core_xml(title):
    return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><cp:coreProperties '
            'xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" '
            'xmlns:dc="http://purl.org/dc/elements/1.1/">'
            f'<dc:title>{escape(title)}</dc:title><dc:language>ru-RU</dc:language></cp:coreProperties>')


CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>"""

PACKAGE_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>"""

DOCUMENT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>
</Relationships>"""


def convert(source):
    text = source.read_text(encoding="utf-8")
    builder = TreeBuilder()
    builder.feed(markdown.markdown(text, extensions=["tables", "fenced_code", "sane_lists"]))
    doc = Document()
    render_blocks(doc, builder.root)
    title = text.splitlines()[0].lstrip("# ").strip()
    parts = {
        "[Content_Types].xml": CONTENT_TYPES,
        "_rels/.rels": PACKAGE_RELS,
        "docProps/core.xml": core_xml(title),
        "word/_rels/document.xml.rels": DOCUMENT_RELS,
        "word/document.xml": document_xml(doc.body),
        "word/styles.xml": STYLES,
        "word/numbering.xml": numbering(doc.ordered_lists),
    }
    target = source.with_suffix(".docx")
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as package:
        for name, content in parts.items():
            package.writestr(zipfile.ZipInfo(name, ZIP_TIME), content.encode("utf-8"), zipfile.ZIP_DEFLATED)
    return target


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("использование: python3 project_files/md2docx.py <файл.md>")
    print(convert(pathlib.Path(sys.argv[1]).resolve()))
