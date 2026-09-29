#!/usr/bin/env python3
"""Summarize saved runs without invoking Gradle or changing raw evidence.

  python3 scripts/perf/kotlin-compile/summarize.py --results /tmp/yona-kotlin-perf-results

Writes analysis.json, analysis-runs.csv, and analysis.csv (long-form statistics).
Only runner-valid, report-proven full compiles enter statistics. Preparation is
excluded; JFR/profile/info/disabled-incremental runs have separate diagnostic
strata. IQR uses inclusive linearly interpolated quartiles; singleton IQR is null.
"""

import argparse
from collections import Counter, defaultdict
import csv
import json
from pathlib import Path
import re
import statistics

TASKS = (':compileKotlin', ':compileTestKotlin')
HEADER = re.compile(r"^Task '([^']+)' finished in ([\d.]+) s$", re.M)
PHASES = {
    'analysis': 'Compiler code analysis',
    'code_generation': 'Compiler code generation',
    'backend': 'Compiler backend',
    'ir_lowering': 'Compiler IR lowering',
    'translation_to_ir': 'Compiler translation to IR',
    'initialization': 'Compiler initialization time',
    'sources_compilation': 'Sources compilation round',
    'run_compilation': 'Run compilation',
    'before_task_action': 'Spent time before task action',
}


def compiler_reports(directory):
    tasks, errors = {}, []
    for path in sorted((directory / 'reports/kotlin-build').glob('*.txt')):
        text = path.read_text(errors='replace')
        headers = list(HEADER.finditer(text))
        for index, header in enumerate(headers):
            task = header[1]
            if task not in TASKS:
                continue
            if task in tasks:
                errors.append(f'Duplicate report for {task}')
                continue
            end = headers[index + 1].start() if index + 1 < len(headers) else len(text)
            section = text[header.end():end]
            time_section = re.search(r'^Time metrics:\n(.*?)(?=^\S|\Z)', section, re.M | re.S)
            times = {name: float(value) for name, value in re.findall(
                r'^\s+([^\n:]+): ([\d.]+) s$', time_section[1] if time_section else '', re.M)}
            reasons = re.findall(r'^\s*Non-incremental compilation will be performed: (.+)$', section, re.M)
            lines = re.search(r'^\s+Number of lines analyzed: (\d+)$', section, re.M)
            classpath = re.search(r'^\s+Number of classpath entries: (\d+)$', section, re.M)
            tasks[task] = dict(report=str(path), reported_task_seconds=float(header[2]),
                               full_compile_evidence=reasons, time_metrics_seconds=times,
                               analyzed_lines=int(lines[1]) if lines else None,
                               classpath_entries=int(classpath[1]) if classpath else None)
    return tasks, errors


def cpu_seconds(value):
    days, separator, clock = value.partition('-')
    total = int(days) * 86400 if separator else 0
    parts = (clock if separator else value).split(':')
    if len(parts) not in (2, 3):
        return None
    try:
        for index, part in enumerate(reversed(parts)):
            total += float(part) * 60 ** index
        return total
    except ValueError:
        return None


def process_metrics(directory, record):
    groups = {'compiler': set(record.get('compiler_pids', [])),
              'gradle': set(record.get('gradle_pids', []))}
    groups['compiler_and_gradle'] = groups['compiler'] | groups['gradle']
    peaks, cpu = defaultdict(list), {}
    path = directory / 'processes.jsonl'
    if path.exists():
        with path.open() as stream:
            for line in stream:
                sample = json.loads(line)
                for group, pids in groups.items():
                    values = [p['rss_kib'] for p in sample['processes'] if p['pid'] in pids]
                    if values:
                        peaks[group].append(sum(values))
                for process in sample['processes']:
                    value = cpu_seconds(process.get('cpu_time', ''))
                    if value is not None:
                        key = (process['pid'], process['started'])
                        cpu[key] = max(value, cpu.get(key, 0))
    result = {}
    for group, pids in groups.items():
        result[group + '_simultaneous_peak_rss_kib'] = max(peaks[group], default=None)
        values = [value for (pid, _), value in cpu.items() if pid in pids]
        result[group + '_sampled_cpu_seconds'] = sum(values) if values else None
    return result


def heap_bytes(value, unit):
    return float(value) * 1024 ** ('BKMGT'.index(unit.upper()))


