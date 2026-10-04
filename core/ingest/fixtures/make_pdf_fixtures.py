"""
Builds the PDF fixtures in ../src/commonTest/resources/pdf/ (docs/pdf/ROADMAP.md, P0).

    python3 -m venv /tmp/pdfenv && /tmp/pdfenv/bin/pip install pymupdf pillow
    # tiffcp (libtiff) must be on the PATH: `brew install libtiff` / `apt install libtiff-tools`
    /tmp/pdfenv/bin/python core/ingest/fixtures/make_pdf_fixtures.py

Everything is made here from text written for the purpose: no third-party content, no third-party fonts
(the PDF base-14 fonts only). Rerunning gives equivalent files, not byte-identical ones (PyMuPDF writes a
random trailer id), so the tests check structure and words, never hashes. See the README next to the PDFs
for what each file holds.
"""

import io
import os
import random
import struct
import subprocess
import tempfile
import zlib

import fitz  # PyMuPDF
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "commonTest", "resources", "pdf")

LETTER = (612, 792)
SLIDE = (960, 540)
SCAN_DPI = 150
FIXED_META = {"title": "", "author": "", "subject": "", "keywords": "", "creator": "", "producer": "Mnemo fixtures",
              "creationDate": "D:20261004000000Z", "modDate": "D:20261004000000Z"}

# ---------------------------------------------------------------------------------------------
# Text for the text pages (outline.pdf, mixed.pdf): short sentences from a fixed word list
# ---------------------------------------------------------------------------------------------

SUBJECTS = ["The membrane", "A tissue", "The valve", "Each cell", "The protein", "A signal", "The lining", "The fibre"]
VERBS = ["carries", "separates", "stores", "releases", "protects", "connects", "filters", "repairs"]
OBJECTS = ["small molecules", "water and salts", "a steady charge", "the nearby layers", "heat and energy",
           "the outer surface", "slow changes", "a thin film"]


def sentences(rng, n):
    return " ".join(f"{rng.choice(SUBJECTS)} {rng.choice(VERBS)} {rng.choice(OBJECTS)}." for _ in range(n))


def text_page(doc, heading, lines, footer=None, size=LETTER):
    """A page of body text: a heading, paragraphs (strings) and an optional footer line."""
    page = doc.new_page(width=size[0], height=size[1])
    page.insert_text((72, 90), heading, fontname="hebo", fontsize=20)
    y = 130
    for para in lines:
        rc = fitz.Rect(72, y, size[0] - 72, y + 160)
        used = page.insert_textbox(rc, para, fontname="tiro", fontsize=12, lineheight=1.35)
        assert used >= 0, f"paragraph does not fit on '{heading}'"
        # insert_textbox returns the unused height
        y += 160 - used + 12
    if footer:
        page.insert_text((72, size[1] - 48), footer, fontname="helv", fontsize=9)
    return page


# ---------------------------------------------------------------------------------------------
# outline.pdf: 12 text pages, bookmarks on four levels, page labels i, ii, 1..10
# ---------------------------------------------------------------------------------------------

OUTLINE_TOC = [
    [1, "Contents", 1],
    [1, "Preface", 2],
    [1, "Part I: Foundations", 3],
    [2, "Chapter 1: Cells", 3],
    [3, "1.1 Membranes", 3],
    [3, "1.2 Organelles", 4],
    [4, "1.2.1 Mitochondria", 4],  # a fourth level: the reader keeps three
    [2, "Chapter 2: Tissues", 6],
    [3, "2.1 Epithelium", 6],
    [3, "2.2 Connective tissue", 7],
    [1, "Part II: Systems", 8],
    [2, "Chapter 3: Circulation", 8],
    [2, "Chapter 4: Respiration", 10],
    [3, "4.1 Gas exchange", 11],
    [1, "Appendix", 12],
    [1, "Errata (no destination)", -1],  # a bookmark that points nowhere: the reader drops it
]
OUTLINE_LABELS = ["i", "ii"] + [str(n) for n in range(1, 11)]


