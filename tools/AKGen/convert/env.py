import logging

import lang.ts.decls as ts
import lang.kt.kt_decls as kt
from convert.check import check_kotlin_class_name, check_kotlin_package_name
from lang.ts.decls import get_ts_string_value
from utils.config import ProxyConfig
type class_name = str
type class_id = str
type field_name = str
type field_id = str
type pkg_name = str
type serialized_name = str

type NameClassMap = dict[class_name, tuple[kt.ClassDecl, pkg_name]]
type FieldNameMap = dict[tuple[class_id, field_id], kt.PropertyDecl]
type ClassRenameMap = dict[class_name, tuple[class_name, pkg_name]]
type KtClassIdToDeclMap = dict[class_id, tuple[kt.ClassDecl, pkg_name]]
type KtClassNameMap = dict[class_name, class_name]
type KtSerializedNameMap = dict[tuple[class_id, serialized_name], kt.PropertyDecl]
class ConvertCache:
    # arkts 源码中枚举类型集合
    arkts_enum_type_set: set[str] = set()
    # arkts 源码中类名到类声明的映射
    arkts_class_name_to_class_decl_map: dict[str, ts.ClassDecl] = {}
    # arkts 中类的类名到装饰器指定的类名和包名映射
    arkts_class_name_to_decorator_name_and_pkg_map: ClassRenameMap = {}
    # Kotlin类名到Kotlin类声明和包名的映射
    kt_name_to_decl_and_pkg_map: NameClassMap = {}
    # ShareClass ID到Kotlin类声明和包名的映射
    kt_id_class_map: KtClassIdToDeclMap = {}
    # 从ShareClass ID和ShareField ID到Kotlin属性声明的映射，key是(ShareClass ID, ShareField ID)，结果为Kotlin属性声明
    kt_share_class_field_id_to_prop_map: FieldNameMap = {}
    kt_serialized_name_map: KtSerializedNameMap = {}
    # 根据不同mode，从ArkTS类名到Kotlin类名和包名的映射
    kt_class_rename_map: ClassRenameMap = {}

    real_obj_and_proxy_obj_name_map: KtClassNameMap = {}  # 实体类名到代理类名

class Env:
    cache: ConvertCache = ConvertCache()
    def __init__(self, config: ProxyConfig):
        self.config = config

def get_suffix(convert_env: Env) -> str:
    return convert_env.config.suffix

def _check_and_handle_share_class_id(cache: ConvertCache, share_id: str, pkg: str, decl: kt.ClassDecl) -> None:
    """检查并处理ShareClass ID的重复情况"""
    if share_id in cache.kt_id_class_map:
        existing_decl, existing_pkg = cache.kt_id_class_map[share_id]
        # 只在同一个包名下检查重复
        if existing_pkg == pkg:
            raise ValueError("[Error 1004] "
                f"Duplicate ShareClass ID '{share_id}' found in package '{pkg}'. "
                f"Already defined in class '{existing_decl.name}', "
                f"cannot redefine in class '{decl.name}'"
            )

def build_kt_id_class_map(convert_env: Env, sfs : list[kt.SourceFile]) :
    default_pkg = convert_env.config.default_package
    if convert_env.config.share_type != "proxy":
        return
    cache = convert_env.cache
    for sf in sfs:
        decls = sf.declarations
        pkg = sf.package
        for decl in decls:
            if not isinstance(decl, kt.ClassDecl):
                continue
            if cache.kt_name_to_decl_and_pkg_map.get(decl.name) is None:
                cache.kt_name_to_decl_and_pkg_map[decl.name] = (decl, pkg)
            else:
                (class_decl, kt_pkg) = cache.kt_name_to_decl_and_pkg_map[decl.name]
                logging.warning(f"class name {pkg or default_pkg}.{decl.name} conflicts with {kt_pkg or default_pkg}.{class_decl.name}")
            if decl.annotations is None:
                continue
            for annotation in decl.annotations:
                if annotation.name == "ShareClass" and annotation.args:
                    arg = annotation.args[0]
                    share_id = kt.get_kt_string_value(arg)
                    _check_and_handle_share_class_id(cache, share_id, pkg, decl)
                    cache.kt_id_class_map[share_id] = (decl, pkg)

