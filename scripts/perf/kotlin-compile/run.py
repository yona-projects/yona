#!/usr/bin/env python3
"""Cold-process, nonincremental Kotlin compilation; Python stdlib only.

Example:
  python3 scripts/perf/kotlin-compile/run.py --repo /path/to/experiment \
    --gradle-user-home /tmp/yona-gradle --results /tmp/yona-results \
    --variant baseline --repeat 3 --strategy in-process --heap 2048m --allow-clean

Only build/, .gradle/, and .kotlin/ in the selected Git checkout are deleted.
Require --allow-clean or an existing .kotlin-compile-perf-checkout marker file.
--prepare permits network access but NEVER contributes performance samples.
--profile (Gradle HTML), --jfr (compiler JVM), and --info are diagnostics: do not
mix their overhead with ordinary runs. All runs record JVM GC logs and ps data.
Task times include compiler task setup, not just frontend/backend execution.
ps CPU is the OS lifetime-average percentage; RSS is sampled, not true peak.
JFR/GC data is retained raw; allocation, GC pause totals, and per-file compiler
phase time are not inferred by this script. Downloads/cached/skipped/failed
compiles and absent/reused compiler PIDs invalidate a performance sample.
"""

import argparse
import csv
import datetime as dt
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import threading
import time
import uuid

COMPILES = (':compileKotlin', ':compileTestKotlin')
DAEMON = 'org.jetbrains.kotlin.daemon.KotlinCompileDaemon'


def process_table():
    text = subprocess.check_output(
        ['ps', '-axo', 'pid=,ppid=,pcpu=,rss=,time=,lstart=,command=', '-ww'], text=True)
    result = {}
    for line in text.splitlines():
        fields = line.split(None, 10)
        if len(fields) != 11:
            continue
        pid, ppid, cpu, rss, cpu_time, *rest = fields
        result[int(pid)] = dict(pid=int(pid), ppid=int(ppid), cpu_percent=float(cpu),
                               rss_kib=int(rss), cpu_time=cpu_time,
                               started=' '.join(rest[:5]), args=rest[5])
    return result


def identity(process):
    return (process['pid'], process['started'])


def select_processes(table, root_pid, marker, observed):
    selected = {p['pid'] for p in table.values()
                if p['pid'] == root_pid or marker in p['args']
                or identity(p) in observed}
    while True:
        children = {p['pid'] for p in table.values() if p['ppid'] in selected}
        if children <= selected:
            break
        selected |= children
    return [table[pid] for pid in sorted(selected)]


def sample_processes(root_pid, marker, stop, observed, errors, target, started):
    try:
        with target.open('w') as stream:
            while True:
                processes = select_processes(process_table(), root_pid, marker, observed)
                for process in processes:
                    observed[identity(process)] = process
                stream.write(json.dumps(dict(seconds=time.monotonic() - started,
                                             processes=processes)) + '\n')
                stream.flush()
                if stop.wait(0.5):
                    break
    except Exception as error:
        errors.append('Process sampler: ' + repr(error))


def stop_owned_daemons(observed, marker):
    stopped = []
    for process in observed.values():
        if DAEMON not in process['args'] or marker not in process['args']:
            continue
        current = process_table().get(process['pid'])
        if not current or identity(current) != identity(process):
            continue
        os.kill(process['pid'], signal.SIGTERM)
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            current = process_table().get(process['pid'])
            if not current or identity(current) != identity(process):
                break
            time.sleep(0.2)
        else:
            # Never signal a PID that has been recycled or lacks our run marker.
            current = process_table().get(process['pid'])
            if current and identity(current) == identity(process) and marker in current['args']:
                os.kill(process['pid'], signal.SIGKILL)
        stopped.append(process['pid'])
    return stopped


def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args], text=True)


def read_events(path):
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text().splitlines() if line]