def make_outline():
    rng = random.Random(14)
    doc = fitz.open()
    heads = {
        1: "Contents", 2: "Preface", 3: "1.1 Membranes", 4: "1.2 Organelles", 5: "1.2 Organelles (continued)",
        6: "2.1 Epithelium", 7: "2.2 Connective tissue", 8: "Chapter 3: Circulation",
        9: "Chapter 3: Circulation (continued)", 10: "Chapter 4: Respiration", 11: "4.1 Gas exchange",
        12: "Appendix",
    }
    for n in range(1, 13):
        label = OUTLINE_LABELS[n - 1]
        body = [f"This is page {n} of the outline fixture, printed page {label}.", sentences(rng, 6), sentences(rng, 5)]
        if n == 1:
            body = ["Contents of the outline fixture."] + [t[1] for t in OUTLINE_TOC if t[0] <= 2 and t[2] > 0]
            body = [f"This is page 1 of the outline fixture, printed page i.", "\n".join(body[1:])]
        text_page(doc, heads[n], body, footer=label)
    doc.set_toc(OUTLINE_TOC)
    doc.set_page_labels([
        {"startpage": 0, "prefix": "", "style": "r", "firstpagenum": 1},
        {"startpage": 2, "prefix": "", "style": "D", "firstpagenum": 1},
    ])
    doc.set_metadata({**FIXED_META, "title": "Outline fixture"})
    return doc


# ---------------------------------------------------------------------------------------------
# Scanned pages: text drawn on a page, rendered to pixels, and only the pixels kept
# ---------------------------------------------------------------------------------------------

class Sheet:
    """A drawing surface for one scanned page that also records its expected transcription."""

    def __init__(self, size=LETTER):
        self.doc = fitz.open()
        self.page = self.doc.new_page(width=size[0], height=size[1])
        self.size = size
        self.y = 80
        self.expected = []

    def heading(self, text):
        self.page.insert_text((72, self.y), text, fontname="hebo", fontsize=22)
        self.expected.append(f"# {text}")
        self.y += 40

    def paragraph(self, text):
        rc = fitz.Rect(72, self.y, self.size[0] - 72, self.y + 200)
        left = self.page.insert_textbox(rc, text, fontname="tiro", fontsize=13, lineheight=1.4)
        assert left >= 0, "paragraph does not fit"
        self.y += 200 - left + 14
        self.expected.append(text)

    def bullets(self, items):
        for item in items:
            self.page.insert_text((84, self.y), "-", fontname="tiro", fontsize=13)
            self.page.insert_text((100, self.y), item, fontname="tiro", fontsize=13)
            self.y += 20
            self.expected.append(f"- {item}")
        self.y += 10

    def text(self, x, y, s, size=15, font="tiro"):
        self.page.insert_text((x, y), s, fontname=font, fontsize=size)

    def formula(self, label, tex, draw):
        """A line `label` followed by a formula drawn by `draw(sheet, x, baseline_y)`; `tex` is its transcription."""
        self.text(72, self.y, label, size=13)
        draw(self, 72, self.y + 46)
        self.expected.append(f"{label}\n\n\\[{tex}\\]")
        self.y += 100

    def gray(self, dpi=SCAN_DPI):
        pix = self.page.get_pixmap(dpi=dpi, colorspace=fitz.csGRAY, alpha=False)
        return Image.frombytes("L", (pix.width, pix.height), pix.samples)


def draw_kinetic(sheet, x, y):
    sheet.text(x, y, "E", 20)
    sheet.text(x + 18, y, "=", 20)
    sheet.text(x + 38, y - 8, "1", 12)
    sheet.page.draw_line((x + 37, y - 6), (x + 47, y - 6), width=0.8)
    sheet.text(x + 38, y + 8, "2", 12)
    sheet.text(x + 52, y, "mv", 20)
    sheet.text(x + 80, y - 9, "2", 12)


