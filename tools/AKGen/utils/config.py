from __future__ import annotations

import json
import sys
from dataclasses import dataclass, field
from pathlib import Path
from enum import Enum

@dataclass(frozen=True, slots=True)
class CommonConfig:
    kotlin_output: str
    imports: list[str] = field(default_factory=list)


@dataclass(frozen=True, slots=True)
class EntityDirConfig:
    common_main: list[str] = field(default_factory=list)
    ohos_arm64_main: list[str] = field(default_factory=list)


@dataclass(frozen=True, slots=True)
class CommonPlatformConfig:
    kotlin_out: str = ""
    entity_dir: EntityDirConfig | None = None


@dataclass(frozen=True, slots=True)
class AndroidConfig:
    src_dir: list[str] = field(default_factory=list)
    kotlin_out: str = ""

@dataclass(frozen=True, slots=True)
class IOSConfig:
    common: CommonPlatformConfig


@dataclass(frozen=True, slots=True)
class PackageDetails:
    arkts_source_dir: list[str]
    
@dataclass(frozen=True, slots=True)
class PackageConfig:
    name: str
    arkts_source_dir: list[str]

@dataclass(frozen=True, slots=True)
class OhosConfig:
    common: CommonConfig
    packages: list[PackageConfig]


@dataclass(frozen=True, slots=True)
class ProxyConfig:
    share_type: str
    enable_share_id: bool
    enable_ios_empty_impl: bool
    enable_json_name: bool
    enable_different_nullability: bool
    suffix: str
    default_package: str
    common: CommonPlatformConfig
    android: AndroidConfig
    ios: IOSConfig
    ohos: OhosConfig

@dataclass(slots=True)
class RootConfig:
    kmp_dir: str
    ohos_dir: str
    ios_dir: str
    proxy: dict[str, ProxyConfig]

g_config: RootConfig = None


def set_config(cfg: RootConfig) -> None:
    global g_config
    g_config = cfg


def get_config() -> RootConfig:
    if g_config is None:
        raise RuntimeError("g_config is not initialized; call parse_config() first")
    return g_config

def _expect_dict(obj: object, where: str) -> dict:
    if not isinstance(obj, dict):
        raise ValueError(f"Expected object at {where}, got {type(obj).__name__}")
    return obj


def _expect_list(obj: object, where: str) -> list:
    if not isinstance(obj, list):
        raise ValueError(f"Expected array at {where}, got {type(obj).__name__}")
    return obj


def _expect_str(obj: object, where: str) -> str:
    if not isinstance(obj, str):
        raise ValueError(f"Expected string at {where}, got {type(obj).__name__}")
    return obj


def _expect_bool(obj: object, where: str) -> bool:
    if not isinstance(obj, bool):
        raise ValueError(f"Expected boolean at {where}, got {type(obj).__name__}")
    return obj

class OutputMode(Enum):
    SingleFile = 0
    Project = 1

def get_output_mode() -> OutputMode:
    # 获取第一个代理配置的 kotlin 输出路径
    cfg = get_config()
    if not cfg.proxy:
        raise RuntimeError("没有配置代理信息")
    
    first_proxy = next(iter(cfg.proxy.values()))
    p: str = first_proxy.ohos.common.kotlin_output
    if p.endswith(".kt"):
        return OutputMode.SingleFile
    return OutputMode.Project

