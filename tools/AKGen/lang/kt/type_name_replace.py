from lang.kt.kt_types import *
from convert.env import *


def extract_ref_type_names(t: Type) -> list[str]:
    names: list[str] = []

    class ExtractRefTypeNamesVisitor(TypeVisitor):
        def visit_ref_type(self, t: RefType) -> "Type":
            if t.name not in {"Array", "List", "Set", "Map", "MutableList", "MutableSet", "MutableMap"}:
                names.append(t.name)
            return t

        def visit_qualified_type(self, t: QualifiedType) -> "Type":
            t.base_type.accept(self)
            return t

        def visit_nullable_type(self, t: NullableType) -> "Type":
            t.base_type.accept(self)
            return t

        def visit_array_type(self, t: ArrayType) -> "Type":
            t.element_type.accept(self)
            return t

        def visit_app_type(self, t: AppType) -> "Type":
            t.base_type.accept(self)
            for arg in t.type_args:
                arg.accept(self)
            return t

    t.accept(ExtractRefTypeNamesVisitor())
    return names


def replace_ref_type_name(env: Env, t: Type) -> Type | None:
    arkts_class_name_dict = env.cache.arkts_class_name_to_class_decl_map
    class ReplaceRefTypeNameVisitor(TypeVisitor):
        def visit_ref_type(self, t: RefType) -> "Type":
            if t.name in env.cache.real_obj_and_proxy_obj_name_map and env.config.share_type == 'proxy':
                new_name = env.cache.real_obj_and_proxy_obj_name_map[t.name]
                return RefType(new_name)
            if t.name in env.cache.kt_class_rename_map:
                new_name, _ = env.cache.kt_class_rename_map[t.name]
                return RefType(new_name)
            if t.name in arkts_class_name_dict:
                kt_name_pkg = get_kt_proxy_class_and_package_name(env, arkts_class_name_dict[t.name])
                if kt_name_pkg is None:
                    return t
                else:
                    new_name, _ = kt_name_pkg
                    return RefType(new_name)
            else:
                return t
    return t.accept(ReplaceRefTypeNameVisitor())