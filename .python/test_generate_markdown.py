import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


class MarkdownCheckTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        source = Path(__file__).resolve().parents[1]
        for name in (".readme", ".changelog"):
            shutil.copytree(source / name, self.root / name)
        (self.root / ".python").mkdir()
        shutil.copy2(source / ".python/generate_markdown.py", self.root / ".python/generate_markdown.py")
        self.assertEqual(0, self.run_generator().returncode)

    def run_generator(self, *args):
        return subprocess.run(
            [sys.executable, str(self.root / ".python/generate_markdown.py"), *args],
            cwd=self.root, capture_output=True, encoding="utf-8",
        )

    def snapshot(self):
        return {path.relative_to(self.root): (path.read_bytes(), path.stat().st_mtime_ns)
                for path in self.root.rglob("*") if path.is_file()}

    def test_clean_check_accepts_windows_newlines_without_writes(self):
        readme = self.root / "README.md"
        readme.write_bytes(readme.read_bytes().replace(b"\n", b"\r\n"))
        before = self.snapshot()
        result = self.run_generator("--check")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Checked 35 generated files", result.stdout)
        self.assertEqual(before, self.snapshot())

    def test_missing_and_stale_outputs_fail_without_repairing_files(self):
        readme = self.root / "README.md"
        readme.write_bytes(readme.read_bytes() + b"stale\n")
        missing = self.root / "app/src/main/assets/doc/CHANGELOG-ar.md"
        missing.unlink()
        before = self.snapshot()
        result = self.run_generator("--check")
        self.assertEqual(1, result.returncode)
        self.assertIn("README.md", result.stderr)
        self.assertIn("CHANGELOG-ar.md", result.stderr)
        self.assertEqual(before, self.snapshot())
        self.assertEqual(0, self.run_generator().returncode)
        self.assertEqual(0, self.run_generator("--check").returncode)


if __name__ == "__main__":
    unittest.main()