def _parse_format(root: dict) -> RootConfig:
    """解析新格式配置文件"""
    kmp_dir = _expect_str(root.get("kmp.dir"), "$.kmp.dir")
    ohos_dir = _expect_str(root.get("ohos.dir"), "$.ohos.dir")
    ios_dir = _expect_str(root.get("ios.dir"), "$.ios.dir")
    
    proxy_obj = _expect_dict(root.get("proxy"), "$.proxy")
    proxies: dict[str, ProxyConfig] = {}
        
    for proxy_name, proxy_val in proxy_obj.items():
        proxy_dict = _expect_dict(proxy_val, f"$.proxy[{proxy_name!r}]")
                
        # shareType 是必填字段，只能是 "proxy" 、 "copy"、"none" 三选一
        share_type_raw = proxy_dict.get("shareType")
        if share_type_raw is None:
            raise ValueError(f"配置文件格式错误：$.proxy[{proxy_name!r}] 缺少必需字段 shareType")
        
        share_type = _expect_str(share_type_raw, f"$.proxy[{proxy_name!r}].shareType")
        if share_type not in ("proxy", "copy", "basic"):
            raise ValueError(f"配置文件格式错误：$.proxy[{proxy_name!r}].shareType 必须是 'proxy' 、 'copy' 或 'basic'，当前值为 '{share_type}'")

        # enableShareId、enableIosEmptyImpl、enableJsonName 仅当 shareType 为 "proxy" 时才有效
        if share_type == "proxy":
            # enableShareId 是可选字段，默认值为 false
            enable_share_id_value = proxy_dict.get("enableShareId")
            if enable_share_id_value is not None:
                enable_share_id = _expect_bool(enable_share_id_value, f"$.proxy[{proxy_name!r}].enableShareId")
            else:
                enable_share_id = False

            # enableIosEmptyImpl 是可选字段，默认为 False
            enable_ios_empty_impl_value = proxy_dict.get("enableIosEmptyImpl")
            if enable_ios_empty_impl_value is not None and enable_share_id == True:
                enable_ios_empty_impl = _expect_bool(enable_ios_empty_impl_value, f"$.proxy[{proxy_name!r}].enableIosEmptyImpl")
            else:
                enable_ios_empty_impl = False

            # enableJsonName 是可选字段，默认值为 false
            enable_json_name_value = proxy_dict.get("enableJsonName")
            if enable_json_name_value is not None:
                enable_json_name = _expect_bool(enable_json_name_value, f"$.proxy[{proxy_name!r}].enableJsonName")
            else:
                enable_json_name = False
        else:
            # 当 shareType 不为 "proxy" 时，这些字段无效，使用默认值
            enable_share_id = False
            enable_ios_empty_impl = False
            enable_json_name = False

        # enableDifferentNullability 是可选字段，默认值为 false
        enable_different_nullability_value = proxy_dict.get("enableDifferentNullability")
        if enable_different_nullability_value is not None:
            enable_different_nullability = _expect_bool(
                enable_different_nullability_value,
                f"$.proxy[{proxy_name!r}].enableDifferentNullability",
            )
        else:
            enable_different_nullability = False

        # suffix 是可选字段，默认值为 "Proxy"
        suffix_value = proxy_dict.get("suffix")
        if suffix_value is not None:
            suffix = _expect_str(suffix_value, f"$.proxy[{proxy_name!r}].suffix")
        else:
            suffix = "Proxy"
            
        # defaultPackage 是可选字段，默认值为 ""
        default_package_value = proxy_dict.get("defaultPackage")
        if default_package_value is not None:
            default_package = _expect_str(default_package_value, f"$.proxy[{proxy_name!r}].defaultPackage")
        else:
            default_package = ""
        
        # 解析 common 配置
        common_obj = _expect_dict(proxy_dict.get("common"), f"$.proxy[{proxy_name!r}].common")
        
        # kotlinOut 是可选字段，仅当 shareType 为 "proxy" 时有效，否则使用默认值 ""
        kotlin_out_data = common_obj.get("kotlinOut")
        if kotlin_out_data is not None and share_type == "proxy":
            common_kotlin_out = _expect_str(kotlin_out_data, f"$.proxy[{proxy_name!r}].common.kotlinOut")
        else:
            common_kotlin_out = ""
        
        # entityDir 是可选字段，仅当 shareType 为 "copy" 时有效，否则使用默认值 None
        entity_dir_data = common_obj.get("entityDir")
        entity_dir = None
        if entity_dir_data is not None and share_type == "copy":
            entity_dir_obj = _expect_dict(entity_dir_data, f"$.proxy[{proxy_name!r}].common.entityDir")
            
            # 解析 commonMain
            common_main_data = entity_dir_obj.get("commonMain")
            if common_main_data is not None:
                common_main = [_expect_str(x, f"$.proxy[{proxy_name!r}].common.entityDir.commonMain[*]") for x in _expect_list(common_main_data, f"$.proxy[{proxy_name!r}].common.entityDir.commonMain")]
                # 验证所有 commonMain 路径必须包含 "commonMain"
                for path in common_main:
                    if "commonMain" not in path:
                        raise ValueError(f"配置文件格式错误：$.proxy[{proxy_name!r}].common.entityDir.commonMain 路径 '{path}' 必须包含 'commonMain'")
            else:
                common_main = []
            
            # 解析 ohosArm64Main
            ohos_arm64_main_data = entity_dir_obj.get("ohosArm64Main")
            if ohos_arm64_main_data is not None:
                ohos_arm64_main = [_expect_str(x, f"$.proxy[{proxy_name!r}].common.entityDir.ohosArm64Main[*]") for x in _expect_list(ohos_arm64_main_data, f"$.proxy[{proxy_name!r}].common.entityDir.ohosArm64Main")]
                # 验证所有 ohosArm64Main 路径必须包含 "ohosArm64Main"
                for path in ohos_arm64_main:
                    if "ohosArm64Main" not in path:
                        raise ValueError(f"配置文件格式错误：$.proxy[{proxy_name!r}].common.entityDir.ohosArm64Main 路径 '{path}' 必须包含 'ohosArm64Main'")
            else:
                ohos_arm64_main = []
            
            entity_dir = EntityDirConfig(common_main=common_main, ohos_arm64_main=ohos_arm64_main)

        proxy_common_config = CommonPlatformConfig(kotlin_out=common_kotlin_out, entity_dir=entity_dir)

        # 解析 ios 配置（可选）
        ios_data = proxy_dict.get("ios")
        if ios_data is not None:
            ios_obj = _expect_dict(ios_data, f"$.proxy[{proxy_name!r}].ios")
            ios_common_data = ios_obj.get("common")
            if ios_common_data is not None:
                ios_common_obj = _expect_dict(ios_common_data, f"$.proxy[{proxy_name!r}].ios.common")
                ios_common_kotlin_out = ios_common_obj.get("kotlinOut")
                if ios_common_kotlin_out is not None:
                    ios_common_kotlin_out = _expect_str(ios_common_kotlin_out, f"$.proxy[{proxy_name!r}].ios.common.kotlinOut")
                    # 验证 iOS 的 kotlin_out 路径必须包含 "iosMain"
                    if "iosMain" not in ios_common_kotlin_out:
                        raise ValueError(f"配置文件格式错误：$.proxy[{proxy_name!r}].ios.common.kotlinOut 路径 '{ios_common_kotlin_out}' 必须包含 'iosMain'")
                ios_common_config = CommonPlatformConfig(kotlin_out=ios_common_kotlin_out)
            else:
                ios_common_config = CommonPlatformConfig(kotlin_out=None)
            ios_config = IOSConfig(common=ios_common_config)
        else:
            # 如果没有 ios 配置，创建默认配置
            ios_config = IOSConfig(common=CommonPlatformConfig(kotlin_out=None))

        # 解析 android 配置（仅当 share_type 为 "proxy" 时有效，且为可选字段）
        android_config = AndroidConfig(src_dir=[], kotlin_out="")
        if share_type == "proxy":
            android_data = proxy_dict.get("android")
            if android_data is not None:
                android_obj = _expect_dict(android_data, f"$.proxy[{proxy_name!r}].android")
                src_dirs = [_expect_str(x, f"$.proxy[{proxy_name!r}].android.srcDir[*]") for x in _expect_list(android_obj.get("srcDir"), f"$.proxy[{proxy_name!r}].android.srcDir")]
                kotlin_out = _expect_str(android_obj.get("kotlinOut"), f"$.proxy[{proxy_name!r}].android.kotlinOut")
                # 验证所有路径必须包含 "androidMain"
                if "androidMain" not in kotlin_out:
                    raise ValueError(f"配置文件格式错误：$.proxy[{proxy_name!r}].android.kotlinOut 路径 '{kotlin_out}' 必须包含 'androidMain'")
                android_config = AndroidConfig(src_dir=src_dirs, kotlin_out=kotlin_out)

        ohos_obj = _expect_dict(proxy_dict.get("ohos"), f"$.proxy[{proxy_name!r}].ohos")

        # 解析 ohos.common
        common_obj = _expect_dict(ohos_obj.get("common"), f"$.proxy[{proxy_name!r}].ohos.common")
        # kotlin_output 是必填字段，且必须包含 "ohosArm64Main"
        kotlin_output = _expect_str(common_obj.get("kotlinOut"), f"$.proxy[{proxy_name!r}].ohos.common.kotlinOut")
        if "ohosArm64Main" not in kotlin_output:
            raise ValueError(f"配置文件格式错误：$.proxy[{proxy_name!r}].ohos.common.kotlinOut 路径 '{kotlin_output}' 必须包含 'ohosArm64Main'")
        
        # imports 是可选字段，默认为空列表
        import_data = common_obj.get("import")
        if import_data is not None:
            imports = [_expect_str(x, f"$.proxy[{proxy_name!r}].ohos.common.import[*]") for x in _expect_list(import_data, f"$.proxy[{proxy_name!r}].ohos.common.import")]
        else:
            imports = []

        common_config = CommonConfig(kotlin_output=kotlin_output, imports=imports)

        # 解析 ohos.packages
        packages_data = ohos_obj.get("packages")
        if packages_data is not None:
            # 支持两种格式：数组格式和对象格式
            package_configs: list[PackageConfig] = []
            if isinstance(packages_data, dict):
                # 对象格式: {"pkg_name": {"arktsSrcDir": [...]}, ...}
                packages_dict = _expect_dict(packages_data, f"$.proxy[{proxy_name!r}].ohos.packages")
                for pkg_name, pkg_info in packages_dict.items():
                    pkg_info_dict = _expect_dict(pkg_info, f"$.proxy[{proxy_name!r}].ohos.packages[{pkg_name!r}]")
                    arkts_dirs_data = pkg_info_dict.get("arktsSrcDir")
                    if arkts_dirs_data is not None:
                        arkts_dirs = [_expect_str(x, f"$.proxy[{proxy_name!r}].ohos.packages[{pkg_name!r}].arktsSrcDir[*]") for x in _expect_list(arkts_dirs_data, f"$.proxy[{proxy_name!r}].ohos.packages[{pkg_name!r}].arktsSrcDir")]
                    else:
                        arkts_dirs = []
                    package_configs.append(PackageConfig(name=pkg_name, arkts_source_dir=arkts_dirs))
            else:
                raise ValueError(f"$.proxy[{proxy_name!r}].ohos.packages 必须是数组或对象，当前类型为 {type(packages_data).__name__}")
        else:
            package_configs = []
                
        ohos_config = OhosConfig(common=common_config, packages=package_configs)
        proxies[proxy_name] = ProxyConfig(
            share_type=share_type,
            enable_share_id=enable_share_id,
            enable_ios_empty_impl=enable_ios_empty_impl,
            enable_json_name=enable_json_name,
            enable_different_nullability=enable_different_nullability,
            suffix=suffix,
            default_package=default_package,
            common=proxy_common_config,
            android=android_config,
            ios=ios_config,
            ohos=ohos_config
        )

    return RootConfig(kmp_dir=kmp_dir, ohos_dir=ohos_dir, ios_dir=ios_dir, proxy=proxies)

