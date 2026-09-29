#!/usr/bin/env python3
"""Prepare dependencies, then compare two refs in alternating fresh-process builds."""
import argparse
import json
from pathlib import Path
import random
import subprocess
import sys
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--repo', type=Path, required=True)
parser.add_argument('--baseline', required=True)
parser.add_argument('--candidate', default='HEAD')
parser.add_argument('--gradle-user-home', type=Path, required=True)
parser.add_argument('--results', type=Path, required=True)
parser.add_argument('--java-home', required=True)
parser.add_argument('--pairs', type=int, default=5)
args = parser.parse_args()
if args.pairs < 5:
    parser.error('Final comparisons require at least five pairs')
repo = args.repo.resolve()
results = args.results.resolve()
results.mkdir(parents=True, exist_ok=True)
script = Path(__file__).with_name('run.py')
refs = {label: subprocess.check_output(['git', '-C', str(repo), 'rev-parse', f'{ref}^{{commit}}'], text=True).strip()
        for label, ref in [('baseline', args.baseline), ('candidate', args.candidate)]}
common = [sys.executable, str(script), '--gradle-user-home', str(args.gradle_user_home.resolve()),
          '--results', str(results), '--java-home', args.java_home, '--allow-clean']
rng = random.Random(837)
schedule = []
for _ in range(args.pairs):
    pair = list(refs)
    rng.shuffle(pair)
    schedule.extend(pair)
(results / 'comparison.json').write_text(json.dumps({'refs': refs, 'seed': 837, 'schedule': schedule}, indent=2))
with tempfile.TemporaryDirectory(prefix='yona-compile-refs-') as temporary:
    checkouts = {}
    try:
        for label, sha in refs.items():
            checkout = Path(temporary) / label
            subprocess.run(['git', '-C', str(repo), 'worktree', 'add', '--detach', str(checkout), sha], check=True)
            checkouts[label] = checkout
            subprocess.run(common + ['--repo', str(checkout), '--variant', f'prepare-{label}', '--prepare'], check=True)
        for label in schedule:
            subprocess.run(common + ['--repo', str(checkouts[label]), '--variant', label], check=True)
    finally:
        for checkout in checkouts.values():
            # Only temporary worktrees created above; measured reports live outside them.
            subprocess.run(['git', '-C', str(repo), 'worktree', 'remove', '--force', str(checkout)], check=True)
subprocess.run([sys.executable, str(script.with_name('summarize.py')), '--results', str(results)], check=True)
