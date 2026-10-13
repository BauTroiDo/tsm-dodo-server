"""Prove that DVT boss defeat's original TALK is genuinely present in client data."""
from pathlib import Path
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1]
TALKS = ROOT / "File.dat" / "CompreseData" / "Talk_C.dat"

class DvtBossNativeTalkAssetTest(unittest.TestCase):
    def test_original_loss_talk_41353_is_real_utf16_asset(self):
        buf = TALKS.read_bytes()
        (count,) = struct.unpack_from("<I", buf, 0)
        self.assertGreater(count, 40000)
        offset = 4
        matches = []
        for _ in range(count):
            talk_id, byte_count = struct.unpack_from("<HH", buf, offset)
            offset += 4
            self.assertEqual(byte_count % 2, 0)
            self.assertLessEqual(offset + byte_count, len(buf))
            if talk_id == 41353:
                matches.append(buf[offset:offset + byte_count].decode("utf-16-le"))
            offset += byte_count
        self.assertEqual(offset, len(buf))
        self.assertEqual(matches, ["Chút năng lực này..."])

if __name__ == "__main__":
    unittest.main()