def parse_config(config_path: str | Path) -> RootConfig:
    path = Path(config_path)
    data = json.loads(path.read_text(encoding="utf-8"))
    root = _expect_dict(data, "$")
    
    # 验证必需的顶层字段
    if "kmp.dir" not in root:
        raise ValueError("配置文件格式错误：缺少必需字段 kmp.dir")
    
    # 验证可选字段的默认值处理
    if "ohos.dir" not in root:
        root["ohos.dir"] = ""
    if "ios.dir" not in root:
        root["ios.dir"] = ""
    if "proxy" not in root:
        root["proxy"] = {}
    
    try:
        res = _parse_format(root)
    except ValueError as e:
        raise ValueError(f"配置解析失败: {e}")
    
    set_config(res)
    return res

def _print_config(cfg: RootConfig) -> None:
    """打印新格式配置信息"""
    print(f"kmp_dir: {cfg.kmp_dir}")
    print(f"ohos_dir: {cfg.ohos_dir}")
    print(f"ios_dir: {cfg.ios_dir}")
    
    print("proxies:")
    for proxy_name, proxy_cfg in cfg.proxy.items():
        print(f"  {proxy_name}:")
        print(f"    shareType: {proxy_cfg.share_type}")
        print(f"    enableShareId: {proxy_cfg.enable_share_id}")
        print(f"    enableIosEmptyImpl: {proxy_cfg.enable_ios_empty_impl}")
        print(f"    enableJsonName: {proxy_cfg.enable_json_name}")
        print(f"    enableDifferentNullability: {proxy_cfg.enable_different_nullability}")
        print(f"    suffix: {proxy_cfg.suffix}")
        print(f"    defaultPackage: {proxy_cfg.default_package}")
        
        print(f"    common.kotlinOut: {proxy_cfg.common.kotlin_out}")
        if proxy_cfg.common.entity_dir:
            print(f"    common.entityDir:")
            print(f"      commonMain: {proxy_cfg.common.entity_dir.common_main}")
            print(f"      ohosArm64Main: {proxy_cfg.common.entity_dir.ohos_arm64_main}")
        
        print(f"    ios.common.kotlinOut: {proxy_cfg.ios.common.kotlin_out or 'None'}")
        
        print(f"    android.srcDir:")
        for src_dir in proxy_cfg.android.src_dir:
            print(f"      - {src_dir}")
        
        print(f"    ohos.common.kotlinOutput: {proxy_cfg.ohos.common.kotlin_output}")
        print(f"    ohos.common.imports:")
        for imp in proxy_cfg.ohos.common.imports:
            print(f"      - {imp}")
        
        print(f"    ohos.packages:")
        for pkg_config in proxy_cfg.ohos.packages:
            print(f"      {pkg_config.name}:")
            for d in pkg_config.arkts_source_dir:
                print(f"        arktsSourceDir: {d}")

def main(argv: list[str]) -> int:
    config_path = Path(argv[1]) if len(argv) > 1 else Path("/Users/songzh/huawei_work/python/arkts-ffi-kt/test/config.json")
    cfg = parse_config(config_path)
    _print_config(cfg)
    return 0

if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