def gc_metrics(directory, record):
    processes = []
    # Rotated files are grouped per JVM before totals are calculated.
    files = defaultdict(list)
    for path in directory.glob('*-gc-*.log*'):
        match = re.fullmatch(r'(gradle|kotlin)-gc-(\d+)\.log(?:\.\d+)?', path.name)
        if match:
            files[(match[1], int(match[2]))].append(path)
    for (kind, pid), paths in sorted(files.items()):
        pauses, heaps = [], []
        recognized = False
        for path in paths:
            for line in path.read_text(errors='replace').splitlines():
                if re.search(r'\[gc\s*\]', line):
                    recognized = True
                    pause = re.search(r'GC\(\d+\) Pause .+ ([\d.]+)ms$', line)
                    if pause:
                        pauses.append(float(pause[1]))
                    heap = re.search(r'([\d.]+)([BKMGT])->([\d.]+)([BKMGT])\(([\d.]+)([BKMGT])\)', line)
                    if heap:
                        heaps.extend((heap_bytes(heap[1], heap[2]), heap_bytes(heap[3], heap[4])))
        processes.append(dict(kind=kind, pid=pid, files=[str(p) for p in paths],
                              pause_count=len(pauses) if recognized else None,
                              pause_ms=sum(pauses) if recognized else None,
                              largest_pause_ms=max(pauses, default=None),
                              observed_heap_peak_bytes=max(heaps, default=None)))
    totals = {}
    for group, pids in [('compiler', set(record.get('compiler_pids', []))),
                        ('compiler_and_gradle', set(record.get('compiler_pids', [])) |
                         set(record.get('gradle_pids', [])))]:
        selected = [p for p in processes if p['pid'] in pids]
        for metric in ('pause_count', 'pause_ms'):
            values = [p[metric] for p in selected]
            totals[f'{group}_gc_{metric}'] = sum(values) if values and None not in values else None
        heaps = [p['observed_heap_peak_bytes'] for p in selected if p['observed_heap_peak_bytes'] is not None]
        # This is the largest individual JVM heap observation, never a simultaneous sum.
        totals[f'{group}_largest_observed_jvm_heap_bytes'] = max(heaps, default=None)
    return processes, totals


def warning_metrics(path):
    if not path.exists():
        return dict(log_bytes=None, warning_headers=None, warning_unique_headers=None,
                    warning_unique_messages=None, warning_header_bytes=None), {}
    raw = path.read_bytes()
    headers = [line for line in raw.splitlines(keepends=True)
               if re.match(rb'^\s*(?:w:|warning:|WARNING:)', line)]
    unique_headers = {line.decode('utf-8', errors='replace').strip() for line in headers}
    messages = [re.sub(r'^\s*(?:w:|warning:|WARNING:)\s*(?:file://\S+:\d+:\d+\s+)?', '',
                       line.decode('utf-8', errors='replace').strip()) for line in headers]
    categories = Counter()
    for message in messages:
        lowered = message.lower()
        diagnostic = re.match(r'\[([A-Z][A-Z_]+)\]', message)
        if diagnostic:
            category = diagnostic[1]
        elif 'deprecated' in lowered:
            category = 'deprecation (wording)'
        elif 'unchecked cast' in lowered:
            category = 'unchecked cast (wording)'
        elif 'unnecessary' in lowered:
            category = 'unnecessary operation (wording)'
        elif 'always' in lowered:
            category = 'constant/redundant expression (wording)'
        elif 'never used' in lowered or 'unused' in lowered:
            category = 'unused declaration (wording)'
        else:
            category = 'other/unclassified'
        categories[category] += 1
    return dict(log_bytes=len(raw), warning_headers=len(headers), warning_unique_headers=len(unique_headers),
                warning_unique_messages=len(set(messages)), warning_header_bytes=sum(map(len, headers))), dict(categories)


def analyze(path):
    record = json.loads(path.read_text())
    if 'strategy' not in record:
        return None  # Separate runtime probes are not compilation samples.
    directory = path.parent
    reports, errors = compiler_reports(directory)
    diagnostics = [key for key in ('jfr', 'profile') if record.get(key)]
    command = record.get('command', [])
    if '--info' in command:
        diagnostics.append('info')
    if '-Pkotlin.incremental=false' in command:
        diagnostics.append('disabled-incremental')
    reasons = list(record.get('invalid_reasons', [])) + errors
    for task in TASKS:
        if not reports.get(task, {}).get('full_compile_evidence'):
            reasons.append(f'{task}: report does not prove a full nonincremental compile')
    kind = 'preparation' if record.get('prepare') else ('diagnostic:' + ','.join(diagnostics) if diagnostics else 'measurement')
    valid = bool(record.get('valid')) and not reasons and kind != 'preparation'
    metrics = {key: record.get(key) for key in ('compileKotlin_seconds', 'compileTestKotlin_seconds',
                                               'compile_sum_seconds', 'wall_seconds')}
    for task in TASKS:
        data = reports.get(task, {})
        for field, label in PHASES.items():
            metrics[f'{task[1:]}_{field}_seconds'] = data.get('time_metrics_seconds', {}).get(label)
        metrics[task[1:] + '_analyzed_lines'] = data.get('analyzed_lines')
        metrics[task[1:] + '_classpath_entries'] = data.get('classpath_entries')
    for phase in PHASES:
        values = [metrics[f'{task[1:]}_{phase}_seconds'] for task in TASKS]
        metrics['compile_sum_' + phase + '_seconds'] = sum(values) if None not in values else None
    metrics.update(process_metrics(directory, record))
    gc, totals = gc_metrics(directory, record)
    metrics.update(totals)
    warnings, categories = warning_metrics(directory / 'build.log')
    metrics.update(warnings)
    return dict(variant=record.get('variant'), run=record.get('run', directory.name),
                kind=kind, valid=valid, runner_valid=record.get('valid'), invalid_reasons=reasons,
                source=str(path), sha=record.get('sha'), strategy=record.get('strategy'),
                heap=record.get('heap'), warnings=record.get('warnings'),
                metrics=metrics, compiler_reports=reports, gc_processes=gc,
                warning_categories=categories)


