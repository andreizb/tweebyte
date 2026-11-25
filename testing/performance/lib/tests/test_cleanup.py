"""Exercise cleanup against disposable files; no services or load tools run."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


LIB = Path(__file__).resolve().parents[1] / 'cleanup.sh'


class CleanupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='tweebyte-cleanup-test-')
        self.root = Path(self.temp.name).resolve()
        self.result = self.root / 'results_owned'
        self.result.mkdir()
        self.raw = self.result / '10_1.jtl'
        self.raw.write_text('raw samples\n')
        (self.result / '10_1_filtered.jtl').write_text('filtered samples\n')
        (self.result / 'cells.csv').write_text('concurrency,n,total_count\n10,1,42\n')
        (self.result / '10_1_jmeter.log').write_text('diagnostic log\n')

    def tearDown(self):
        self.temp.cleanup()

    def run_driver(self, body='cleanup_result_complete', keep_raw='0'):
        env = dict(os.environ, KEEP_RAW=keep_raw, TMPDIR=str(self.root))
        env.pop('BENCH_SWEEP_STATE', None)
        return subprocess.run(
            ['/bin/bash', '-c', 'set -euo pipefail\nsource "$1"\n'
             'cleanup_result_init "$2" jmeter 1 "10"\n' + body,
             'test-driver', str(LIB), str(self.result)],
            env=env, capture_output=True, text=True, timeout=30)

    @unittest.skipUnless(shutil.which('lsof'), 'safe automatic pruning requires lsof')
    def test_completed_run_prunes_only_raw_files(self):
        result = self.run_driver()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(self.raw.exists(), result.stderr)
        self.assertFalse((self.result / '10_1_filtered.jtl').exists())
        self.assertTrue((self.result / 'cells.csv').exists())
        self.assertEqual((self.result / '10_1_jmeter.log').read_text(), 'diagnostic log\n')

    def test_keep_raw_applies_to_success_failure_and_signals(self):
        for ending, code in [('cleanup_result_complete', 0), ('exit 7', 7),
                             ('kill -TERM $$', 143), ('kill -INT $$', 130)]:
            with self.subTest(ending=ending):
                result = self.run_driver(ending, keep_raw='1')
                self.assertEqual(result.returncode, code, result.stderr)
                self.assertTrue(self.raw.exists())

    def test_failure_and_cancellation_preserve_partial_results(self):
        for ending, code in [('exit 7', 7), ('kill -TERM $$', 143), ('kill -INT $$', 130)]:
            with self.subTest(ending=ending):
                result = self.run_driver(ending)
                self.assertEqual(result.returncode, code, result.stderr)
                self.assertTrue(self.raw.exists())
                self.assertTrue((self.result / '10_1_jmeter.log').exists())

    def test_missing_or_incomplete_aggregation_preserves_samples(self):
        for csv in ['', 'concurrency,n,total_count\n', 'concurrency,n,total_count\n10,1,0\n',
                    'concurrency,n,total_count\n10,2,42\n', 'concurrency,n,total_count\n20,1,42\n']:
            with self.subTest(csv=csv):
                (self.result / 'cells.csv').write_text(csv)
                result = self.run_driver()
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertTrue(self.raw.exists())

    def test_active_registered_writer_prevents_pruning(self):
        writer = subprocess.Popen(['sleep', '30'])
        try:
            result = self.run_driver(f'cleanup_track_writer {writer.pid}\ncleanup_result_complete')
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertTrue(self.raw.exists())
        finally:
            writer.terminate()
            writer.wait(timeout=5)

    @unittest.skipUnless(shutil.which('lsof'), 'requires open-file inspection')
    def test_unregistered_open_file_prevents_pruning(self):
        with self.raw.open('a'):
            result = self.run_driver()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(self.raw.exists())

    def test_foreign_newer_run_survives_sweep_abort(self):
        foreign = self.root / 'results_foreign'
        foreign.mkdir()
        foreign_raw = foreign / 'other.jtl'
        foreign_raw.write_text('another operator\n')
        env = dict(os.environ, TMPDIR=str(self.root))
        env.pop('BENCH_SWEEP_STATE', None)
        result = subprocess.run(
            ['/bin/bash', '-c', 'set -euo pipefail\nsource "$1"\ncleanup_sweep_init\n'
             'touch "$2"\nkill -TERM $$', 'sweep', str(LIB), str(foreign)],
            env=env, capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 143, result.stderr)
        self.assertEqual(foreign_raw.read_text(), 'another operator\n')
        self.assertTrue(self.raw.exists())

    def test_unowned_path_and_symlink_are_never_cleanup_targets(self):
        link = self.root / 'alias'
        link.symlink_to(self.result, target_is_directory=True)
        result = self.run_driver(
            'cleanup_result_complete\n'
            'printf "foreign owner\\n" > "$2/.cleanup-owner"\n'
            'cleanup_raw_samples "$2"\ncleanup_raw_samples "${2%/*}/alias"')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(self.raw.exists())

    def test_symlinked_aggregate_is_not_cleanup_evidence(self):
        aggregate = self.result / 'cells.csv'
        external = self.root / 'external.csv'
        aggregate.rename(external)
        aggregate.symlink_to(external)
        result = self.run_driver()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(self.raw.exists())


if __name__ == '__main__':
    unittest.main()
