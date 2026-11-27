import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location(
    "sonar_coverage", Path(__file__).with_name("prepare-sonar-coverage.py")
)
coverage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(coverage)

JACOCO = """
<report name="service"><package name="ro/tweebyte/example">
<sourcefile name="Example.java">
<line nr="10" mi="0" ci="1" mb="1" cb="1"/>
<line nr="12" mi="1" ci="0" mb="0" cb="0"/>
</sourcefile></package></report>
"""


class TestSonarCoverage(unittest.TestCase):
    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory()
        self.addCleanup(self.workspace.cleanup)
        self.old_dir = Path.cwd()
        os.chdir(self.workspace.name)
        self.addCleanup(os.chdir, self.old_dir)

    def test_distinguishes_same_java_package_and_file_across_stacks(self):
        modules = [Path("backend", stack, "gateway-service") for stack in ("async", "reactive")]
        for module in modules:
            source = module / "src/main/java/ro/tweebyte/example/Example.java"
            source.parent.mkdir(parents=True)
            source.touch()
            report = module / "target/site/jacoco/jacoco.xml"
            report.parent.mkdir(parents=True)
            report.write_text(JACOCO)

        result = Path("java.xml")
        coverage.java_coverage(modules, result)
        files = ET.parse(result).getroot().findall("file")
        self.assertEqual([f.attrib["path"].split("/")[1] for f in files], ["async", "reactive"])
        self.assertEqual(
            files[0][0].attrib,
            {"lineNumber": "10", "covered": "true", "branchesToCover": "2", "coveredBranches": "1"},
        )
        self.assertEqual(files[0][1].attrib, {"lineNumber": "12", "covered": "false"})
        (modules[0] / "src/main/java/ro/tweebyte/example/Example.java").unlink()
        with self.assertRaisesRegex(ValueError, "source path not found"):
            coverage.java_coverage(modules, result)

    def test_rewrites_frontend_path_relative_to_scan_root(self):
        src = Path("frontend/src/app/example.ts")
        src.parent.mkdir(parents=True)
        src.touch()
        report = Path("in.lcov")
        report.write_text("TN:\nSF:src/app/example.ts\nDA:1,0\nend_of_record\n")
        result = Path("out.lcov")
        coverage.frontend_coverage(report, result, Path.cwd())
        self.assertIn("SF:frontend/src/app/example.ts\nDA:1,0\n", result.read_text())

    def test_rejects_frontend_paths_outside_source_tree(self):
        Path("frontend").mkdir()
        Path("unrelated.ts").touch()
        report = Path("in.lcov")
        report.write_text("SF:../unrelated.ts\n")
        with self.assertRaisesRegex(ValueError, "Invalid frontend source path"):
            coverage.frontend_coverage(report, Path("out.lcov"), Path.cwd())


if __name__ == "__main__":
    unittest.main()