def draw_pythagoras(sheet, x, y):
    for bx, base, exp in [(x, "a", "2"), (x + 50, "b", "2"), (x + 100, "c", "2")]:
        sheet.text(bx, y, base, 20)
        sheet.text(bx + 11, y - 9, exp, 12)
    sheet.text(x + 28, y, "+", 20)
    sheet.text(x + 76, y, "=", 20)


def draw_quadratic(sheet, x, y):
    sheet.text(x, y, "x  =", 20)
    nx = x + 52  # the fraction starts here
    sheet.text(nx, y - 14, "-b", 18)
    sheet.text(nx + 28, y - 14, "±", 18)
    rx = nx + 48  # radical sign over b^2 - 4ac
    sheet.page.draw_polyline([(rx, y - 20), (rx + 4, y - 14), (rx + 8, y - 34), (rx + 118, y - 34)], width=1.0)
    sheet.text(rx + 12, y - 14, "b", 18)
    sheet.text(rx + 22, y - 23, "2", 11)
    sheet.text(rx + 34, y - 14, "-  4ac", 18)
    sheet.page.draw_line((nx - 4, y - 3), (rx + 124, y - 3), width=1.0)
    sheet.text(nx + 52, y + 17, "2a", 18)


def scan_sheets():
    s1 = Sheet()
    s1.heading("The Water Cycle")
    s1.paragraph("Water moves between the oceans, the air and the land in a continuous loop. The sun warms the "
                 "surface of the sea and the water evaporates into the air as vapour.")
    s1.paragraph("High in the sky the vapour cools and condenses into tiny droplets, which gather as clouds. "
                 "When the droplets grow heavy they fall as rain, snow or hail, and the water collects again in "
                 "rivers, lakes and the ground.")
    s1.bullets(["evaporation", "condensation", "precipitation", "collection"])

    s2 = Sheet()
    s2.heading("Energy and Motion")
    s2.paragraph("Three formulas that every student meets early on.")
    s2.formula("Kinetic energy of a moving body:", "E = \\tfrac{1}{2} m v^{2}", draw_kinetic)
    s2.formula("The sides of a right triangle:", "a^{2} + b^{2} = c^{2}", draw_pythagoras)
    s2.formula("The roots of a quadratic equation:", "x = \\frac{-b \\pm \\sqrt{b^{2} - 4ac}}{2a}", draw_quadratic)

    s3 = Sheet()
    s3.heading("Notes on Memory")
    s3.paragraph("Things are remembered better when they are recalled than when they are reread. A short test "
                 "after reading does more for memory than a second reading of the same page.")
    s3.paragraph("Spacing helps too: seeing a fact again just before it would be forgotten makes it last longer.")
    s3.bullets(["recall beats rereading", "space the repetitions", "sleep after learning"])
    return [s1, s2, s3]


# ---------------------------------------------------------------------------------------------
# A small PDF writer for pages that are one image, in a given encoding
# ---------------------------------------------------------------------------------------------

