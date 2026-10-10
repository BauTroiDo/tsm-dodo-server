import base64
import json
import struct
import unittest
from pathlib import Path
from dvt_markstep_parity import compare, parse, repair_navigation


def record(mission, step, end, condition=None, description="", guide=""):
    def text(value):
        raw = value.encode("utf-16le")
        return struct.pack("<H", len(raw)) + raw
    conditions = [condition or [0] * 8] + [[0] * 8 for _ in range(4)]
    return (struct.pack("<HB", mission, step) + text(description) +
            b"".join(struct.pack("<BHBHHHBB", *value) for value in conditions) +
            text(guide) + struct.pack("<HHHBBBB", *end))


def binary(*records):
    return struct.pack("<I", len(records)) + b"".join(records)


class MarkStepParityTest(unittest.TestCase):
    def test_localization_and_variable_offsets_do_not_change_navigation(self):
        end = [25021, 430, 560, 2, 7, 1, 0]
        client = binary(record(11566, 2, end, description="白帝城", guide="導航"))
        server = binary(record(11566, 2, end, description="Đến Bạch Đế thành", guide="Đi"))
        report = compare(client, server)
        self.assertNotEqual(report["client_sha256"], report["server_sha256"])
        self.assertEqual([], report["structural_differences"])

    def test_count_item_id_and_door_id_changes_are_reported(self):
        end = [49992, 860, 420, 1, 1, 1, 0]
        original = record(13149, 1, end, [3, 29745, 10, 49992, 860, 420, 0, 0])
        for condition, target in [([3, 29745, 3, 49992, 860, 420, 0, 0], end),
                                  ([3, 29746, 10, 49992, 860, 420, 0, 0], end),
                                  ([3, 29745, 10, 49992, 860, 420, 0, 0], [49992, 860, 420, 1, 5, 1, 0])]:
            with self.subTest(condition=condition, target=target):
                self.assertEqual(1, len(compare(binary(original), binary(record(13149, 1, target, condition)))["structural_differences"]))

    def test_missing_records_are_separate_from_changed_records(self):
        row = record(11566, 2, [25021, 430, 560, 2, 7, 1, 0])
        extra = record(13452, 1, [0] * 7)
        report = compare(binary(row), binary(row, extra))
        self.assertEqual([[13452, 1]], report["server_only"])
        self.assertEqual([], report["client_only"])
        self.assertEqual([], report["structural_differences"])

    def test_rejects_truncation_trailing_duplicate_and_bad_utf16(self):
        row = record(11566, 2, [25021, 430, 560, 2, 7, 1, 0])
        odd = bytearray(binary(row))
        odd[7:9] = b"\x01\x00"
        for bad in (binary(row)[:-1], binary(row) + b"x", binary(row, row), bytes(odd), b"\xff\xff\xff\xff"):
            with self.subTest(length=len(bad)), self.assertRaises(ValueError):
                parse(bad)

    def test_actual_pc_and_server_door_records_match(self):
        fixture = json.loads(Path(__file__).with_name("dvt_markstep_door_records.json").read_text(encoding="utf-8"))
        client = base64.b64decode(fixture["client_binary_base64"])
        server = base64.b64decode(fixture["server_binary_base64"])
        report = compare(client, server)
        self.assertEqual(2, report["common_records"])
        self.assertEqual([], report["structural_differences"])
        rows = parse(client)
        self.assertEqual([25021, 430, 560, 2, 7, 1, 0], rows[(11566, 2)]["end"])
        self.assertEqual([12431, 990, 720, 2, 5, 1, 0], rows[(11934, 4)]["end"])

    def test_repair_changes_only_two_door_id_bytes_in_actual_records(self):
        fixture = json.loads(Path(__file__).with_name("dvt_markstep_door_records.json").read_text(encoding="utf-8"))
        for source in ("client", "server"):
            with self.subTest(source=source):
                original = base64.b64decode(fixture[source + "_binary_base64"])
                fixed = repair_navigation(original)
                before, after = parse(original), parse(fixed)
                differences = [i for i, pair in enumerate(zip(original, fixed)) if pair[0] != pair[1]]
                self.assertEqual(len(original), len(fixed))
                self.assertEqual([row["offset"] + row["length"] - 3 for row in before.values()], differences)
                for key in before:
                    self.assertEqual(3, after[key]["end"][4])
                    self.assertEqual(before[key]["description"], after[key]["description"])
                    self.assertEqual(before[key]["guide"], after[key]["guide"])
                    self.assertEqual(before[key]["conditions"], after[key]["conditions"])
                    self.assertEqual(before[key]["end"][:4] + before[key]["end"][5:], after[key]["end"][:4] + after[key]["end"][5:])
                self.assertEqual(fixed, repair_navigation(fixed))

    def test_repair_rejects_missing_or_changed_anchor(self):
        one = record(11566, 2, [25021, 430, 560, 2, 7, 1, 0])
        changed = record(11934, 4, [12431, 990, 720, 2, 6, 1, 0])
        for value in (binary(one), binary(one, changed)):
            with self.subTest(length=len(value)), self.assertRaises(ValueError):
                repair_navigation(value)

    def test_repository_targets_match_actual_dvt_eve_doors_and_mission_gates(self):
        root = Path(__file__).resolve().parent.parent
        path = root / "File.dat/Data-ios/IOS_MarkStep_C.dat"
        if not path.exists():
            self.skipTest("Run from repository tools/ for full DVT data integration")
        from eve_tool import EveArchive
        rows = parse(path.read_bytes())
        eve = EveArchive(root / "gamedata/Eve.emg")
        for mission, step, scene, event in [(11566, 2, 25021, 7), (11934, 4, 12431, 6)]:
            with self.subTest(mission=mission, step=step):
                end = rows[(mission, step)]["end"]
                self.assertEqual(scene, end[0])
                self.assertEqual(2, end[3])
                self.assertEqual(3, end[4])
                data = eve.parse_scene(scene)
                door = next(d for d in data["doors"] if d["id"] == end[4])
                self.assertEqual([event], door["events"])
                conditions = data["events"][event]["conditions"]
                self.assertTrue(any(c["class"] == 2 and c["param"] == mission and
                                    c["pstyle"] == 1 and c["ops"] == 5 and c["val"] == step
                                    for c in conditions))


if __name__ == "__main__":
    unittest.main()

