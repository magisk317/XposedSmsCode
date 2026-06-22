#!/usr/bin/env python3
"""
Wrapper script that delegates to magisk-ci-toolkit.
"""
import os
import sys
import subprocess

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TOOLKIT_SCRIPT = os.path.join(SCRIPT_DIR, '..', '_toolkit', 'security', 'manage_dependency_forces.py')

# Set default project for XposedSmsCode
os.environ.setdefault('MAGISK_DEFAULT_PROJECT', ':app')

sys.exit(subprocess.call([sys.executable, TOOLKIT_SCRIPT] + sys.argv[1:]))