class RawPdf:
    """Pages that are a single image each; `images` are (width, height, dict entries, stream bytes)."""

    def __init__(self, size=LETTER):
        self.size = size
        self.objs = []  # object bodies, 1-based ids

    def _add(self, body: bytes) -> int:
        self.objs.append(body)
        return len(self.objs)

    def build(self, images):
        # ids: 1 catalog, 2 pages, then per page: page, content, image
        self.objs = [b"", b""]
        page_ids = []
        for width, height, entries, data in images:
            img_id = len(self.objs) + 3
            page_id = self._add(b"")
            content = f"q {self.size[0]} 0 0 {self.size[1]} 0 0 cm /Im0 Do Q".encode()
            content_id = self._add(b"<< /Length %d >>\nstream\n" % len(content) + content + b"\nendstream")
            got = self._add(
                b"<< /Type /XObject /Subtype /Image /Width %d /Height %d " % (width, height)
                + entries.encode() + b" /Length %d >>\nstream\n" % len(data) + data + b"\nendstream")
            assert got == img_id
            self.objs[page_id - 1] = (
                f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 {self.size[0]} {self.size[1]}] "
                f"/Resources << /XObject << /Im0 {img_id} 0 R >> >> /Contents {content_id} 0 R >>").encode()
            page_ids.append(page_id)
        self.objs[0] = b"<< /Type /Catalog /Pages 2 0 R >>"
        kids = " ".join(f"{i} 0 R" for i in page_ids)
        self.objs[1] = f"<< /Type /Pages /Kids [{kids}] /Count {len(page_ids)} >>".encode()
        out = io.BytesIO()
        out.write(b"%PDF-1.5\n%\xe2\xe3\xcf\xd3\n")
        offsets = []
        for i, body in enumerate(self.objs, 1):
            offsets.append(out.tell())
            out.write(b"%d 0 obj\n" % i + body + b"\nendobj\n")
        xref = out.tell()
        out.write(b"xref\n0 %d\n0000000000 65535 f \n" % (len(self.objs) + 1))
        for off in offsets:
            out.write(b"%010d 00000 n \n" % off)
        out.write(b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(self.objs) + 1, xref))
        return out.getvalue()


def flate_image(img):
    return img.width, img.height, "/ColorSpace /DeviceGray /BitsPerComponent 8 /Filter /FlateDecode", \
        zlib.compress(img.tobytes(), 9)


def bilevel(img):
    return img.point(lambda v: 255 if v > 160 else 0).convert("1", dither=Image.Dither.NONE)


def g4_strip(img1):
    """CCITT Group 4 data of a 1-bit image (black text = black runs), made by libtiff's tiffcp."""
    # PIL's 1-bit images have 1 = white, and libtiff codes 1-bits as the black runs: invert so the text is coded
    # as black and the page as white (checked by rendering: MuPDF and Poppler show black text on white).
    inverted = img1.convert("L").point(lambda v: 255 - v).convert("1", dither=Image.Dither.NONE)
    with tempfile.TemporaryDirectory() as tmp:
        src, dst = os.path.join(tmp, "in.tif"), os.path.join(tmp, "out.tif")
        inverted.save(src, compression=None)
        subprocess.run(["tiffcp", "-c", "g4", "-r", str(img1.height), src, dst], check=True, capture_output=True)
        with Image.open(dst) as t:
            offsets, counts = t.tag_v2[273], t.tag_v2[279]
            assert len(offsets if isinstance(offsets, tuple) else [offsets]) == 1, "expected a single strip"
            off = offsets[0] if isinstance(offsets, tuple) else offsets
            n = counts[0] if isinstance(counts, tuple) else counts
        raw = open(dst, "rb").read()
        return raw[off:off + n]


def ccitt_image(img1, invert):
    data = g4_strip(img1)
    black_is_1 = "true" if invert else "false"
    return img1.width, img1.height, ("/ColorSpace /DeviceGray /BitsPerComponent 1 /Filter /CCITTFaxDecode "
                                     f"/DecodeParms << /K -1 /Columns {img1.width} /Rows {img1.height} "
                                     f"/BlackIs1 {black_is_1} >>"), data


def jbig2_segments(width, height, mmr):
    """An embedded JBIG2 stream (no file header): page information + one immediate MMR generic region."""
    page_info = struct.pack(">IIIIBH", width, height, 0, 0, 0, 0)
    seg0 = struct.pack(">IBBBI", 0, 48, 0, 1, len(page_info)) + page_info
    region = struct.pack(">IIIIB", width, height, 0, 0, 0) + b"\x01" + mmr  # flags: MMR = 1
    seg1 = struct.pack(">IBBBI", 1, 38, 0, 1, len(region)) + region
    return seg0 + seg1


