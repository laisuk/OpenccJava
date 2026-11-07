"""Compare a git revision's parser against the working tree, without dependencies.
Run from the repository root: python openccjava/bench/run-minidict.py [baseline-ref]
Requires Python 3, git, and a JDK 9+ (compiles with --release 8).
Uses the bundled snapshot; timings exclude file I/O and initial UTF-8 decoding.
Cold = first parse in each fresh JVM, not process launch. Stream mode includes decoding.
"""
import pathlib
import statistics
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
BUILD = ROOT / 'openccjava/build/minidict-bench'
BUILD.mkdir(parents=True, exist_ok=True)
revision = sys.argv[1] if len(sys.argv) > 1 else 'HEAD'
source = subprocess.check_output(['git', 'show', revision + ':openccjava/src/main/java/openccjava/MiniDictJson.java'], cwd=ROOT).decode('utf-8')
(BUILD / 'BaselineMiniDictJson.java').write_text(source.replace('MiniDictJson', 'BaselineMiniDictJson'), encoding='utf-8')
sources = list((ROOT / 'openccjava/src/main/java').rglob('*.java'))
# Write a javac argument file to stay within Windows command-line limits.
args = ['--release', '8', '-encoding', 'UTF-8', '-d', str(BUILD)] + [str(p) for p in sources] + [str(BUILD / 'BaselineMiniDictJson.java'), str(ROOT / 'openccjava/bench/MiniDictJsonBench.java')]
(BUILD / 'javac.args').write_text('\n'.join('"' + a.replace('\\', '/') + '"' for a in args), encoding='utf-8')
subprocess.run(['javac', '@' + str(BUILD / 'javac.args')], cwd=ROOT, check=True)
base = ['java', '-Xms256m', '-Xmx256m', '-cp', str(BUILD), 'openccjava.MiniDictJsonBench', str(ROOT / 'openccjava/src/main/resources/dicts/dictionary_maxlength.json')]
subprocess.run(base + ['verify'], check=True)
subprocess.run(['java', '-version'], check=True)
for mode in ['string', 'stream']:
    values = {'before': [], 'after': []}
    for fork in range(7):
        for variant in (['before', 'after'] if fork % 2 == 0 else ['after', 'before']):
            result = subprocess.check_output(base + [variant, mode], text=True)
            values[variant].append([float(x) for x in result.split()])
    for variant, rows in values.items():
        medians = [statistics.median(col) for col in zip(*rows)]
        print(mode, variant, 'cold_ms=%.3f warm_ms=%.3f allocated_bytes=%.0f' % tuple(medians), flush=True)
        print('  raw [cold_ms, warm_median_ms, allocation_median_bytes]:', rows, flush=True)
