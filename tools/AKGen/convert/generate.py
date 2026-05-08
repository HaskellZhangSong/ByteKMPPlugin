from dataclasses import dataclass

import lang.kt.kt_decls as kt
import utils.file as fs
from pathlib import Path
from collections import defaultdict
from convert.decls import convert_class, ClassInfo, TypeAliasResult, convert_interface
import convert.env as env
import lang.ts.decls as ts
from convert.types import KtNode
from lang.kt.pretty import pretty_decl
from lang.ts.parser_ts import parse_ts_file
import utils.config as conf
from utils.cmd_args import CliArgs
import lang.kt.kt_parser as ktp
import convert.pretty as ppr
import os
import logging

common_import = [
    "import com.bd.kmp.infra.ffi.OhosFFIContext",
    "import com.bd.kmp.infra.ffi.types.ArkObjectSafe",
    "import com.bd.kmp.ohos_ffi.annotation.ArkTsExportCustomTransform",
    "import com.bd.kmp.ohos_ffi.annotation.KotlinExportClass",
    "import com.bd.kmp.ohos_ffi.transform.ArkTsExportCustomTransformer",
    "import com.bd.kmp.infra.ffi.napi.*",
    "import kotlinx.cinterop.ExperimentalForeignApi",
    "import platform.ohos.image.napi_env",
    "import platform.ohos.napi.napi_value"
]

@dataclass
class KotlinFileOutput:
    output_path: Path
    package: str
    imports: list[str]
    decls: list[str]
    add_experimental_foreign_api: bool

def write_kotlin_file_output(args: CliArgs, kfo: KotlinFileOutput) -> None:
    pkg = [] if kfo.package == '' else [f'package {kfo.package.replace("\"", "")}']
    imports = kfo.imports
    decls = kfo.decls
    src: KtNode = pkg + imports + decls
    if kfo.add_experimental_foreign_api:
        src = ["@file:OptIn(ExperimentalForeignApi::class)"] + src
    pretty_src = ppr.pretty_print_kt_node(src)
    if args.verbose:
        logging.info(f"write file to: {kfo.output_path}")
    os.makedirs(os.path.dirname(kfo.output_path), exist_ok=True)
    with open(kfo.output_path, "w") as f:
        f.write(pretty_src)

# TODO: analyze imports in ClassInfo and import only necessary packages
# get the types from prop decls and method decls, get the class names from those types,
# then get the package names from env.kt_id_class_map
def _get_class_imports(ci: list[ClassInfo]) -> list[str]:
    imports: set[str] = set()
    for c in ci:
        if c.package != '':
            imports.add(f'import {c.package}')
    return list(imports)

def _get_custom_imports(ci: ClassInfo) -> list[str]:
    imports: list[str] = []
    for imp in ci.custom_import_list:
        imports.append(f'import {imp}')
    return imports

def _build_env(convert_env: env.Env,
               ts_sf: list[ts.SourceFile],
               kt_sf: list[kt.SourceFile]):
    # Build env
    arkts_decls: list[ts.Decl] = []
    for sf in ts_sf:
        arkts_decls += sf.decls
    env.build_arkts_deco_rename_map(convert_env, arkts_decls)
    env.build_arkts_class_name_to_class_decl_map(convert_env, arkts_decls)
    env.build_kt_id_class_map(convert_env, kt_sf)
    env.build_kt_shared_class_field_name_map(convert_env)
    env.build_kt_serialized_name_map(convert_env)
    env.build_kt_class_rename_map(convert_env, arkts_decls)
    env.build_ts_enum_type_set(convert_env, arkts_decls)
    env.build_real_obj_and_proxy_obj_name_map(convert_env, arkts_decls)


