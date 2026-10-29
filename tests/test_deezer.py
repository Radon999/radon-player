"""Deezer's personal data file: an .xlsx whose "10_listeningHistory" sheet lists every play."""
import tempfile
import unittest
import zipfile
from pathlib import Path

from app import load

om = load()

M = 'xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"'
R = 'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'


def make_xlsx(path, rows):
    """A small .xlsx as Deezer's: an account sheet first, then the history (text in shared strings)."""
    shared, idx = [], {}

    def cell(ref, v):
        if isinstance(v, (int, float)):
            return f'<c r="{ref}"><v>{v}</v></c>'
        if v not in idx:
            idx[v] = len(shared)
            shared.append(v)
        return f'<c r="{ref}" t="s"><v>{idx[v]}</v></c>'
    body = "".join(f'<row r="{i + 1}">' + "".join(cell(f"{chr(65 + j)}{i + 1}", v) for j, v in enumerate(r)) + "</row>"
                   for i, r in enumerate(rows))
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("xl/workbook.xml", f'<workbook {M} {R}><sheets><sheet name="1_account" sheetId="1" r:id="rId1"/>'
                                      f'<sheet name="10_listeningHistory" sheetId="2" r:id="rId2"/></sheets></workbook>')
        z.writestr("xl/_rels/workbook.xml.rels",
                   '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                   '<Relationship Id="rId1" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Target="worksheets/sheet2.xml"/>'
                   '</Relationships>')
        z.writestr("xl/worksheets/sheet1.xml", f'<worksheet {M}><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>me</t></is></c></row></sheetData></worksheet>')
        z.writestr("xl/worksheets/sheet2.xml", f'<worksheet {M}><sheetData>{body}</sheetData></worksheet>')
        z.writestr("xl/sharedStrings.xml", f'<sst {M}>' + "".join(f"<si><t>{s}</t></si>" for s in shared) + "</sst>")


class DeezerTest(unittest.TestCase):
    def test_import(self):
        d = Path(tempfile.mkdtemp())
        x = d / "deezer-data_123.xlsx"
        make_xlsx(x, [["Song Title", "Artist", "ISRC", "Album Title", "IP Address", "Listening Time", "Platform Name",
                       "Platform Model", "Date"],
                      ["Mercury", "Imagine Dragons", "USUM72206012", "Mercury - Act 1", "1.2.3.4", 180, "android", "x",
                       "2024-05-01 18:02:11"],
                      ["Skipped", "Someone", "", "", "", 12, "", "", "2024-05-01 18:05:00"],   # under 30 s: not a play
                      ["Arrow", "half•alive", "", "Now, Not Yet", "", "203", "", "", "2024-05-02 09:00:00"]])
        h = om.DeezerHistory(d / "history.json")
        self.assertEqual(h.import_file(str(x)), 2)
        self.assertEqual([(p[1], p[2], p[3], p[4]) for p in h.plays],
                         [("Imagine Dragons", "Mercury", "Mercury - Act 1", 180000), ("half•alive", "Arrow", "Now, Not Yet", 203000)])
        self.assertEqual(h.import_file(str(x)), 0)   # the same plays again: not counted twice
        # The zip Deezer's download came in, with the .xlsx inside
        z = d / "deezer.zip"
        with zipfile.ZipFile(z, "w") as f:
            f.write(x, "deezer-data_123.xlsx")
        self.assertEqual(h.import_file(str(z)), 0)

    def test_french_and_not_deezer(self):
        rows = [["Titre", "Artiste", "Album", "Temps d'écoute", "Date d'écoute"], ["Alors on danse", "Stromae", "Cheese", "3:05", "2023-01-02 10:00:00"]]
        self.assertEqual(om.DeezerHistory.parse_rows(rows)[0][1:], ("Stromae", "Alors on danse", "Cheese", 185000))
        d = Path(tempfile.mkdtemp())
        bad = d / "x.zip"
        with zipfile.ZipFile(bad, "w") as f:
            f.writestr("a.txt", "hi")
        with self.assertRaises(ValueError):
            om.DeezerHistory(d / "h.json").import_file(str(bad))


if __name__ == "__main__":
    unittest.main()