def jbig2_image(img1):
    data = jbig2_segments(img1.width, img1.height, g4_strip(img1))
    return img1.width, img1.height, "/ColorSpace /DeviceGray /BitsPerComponent 1 /Filter /JBIG2Decode", data


# ---------------------------------------------------------------------------------------------
# The other fixtures
# ---------------------------------------------------------------------------------------------

def jpeg_bytes(img, quality=72):
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=quality, optimize=True)
    return buf.getvalue()


def make_mixed(sheets):
    """Six pages: text, scan, text, scan, text, scan."""
    rng = random.Random(7)
    doc = fitz.open()
    for i in range(3):
        n = 2 * i + 1
        text_page(doc, f"Text page {n}",
                  [f"This is page {n} of the mixed fixture and it has a text layer.", sentences(rng, 6)], footer=str(n))
        page = doc.new_page(width=LETTER[0], height=LETTER[1])
        page.insert_image(page.rect, stream=jpeg_bytes(sheets[i].gray()))
    doc.set_metadata({**FIXED_META, "title": "Mixed fixture"})
    return doc


def make_ocr_layer(sheet):
    """A scan with an invisible text layer, as OCR software leaves it: it must read as text, not as scanned."""
    doc = fitz.open()
    page = doc.new_page(width=LETTER[0], height=LETTER[1])
    page.insert_image(page.rect, stream=jpeg_bytes(sheet.gray()))
    y = 80
    for chunk in ["The Water Cycle", "Water moves between the oceans, the air and the land in a continuous loop.",
                  "The sun warms the surface of the sea and the water evaporates into the air as vapour."]:
        page.insert_text((72, y), chunk, fontname="helv", fontsize=12, render_mode=3)  # 3 = invisible
        y += 20
    doc.set_metadata({**FIXED_META, "title": "OCR layer fixture"})
    return doc


def slide(doc, title):
    page = doc.new_page(width=SLIDE[0], height=SLIDE[1])
    page.insert_text((48, 70), title, fontname="hebo", fontsize=30)
    return page


def box(page, rect, label, fill):
    page.draw_rect(rect, color=(0.1, 0.1, 0.1), fill=fill, width=1.5, radius=0.15)
    page.insert_textbox(rect + (0, 28, 0, 0), label, fontname="helv", fontsize=16, align=fitz.TEXT_ALIGN_CENTER)


def arrow(page, a, b):
    page.draw_line(a, b, color=(0.1, 0.1, 0.1), width=2)
    dx, dy = b[0] - a[0], b[1] - a[1]
    norm = (dx * dx + dy * dy) ** 0.5
    ux, uy = dx / norm, dy / norm
    p1 = (b[0] - 12 * ux + 6 * uy, b[1] - 12 * uy - 6 * ux)
    p2 = (b[0] - 12 * ux - 6 * uy, b[1] - 12 * uy + 6 * ux)
    page.draw_polyline([p1, b, p2], color=(0.1, 0.1, 0.1), fill=(0.1, 0.1, 0.1), closePath=True)