def convert_module_paths(args: CliArgs, proxy_config: conf.ProxyConfig, paths: list[str], module_name: str = '') :
    convert_env = env.Env(proxy_config)
    # 从配置中获取项目路径
    config = conf.get_config()

    # 把三端路径转换为绝对路径
    # TODO: 其实更好的修改方式是在解析配置文件时就把路径转换为绝对路径，现在先在这里转换，后续可以优化
    config_file_dir_path = args.config.parent.expanduser().resolve()

    if not Path(config.kmp_dir).is_absolute():
       config.kmp_dir = (config_file_dir_path / Path(config.kmp_dir)).expanduser().resolve().__str__()

    if not Path(config.ohos_dir).is_absolute():
       config.ohos_dir = (config_file_dir_path / Path(config.ohos_dir)).expanduser().resolve().__str__()

    if not Path(config.ios_dir).is_absolute():
       config.ios_dir = (config_file_dir_path / Path(config.ios_dir)).expanduser().resolve().__str__()

    arkts_project_path = Path(config.ohos_dir).expanduser().resolve()
    dirs: list[Path] = [arkts_project_path / Path(p) for p in paths]

    src_files: list[Path] = [
        f
        for d in dirs
        for f in fs.get_files(d, exts=fs.ARKTS_FILE_EXTS)
    ]
    arkts_asts: list[ts.SourceFile] = [parse_ts_file(f, module_name) for f in src_files]
    android_srcs = []
    kmp_prefix_dir = Path(config.kmp_dir)
    if proxy_config.share_type == 'proxy':
        android_srcs = proxy_config.android.src_dir
    elif proxy_config.share_type == 'copy':
        android_srcs = proxy_config.common.entity_dir.common_main
    elif proxy_config.share_type == 'basic':
        android_srcs = []

    android_src_file_paths = [
        f
        for d in android_srcs
        for f in fs.get_files(kmp_prefix_dir / Path(d), exts=fs.KOTLIN_FILE_EXTS)
    ]

    kotlin_asts: list[kt.SourceFile] = [ktp.parse_kotlin_file(f) for f in android_src_file_paths]

    # 从配置中获取 enable_share_id 值
    _build_env(convert_env, arkts_asts, kotlin_asts)
    class_info: list[ClassInfo] = []
    kt_res: list[KotlinFileOutput] = []
    kotlin_project_path = config.kmp_dir
    kotlin_common_output_path = proxy_config.common.kotlin_out
    kotlin_output_path        = proxy_config.ohos.common.kotlin_output
    kotlin_ios_output_path = proxy_config.ios.common.kotlin_out

    kotlin_path_prefix = ((Path(kotlin_project_path) / Path(kotlin_output_path))
                          .expanduser().resolve())

    kotlin_ios_path_prefix = ((Path(kotlin_project_path) / Path(kotlin_ios_output_path))
                              .expanduser().resolve())

    kotlin_expect_class_decls_output_path = ((Path(kotlin_project_path) / Path(kotlin_common_output_path))
                                             .expanduser().resolve())

    mode = conf.get_output_mode()
    config_imports = []
    if proxy_config.ohos.common.imports:
        config_imports = [f'import {imp}' for imp in proxy_config.ohos.common.imports]

    match mode:
        case conf.OutputMode.SingleFile:
            class_decls = []
            expect_class = []
            ios_class = []
            for sf in arkts_asts:
                for d in sf.decls:
                    if (isinstance(d, ts.ClassDecl) and d.has_export_decorator()
                            and env.get_kt_proxy_class_and_package_name(convert_env, d) is not None):
                        nodes, expect_class, ios_empty_class, alias, ci = convert_class(convert_env, d, sf.module)
                        class_info.append(ci)
                        class_decls += nodes
                        ios_class += ios_empty_class
                    elif (isinstance(d, ts.InterfaceDecl) and d.has_export_decorator()
                            and env.get_kt_proxy_class_and_package_name(convert_env, d) is not None):
                        nodes, expect_class, ios_empty_class, alias, ci = convert_interface(convert_env, d, sf.module)
                        class_info.append(ci)
                        class_decls += nodes
                        ios_class += ios_empty_class
            imports = []
            # combine custom_import_list
            for ci in class_info:
                custom_imports = _get_custom_imports(ci)
                imports += custom_imports
            imports = list(set(imports))
            # check ClassInfo, all packages should be same
            package_name_set = set()
            for i in class_info:
                package_name_set.add(i.package)

            kfo = KotlinFileOutput(kotlin_path_prefix,
                                   package_name_set.pop(),
                                   common_import + config_imports + imports,
                                   class_decls,
                                   True)
            expect_class_output = None
            if expect_class:
                expect_class_output = KotlinFileOutput(kotlin_expect_class_decls_output_path,
                                                package_name_set.pop(),
                                                imports,
                                                expect_class,
                                                False) if expect_class is not [] else None

            kt_res.append(kfo)
            if expect_class_output is not None:
                kt_res.append(expect_class_output)

            if ios_class:
                ios_class_output = KotlinFileOutput(kotlin_ios_path_prefix,
                                                package_name_set.pop(),
                                                imports,
                                                ios_class,
                                                False)
                kt_res.append(ios_class_output)
            return kt_res

        case conf.OutputMode.Project:
            # need to get import from convert_env.cache.kt_name_class_map
            all_class_packages = set([f'import {p}.*' for c, p in convert_env.cache.kt_class_rename_map.values()])
            all_import_list = list(all_class_packages)
            all_import_list.sort()
            all_type_aliases_pkg_pair: list[TypeAliasResult] = []
            # per package file
            for sf in arkts_asts:
                for d in sf.decls:
                    if (isinstance(d, ts.ClassDecl) and d.has_export_decorator()
                            and env.get_kt_proxy_class_and_package_name(convert_env, d) is not None):
                        nodes, expect_class, ios_empty_class, alias, ci = convert_class(convert_env, d, sf.module)
                        all_type_aliases_pkg_pair.append((ci.package, alias))
                        custom_imports = _get_custom_imports(ci)
                        class_info.append(ci)
                        # need to add all imports later
                        package_file_path = Path(ci.package.replace('.', '/')) / Path(f'{ci.name}.kt')
                        kt_file_output_path = (kotlin_path_prefix / package_file_path).expanduser().resolve()
                        # remove import {ci.package} from imports
                        inter_class_import_list = list(all_class_packages - {f'import {ci.package}.*'})
                        inter_class_import_list.sort()
                        kfo = KotlinFileOutput(kt_file_output_path,
                                               ci.package,
                                               inter_class_import_list + common_import + config_imports + custom_imports,
                                               nodes,
                                               True)
                        expect_class_output = None
                        if expect_class:
                            expect_class_output = KotlinFileOutput((kotlin_expect_class_decls_output_path / package_file_path).expanduser().resolve(),
                                                                   ci.package,
                                                                   inter_class_import_list + custom_imports,
                                                                   expect_class,
                                                                   False)
                        kt_res.append(kfo)
                        if expect_class_output is not None:
                            kt_res.append(expect_class_output)

                        if ios_empty_class != []:
                            ios_empty_class_output = KotlinFileOutput((kotlin_ios_path_prefix / package_file_path).expanduser().resolve(),
                                                                    ci.package,
                                                                    inter_class_import_list + custom_imports,
                                                                    ios_empty_class,
                                                                    False)
                            kt_res.append(ios_empty_class_output)
                    elif (isinstance(d, ts.InterfaceDecl) and d.has_export_decorator()
                            and env.get_kt_proxy_class_and_package_name(convert_env, d) is not None):
                        nodes, expect_class, ios_empty_class, alias, ci = convert_interface(convert_env, d, sf.module)
                        all_type_aliases_pkg_pair.append((ci.package, alias))
                        custom_imports = _get_custom_imports(ci)
                        class_info.append(ci)
                        # need to add all imports later
                        package_file_path = Path(ci.package.replace('.', '/')) / Path(f'{ci.name}.kt')
                        kt_file_output_path = (kotlin_path_prefix / package_file_path).expanduser().resolve()
                        # remove import {ci.package} from imports
                        inter_class_import_list = list(all_class_packages - {f'import {ci.package}.*'})
                        inter_class_import_list.sort()
                        kfo = KotlinFileOutput(kt_file_output_path,
                                               ci.package,
                                               inter_class_import_list + common_import + config_imports + custom_imports,
                                               nodes,
                                               True)
                        expect_class_output = None
                        if expect_class:
                            expect_class_output = KotlinFileOutput((kotlin_expect_class_decls_output_path / package_file_path).expanduser().resolve(),
                                                                   ci.package,
                                                                   inter_class_import_list + custom_imports,
                                                                   expect_class,
                                                                   False)
                        kt_res.append(kfo)
                        if expect_class_output is not None:
                            kt_res.append(expect_class_output)

                        if ios_empty_class != []:
                            ios_empty_class_output = KotlinFileOutput((kotlin_ios_path_prefix / package_file_path).expanduser().resolve(),
                                                                    ci.package,
                                                                    inter_class_import_list + custom_imports,
                                                                    ios_empty_class,
                                                                    False)
                            kt_res.append(ios_empty_class_output)
            if all_type_aliases_pkg_pair != []:
                package_indexed_typealias = defaultdict(list)
                for key, value in all_type_aliases_pkg_pair:
                    package_indexed_typealias[key]+=value
                pkg_to_typealias_dict = dict(package_indexed_typealias)
                for pkg, type_aliases in pkg_to_typealias_dict.items():
                    package_path = Path(pkg.replace('.', '/'))
                    file_name = ''.join(list(map (lambda x: x.title(), pkg.split('.'))))
                    type_alias_file_output = KotlinFileOutput((Path(kotlin_project_path) / Path(proxy_config.android.kotlin_out) / package_path / Path(f'{file_name}TypeAliases.kt')).expanduser().resolve(),
                                                            pkg,
                                                            list(all_import_list),
                                                            type_aliases,
                                                            False)
                    kt_res.append(type_alias_file_output)
            return kt_res