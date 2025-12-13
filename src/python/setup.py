"""
K Language Python Bridge

This package provides Python integration for the K constraint programming language.
It enables K models to call Python functions during CEGAR refinement.

Installation:
    pip install k-python-bridge

    Or from source:
    pip install -e klang/src/python

Requirements:
    - py4j: For Java-Python communication
    - debugpy: For debugging (optional)

Usage:
    # Start the bridge server
    python -m k_python_bridge

    # Or with debugging enabled
    python -m k_python_bridge --debug
"""

from setuptools import setup, find_packages

setup(
    name='k-python-bridge',
    version='0.1.0',
    description='Python bridge for K constraint programming language',
    author='K Language Team',
    author_email='klang@jpl.nasa.gov',
    url='https://github.com/nasa-jpl/klang',
    py_modules=['k_python_bridge'],
    install_requires=[
        'py4j>=0.10.9',
    ],
    extras_require={
        'debug': ['debugpy>=1.6.0'],
        'dev': ['pytest', 'numpy'],
    },
    entry_points={
        'console_scripts': [
            'k-python-bridge=k_python_bridge:main',
        ],
    },
    classifiers=[
        'Development Status :: 3 - Alpha',
        'Intended Audience :: Developers',
        'License :: OSI Approved :: Apache Software License',
        'Programming Language :: Python :: 3',
        'Programming Language :: Python :: 3.8',
        'Programming Language :: Python :: 3.9',
        'Programming Language :: Python :: 3.10',
        'Programming Language :: Python :: 3.11',
        'Topic :: Scientific/Engineering',
    ],
    python_requires='>=3.8',
)

