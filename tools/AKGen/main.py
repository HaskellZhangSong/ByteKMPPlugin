from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
from utils.config import RootConfig, parse_config, g_config, get_config
from utils.file import *
from utils.cmd_args import *
from convert.generate import convert_module_paths, KotlinFileOutput, write_kotlin_file_output
import logging

def main(argv: list[str] | None = None) -> int:
    args: CliArgs = parse_args(argv)
    logging.basicConfig(level=logging.DEBUG, format='[%(levelname)s] %(message)s')
    if args.debug:
        logging.debug(args)
    cfg: RootConfig = parse_config(args.config)
    for proxyConfig in cfg.proxy.values():
        for m in proxyConfig.ohos.packages:
            module_name = m.name
            arkts_source_dirs = m.arkts_source_dir
            logging.info(f"Processing module: {module_name}")
            outputs: list[KotlinFileOutput] = convert_module_paths(args, proxyConfig, arkts_source_dirs, module_name)
            for output in outputs:
                write_kotlin_file_output(args, output)
    return 0


"""
poetry run python main.py \
--config projects/python/arkts-ffi-kt/test/config.json
"""
"""
poetry run python main.py \
--config /Users/WorkSpace/bd/ByteKMPPlugin/demo/ohos_ffi_demo/config.json
"""
if __name__ == "__main__":
    raise SystemExit(main())