def make_slides():
    doc = fitz.open()
    # 1: a cycle of four boxes
    p = slide(doc, "The cycle")
    cells = [fitz.Rect(120, 140, 300, 220), fitz.Rect(620, 140, 800, 220),
             fitz.Rect(620, 380, 800, 460), fitz.Rect(120, 380, 300, 460)]
    for r, label, fill in zip(cells, ["Evaporate", "Condense", "Fall", "Collect"],
                              [(1, .93, .7), (.8, .9, 1), (.75, .95, .8), (.95, .8, .85)]):
        box(p, r, label, fill)
    arrow(p, (300, 180), (620, 180))
    arrow(p, (710, 220), (710, 380))
    arrow(p, (620, 420), (300, 420))
    arrow(p, (210, 380), (210, 220))
    # 2: a block diagram with a branch
    p = slide(doc, "From input to output")
    box(p, fitz.Rect(60, 220, 240, 300), "Input", (.85, .9, 1))
    box(p, fitz.Rect(390, 140, 570, 220), "Filter", (1, .93, .7))
    box(p, fitz.Rect(390, 320, 570, 400), "Store", (.75, .95, .8))
    box(p, fitz.Rect(720, 220, 900, 300), "Output", (.95, .8, .85))
    arrow(p, (240, 250), (390, 190))
    arrow(p, (240, 270), (390, 360))
    arrow(p, (570, 190), (720, 245))
    arrow(p, (570, 360), (720, 275))
    # 3: a bar chart with labelled axes
    p = slide(doc, "Recall after one week")
    p.draw_line((140, 460), (860, 460), width=2)
    p.draw_line((140, 460), (140, 120), width=2)
    for i, (label, h, col) in enumerate([("Reread", 120, (.8, .8, .85)), ("Quiz once", 200, (.6, .75, .95)),
                                         ("Spaced quizzes", 320, (.35, .6, .9))]):
        x = 220 + i * 210
        p.draw_rect(fitz.Rect(x, 460 - h, x + 140, 460), fill=col, color=(0.1, 0.1, 0.1))
        p.insert_text((x + 4, 484), label, fontname="helv", fontsize=14)
    p.insert_text((40, 300), "% recalled", fontname="helv", fontsize=14, rotate=90)
    # 4: a raster diagram (a food chain), as a pasted picture would be
    sheet = Sheet(size=SLIDE)
    names = ["Grass", "Rabbit", "Fox"]
    for i, name in enumerate(names):
        r = fitz.Rect(70 + i * 290, 200, 250 + i * 290, 300)
        sheet.page.draw_oval(r, fill=(.85 - .15 * i, .95 - .1 * i, .85 + .05 * i), color=(0.1, 0.1, 0.1), width=2)
        sheet.page.insert_textbox(r + (0, 36, 0, 0), name, fontname="helv", fontsize=20, align=fitz.TEXT_ALIGN_CENTER)
        if i:
            arrow(sheet.page, (r.x0 - 40, 250), (r.x0, 250))
    p = slide(doc, "A food chain")
    pix = sheet.page.get_pixmap(dpi=96, colorspace=fitz.csRGB, alpha=False)
    scale = 96 / 72  # the pixmap is in pixels, the page in points
    img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples).crop(
        (0, round(160 * scale), pix.width, round(340 * scale)))
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    p.insert_image(fitz.Rect(0, 160, SLIDE[0], 340), stream=buf.getvalue())
    doc.set_metadata({**FIXED_META, "title": "Slides fixture"})
    return doc


def save(doc, name):
    path = os.path.join(OUT, name)
    doc.save(path, garbage=4, deflate=True)
    doc.close()
    return path


def write(name, data):
    path = os.path.join(OUT, name)
    with open(path, "wb") as f:
        f.write(data)
    return path


def main():
    os.makedirs(OUT, exist_ok=True)
    save(make_outline(), "outline.pdf")

    sheets = scan_sheets()
    grays = [s.gray() for s in sheets]
    write("scanned.pdf", RawPdf().build([flate_image(g) for g in grays]))
    ones = [bilevel(g) for g in grays]
    write("scanned-ccitt.pdf", RawPdf().build([ccitt_image(i, invert=False) for i in ones]))
    write("scanned-jbig2.pdf", RawPdf().build([jbig2_image(i) for i in ones]))

    save(make_mixed(sheets), "mixed.pdf")
    save(make_slides(), "slides.pdf")
    save(make_ocr_layer(sheets[0]), "ocr-layer.pdf")

    # what the scanned pages say, for tests and for comparing a transcription by eye
    for i, s in enumerate(sheets, 1):
        write(f"scanned-page-{i}.expected.md", ("\n\n".join(s.expected) + "\n").encode())

    for name in sorted(os.listdir(OUT)):
        if name.endswith(".pdf"):
            print(f"{os.path.getsize(os.path.join(OUT, name)):>8}  {name}")


if __name__ == "__main__":
    main()