def _get_kt_class_rename_map_by_id(convert_env: Env, id: ts.StringValue, decl: ts.ClassDecl, used_ids: set[str]) -> None:
    cache = convert_env.cache
    if convert_env.config.enable_share_id == False:
        raise ValueError("[Error 1007] ShareId is not allowed for this class")

    id_val = id.value.replace('"', '').replace("'", '')
    # 检查 id 是否重复
    if id_val in used_ids:
        raise ValueError(
            f"[Error 1004] Duplicate id '{id_val}' found in class '{decl.name}'. "
            f"Each class must have a unique id."
        )
    used_ids.add(id_val)
    
    if id_val in cache.kt_id_class_map:
        kt_class_decl, pkg_name = cache.kt_id_class_map[id_val]
        cache.kt_class_rename_map[decl.name] = (kt_class_decl.name + convert_env.config.suffix, pkg_name)
    else:
        logging.warning(f"[Warn 1008] ShareId {id_val} not found")

def _get_kt_class_rename_map_by_name(convert_env: Env, package: ts.StringValue, name: ts.TsValue | None, decl: ts.ClassDecl, pkg_class_names: dict[str, set[class_name]]) -> None:
    cache = convert_env.cache
    pkg_val = package.value.replace('"', '').replace("'", '')
    check_kotlin_package_name(pkg_val)
    # 初始化该包名的类名集合
    if pkg_val not in pkg_class_names:
        pkg_class_names[pkg_val] = set()
    if isinstance(name, ts.StringValue):
        name_val = name.value.replace('"', '').replace("'", '')
        check_kotlin_class_name(name_val)
        # 检查同包名下类名是否重复
        if name_val in pkg_class_names[pkg_val]:
            raise ValueError(
                f"[Error 1003] Duplicate class name '{name_val}' found in package '{pkg_val}'. "
                f"Class names must be unique within the same package."
            )
        pkg_class_names[pkg_val].add(name_val)
        cache.kt_class_rename_map[decl.name] = (name_val, pkg_val)
    else:
        proxy_name = decl.name + convert_env.config.suffix
        check_kotlin_class_name(proxy_name)
        # 检查同包名下类名是否重复
        if proxy_name in pkg_class_names[pkg_val]:
            raise ValueError(
                f"[Error 1003] Duplicate class name '{proxy_name}' found in package '{pkg_val}'. "
                f"Class names must be unique within the same package."
            )
        pkg_class_names[pkg_val].add(proxy_name)
        cache.kt_class_rename_map[decl.name] = (proxy_name, pkg_val)
def _get_kt_class_rename_map_by_default(convert_env: Env, decl: ts.ClassDecl, pkg_class_names: dict[str, set[class_name]]) -> None:
    cache = convert_env.cache
    proxy_name = decl.name + convert_env.config.suffix
    check_kotlin_class_name(proxy_name)
    pkg_val = convert_env.config.default_package
    if pkg_val == "":
        raise ValueError("[Error 1002] Default package name cannot be empty")

    # 检查同包名下类名是否重复
    if proxy_name in pkg_class_names.get(pkg_val, set()):
        raise ValueError(
            f"[Error 1003] Duplicate class name '{proxy_name}' found in package '{pkg_val}'. "
            f"Class names must be unique within the same package."
        )
    if pkg_class_names.get(pkg_val, None) is None:
        pkg_class_names[pkg_val] = set()
    pkg_class_names[pkg_val].add(proxy_name)
    cache.kt_class_rename_map[decl.name] = (proxy_name, pkg_val)

def build_kt_class_rename_map(convert_env: Env, arkts_decls : list[ts.Decl]) :
    # 用于跟踪每个包名下已使用的类名
    pkg_class_names : dict[str, set[class_name]] = {}
    # 用于跟踪已使用的 id
    used_ids : set[str] = set()
    for decl in arkts_decls:
        if isinstance(decl, ts.ClassDecl) and decl.has_export_decorator():
            id = decl.get_decorator_key("id")
            name = decl.get_decorator_key("name")
            package = decl.get_decorator_key("package")
            if id is not None and ((name is not None) or (package is not None)):
                raise ValueError("[Error 1009] id and name/package are not allowed to be specified at the same time")

            if isinstance(id, ts.StringValue) and convert_env.config.share_type == "proxy":
                _get_kt_class_rename_map_by_id(convert_env, id, decl, used_ids)
                continue

            if isinstance(package, ts.StringValue):
                _get_kt_class_rename_map_by_name(convert_env, package, name, decl, pkg_class_names)
                continue

            _get_kt_class_rename_map_by_default(convert_env, decl, pkg_class_names)

def build_ts_enum_type_set(convert_env: Env, decls : list[ts.Decl]):
    cache = convert_env.cache
    for decl in decls:
        if isinstance(decl, ts.EnumDecl):
            cache.arkts_enum_type_set.add(decl.name)