def aggregate(root):
    fields = ['variant', 'run', 'strategy', 'heap', 'warnings', 'valid', 'returncode',
              'compileKotlin_seconds', 'compileTestKotlin_seconds', 'compile_sum_seconds',
              'wall_seconds', 'compiler_peak_rss_kib', 'compiler_pids', 'invalid_reasons']
    rows = []
    for file in sorted(root.glob('*/*/result.json')):
        record = json.loads(file.read_text())
        if 'strategy' not in record:
            continue  # Separate runtime probes are not compilation samples.
        row = {key: record.get(key, '') for key in fields}
        row['compiler_pids'] = ';'.join(map(str, record.get('compiler_pids', [])))
        row['invalid_reasons'] = '; '.join(record.get('invalid_reasons', []))
        rows.append(row)
    for name, selected in [('runs.csv', rows), ('valid-compiles.csv', [r for r in rows if r['valid']])]:
        with (root / name).open('w', newline='') as stream:
            writer = csv.DictWriter(stream, fieldnames=fields)
            writer.writeheader()
            writer.writerows(selected)


def run_one(args, number, previous_pids):
    token = uuid.uuid4().hex
    label = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S') + f'-{number:02d}-{token[:8]}'
    directory = args.results / args.variant / label
    directory.mkdir(parents=True)
    marker = f'-Dyona.kotlin.perf.run={token}'
    task_file = directory / 'tasks.jsonl'
    record = dict(variant=args.variant, run=label, strategy=args.strategy, heap=args.heap,
                  warnings=args.warnings, prepare=args.prepare, profile=args.profile, jfr=args.jfr,
                  repo=str(args.repo), gradle_user_home=str(args.gradle_user_home),
                  java_home=args.java_home, command=[], returncode=None, valid=False,
                  invalid_reasons=[], compiler_pids=[])
    record.update(load_average_before=os.getloadavg(), visible_cpu_count=os.cpu_count(),
                  gc=args.gc, compiler_args=args.compiler_arg, tee=args.tee,
                  disable_incremental=args.disable_incremental)
    observed, errors = {}, []
    stop = threading.Event()
    sampler = None
    process = None
    started = time.monotonic()
    try:
        record['sha'] = git(args.repo, 'rev-parse', 'HEAD').strip()
        (directory / 'changes.diff').write_text(git(args.repo, 'diff', '--binary', 'HEAD'))
        (directory / 'git-status.txt').write_text(git(args.repo, 'status', '--porcelain=v1'))
        shutil.copy2(__file__, directory / 'run.py')
        shutil.copy2(Path(__file__).with_name('tasks.init.gradle'), directory / 'tasks.init.gradle')
        for name in ('build', '.gradle', '.kotlin'):
            target = args.repo / name
            if target.is_symlink():
                raise ValueError(f'Refusing to delete symlink: {target}')
            if target.exists():
                if not target.is_dir():
                    raise ValueError(f'Refusing to delete non-directory: {target}')
                shutil.rmtree(target)
        record['preexisting_kotlin_daemons'] = [p for p in process_table().values() if DAEMON in p['args']]
        (directory / 'daemon-registry').mkdir()
        gradle_jvm = [f'-Xmx{args.heap}', '-Dfile.encoding=UTF-8', marker,
                      f'-Xlog:gc*:file={directory}/gradle-gc-%p.log:time,uptime,level,tags',
                      f'-Dyona.perf.tasks={task_file}',
                      f'-Dkotlin.daemon.options=runFilesPath={directory}/daemon-registry']
        daemon_jvm = [f'-Xmx{args.heap}', marker,
                      f'-Xlog:gc*:file={directory}/kotlin-gc-%p.log:time,uptime,level,tags']
        if args.gc:
            (gradle_jvm if args.strategy == 'in-process' else daemon_jvm).append(f'-XX:+Use{args.gc}GC')
        if args.compiler_arg:
            gradle_jvm.append('-Dyona.perf.compilerArgs=' + '|'.join(args.compiler_arg))
        if args.test_compiler_arg:
            gradle_jvm.append('-Dyona.perf.testCompilerArgs=' + '|'.join(args.test_compiler_arg))
        if args.warnings == 'suppress':
            gradle_jvm.append('-Dyona.perf.suppressWarnings=true')
        if args.jfr:
            compiler_jvm = gradle_jvm if args.strategy == 'in-process' else daemon_jvm
            compiler_jvm.append(f'-XX:StartFlightRecording=filename={directory}/compiler.jfr,settings=profile,dumponexit=true')
        command = [str(args.repo / 'gradlew'), '--no-daemon', '--no-build-cache',
                   '--no-configuration-cache', '--max-workers=2', '--console=plain',
                   '--init-script', str(directory / 'tasks.init.gradle'),
                   '-Pkotlin.build.report.output=file',
                   f'-Pkotlin.compiler.execution.strategy={args.strategy}',
                   '-Pkotlin.daemon.useFallbackStrategy=false',
                   '-Dorg.gradle.jvmargs=' + ' '.join(gradle_jvm),
                   '-Pkotlin.daemon.jvmargs=' + ' '.join(daemon_jvm)]
        if args.disable_incremental:
            command.append('-Pkotlin.incremental=false')
        if not args.prepare:
            command.append('--offline')
        if args.profile:
            command.append('--profile')
        if args.info:
            command.append('--info')
        command += ['-P' + value.removeprefix('-P') for value in args.property]
        command += ['clean', 'bootJar', 'testClasses']
        environment = os.environ.copy()
        environment['GRADLE_USER_HOME'] = str(args.gradle_user_home)
        if args.java_home:
            environment['JAVA_HOME'] = args.java_home
            environment['PATH'] = str(Path(args.java_home) / 'bin') + os.pathsep + environment.get('PATH', '')
        record['environment'] = {key: environment.get(key) for key in
                                 ('JAVA_HOME', 'GRADLE_USER_HOME', 'JAVA_OPTS', 'GRADLE_OPTS',
                                  'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS')}
        record['command'] = command
        (directory / 'command.json').write_text(json.dumps(record, indent=2) + '\n')
        started = time.monotonic()
        with (directory / 'build.log').open('wb') as log:
            process = subprocess.Popen(command, cwd=args.repo, env=environment,
                                       stdout=subprocess.PIPE if args.tee else log, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            record['launcher_pid'] = process.pid
            sampler = threading.Thread(target=sample_processes,
                                       args=(process.pid, marker, stop, observed, errors,
                                             directory / 'processes.jsonl', started), daemon=True)
            sampler.start()
            if args.tee:
                while chunk := process.stdout.read1(65536):
                    log.write(chunk)
                    log.flush()
                    sys.stdout.buffer.write(chunk)
                    sys.stdout.buffer.flush()
            record['returncode'] = process.wait()
            record['wall_seconds'] = time.monotonic() - started
    except BaseException as error:
        errors.append(repr(error))
        if process and process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
            record['returncode'] = process.returncode
    finally:
        stop.set()
        if sampler:
            sampler.join()
        try:
            record['terminated_owned_kotlin_daemons'] = stop_owned_daemons(observed, marker)
        except Exception as error:
            errors.append('Daemon cleanup: ' + repr(error))
        try:
            if (args.repo / 'build/reports').is_dir():
                shutil.copytree(args.repo / 'build/reports', directory / 'reports')
            record['classfiles'] = {}
            for source_set in ('main', 'test'):
                files = list((args.repo / 'build/classes/kotlin' / source_set).rglob('*.class'))
                record['classfiles'][source_set] = {
                    'count': len(files), 'bytes': sum(path.stat().st_size for path in files)}
            events = read_events(task_file)
            record['tasks'] = [event for event in events if event['event'] == 'finish']
            record['gradle_pids'] = sorted({event['gradle_pid'] for event in events})
            compilers = [p for p in observed.values() if
                         (args.strategy == 'daemon' and DAEMON in p['args'] and marker in p['args']) or
                         (args.strategy == 'in-process' and p['pid'] in record['gradle_pids'])]
            record['compiler_processes'] = compilers
            record['compiler_pids'] = sorted({p['pid'] for p in compilers})
            reasons = record['invalid_reasons']
            if args.prepare:
                reasons.append('Online preparation, not a measurement')
            if record['returncode'] != 0:
                reasons.append('Gradle did not succeed')
            if not compilers:
                reasons.append('Actual compiler JVM not observed')
            if previous_pids.intersection(record['compiler_pids']):
                reasons.append('Compiler PID reused across runs')
            previous_pids.update(record['compiler_pids'])
            for process_info in compilers:
                if any(process_info['pid'] == p['pid'] for p in record.get('preexisting_kotlin_daemons', [])):
                    reasons.append('Compiler daemon existed before this run')
            durations = []
            for task_name in COMPILES:
                matches = [task for task in record['tasks'] if task['task'] == task_name]
                if len(matches) != 1 or not matches[0]['executed'] or not matches[0]['did_work'] or any(
                        matches[0][key] for key in ('skipped', 'up_to_date', 'no_source', 'skip_message', 'failure')):
                    reasons.append(f'{task_name} was not a successful full executed compile')
                else:
                    seconds = matches[0]['seconds']
                    record[task_name[1:] + '_seconds'] = seconds
                    durations.append(seconds)
            if len(durations) == 2:
                record['compile_sum_seconds'] = sum(durations)
            log_text = (directory / 'build.log').read_text(errors='replace') if (directory / 'build.log').exists() else ''
            if re.search(r'(?im)^\s*Downloading\b', log_text):
                reasons.append('Download observed in build log')
            samples_path = directory / 'processes.jsonl'
            if samples_path.exists():
                peaks = [p['rss_kib'] for line in samples_path.read_text().splitlines()
                         for p in json.loads(line)['processes'] if p['pid'] in record['compiler_pids']]
                record['compiler_peak_rss_kib'] = max(peaks, default=None)
            reasons.extend(errors)
            record['valid'] = not reasons
        except Exception as error:
            record['invalid_reasons'].extend(errors + ['Result processing: ' + repr(error)])
        record.setdefault('wall_seconds', time.monotonic() - started)
        (directory / 'result.json').write_text(json.dumps(record, indent=2) + '\n')
        aggregate(args.results)
    print(f"{directory}: valid={record['valid']} exit={record['returncode']} "
          f"compile_sum={record.get('compile_sum_seconds', 'unavailable')}", flush=True)
    return record['returncode'] == 0 and (record['valid'] or args.prepare)


def self_check():
    # One tiny invariant check; no Gradle invocation or filesystem deletion.
    table = {1: dict(pid=1, ppid=0, started='a', args='launcher'),
             2: dict(pid=2, ppid=1, started='b', args='java'),
             3: dict(pid=3, ppid=0, started='c', args='java marker'),
             4: dict(pid=4, ppid=0, started='d', args='unrelated')}
    assert [p['pid'] for p in select_processes(table, 1, 'marker', {})] == [1, 2, 3]
    assert [p['pid'] for p in select_processes(table, 9, 'absent', {(2, 'b'): table[2]})] == [2]
    print('Process ownership checks passed; no build was run.')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--repo', type=Path)
    parser.add_argument('--gradle-user-home', type=Path)
    parser.add_argument('--results', type=Path)
    parser.add_argument('--variant')
    parser.add_argument('--repeat', type=int, default=1)
    parser.add_argument('--strategy', choices=['in-process', 'daemon'], default='in-process')
    parser.add_argument('--heap', default='2048m')
    parser.add_argument('--warnings', choices=['default', 'suppress'], default='default')
    parser.add_argument('--java-home')
    parser.add_argument('--allow-clean', action='store_true')
    parser.add_argument('--prepare', action='store_true')
    parser.add_argument('--tee', action='store_true')
    parser.add_argument('--profile', action='store_true')
    parser.add_argument('--jfr', action='store_true')
    parser.add_argument('--info', action='store_true')
    parser.add_argument('--gc', choices=['G1', 'Parallel'])
    parser.add_argument('--compiler-arg', action='append', default=[],
                        help='Verified compiler argument, e.g. --compiler-arg=-Xbackend-threads=2')
    parser.add_argument('--test-compiler-arg', action='append', default=[],
                        help='Verified compiler argument applied only to compileTestKotlin')
    parser.add_argument('--disable-incremental', action='store_true',
                        help='Separate diagnostic condition; default preserves repository incremental settings')
    parser.add_argument('--property', action='append', default=[], help='Additional Gradle project property KEY=VALUE')
    parser.add_argument('--summarize', action='store_true', help='Rebuild CSVs from --results without compiling')
    parser.add_argument('--self-check', action='store_true')
    args = parser.parse_args()
    if args.self_check:
        self_check()
        return 0
    if not args.results:
        parser.error('--results is required')
    args.results = args.results.expanduser().resolve()
    if args.summarize:
        aggregate(args.results)
        return 0
    if not all((args.repo, args.gradle_user_home, args.variant)):
        parser.error('--repo, --gradle-user-home, and --variant are required')
    args.repo = args.repo.expanduser().resolve()
    args.gradle_user_home = args.gradle_user_home.expanduser().resolve()
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', args.variant):
        parser.error('--variant must be a single simple filename')
    if args.repeat < 1 or not re.fullmatch(r'[1-9][0-9]*[mMgG]', args.heap):
        parser.error('--repeat must be positive; --heap must be e.g. 4096m or 4g')
    if args.results.is_relative_to(args.repo) or args.gradle_user_home.is_relative_to(args.repo):
        parser.error('Results and dedicated Gradle home must be outside the checkout')
    if args.gradle_user_home == (Path.home() / '.gradle').resolve():
        parser.error('Refusing the normal ~/.gradle; use a dedicated Gradle user home')
    if re.search(r'[\s,:]', str(args.results)):
        parser.error('Results path cannot contain whitespace, commas, or colons (JVM diagnostic filenames)')
    if Path(git(args.repo, 'rev-parse', '--show-toplevel').strip()).resolve() != args.repo:
        parser.error('--repo must be the Git checkout root')
    marker = args.repo / '.kotlin-compile-perf-checkout'
    if not args.allow_clean and not (marker.is_file() and not marker.is_symlink()):
        parser.error('Deleting build/.gradle/.kotlin requires --allow-clean or the checkout marker')
    if not (args.repo / 'gradlew').is_file():
        parser.error('The selected checkout has no Gradle wrapper')
    protected = {'kotlin.incremental', 'kotlin.compiler.execution.strategy',
                 'kotlin.daemon.jvmargs', 'kotlin.daemon.useFallbackStrategy',
                 'kotlin.build.report.output', 'org.gradle.jvmargs', 'org.gradle.daemon',
                 'org.gradle.configuration-cache', 'org.gradle.caching', 'org.gradle.workers.max'}
    for value in args.property:
        key, separator, _ = value.removeprefix('-P').partition('=')
        if not separator or not key or key in protected:
            parser.error(f'Property must be KEY=VALUE and cannot override measurement controls: {value}')
    if args.java_home:
        args.java_home = str(Path(args.java_home).expanduser().resolve())
        if not (Path(args.java_home) / 'bin/java').is_file():
            parser.error('--java-home has no bin/java')
    args.results.mkdir(parents=True, exist_ok=True)
    args.gradle_user_home.mkdir(parents=True, exist_ok=True)
    previous = {pid for file in args.results.glob('*/*/result.json')
                for pid in json.loads(file.read_text()).get('compiler_pids', [])}
    for number in range(1, (1 if args.prepare else args.repeat) + 1):
        if not run_one(args, number, previous):
            return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