def describe(values):
    quartiles = statistics.quantiles(values, n=4, method='inclusive') if len(values) >= 2 else None
    return dict(n=len(values), median=statistics.median(values), minimum=min(values), maximum=max(values),
                range=max(values) - min(values), iqr=quartiles[2] - quartiles[0] if quartiles else None)


def write_csv(path, rows, fields):
    with path.open('w', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def self_check():
    assert cpu_seconds('02:03.50') == 123.5
    assert cpu_seconds('1-02:03:04') == 93784
    assert describe([1, 2, 3]) == dict(n=3, median=2, minimum=1, maximum=3, range=2, iqr=1)
    assert describe([5])['iqr'] is None
    print('Summary arithmetic checks passed; no build was run.')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--results', type=Path)
    parser.add_argument('--self-check', action='store_true')
    args = parser.parse_args()
    if args.self_check:
        self_check()
        return
    if not args.results or not args.results.is_dir():
        parser.error('--results must name an existing results directory')
    runs = [run for path in sorted(args.results.glob('*/*/result.json')) if (run := analyze(path)) is not None]
    groups = defaultdict(list)
    for run in runs:
        if run['valid']:
            for metric, value in run['metrics'].items():
                if isinstance(value, (int, float)) and not isinstance(value, bool):
                    groups[(run['variant'], run['kind'], run['strategy'], run['heap'], run['warnings'], metric)].append(value)
    summary = [dict(zip(('variant', 'kind', 'strategy', 'heap', 'warnings', 'metric'), key), **describe(values))
               for key, values in sorted(groups.items())]
    output = dict(runs=runs, statistics=summary, limitations=[
        'Task report sections only; build-wide metric totals are not added again.',
        'Nested compiler metrics overlap: code generation includes backend and IR lowering.',
        'Full compile proof requires the per-task Non-incremental compilation will be performed report line.',
        'CPU totals are maximum sampled cumulative CPU times per process, a lower bound at sampler shutdown.',
        'RSS is the largest simultaneous sum in one ps sample; it is not true peak or unique physical memory.',
        'GC totals are whole-JVM logged stop-the-world Pause summary lines, not task-only times or concurrent GC.',
        'Heap maxima are observed before/after GC, not continuous high-water marks; combined heap is not summed.',
        'Warning bytes count header lines only; complete output bytes are reported separately.',
        'Wording-based warning categories are heuristic, not compiler diagnostic IDs.',
        'No allocation totals or per-file compiler timing are inferred; missing metrics remain null.',
        'Median/range/IQR use only valid runs within the exact variant, diagnostic kind, strategy, heap and warning group.',
        'IQR uses inclusive interpolated quartiles and is null for a singleton.',
    ])
    (args.results / 'analysis.json').write_text(json.dumps(output, indent=2) + '\n')
    write_csv(args.results / 'analysis.csv', summary,
              ['variant', 'kind', 'strategy', 'heap', 'warnings', 'metric', 'n', 'median', 'minimum', 'maximum', 'range', 'iqr'])
    columns = sorted({metric for run in runs for metric in run['metrics']})
    flat = [dict(variant=run['variant'], run=run['run'], kind=run['kind'], valid=run['valid'],
                 invalid_reasons='; '.join(run['invalid_reasons']), **run['metrics']) for run in runs]
    write_csv(args.results / 'analysis-runs.csv', flat, ['variant', 'run', 'kind', 'valid', 'invalid_reasons'] + columns)
    print(f"Analyzed {len(runs)} runs; {sum(run['valid'] for run in runs)} report-proven valid runs. "
          f"Wrote analysis.json, analysis.csv, analysis-runs.csv to {args.results}")


if __name__ == '__main__':
    main()
