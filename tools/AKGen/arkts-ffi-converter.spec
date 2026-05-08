# -*- mode: python ; coding: utf-8 -*-

import os
from PyInstaller.utils.hooks import collect_data_files

block_cipher = None

# 获取当前项目的路径
project_root = os.path.abspath('.')

# 定义需要包含的资源文件 (源路径, 目标文件夹)
# 注意：配置文件路径在运行时通过命令行参数指定
added_files = [
    ('test/config.json', 'test'),
    # 如果有其他测试用例或资源，可以在此添加
    ('test/cases', 'test/cases'),
]

# 自动收集 tree-sitter 相关的所有数据文件
datas = added_files
for pkg in ['tree_sitter', 'tree_sitter_kotlin', 'tree_sitter_typescript']:
    try:
        datas += collect_data_files(pkg)
    except:
        pass

a = Analysis(
    ['main.py'],  # 确保入口是 main.py
    pathex=[project_root],
    binaries=[],
    datas=datas,
    hiddenimports=[
        'convert', 'lang', 'utils',
        'lang.kt', 'lang.ts', 'convert.types',
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,   # 在单文件模式下，二进制文件、压缩包和数据都在这里
    a.zipfiles,
    a.datas,
    [],
    name='arkts-ffi-converter',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True, # 保持命令行界面
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)