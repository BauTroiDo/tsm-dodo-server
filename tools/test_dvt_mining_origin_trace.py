import tempfile
import unittest
from pathlib import Path
import importlib.util

TOOL = Path(__file__).with_name("dvt_mining_origin_trace.py")
spec = importlib.util.spec_from_file_location("origin_trace", TOOL)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)

HEADER = ",".join(audit.COLUMNS)
# Synthetic algorithm-validation examples; never DVT rates.
ROW = "DVT_ORIGINAL,test-raw-001,foreman,2026-01-01T00:00:00Z,2026-01-01T00:06:00Z,4,3,100,102,600000000,599999998,no"


class TraceAuditTests(unittest.TestCase):
    def load(self, rows):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "sample.csv"
            path.write_text(HEADER + "\n" + "\n".join(rows), encoding="utf-8")
            return audit.read_trials(path)

    def test_one_trial_is_not_accepted_as_exact_rate(self):
        report = audit.summarize(self.load([ROW]))
        self.assertEqual(report["accepted_trials"], 1)
        self.assertFalse(report["ready_to_code_exact_original_formula"])
        self.assertFalse(report["worker_observations"]["foreman"]["sufficient_repeated_samples"])

    def test_three_samples_still_not_authoritative(self):
        report = audit.summarize(self.load([ROW, ROW, ROW]))
        self.assertTrue(report["worker_observations"]["foreman"]["constant_observed_ts_gain"])
        self.assertFalse(report["ready_to_code_exact_original_formula"])

    def test_confounded_currency_trial_is_excluded(self):
        report = audit.summarize(self.load([ROW.replace(",no", ",yes")]))
        self.assertEqual(report["accepted_trials"], 0)
        self.assertEqual(report["rejected_trials"], 1)

    def test_invalid_source_rejected(self):
        with self.assertRaises(ValueError):
            self.load([ROW.replace("DVT_ORIGINAL", "DEV_EMULATOR")])

    def test_bad_reserve_or_negative_ts_delta_rejected(self):
        with self.assertRaises(ValueError):
            self.load([ROW.replace("600000000,599999998", "700000000,699999998")])
        report = audit.summarize(self.load([ROW.replace(",100,102,", ",102,100,")]))
        self.assertEqual(report["rejected_trials"], 1)


if __name__ == "__main__":
    unittest.main()

[executed on device: DESKTOP-Q96IFHO (32ea4e17-03d0-439c-bd47-fee62bd241c0)]