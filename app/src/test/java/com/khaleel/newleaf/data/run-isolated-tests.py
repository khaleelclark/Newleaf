#!/usr/bin/env python3
"""Run pure Kotlin data tests from cached Gradle dependencies, independently of UI compilation."""
from pathlib import Path
import os
import subprocess
import tempfile
import sys

project = Path(__file__).resolve().parents[8]
cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
def jar(group, name, version):
    matches = list((cache / group / name / version).glob('*/*.jar'))
    if not matches:
        raise RuntimeError(f'Missing cached {group}:{name}:{version}; resolve Gradle dependencies first')
    return str(matches[0])
stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.2.10')
annotations = jar('org.jetbrains', 'annotations', '23.0.0')
runtime = os.pathsep.join([stdlib, annotations, jar('com.google.code.gson', 'gson', '2.11.0'), jar('junit', 'junit', '4.13.2'), jar('org.hamcrest', 'hamcrest-core', '1.3')])
compiler = os.pathsep.join([jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.2.10'), stdlib, annotations,
    jar('org.jetbrains.kotlin', 'kotlin-reflect', '2.2.10'), jar('org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330'),
    jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.10.2')])
source = project / 'app/src/main/java/com/khaleel/newleaf/data'
excluded = {'JournalRepository.kt', 'JournalDatabase.kt', 'DeviceCredentials.kt', 'JournalStorage.kt'}
sources = [str(p) for p in source.glob('*.kt') if p.name not in excluded]
sources += [str(Path(__file__).with_name(name)) for name in ['DomainTest.kt', 'ExportsTest.kt', 'SampleExports.kt']]
with tempfile.TemporaryDirectory(prefix='newleaf-domain-tests-') as output:
    subprocess.run(['java', '-cp', compiler, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-classpath', runtime, '-d', output, *sources], check=True)
    subprocess.run(['java', '-cp', output + os.pathsep + runtime, 'org.junit.runner.JUnitCore', 'com.khaleel.newleaf.data.DomainTest', 'com.khaleel.newleaf.data.ExportsTest'], check=True)

    if len(sys.argv) == 3 and sys.argv[1] == '--samples':
        subprocess.run(['java', '-cp', output + os.pathsep + runtime, 'com.khaleel.newleaf.data.SampleExports', sys.argv[2]], check=True)