def build_kt_shared_class_field_name_map(convert_env: Env):
    if convert_env.config.share_type != "proxy":
        return
    cache = convert_env.cache
    for key, (decl, _) in cache.kt_id_class_map.items():
        # 用于跟踪当前类下已使用的 ShareField ID
        used_field_ids : set[str] = set()
        if decl.members is None:
            continue
        for member in decl.members:
            if isinstance(member, kt.PropertyDecl) and member.annotations:
                for annotation in member.annotations:
                    if annotation.name == "ShareField":
                        arg = annotation.args[0]
                        field_id = kt.get_kt_string_value(arg)
                        # 检查同一个类下 ShareField ID 是否重复
                        if field_id in used_field_ids:
                            raise ValueError(
                                f"[Error 2003] Duplicate ShareField ID '{field_id}' found in class '{decl.name}'. "
                                f"ShareField IDs must be unique within the same class."
                            )
                        used_field_ids.add(field_id)
                        cache.kt_share_class_field_id_to_prop_map[(key, field_id)] = member

def build_kt_serialized_name_map(convert_env: Env):
    if convert_env.config.share_type != "proxy":
        return
    cache = convert_env.cache
    for key, (decl, _) in cache.kt_id_class_map.items():
        # 用于跟踪当前类下已使用的 SerializedName
        used_serialized_names : set[str] = set()
        if decl.members is None:
            continue
        for member in decl.members:
            if isinstance(member, kt.PropertyDecl) and member.annotations:
                for annotation in member.annotations:
                    if annotation.name == "SerializedName":
                        arg = annotation.args[0]
                        serialized_name = kt.get_kt_string_value(arg)
                        # 检查同一个类下 SerializedName 是否重复
                        if serialized_name in used_serialized_names:
                            raise ValueError(
                                f"[Error 2003] Duplicate SerializedName '{serialized_name}' found in class '{decl.name}'. "
                                f"SerializedName must be unique within the same class."
                            )
                        used_serialized_names.add(serialized_name)
                        cache.kt_serialized_name_map[(key, serialized_name)] = member

def build_arkts_class_name_to_class_decl_map(convert_env: Env, decls : list[ts.Decl]):
    cache = convert_env.cache
    for decl in decls:
        if isinstance(decl, ts.ClassDecl):
                cache.arkts_class_name_to_class_decl_map[decl.name] = decl

def build_real_obj_and_proxy_obj_name_map(convert_env: Env, decls : list[ts.Decl]):
    cache = convert_env.cache
    for decl in decls:
        if isinstance(decl, ts.ClassDecl):
            class_name_ts = decl.name
            class_name_proxy = get_class_name_and_package_name(convert_env, decl.name)[0]
            class_id = decl.get_decorator_key("id")
            class_id = class_id.value if isinstance(class_id, ts.StringValue) else None
            class_name_real = cache.kt_id_class_map.get(class_id)
            if class_name_real is not None:
                name = class_name_real[0].name if class_id is not None else class_name_ts
                cache.real_obj_and_proxy_obj_name_map[name] = class_name_proxy

def get_class_name_and_package_name(convert_env: Env,  name: str) -> tuple[str, str]:
    cache = convert_env.cache
    result = cache.kt_class_rename_map.get(name, (name + convert_env.config.suffix, ''))
    return result

def build_arkts_deco_rename_map(convert_env: Env, arkts_decls : list[ts.Decl]):
    cache = convert_env.cache
    for decl in arkts_decls:
        if isinstance(decl, ts.ClassDecl) and decl.has_export_decorator():
            deco_name = get_ts_string_value(decl.get_decorator_key("name"))
            package_name = get_ts_string_value(decl.get_decorator_key("package"))
            if package_name is None:
                package_name = convert_env.config.default_package
            if deco_name is not None:
                cache.arkts_class_name_to_decorator_name_and_pkg_map[decl.name] = (deco_name, package_name)


def get_kt_proxy_class_and_package_name(convert_env: Env, c: ts.ClassDecl | ts.InterfaceDecl) -> tuple[class_name, pkg_name] | None:
    cache = convert_env.cache
    share_type = convert_env.config.share_type
    default_res = (c.name + convert_env.config.suffix, convert_env.config.default_package)
    if share_type == "basic" or share_type == "copy":
        return cache.arkts_class_name_to_decorator_name_and_pkg_map.get(c.name, default_res)
    elif convert_env.config.share_type == "proxy":
        share_id = ts.get_ts_string_value(c.get_decorator_key('id'))
        if share_id is not None and convert_env.config.enable_share_id:
            res = cache.kt_id_class_map.get(share_id)
            if res is None:
                return None
            kt_class_decl, package_name = res
            return kt_class_decl.name + convert_env.config.suffix, package_name
        else:
            return cache.kt_class_rename_map.get(c.name, default_res)
    else:
        raise ValueError(f"Unsupported share type: {convert_env.config.share_type}")