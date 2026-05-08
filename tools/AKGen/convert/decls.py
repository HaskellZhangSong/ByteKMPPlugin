import logging
from typing import cast, ReadOnly
from enum import EnumType
from types import MemberDescriptorType
from unittest.mock import DEFAULT

from pip._internal.commands import inspect
import warnings

from convert.env import KtSerializedNameMap
from convert.types import *
from convert.decorators import *
from convert.check import kotlin_keyword
import convert.env as env
import lang.ts.ts_types as ts
import lang.ts.decls as ts_decls
import lang.kt.type_parser as kt_type_parser
import lang.kt.kt_types as kt
import lang.kt.kt_decls as kt_decls
from lang.kt.kt_decls import PropertyModifier
from lang.kt.kt_types import get_primitive_type, get_prim_array_type, PrimTypeEnum, is_nullable_type, PrimArrayType
from convert.pretty import multi_line_str_to_kt_node
from convert.check import kotlin_keyword, check_kotlin_field_name
from lang.kt.type_name_replace import replace_ref_type_name, extract_ref_type_names


def get_getter_prop_transformer(convert_env: env.Env, arkts_type: ts.Type, kt_type : kt.Type, nullable = False, env_name = '') -> KtNode:
    bangbang = '!!' if not nullable else ''
    match (arkts_type, kt_type):
        # 0. BigInt
        case (ts.BigIntType(), kt.PrimType(PrimTypeEnum.LONG)):
            return [f'it.toKBigInt({env_name})' + bangbang]

        case (ts.BigIntType(), kt.PrimType(PrimTypeEnum.INT)):
            return [f'it.toKBigInt({env_name})' + bangbang + '.toInt()']

        case (ts.BigIntType(), kt.QualifiedType(["kotlin"], ty)):
            return get_getter_prop_transformer(convert_env, ts.BigIntType(), ty, nullable)

        # 1. Primitive Types
        case (_, kt.PrimType(prim_type = n)):
            return [f'it.toK{kt_type}({env_name})' + bangbang]

        case (ts.NumberType(), kt.PrimType(PrimTypeEnum.LONG)):
            return [f'it.toKLong({env_name})' + bangbang]

        case (ts.NumberType(), kt.QualifiedType(["kotlin"], ty)):
            return get_getter_prop_transformer(convert_env, ts.NumberType(), ty, nullable, env_name)

        # 2. Ref Object
        case (_, kt.RefType(name = ref_name)):
            return [f'{ref_name}(it)'] # No bangbang for RefType

        # 3. ArrayType
        # 3.1 ArrayType, PrimArrayType
        case (ts.ArrayType(ts.NumberType()),
              kt.PrimArrayType(arr_enum)):
            return [f'it.arkArrayTo{arr_enum.value}({env_name})' + bangbang]

        case(ts.ArrayType(ts.NumberType()),
             kt.QualifiedType(["kotlin"], prim_array_type)):
            return get_getter_prop_transformer(convert_env, arkts_type, prim_array_type, nullable)

        # 3.2 ArrayType, List<Type>
        case (ts.ArrayType(elem),
              kt.AppType(kt.RefType(name = "List"), kt_args)):
            return [f'it.arkArrayToKList {{',
                        get_getter_prop_transformer(convert_env, elem, kt_args[0], False, env_name),
                    '}' + bangbang]

        # 3.3 Any Type to MutableList<Type>
        case (_,
              kt.AppType((kt.RefType(name = "MutableList")), kt_args)):
            return [f'OhosFFIMutableList(ArkArray(it)){bangbang}']

        # 4. RefType Array
        # 4.1 RefType Array number, PrimArrayType
        case (ts.AppType(ts.RefType("Array"), args),
              kt.PrimArrayType(arr_enum)):
            return f'it.arkArrayTo{arr_enum.value}({env_name})' + bangbang

        # 4.2 RefType Array, List<Type>
        case (ts.AppType(ts.RefType("Array"), args),
              kt.AppType(kt.RefType(name="List"), kt_args)):
            return [f'it.arkArrayToKList {{',
                        get_getter_prop_transformer(convert_env, args[0], kt_args[0], False, env_name),
                    '}' + bangbang]

        # 4.3 RefType Array to kotlin Array Type
        case (ts.AppType(ts.RefType("Array"), args),
              kt.AppType(kt.RefType(name="Array"), kt_args)):
            return [f'it.arkArrayToKArray {{',
                        get_getter_prop_transformer(convert_env, args[0], kt_args[0], False, env_name),
                    '}' + bangbang]

        # List type to List type
        case (ts.AppType(ts.RefType("List"), args),
              kt.AppType(kt.RefType("List"), kt_args)):
            return [f'it.arkListToKList {{',
                        get_getter_prop_transformer(convert_env, args[0], kt_args[0], False, env_name),
                    f'}}' + bangbang]

        # 5. Sendable Array
        # 5.1 Sendable Array , PrimArrayType
        case (ts.AppType(ts.QualifiedType(["collections"], ts.RefType("Array")), args),
              kt.PrimArrayType(prim_type=arr_enum)):
            return [f'it.arkSendableArrayTo{arr_enum.value}({env_name})' + bangbang]

        # 5.2 Sendable Array, List<Type>
        case (ts.AppType(ts.QualifiedType(["collections"], ts.RefType("Array")), args),
              kt.AppType(kt.RefType(name = "List"), kt_args)):
            return [f'it.arkSendableArrayToKList {{',
                        get_getter_prop_transformer(convert_env, args[0], kt_args[0], False, env_name),
                    '}' + bangbang]

        # 6. Map
        case(ts.AppType((ts.RefType("Map")), args),
             kt.AppType(kt.RefType(name="Map"), kt_args)):
            return [f'it.arkMapToKMap {{',
                        get_getter_prop_transformer(convert_env, args[1], kt_args[1], False, env_name),
                    '}' + bangbang]

        # 7. Sendable Map
        case (ts.AppType(ts.QualifiedType(["collections"], ts.RefType("Map")), args),
              kt.AppType(kt.RefType(name = "Map"), kt_args)):
            return [f'it.arkSendableMapToKMap {{',
                        get_getter_prop_transformer(convert_env, args[1], kt_args[1], False, env_name),
                    '}' + bangbang]

        # 8 Union Type
        case (ts.UnionType(bt), kt_ty):
            nullable_ty = ts.union_type_to_nullable_type(arkts_type)
            return get_getter_prop_transformer(convert_env, nullable_ty, kt_ty, True, env_name)

        # 9 Nullable Type
        case (ts.NullableType(bt), kt.NullableType(kt_bt)):
            return get_getter_prop_transformer(convert_env, bt, kt_bt, True, env_name)

        case (ts.NullableType(bt), kt_ty):
            logging.warning(f"cannot convert nullable {arkts_type.span} arkts type {arkts_type} to non-nullable kotlin type {kt_type}")
            return []

        case (ts_ty, kt.NullableType(kt_bt)):
            return get_getter_prop_transformer(convert_env, ts_ty, kt_bt, True, env_name)

        # 10 "kotlin" Qualified type
        case (ts_ty, kt.QualifiedType(["kotlin"], kt_ty)):
            return get_getter_prop_transformer(convert_env, ts_ty, kt_ty, nullable, env_name)

        case (ts.RefType(name), kt.QualifiedType(ns, kt.RefType(kt_ty_name))):
            # When name is Bar, kt_ty_name can be f'com.example.BarKMP'
            # so we cannot assert kt_ty_name == name
            return [f'{kt_type}(it)']

        case (ts.RefType(name), kt.RefType(kt_ty)):
            return [f'{kt_type}(it)']

        case _:
            logging.warning(f"cannot convert {arkts_type.span} arkts type {arkts_type.signature} to kotlin type {kt_type}")
            return []

def get_setter_prop_transformer(convert_env: env.Env, arkts_type: ts.Type, kt_type : kt.Type, nullable = False) -> KtNode:
    cache = convert_env.cache
    match (arkts_type, kt_type):
        # 0. BigInt Type
        case (ts.BigIntType(), kt.PrimType(PrimTypeEnum.LONG)):
            return ['it.toArkBigInt()']

        case (ts.BigIntType(), kt.PrimType(PrimTypeEnum.INT)):
            return ['it.toLong.toArkBigInt()']

        case (ts.BigIntType(), kt.QualifiedType(["kotlin"], kt_ty)):
            return get_setter_prop_transformer(convert_env, arkts_type, kt_ty, nullable)

        # 1. Primitive Types
        case (ts.NumberType(), kt.PrimType(prim_enum)):
            assert(prim_enum in kt.number_type)
            return ['it.toArkNumber()']

        case (ts.NumberType(), kt.QualifiedType(["kotlin"], kt_ty)):
            return get_setter_prop_transformer(convert_env, ts.NumberType(), kt_ty, nullable)

        case (ts.BooleanType(), kt.PrimType(PrimTypeEnum.BOOLEAN)):
            return ['it.toArkBoolean()']

        case (ts.BooleanType(), kt.QualifiedType(["kotlin"], kt_ty)):
            return get_setter_prop_transformer(convert_env, ts.BooleanType(), kt_ty, nullable)

        case (ts.StringType(), kt.PrimType(PrimTypeEnum.STRING)):
            return ['it.toArkString()']

        case(ts.StringType(), kt.QualifiedType(["kotlin"], kt_ty)):
            return get_setter_prop_transformer(convert_env, ts.StringType(), kt_ty, nullable)

        # 2. Ref Object
        case (_, kt.RefType(name=ref_name)):
            return [f'it?.ref?.napiValue ?: getArkUndefined()']

        # 2.1 enum typescript type
        case (ts.RefType(ts_name), kt.PrimType(PrimTypeEnum.INT)):
            if cache.arkts_enum_type_set.__contains__(ts_name):
                return ['it.toArkNumber()']
            else:
                raise TypeError(f"cannot convert arkts enum type {arkts_type} to kotlin type {kt_type}")

        case (ts.RefType(ts_name), kt.QualifiedType(["kotlin"], kt_ty)):
            return get_setter_prop_transformer(convert_env, arkts_type, kt_ty, nullable)

        # 3. ArrayType
        # 3.1 ArrayType, PrimArrayType
        case (ts.ArrayType(elem),
              kt.PrimArrayType(arr_enum)):
            return [f'it.toArkArray() {{ it.toArk{arr_enum.value}() }}']

        case (ts.ArrayType(elem),
              kt.QualifiedType(["kotlin"], prim_type)):
            return get_setter_prop_transformer(convert_env, arkts_type, prim_type, nullable)

        # 3.2 ArrayType, List<Type>
        case (ts.ArrayType(elem),
              kt.AppType(kt.RefType(name="List"), kt_args)):
            return [f'it.toArkArray {{',
                        get_setter_prop_transformer(convert_env, elem, kt_args[0]),
                    '}']
        # 3.3 ArrayType, MutableList<Type>
        case(ts.ArrayType(elem),
             kt.AppType((kt.RefType(name="MutableList")), kt_args)):
            return [f'it.toArkArray {{',
                        get_setter_prop_transformer(convert_env, elem, kt_args[0]),
                    '}']

        # List Type, List Type
        case (ts.AppType(ts.RefType("List"), args),
              kt.AppType(kt.RefType("List"), kt_args)):
            return [f'it.toArkList() {{',
                        get_setter_prop_transformer(convert_env, args[0], kt_args[0]),
                    f' }}']

        # 4. RefType Array
        # 4.1 RefType Array number,  PrimArrayType
        case (ts.AppType(ts.RefType("Array"), args),
              kt.PrimArrayType(arr_enum)):
            return f'it.toArkArray() {{ it.toArk{arr_enum.value}() }}'

        case(ts.AppType(ts.RefType("Array"), args),
             kt.QualifiedType(["kotlin"], arr_enum)):
            return get_setter_prop_transformer(convert_env, arkts_type, arr_enum)

        # 4.2 RefType Array, List<Type>
        case (ts.AppType(ts.RefType("Array"), args),
              kt.AppType(kt.RefType(name="List"), kt_args)):
            return [f'it.toArkArray {{',
                        get_setter_prop_transformer(convert_env, args[0], kt_args[0]),
                    '}']

        case (ts.AppType(ts.RefType("Array"), args),
              kt.AppType(kt.RefType(name="Array"), kt_args)):
            return [f'it.toArkArray {{',
                        get_setter_prop_transformer(convert_env, args[0], kt_args[0]),
                    '}']

        case (ts.AppType(ts.RefType("Array"), args),
              kt.AppType((kt.RefType(name="MutableList")), kt_args)):
            return [f'it.toArkArray {{',
                        get_setter_prop_transformer(convert_env, args[0], kt_args[0]),
                    '}']

        # 5. Sendable Array
        # 5.1 Sendable Array , PrimArrayType
        case (ts.AppType(ts.QualifiedType(qualifiers=["collections"], base_type=ts.RefType("Array")), args),
              kt.PrimArrayType(prim_type=arr_enum)):
            return [f'it.toArkSendableArray()']

        # 5.2 Sendable Array -> List<Type>
        case (ts.AppType(ts.QualifiedType(qualifiers=["collections"], base_type=ts.RefType("Array")), args),
              kt.AppType(kt.RefType(name = "List"), kt_args)):
            return [f'it.toArkSendableArray {{',
                        get_setter_prop_transformer(convert_env, args[0], kt_args[0]),
                    '}']

        case (ts.AppType(ts.QualifiedType(qualifiers=["collections"], base_type=ts.RefType("Array")), args),
              kt.AppType(kt.RefType(name="MutableList"), kt_args)):
            return [f'it.toArkSendableArray {{',
                        get_setter_prop_transformer(convert_env, args[0], kt_args[0]),
                    '}']

        # 6. Map
        case (ts.AppType(ts.RefType("Map"), args),
              kt.AppType(kt.RefType(name="Map"), kt_args)):
            return [f'it.toArkMap {{',
                        get_setter_prop_transformer(convert_env, args[1], kt_args[1]),
                    '}']

        # 7 Sendable Map.
        case (ts.AppType(ts.QualifiedType(qualifiers=["collections"], base_type=ts.RefType("Map")), args),
              kt.AppType(kt.RefType(name="Map"), kt_args)):
            return [f'it.toArkSendableMap {{',
                        get_setter_prop_transformer(convert_env, args[1], kt_args[1]),
                    '}']

        # 8 Union Type
        case (ts.UnionType(bt), kt_ty):
            nullable_ty = ts.union_type_to_nullable_type(arkts_type)
            return get_setter_prop_transformer(convert_env, nullable_ty, kt_ty, True)

        # 9 Nullable Type
        case (ts.NullableType(bt), kt.NullableType(kt_bt)):
            return get_setter_prop_transformer(convert_env, bt, kt_bt, True)

        case (ts_ty, kt.NullableType(kt_bt)):
            return get_setter_prop_transformer(convert_env, ts_ty, kt_bt, True)

        case (ts.NullableType(bt), kt_ty):
            logging.warning(f"cannot convert nullable {arkts_type.span} arkts type {arkts_type} to non-nullable kotlin type {kt_type}")
            return []

        # 10 handle rest kotlin qualified types
        case (ts_ty, kt.QualifiedType(["kotlin"], kt_ty)):
            return get_setter_prop_transformer(convert_env, ts_ty, kt_ty, nullable)

        case (ts.RefType(_), kt.QualifiedType(ns, kt_ty)):
            return [f'it?.ref?.napiValue ?: getArkUndefined()']

        case (ts.RefType(_), kt.RefType(kt_ty)):
            return [f'it?.ref?.napiValue ?: getArkUndefined()']
        case _:
            logging.warning(f"cannot convert {arkts_type.span} arkts type {arkts_type.signature} to kotlin type {kt_type}")
            return []

class KtTypeFrom(Enum):
    TYPE_DECO = 'type_decorator'
    CUSTOM_TYPE_DECO = 'custom_type'
    SHARE_ID = 'share_id'
    DEFAULT = 'default'
    JSON_NAME = 'json_name'

@dataclass
class PropInfo:
    name : str
    kotlin_type : kt.Type
    is_mutable : bool
    is_decorator_defined_type : bool
    kt_type_from : KtTypeFrom
    kt_modifiers : list[PropertyModifier]
    should_field_in_expect : bool = False

def get_prop_name_and_type(convert_env: env.Env, parent: ClassDecl | InterfaceDecl, p: ts_decls.PropertyDecl) -> tuple[list[PropertyModifier], str, kt.Type, KtTypeFrom] | None:
    """
    :return:
        kotlin modifiers, if no corresponding kotlin code the result is []
        kotlin field name,
        kotlin type,
        the source kind of the kotlin type.
    """
    cache = convert_env.cache
    share_type = convert_env.config.share_type
    kt_modifiers = []
    field_name_in_deco: str | None = get_ts_string_value(p.get_decorator_key('name'))
    field_name_in_deco_or_default_name: str = field_name_in_deco if field_name_in_deco is not None else p.name
    if kotlin_keyword.__contains__(field_name_in_deco_or_default_name):
        warnings.warn(
            f"Field name '{field_name_in_deco_or_default_name}' is a Kotlin keyword. "
            f"It will be automatically escaped with backticks (`{field_name_in_deco_or_default_name}`) in the generated code."
        )
        field_name_in_deco_or_default_name = '`' + field_name_in_deco_or_default_name + '`'

    kt_type_in_deco: str | None = get_ts_string_value(p.get_decorator_key('type'))
    kt_type_in_deco_or_default_type = kt_type_parser.parse_kt_type(
        kt_type_in_deco.replace("\"", "")) if kt_type_in_deco is not None else to_default_kt_type(convert_env, p.type)

    kt_custom_type_in_deco: str | None = get_ts_string_value(p.get_decorator_key('custom_type'))
    kt_custom_type_in_deco_or_none = kt_type_parser.parse_kt_type(
        kt_custom_type_in_deco.replace("\"", "")) if kt_custom_type_in_deco is not None else None

    kt_type_from = KtTypeFrom.DEFAULT
    if kt_type_in_deco is not None:
        kt_type_from = KtTypeFrom.TYPE_DECO

    if kt_custom_type_in_deco is not None:
        kt_type_from = KtTypeFrom.CUSTOM_TYPE_DECO

    if share_type == 'basic' or share_type == 'copy':
        name = field_name_in_deco or p.name
        ty = kt_custom_type_in_deco_or_none or kt_type_in_deco_or_default_type
        return kt_modifiers, name, ty, kt_type_from

    # use id to find field name when enable_share_id
    elif share_type == 'proxy' and convert_env.config.enable_share_id :
        class_id: str | None = get_ts_string_value(parent.get_decorator_key('id'))
        # if no class id use name in deco otherwise use arkts field name
        if class_id is None:
            name = field_name_in_deco or p.name
            ty = kt_custom_type_in_deco_or_none or kt_type_in_deco_or_default_type
            return kt_modifiers, name, ty, kt_type_from
        else:
            # if it has class id, the kotlin prop name can be
            # 1. in field id map
            field_id: str | None = get_ts_string_value(p.get_decorator_key('id'))
            if field_id is not None:
                # 3.1.3 如果有 share_id 以 share_id 为先
                kt_prop = cache.kt_share_class_field_id_to_prop_map.get((class_id, field_id))

                if kt_prop and extract_ref_type_names(kt_prop.type) != []:
                    # 从类型中取出所有引用类型，若引用类型不在Kotlin源码文件的索引中，那么不进行转换。
                    kt_ref_names = extract_ref_type_names(kt_prop.type)
                    if (not all(name in cache.kt_name_to_decl_and_pkg_map and
                                    cache.kt_name_to_decl_and_pkg_map[name][0].annotation_contains("ShareClass")
                                for name in kt_ref_names)):
                            logging.warning(f"In arkts {p.type.span} class '{parent.name}' field '{p.name}', converted Kotlin type '{kt_prop.type}' contains a kotlin class that has no ShareClass id. Generation skipped.")
                            return None

                if kt_prop is not None:
                    replaced_ty = replace_ref_type_name(convert_env, kt_prop.type)
                    kt_type_from = KtTypeFrom.SHARE_ID
                    ty = kt_custom_type_in_deco_or_none or replaced_ty
                    return kt_prop.modifier, kt_prop.name, ty, kt_type_from
                else:
                    logging.warning(f"Cannot find shared field with class id: '{class_id}' and field id: '{field_id}'")
                    return None
            else:
                # 2. if config enable enableJsonName, search in serialized name map
                if convert_env.config.enable_json_name:
                    kt_prop = cache.kt_serialized_name_map.get((class_id, p.name))
                    # 根据3.1.3冲突规则，arkts字段上如果有 name 字段，那么SerializedName无效
                    # 这里，field_name_in_deco 为 None 时才找到的结果才会生效
                    if kt_prop is not None and field_name_in_deco is None:
                        kt_type_from = KtTypeFrom.JSON_NAME
                        ty = kt_custom_type_in_deco_or_none or kt_prop.type
                        return kt_modifiers, kt_prop.name, ty, kt_type_from
                    # 3. otherwise deco name or arkts field name
                    else:
                        ty = kt_custom_type_in_deco_or_none or kt_type_in_deco_or_default_type
                        return kt_modifiers, field_name_in_deco_or_default_name, ty, kt_type_from
                else:
                    ty = kt_custom_type_in_deco_or_none or kt_type_in_deco_or_default_type
                    return kt_modifiers, field_name_in_deco_or_default_name, ty, kt_type_from
    else:
        ty = kt_custom_type_in_deco_or_none or kt_type_in_deco_or_default_type
        return kt_modifiers, field_name_in_deco_or_default_name, ty, kt_type_from


def get_prop_info(convert_env: env.Env, parent: ClassDecl | InterfaceDecl, p: ts_decls.PropertyDecl) -> PropInfo | None :
    cache = convert_env.cache
    name_type = get_prop_name_and_type(convert_env, parent, p)
    if name_type is None:
        return None
    kt_modifiers, field_name, field_kt_type, kt_ty_from = name_type
    check_kotlin_field_name(field_name)
    mutable_in_deco: BooleanValue | None = p.get_decorator_key('mutable')

    if mutable_in_deco and mutable_in_deco.value and p.mod.__contains__(Modifier.READONLY):
        logging.error(f"[2201] Field '{p.name}' in class '{parent.name}' is marked as 'val' but has 'mutable' decorator is true.")
        raise ValueError(f"Field '{p.name}' in class '{parent.name}' is marked as 'val' but has 'mutable' decorator argument set to true.")

    if PropertyModifier.VAR in kt_modifiers and p.mod.__contains__(Modifier.READONLY) and (kt_ty_from in {KtTypeFrom.SHARE_ID, KtTypeFrom.JSON_NAME}):
        logging.error(f"[2202] Field '{p.name}' in class '{parent.name}' has 'readonly' but should be 'var' according to its Kotlin type.")
        raise ValueError(f"Field '{p.name}' in class '{parent.name}' has 'readonly' but should be 'var' according to its Kotlin type.")


    is_decorator_defined_type = p.get_decorator_key('type') is not None

    mutable = True

    # it kt_modifiers are empty the type is not from kotlin file
    if kt_modifiers == []:
        # readonly and mutable_in_deco
        mutable = (not p.mod.__contains__(Modifier.READONLY)) and (mutable_in_deco.value if mutable_in_deco else True)
        kt_modifiers = [PropertyModifier.VAR] if mutable else [PropertyModifier.VAL]
    else:
        mutable = PropertyModifier.VAR in kt_modifiers

    class_id: str | None = get_ts_string_value(parent.get_decorator_key('id'))

    needs_expect_class = (convert_env.config.share_type == 'proxy'
                          and
                          ((convert_env.config.enable_share_id
                            and
                            class_id is not None
                            and
                            class_id in cache.kt_id_class_map)
                           or
                           parent.name in cache.kt_name_to_decl_and_pkg_map))
    in_expect_class = False
    if needs_expect_class:
        if kt_ty_from in [KtTypeFrom.SHARE_ID, KtTypeFrom.JSON_NAME]:
            in_expect_class = True
        else:
            # field is not linked by id, need to check if there is a field with same name and type in kotlin file,
            # if there is such a field, we also put this field in expect class to make sure the compatibility between kotlin and arkts
            # compare the field, modifier, name and type in kotlin file
            if  class_id is not None and cache.kt_id_class_map.get(class_id) is not None:
                kt_class, pkg_name = cache.kt_id_class_map[class_id]
                for member in filter(lambda d: isinstance(d, kt_decls.PropertyDecl), kt_class.members):
                    kt_mutable = kt_decls.PropertyModifier.VAR if mutable else kt_decls.PropertyModifier.VAL
                    if (member.name == field_name
                        and replace_ref_type_name(convert_env, member.type)  == field_kt_type
                        and kt_mutable in member.modifier):
                        in_expect_class = True
                        break

    return PropInfo(name=field_name, kotlin_type=field_kt_type, is_mutable=mutable,
                    is_decorator_defined_type=is_decorator_defined_type, kt_type_from=kt_ty_from,
                    kt_modifiers=kt_modifiers, should_field_in_expect=in_expect_class)

def convert_toplevel_getter(convert_env: env.Env, ts_prop: ts_decls.PropertyDecl, ts_type: ts.Type, kt_type: kt.Type, is_nullable = False) -> KtNode | None:
    bangbang = '!!' if not is_nullable else ''
    prop_name = ts_prop.name
    match (ts_type, kt_type):
        case (ts.NullableType(ts_ty), kt.NullableType(kt_ty)):
            return convert_toplevel_getter(convert_env, ts_prop, ts_ty, kt_ty, True)

        # 当 ArkTs 可空，Kotlin 不可空时，getter 需要有默认值
        case (ts.NullableType(ts_ty), kt_ty):
            default_value = get_type_default_value(kt_ty)
            if not default_value:
                logging.warning(f"Field skipped. no default value for type for {kt_ty} corresponding to nullable ArkTs type {ts_type} at {ts_type.span}. ")
                return None
            return (convert_toplevel_getter(convert_env, ts_prop, ts_ty, kt_ty, True)
                    + ['?: run {',
                            [f'val value = {default_value}',
                             *convert_setter_body(convert_env, ts_prop, ts_ty, kt_ty),
                             f'value'],
                        '}'])

        # 当 ArkTs 不为空，Kotlin可为空时，getter不需要特殊处理
        case(ts_ty, kt.NullableType(kt_ty)):
            return convert_toplevel_getter(convert_env, ts_prop, ts_ty, kt_ty, True)

        case (_, kt.QualifiedType(["kotlin"], kt_ty)):
            return convert_toplevel_getter(convert_env, ts_prop, ts_type, kt_ty)

        case (ts.BigIntType(), kt.PrimType(PrimTypeEnum.LONG)):
            return [f'get() = ref.getBigInt("{prop_name}"){bangbang}']

        case (ts.BigIntType(), kt.PrimType(PrimTypeEnum.INT)):
            return [f'get() = ref.getBigInt("{prop_name}"){bangbang}.toInt()']

        case (_, kt.PrimType(_)):
            return [f'get() = ref.get{kt_type}("{prop_name}"){bangbang}']

        case (_, PrimArrayType(prim_ty)):
            return [f'get() = ref.getProperty("{prop_name}") {{' , [f'arkSendableArrayToK{prim_ty}()'], f'}}{bangbang}']

        case (_, kt.RefType(ref_name)):
            return [f'get() = ref.getProperty("{prop_name}") {{' , [f'{ref_name}(it)'], f'}}{bangbang}']

        case (_, kt_ty):
            get_prop = f'get() = ref.getProperty("{prop_name}") {{'
            return [get_prop, get_getter_prop_transformer(convert_env, ts_type, kt_ty), f'}}{bangbang}']


def convert_field_getter(convert_env: env.Env, parent : ClassDecl, ts_prop: ts_decls.PropertyDecl,  prop_info: PropInfo) -> KtNode:
    kotlin_type = prop_info.kotlin_type
    custom_getter: TsValue = ts_prop.get_decorator_key('custom_getter')
    if custom_getter is not None:
        code = multi_line_str_to_kt_node(cast(StringValue, custom_getter).value.replace('\\n', '\n'))
        return [f'get() {{',
                    code,
                '}']

    return convert_toplevel_getter(convert_env, ts_prop, ts_prop.type, kotlin_type)


def convert_setter_body(convert_env: env.Env, ts_prop: ts_decls.PropertyDecl, ts_type: ts.Type, kt_type: kt.Type,
                            nullable=False, value_name = "value") -> KtNode:
    prop_name = ts_prop.name
    match (ts_type, kt_type):
        case (ts.NullableType(ts_ty), kt.NullableType(kt_ty)):
            return convert_setter_body(convert_env, ts_prop, ts_ty, kt_ty, True)

        case (_, kt.QualifiedType(["kotlin"], kt_ty)):
            return convert_setter_body(convert_env, ts_prop, ts_type, kt_ty)

        case (ts.BigIntType(), kt.PrimType(PrimTypeEnum.LONG)):
            return [f'ref.setBigInt("{prop_name}", {value_name})']

        case (ts.BigIntType(), kt.PrimType(PrimTypeEnum.INT)):
            question_mark = '?' if nullable else ''
            return [f'ref.setBigInt("{prop_name}", {value_name}{question_mark}.toLong())']

        case (_, kt.PrimType(_)):
            return [f'ref.set{kt_type}("{prop_name}", {value_name})']

        case (_, PrimArrayType(prim_ty)):
            return [f'ref.set{prim_ty}("{prop_name}", {value_name}) {{',
                        [f'it.ArkSendableArray()'],
                    '}']

        case (_, kt.RefType(_)):
            return [f'ref.setProperty("{prop_name}", {value_name}) {{',
                        [f'it?.ref?.napiValue ?: getArkUndefined()'],
                    '}']

        case (_, kt_ty):
            return [f'ref.setProperty("{prop_name}", {value_name}) {{',
                        get_setter_prop_transformer(convert_env, ts_type, kt_ty),
                    '}']

def convert_toplevel_setter(convert_env: env.Env, ts_prop: ts_decls.PropertyDecl, ts_type: ts.Type, kt_type: kt.Type, nullable=False) -> KtNode:
    match (ts_type, kt_type):
        case(ts.NullableType(ts_ty), kt.NullableType(kt_ty)):
            return convert_setter_body(convert_env, ts_prop, ts_ty, kt_ty, True)
        # ArkTs 可空， Kotlin为不可空
        # 对于 setter，没有特别的
        case(ts.NullableType(ts_ty), kt_ty):
            return convert_toplevel_setter(convert_env, ts_prop, ts_ty, kt_ty)

        # ArkTs 不可空，Kotlin为空可
        # 对于 setter，结果需要用 ?.let 包裹，避免 Kotlin 端传入 null 导致 NPE
        case(ts_ty, kt.NullableType(kt_ty)):
            body = convert_setter_body(convert_env, ts_prop, ts_ty, kt_ty, False, "it")
            return ['set(value) {',
                        [f'value?.let {{', body, '}'],
                    '}']

        case (ts_ty, kt_ty):
            body = convert_setter_body(convert_env, ts_prop, ts_ty, kt_ty)
            return ['set(value) {',
                        body,
                    '}']

def convert_field_setter(convert_env: env.Env, parent : ClassDecl, ts_prop: ts_decls.PropertyDecl, prop_info: PropInfo):
    custom_setter: TsValue = ts_prop.get_decorator_key('custom_setter')
    if custom_setter is not None:
        code = multi_line_str_to_kt_node(cast(StringValue, custom_setter).value.replace('\\n', '\n'))
        return [f'set(value) {{',
                    code,
                '}']
    kotlin_type = prop_info.kotlin_type
    return convert_toplevel_setter(convert_env, ts_prop, ts_prop.type, kotlin_type)

def convert_prop(convert_env: env.Env, parent : ClassDecl | InterfaceDecl, p: ts_decls.PropertyDecl,
                 is_actual_class = False) -> tuple[KtNode, PropInfo] | None:
    prop_info : PropInfo = get_prop_info(convert_env, parent, p)
    if prop_info is None:
        return None
    prop_name = prop_info.name
    kotlin_type = prop_info.kotlin_type
    is_mutable = prop_info.is_mutable
    modifier = 'var' if is_mutable else 'val'
    if prop_info.kt_modifiers:
        modifier = ' '.join(map(lambda x: x.name.lower(), prop_info.kt_modifiers))
    var_decl : str = f"{modifier} {prop_name}: {kotlin_type}"
    if is_actual_class and prop_info.should_field_in_expect:
        var_decl = f"actual {var_decl}"
    # generate get
    getter_kt_code : KtNode = convert_field_getter(convert_env, parent, p, prop_info)
    if not getter_kt_code:
        return None
    res = [var_decl, getter_kt_code]
    if is_mutable and PropertyModifier.VAR in prop_info.kt_modifiers:
        setter_kt_code: KtNode = convert_field_setter(convert_env, parent, p, prop_info)
        res.append(setter_kt_code)

    return res, prop_info

def convert_prop_decl(convert_env: env.Env, parent : ClassDecl | InterfaceDecl, p: ts_decls.PropertyDecl) -> KtNode | None:
    prop_info : PropInfo = get_prop_info(convert_env, parent, p)
    if prop_info is None:
        return None
    prop_name = prop_info.name
    kotlin_type = prop_info.kotlin_type
    is_mutable = prop_info.is_mutable
    modifier = 'var' if is_mutable else 'val'
    if prop_info.kt_modifiers:
        modifier = ' '.join(map(lambda x: x.name.lower(), prop_info.kt_modifiers))
    var_decl : str = f"{modifier} {prop_name}: {kotlin_type}"
    res = [var_decl]

    return res

def get_convert_methods(c: ClassDecl) -> list[ClassFuncDecl]:
    deco_export_all_functions: TsValue = c.get_decorator_key('export_all_method')
    export_all_functions: bool = False if deco_export_all_functions is None else deco_export_all_functions.value
    class_methods = []
    if export_all_functions:
        # Include all fields except those with NoExportField decorator
        for p in c.members:
            if isinstance(p, ClassFuncDecl and 'static' not in cast(ClassFuncDecl, p).modifier
                          and cast(ClassFuncDecl, p).func_type == MethodType.METHOD):
                if p.deco is None or (p.deco is not None and p.deco.name != 'NoExportMethod'):
                    class_methods.append(p)
    else:
        # Include only fields with ExportField decorator
        for p in c.members:
            if isinstance(p, ClassFuncDecl) and p.deco is not None and p.deco.name == 'ExportMethod':
                class_methods.append(p)
    return class_methods

def convert_method(convert_env: env.Env, method: ts_decls.ClassFuncDecl, module_name: str) -> KtNode:
    params_info = []
    deco = method.deco
    params = get_decorators_value([deco], 'ExportMethod', ['param_type'])
    for param_name, ts_param_type in method.params:
        if params is not None and params.dict.get(param_name) is not None:
            kt_param_type = kt_type_parser.parse_kt_type(params.dict[param_name].value)
        else:
            kt_param_type = to_default_kt_type(convert_env, ts_param_type)
        params_info.append(f"{param_name}: {str(kt_param_type)}")
    params_nodes = ', '.join(params_info)
    args = []
    for arg_name, arg_type in method.params:
        args += class_constructor_param_transformer(convert_env, arg_name, arg_type,
                                                    to_default_kt_type(convert_env, arg_type))

    return_in_deco = get_decorators_value([deco], 'ExportMethod', ['return_type'])
    if return_in_deco is not None:
        return_type = kt_type_parser.parse_kt_type(return_in_deco)
    else:
        return_type = to_default_kt_type(convert_env, method.return_type)
    match (method.params, method.return_type):
        case ([], None):
            return [f'fun {method.name}() {{',
                        [f'callMethod("{method.name}")'],
                    '}']
        case (params, None):
            return [f'fun {method.name}({params_nodes}) {{',
                        [f'callMethod("{method.name}", ',
                        [f'arrayOf(',
                            [','.join(args)], ')']
                     ]]
        case ([], return_ty):
            ret_res = [get_getter_prop_transformer(convert_env, return_ty, return_type, is_nullable_type(return_type),'e')]
            bangbang = '!!' if not is_nullable_type(return_type) else ''

            return [f'fun {method.name}(): {return_type} {{',
                        [f'val ret = callMethod("{method.name}", ',
                            [f'{{ e, it -> ',
                                    ret_res,
                                f'}}'],
                            ')'],
                        [f'return ret{bangbang}'], ''
                ]
        case (params, return_ty):
            ret_res = [get_getter_prop_transformer(convert_env, return_ty, return_type,
                                                   is_nullable_type(return_type), 'e')]
            bangbang = '!!' if not is_nullable_type(return_type) else ''

            return [f'fun {method.name}({params_nodes}): {return_type} {{',
                        [f'val ret = callMethod("{method.name}", ',
                            [f'arrayOf(',
                                [', '.join(args)],
                             f'), '],
                            [f'{{ e, it -> ',
                                        ret_res,
                             f'}}'],
                        ')'],
                        [f'return ret{bangbang}'], ''
                    ]


@dataclass
class ClassInfo:
    name : str
    package : str
    custom_import_list : list[str]

type PackageName = str
type TypeAliasResult = tuple[PackageName, KtNode]

type ClassNode = KtNode
type ExpectClassNode = KtNode
type IOSEmptyClassNode = KtNode
type TypeAlias = KtNode

def convert_interface(convert_env : env.Env, c: InterfaceDecl, module_name: str = '') -> tuple[ClassNode, ExpectClassNode, IOSEmptyClassNode, TypeAlias, ClassInfo]:
    cache = convert_env.cache
    class_name, package_name = env.get_kt_proxy_class_and_package_name(convert_env, c)
    # 如果开启了share id，那么用kotlin项目中的package，否则用装饰器里package或者默认package
    if convert_env.config.enable_share_id:
        package_name = package_name or get_ts_string_value(c.get_decorator_key('package')) or convert_env.config.default_package
    else:
        package_name = get_ts_string_value(c.get_decorator_key('package')) or convert_env.config.default_package

    constr: KtNode = convert_interface_constructor(convert_env, c, module_name)
    deco_export_all_fields: TsValue = c.get_decorator_key('export_all_fields')
    export_all_fields: bool = True if deco_export_all_fields == None else deco_export_all_fields.value

    prop_members = []
    if export_all_fields:
        # Include all fields except those with NoExportField decorator
        for p in c.members:
            if isinstance(p, PropertyDecl) and Modifier.STATIC not in p.mod:
                if p.deco is None or (p.deco is not None and p.deco.name != 'NoExportField'):
                    prop_members.append(p)
    else:
        # Include only fields with ExportField decorator
        for p in c.members:
            if isinstance(p, PropertyDecl) and p.deco is not None and p.deco.name == 'ExportField':
                prop_members.append(p)
    expect_prop_decls = []
    prop_decls = []
    # 用于跟踪已使用的 Field_id 字段
    used_field_ids : set[str] = set()
    # 用于跟踪已使用的 Field_name 字段
    used_field_names : set[str] = set()
    class_id : str | None = get_ts_string_value(c.get_decorator_key('id'))
    needs_expect_class = (convert_env.config.share_type == 'proxy'
                          and
                          ((convert_env.config.enable_share_id
                            and
                            class_id is not None
                            and
                            class_id in cache.kt_id_class_map)
                           or
                           c.name in cache.kt_name_to_decl_and_pkg_map))

    for p in prop_members:
        # Field_id重复性检查
        field_id: StringValue | None = p.get_decorator_key('id')
        if field_id is not None:
            if field_id.value in used_field_ids:
                raise ValueError(f"[Error 2003] Duplicate field_id '{field_id.value}' found in class {c.name}")
            used_field_ids.add(field_id.value)
        # Field_name重复性检查
        field_name: StringValue | None = p.get_decorator_key('name')
        if field_name is not None:
            if field_name.value in used_field_names:
                raise ValueError(f"[Error 2002] Duplicate field name '{field_name.value}' found in class {c.name}")
            used_field_names.add(field_name.value)

        convert_prop_result = convert_prop(convert_env, c, p, needs_expect_class)
        prop_node, prop_info = convert_prop_result if convert_prop_result is not None else (None, None)
        if prop_node is not None:
            prop_decls += prop_node

        if needs_expect_class and prop_info and prop_info.should_field_in_expect:
            expect_prop_decls += convert_prop_decl(convert_env, c, p)

    # 暂且不生成方法
    # methods = get_convert_methods(c)
    # method_nodes = []
    # for method in methods:
    #     method_nodes += convert_method(convert_env, method, module_name)

    transformer: KtNode = convert_class_transformer(convert_env, c)

    custom_code: TsValue = c.get_decorator_key('custom_code')
    code = []
    if custom_code is not None:
        code = multi_line_str_to_kt_node(cast(StringValue, custom_code).value.replace('\\n', '\n'))

    # generate real object and proxy object conversion function when proxy
    proxy_to_from_real_function: KtNode = []
    if convert_env.config.share_type == 'copy':
        proxy_to_from_real_function = convert_real_obj_and_proxy_obj_function(convert_env, c)

    # generate expect class when proxy
    expect_class = []
    empty_actual_class_for_ios = []
    modified_class_keyword = 'class '
    if needs_expect_class:
        expect_class: KtNode = [f'expect class {class_name} {{',
                                    expect_prop_decls,
                                '}']
        modified_class_keyword = 'actual class '

        actual_props = ['actual ' + item for item in expect_prop_decls]

        empty_actual_class_for_ios = [f'actual class {class_name} {{',
                                            *[[ios_prop, ['get() { TODO() }', 'set(value) { TODO() }']] for ios_prop in actual_props],
                                      '}'] if convert_env.config.enable_ios_empty_impl else []

    clazz : KtNode = (['@KotlinExportClass(customTransform = true)',
                       modified_class_keyword + class_name+'(var ref: ArkObjectSafe)' + ' {',
                           ['companion object {}'] +
                            constr +
                            prop_decls +
                            code,
                      '}', ''] +
                      transformer + [''] +
                      proxy_to_from_real_function)

    ts_value_custom_import: ListValue = c.get_decorator_key('custom_import')
    custom_import: list[str] = list(map(lambda x: cast(StringValue, x).value, ts_value_custom_import.list)) if ts_value_custom_import is not None else []
    # generate typealias
    typealias_node = []
    if needs_expect_class:
        if class_id is not None:
            kt_class = cache.kt_id_class_map[class_id][0]
        else:
            kt_class = cache.kt_name_to_decl_and_pkg_map[c.name][0]
        # for possible inner classes
        class_name_qualified_kt_class_name = '.'.join(list(kt_class.parent_class or []) + [kt_class.name])
        typealias_node = [f'actual typealias {class_name} = {class_name_qualified_kt_class_name}']

    return clazz, expect_class, empty_actual_class_for_ios, typealias_node, ClassInfo(name=class_name, package=package_name, custom_import_list=custom_import)

def convert_interface_constructor(convert_env: env.Env, c: InterfaceDecl, module_path: str) -> KtNode:
    # 获取Kotlin类名和包名映射，如果没有则使用默认的Proxy后缀
    cache = convert_env.cache
    cn, pkg = convert_env.cache.kt_class_rename_map.get(c.name, (c.name + env.get_suffix(convert_env), ''))

    # 存储生成的所有构造函数
    constructors: KtNode = []

    # 生成第一个构造函数：接收napi_value参数，委托给第二个构造函数
    # 这是一个便捷构造函数，自动获取线程本地存储的环境
    constructor: KtNode = [
        'constructor(value: napi_value) : this(OhosFFIContext.tlsEnv, value)'
    ]
    constructors += constructor

    # 生成第二个构造函数：接收env和value参数
    # 这是主要的构造函数，负责实际的对象创建和初始化
    constructor: KtNode = [
        'constructor(env: napi_env, value: napi_value) : this(ArkObjectSafe(env, value))'
    ]
    constructors += constructor
    return constructors

def convert_class(convert_env : env.Env, c: ClassDecl, module_name: str = '') -> tuple[ClassNode, ExpectClassNode, IOSEmptyClassNode, TypeAlias, ClassInfo]:
    cache = convert_env.cache
    class_name, package_name = env.get_kt_proxy_class_and_package_name(convert_env, c)
    # 如果开启了share id，那么用kotlin项目中的package，否则用装饰器里package或者默认package
    if convert_env.config.enable_share_id:
        package_name = package_name or get_ts_string_value(c.get_decorator_key('package')) or convert_env.config.default_package
    else:
        package_name = get_ts_string_value(c.get_decorator_key('package')) or convert_env.config.default_package

    constr: KtNode = convert_class_constructor(convert_env, c, module_name)
    deco_export_all_fields: TsValue = c.get_decorator_key('export_all_fields')
    export_all_fields: bool = True if deco_export_all_fields == None else deco_export_all_fields.value

    prop_members = []
    if export_all_fields:
        # Include all fields except those with NoExportField decorator
        for p in c.members:
            if isinstance(p, PropertyDecl) and Modifier.STATIC not in p.mod:
                if p.deco is None or (p.deco is not None and p.deco.name != 'NoExportField'):
                    prop_members.append(p)
    else:
        # Include only fields with ExportField decorator
        for p in c.members:
            if isinstance(p, PropertyDecl) and p.deco is not None and p.deco.name == 'ExportField':
                prop_members.append(p)
    expect_prop_decls = []
    prop_decls = []
    # 用于跟踪已使用的 Field_id 字段
    used_field_ids : set[str] = set()
    # 用于跟踪已使用的 Field_name 字段
    used_field_names : set[str] = set()
    class_id : str | None = get_ts_string_value(c.get_decorator_key('id'))
    needs_expect_class = (convert_env.config.share_type == 'proxy'
                          and
                          ((convert_env.config.enable_share_id
                            and
                            class_id is not None
                            and
                            class_id in cache.kt_id_class_map)
                           or
                           c.name in cache.kt_name_to_decl_and_pkg_map))

    for p in prop_members:
        # Field_id重复性检查
        field_id: StringValue | None = p.get_decorator_key('id')
        if field_id is not None:
            if field_id.value in used_field_ids:
                raise ValueError(f"[Error 2003] Duplicate field_id '{field_id.value}' found in class {c.name}")
            used_field_ids.add(field_id.value)
        # Field_name重复性检查
        field_name: StringValue | None = p.get_decorator_key('name')
        if field_name is not None:
            if field_name.value in used_field_names:
                raise ValueError(f"[Error 2002] Duplicate field name '{field_name.value}' found in class {c.name}")
            used_field_names.add(field_name.value)

        convert_prop_result = convert_prop(convert_env, c, p, needs_expect_class)
        prop_node, prop_info = convert_prop_result if convert_prop_result is not None else (None, None)
        if prop_node is not None:
            prop_decls += prop_node

        if needs_expect_class and prop_info and prop_info.should_field_in_expect:
            expect_prop_decls += convert_prop_decl(convert_env, c, p)

    methods = get_convert_methods(c)
    method_nodes = []
    for method in methods:
        method_nodes += convert_method(convert_env, method, module_name)

    transformer: KtNode = convert_class_transformer(convert_env, c)

    custom_code: TsValue = c.get_decorator_key('custom_code')
    code = []
    if custom_code is not None:
        code = multi_line_str_to_kt_node(cast(StringValue, custom_code).value.replace('\\n', '\n'))

    # generate real object and proxy object conversion function when proxy
    proxy_to_from_real_function: KtNode = []
    if convert_env.config.share_type == 'copy':
        proxy_to_from_real_function = convert_real_obj_and_proxy_obj_function(convert_env, c)

    # generate expect class when proxy
    expect_class = []
    empty_actual_class_for_ios = []
    modified_class_keyword = 'class '
    if needs_expect_class:
        expect_class: KtNode = [f'expect class {class_name} {{',
                                    expect_prop_decls,
                                '}']
        modified_class_keyword = 'actual class '

        actual_props = ['actual ' + item for item in expect_prop_decls]

        empty_actual_class_for_ios = [f'actual class {class_name} {{',
                                            *[[ios_prop, ['get() { TODO() }', 'set(value) { TODO() }']] for ios_prop in actual_props],
                                      '}'] if convert_env.config.enable_ios_empty_impl else []

    clazz : KtNode = (['@KotlinExportClass(customTransform = true)',
                       modified_class_keyword + class_name+'(var ref: ArkObjectSafe)' + ' {',
                           ['companion object {}'] +
                            constr +
                            prop_decls +
                            method_nodes +
                            code,
                      '}', ''] +
                      transformer + [''] +
                      proxy_to_from_real_function)

    ts_value_custom_import: ListValue = c.get_decorator_key('custom_import')
    custom_import: list[str] = list(map(lambda x: cast(StringValue, x).value, ts_value_custom_import.list)) if ts_value_custom_import is not None else []
    # generate typealias
    typealias_node = []
    if needs_expect_class:
        if class_id is not None:
            kt_class = cache.kt_id_class_map[class_id][0]
        else:
            kt_class = cache.kt_name_to_decl_and_pkg_map[c.name][0]
        # for possible inner classes
        class_name_qualified_kt_class_name = '.'.join(list(kt_class.parent_class or []) + [kt_class.name])
        typealias_node = [f'actual typealias {class_name} = {class_name_qualified_kt_class_name}']

    return clazz, expect_class, empty_actual_class_for_ios, typealias_node, ClassInfo(name=class_name, package=package_name, custom_import_list=custom_import)

def convert_class_transformer(convert_env: env.Env, c: ClassDecl) -> KtNode:
    cache = convert_env.cache
    cn, pkg = env.get_kt_proxy_class_and_package_name(convert_env, c)
    transformer_name : str = cn + 'Transformer'
    clazz: KtNode = [
        f'@ArkTsExportCustomTransform({cn}::class)',
        f'object {transformer_name} : ArkTsExportCustomTransformer<{cn}> {{',
            [f'override fun fromJsObject(obj: napi_value): {cn} {{',
                [f'return {cn}(obj)'],
            '}'],
            [f'override fun toJsObject(obj: {cn}): napi_value {{',
                ['return obj.ref.napiValue'],
            '}'],
        '}'
    ]
    return clazz

def class_constructor_param_transformer(convert_env: env.Env, param_name: str, arkts_type: ts.Type, kt_type : kt.Type, env_name = "env") -> KtNode:
    match (arkts_type, kt_type):
        # 0. BigInt Type
        case (ts.BigIntType(), kt.PrimType(PrimTypeEnum.LONG)):
            return [f'ArkBigIntSafe.create({env_name}, {param_name})']

        case (ts.BigIntType(), kt.QualifiedType(["kotlin"], kt_ty)):
            return class_constructor_param_transformer(convert_env, param_name, ts.BigIntType(), kt_ty)
        # 1. Primitive Types
        case (ts.NumberType(), kt.PrimType(prim_enum)):
            assert(prim_enum in kt.number_type)
            return [f'ArkNumberSafe.create({env_name}, {param_name})']
        
        case (ts.NumberType(), kt.QualifiedType(["kotlin"], kt_ty)):
            return class_constructor_param_transformer(convert_env, param_name, ts.NumberType(), kt_ty)

        case (ts.BooleanType(), kt.PrimType(PrimTypeEnum.BOOLEAN)):
            return [f'ArkBooleanSafe.create({env_name}, {param_name})']
        
        case (ts.BooleanType(), kt.QualifiedType(["kotlin"], kt_ty)):
            return class_constructor_param_transformer(convert_env, param_name, ts.BooleanType(), kt_ty)

        case (ts.StringType(), kt.PrimType(PrimTypeEnum.STRING)):
            return [f'ArkStringSafe.create({env_name}, {param_name})']
        
        case(ts.StringType(), kt.QualifiedType(["kotlin"], kt_ty)):
            return class_constructor_param_transformer(convert_env, param_name, ts.StringType(), kt_ty)

        case (ts_ty, kt.QualifiedType(["kotlin"], kt_ty)):
            return class_constructor_param_transformer(convert_env, param_name, ts.StringType(), kt_ty)

        # 2. Ref Object
        case (_, kt.RefType(name=ref_name)):
            return [f'{param_name}?.ref?.napiValue ?: getArkUndefined()']

        # 2.1 enum typescript type
        case (ts.RefType(name=ts_name), kt.PrimType(PrimTypeEnum.INT)):
            if convert_env.cache.arkts_enum_type_set.__contains__(ts_name):
                return [f'ArkNumberSafe.create({env_name}, {param_name})']
            else:
                raise TypeError(f"cannot convert arkts enum type {arkts_type} to kotlin type {kt_type}")

        # 3. ArrayType
        # 3.1 ArrayType, PrimArrayType
        case (ts.ArrayType(elem),
              kt.PrimArrayType(prim_type=arr_enum)):
            return [f'ArkArraySafe.create({env_name}, {param_name})']

        # 3.2 ArrayType, List<Type>
        case (ts.ArrayType(elem),
              kt.AppType(kt.RefType(name="List"), kt_args)):
            return [f'ArkArraySafe.create({env_name}, {param_name}) {{',
                        class_constructor_param_transformer(convert_env, "it", elem, kt_args[0]),
                    '}']

        # 4. RefType Array
        # 4.1 RefType Array number,  PrimArrayType
        case (ts.AppType(ts.RefType("Array"), args),
              kt.PrimArrayType(arr_enum)):
            return [f'ArkArraySafe.create({env_name}, {param_name})']

        # 4.2 RefType Array, List<Type>
        case (ts.AppType(ts.RefType("Array"), args),
              kt.AppType(kt.RefType(name="List"), kt_args)):
            return [f'ArkArraySafe.create({env_name}, {param_name}) {{',
                        class_constructor_param_transformer(convert_env, "it", args[0], kt_args[0]),
                    '}']

        case (ts.AppType(ts.RefType("Array"), args),
              kt.AppType(kt.RefType(name="Array"), kt_args)):
            return [f'ArkArraySafe.create({env_name}, {param_name}) {{',
                        class_constructor_param_transformer(convert_env, "it", args[0], kt_args[0]),
                    '}']

        # List to List
        case (ts.AppType(ts.RefType("List"), args),
              kt.AppType(kt.RefType("List"), kt_args)):
            return [f'ArkListSafe.create({env_name}, {param_name}) {{',
                        class_constructor_param_transformer(convert_env, "it", args[0], kt_args[0]),
                    '}']

        # 5. Sendable Array
        # 5.1 Sendable Array , PrimArrayType
        case (ts.AppType(ts.QualifiedType(qualifiers=["collections"], base_type=ts.RefType("Array")), args),
              kt.PrimArrayType(prim_type=arr_enum)):
            return [f'ArkSendableArraySafe.create({env_name}, {param_name})']

        # 5.2 Sendable Array -> List<Type>
        case (ts.AppType(ts.QualifiedType(qualifiers=["collections"], base_type=ts.RefType("Array")), args),
              kt.AppType(kt.RefType(name = "List"), kt_args)):
            return [f'ArkArraySafe.create({env_name}, {param_name}) {{',
                        class_constructor_param_transformer(convert_env, "it", args[0], kt_args[0]),
                    '}']
        # 6. Map
        case (ts.AppType(ts.RefType("Map"), args),
              kt.AppType(kt.RefType(name="Map"), kt_args)):
            return [f'ArkMapSafe.create({env_name}, {param_name}) {{',
                        class_constructor_param_transformer(convert_env, "it", args[0], kt_args[0]),
                    '}']

        # 7 Sendable Map.
        case (ts.AppType(ts.QualifiedType(qualifiers=["collections"], base_type=ts.RefType("Map")), args),
              kt.AppType(kt.RefType(name="Map"), kt_args)):
            return [f'ArkSendableMapSafe.create({env_name}, {param_name}) {{',
                        class_constructor_param_transformer(convert_env, "it", args[1], kt_args[1]),
                    '}']

        # 8 Union Type
        case (ts.UnionType(bt), kt_ty):
            nullable_ty = ts.union_type_to_nullable_type(arkts_type)
            return class_constructor_param_transformer(convert_env, param_name, nullable_ty, kt_ty)

        # 9 Nullable Type
        case (ts.NullableType(bt), kt.NullableType(kt_bt)):
            return class_constructor_param_transformer(convert_env, param_name, bt, kt_bt)

        case (ts_ty, kt.NullableType(kt_bt)):
            return class_constructor_param_transformer(convert_env, param_name, ts_ty, kt_bt)

        case (ts.NullableType(bt), kt_ty):
            raise TypeError(f"cannot convert nullable arkts type {arkts_type} to non-nullable kotlin type {kt_type}")

        case _: raise TypeError(f"cannot convert arkts type {arkts_type} kotlin type {kt_type}")


def convert_class_constructor(convert_env: env.Env, c: ClassDecl, module_path: str) -> KtNode:
    # 获取Kotlin类名和包名映射，如果没有则使用默认的Proxy后缀
    cache = convert_env.cache
    cn, pkg = convert_env.cache.kt_class_rename_map.get(c.name, (c.name + env.get_suffix(convert_env), ''))

    # 存储生成的所有构造函数
    constructors: KtNode = []

    # 生成第一个构造函数：接收napi_value参数，委托给第二个构造函数
    # 这是一个便捷构造函数，自动获取线程本地存储的环境
    constructor: KtNode = [
        'constructor(value: napi_value) : this(OhosFFIContext.tlsEnv, value)'
    ]
    constructors += constructor

    # 生成第二个构造函数：接收env和value参数
    # 这是主要的构造函数，负责实际的对象创建和初始化
    constructor: KtNode = [
        'constructor(env: napi_env, value: napi_value) : this(ArkObjectSafe(env, value))'
    ]
    constructors += constructor
    # 如果提供了模块路径，则生成基于TypeScript类构造函数的Kotlin构造函数
    if module_path is not None:
        # 检查是否存在显式的构造函数声明
        has_explicit_constructor = False

        # 遍历类的所有成员，查找构造函数声明
        for member in c.members:
            # 检查是否为构造函数声明
            if isinstance(member, ClassFuncDecl) and member.func_type == MethodType.CONSTRUCTOR:
                has_explicit_constructor = True
                # 存储构造函数参数的相关信息
                constructor_params_info = []          # 参数类型声明信息
                constructor_param_names_info = []      # 参数名称信息
                constructor_params_transformers_info = []  # 参数转换逻辑代码

                # 如果构造函数有参数
                if member.params:
                    params_info = []           # 存储参数声明字符串
                    param_names_info = []      # 存储参数名称
                    params_transformers_info = []  # 存储参数转换逻辑代码

                    # 遍历每个参数
                    for param_name, ts_param_type in member.params:
                        # 将TypeScript类型转换为Kotlin默认类型
                        kt_param_type = to_default_kt_type(convert_env, ts_param_type)
                        # 构建参数声明字符串 "paramName: KotlinType"
                        params_info.append(f"{param_name}: {str(kt_param_type)}")
                        # 记录参数名称
                        param_names_info.append(f"{param_name}")
                        # 生成参数转换逻辑代码
                        params_transformers_info += class_constructor_param_transformer(convert_env, param_name, ts_param_type, kt_param_type)

                    # 合并所有参数信息
                    constructor_params_info.append(f"{', '.join(params_info)}")
                    constructor_param_names_info.append(f"{', '.join(param_names_info)}")
                    constructor_params_transformers_info.append(f"{', '.join(params_transformers_info)}")

                # 构建构造函数参数字符串
                constructor_params = ', '.join(constructor_params_info)
                # 构建构造函数体：拼接tlsEnv和参数名
                # 无参数时只保留tlsEnv，有参数时包含所有参数名
                constructor_body_parts = ['OhosFFIContext.tlsEnv', *constructor_param_names_info]
                constructor_body = ', '.join(constructor_body_parts)
                # 生成第三个构造函数：便捷构造函数，自动获取环境
                constructor: KtNode = [
                    f'constructor({constructor_params}) : this({constructor_body})'
                ]
                constructors += constructor

                # 构建带环境参数的构造函数参数列表
                constructor_params_parts = ['env: napi_env', *constructor_params_info]
                constructor_params = ', '.join(constructor_params_parts)
                # 构建构造函数体参数
                constructor_body = []
                # 根据是否有参数转换器来决定构造函数体的内容
                if constructor_params_transformers_info == []:
                    # 无参数转换器时的基本调用
                    constructor_body = ['env,', f'"{module_path}",', f'"{c.name}"']
                else:
                    # 有参数转换器时，包含转换器数组
                    constructor_body = ['env,', f'"{module_path}",', f'"{c.name}",', f'arrayOf({", ".join(constructor_params_transformers_info)})']

                # 生成第四个构造函数：完整的构造函数，包含环境和所有参数
                constructor: KtNode = [
                    f'constructor({constructor_params}) : this(',
                        # 调用ArkObjectSafe.tryCreate进行对象创建
                        ['ArkObjectSafe.create(',
                            # 传递环境、模块路径、类名和参数转换器
                            constructor_body,
                        ')'],
                    ')'
                ]
                constructors += constructor

        # 如果没有显式的构造函数声明，生成默认的无参构造函数
        if not has_explicit_constructor:
            # 生成第三个构造函数：带环境参数的无参构造函数（必须先定义，因为第四个构造函数会委托给它）
            constructor_body = ['env,', f'"{module_path}",', f'"{c.name}"']
            constructor: KtNode = [
                f'constructor(env: napi_env) : this(',
                    ['ArkObjectSafe.create(',
                        constructor_body,
                    ')'],
                ')'
            ]
            constructors += constructor

            # 生成第四个构造函数：无参便捷构造函数，委托给上面的constructor(env)
            constructor: KtNode = [
                f'constructor() : this(OhosFFIContext.tlsEnv)'
            ]
            constructors += constructor
    return constructors

def convert_real_obj_and_proxy_obj_function(convert_env: env.Env, c: ClassDecl) -> KtNode:
    cache = convert_env.cache
    # 1. 获取类名信息
    ts_class_name = c.name
    # 代理类名：从重命名映射表获取
    proxy_class_name = env.get_class_name_and_package_name(convert_env, c.name)[0]

    # 获取真实类名：如果存在 @ExportClass.id 装饰器，则从共享 ID 映射中获取对应的 Kotlin 类名
    class_id = c.get_decorator_key("id")
    class_id = class_id.value if isinstance(class_id, ts_decls.StringValue) else None
    real_class_name = cache.kt_id_class_map.get(class_id)[0].name if class_id is not None else ts_class_name

    # 2. 校验：确保 Kotlin 中存在对应的真实类声明
    if real_class_name not in cache.kt_name_to_decl_and_pkg_map:
        warnings.warn(f"Generate to/from real func: ArkTS class {ts_class_name} has no corresponding class in Kotlin.", UserWarning)
        return []

    # 用于存储字段转换的具体赋值代码
    to_real_obj_func_assign_lines: list[KtNode] = []
    from_real_obj_func_assign_lines: list[KtNode] = []
    
    # 3. 遍历类的成员，处理字段拷贝逻辑
    for ts_member in c.members:
        if isinstance(ts_member, ts_decls.PropertyDecl):
            # 仅处理可变字段（var）
            if ts_member.is_mutable():
                # 获取该字段在代理与真实对象间相互赋值的代码
                assign_code = handle_real_field_and_proxy_field(convert_env, class_id, real_class_name, proxy_class_name, ts_member)
                if assign_code is not None:
                    to_real_obj_func_assign_lines.append(assign_code[0]) # Proxy -> Real 的赋值语句
                    from_real_obj_func_assign_lines.append(assign_code[1]) # Real -> Proxy 的赋值语句
            else:
                warnings.warn(f"Generate to/from real func: ArkTS field {ts_class_name}.{ts_member.name}: {ts_member.type} is not mutable.", UserWarning)

    # 4. 构建 toRealObject 函数定义
    to_real_obj_function = [
        f"fun {proxy_class_name}.toRealObject(): {real_class_name}" + " {",
            [f"val obj = {real_class_name}()"] +
            to_real_obj_func_assign_lines +
            ["return obj"],
        "}"
    ]
    
    # 5. 构建 fromRealObject 函数定义（定义在 Companion Object 中）
    from_real_obj_function = [
        f"fun {proxy_class_name}.Companion.fromRealObject(obj: {real_class_name}): {proxy_class_name}" + " {",
            [f"val proxy = {proxy_class_name}()"] +
            from_real_obj_func_assign_lines +
            ["return proxy"],
        "}"
    ]
    
    # 返回两个函数的定义节点
    return to_real_obj_function + from_real_obj_function

def handle_real_field_and_proxy_field(convert_env: env.Env, class_id: str | None, real_class_name: str, proxy_class_name, ts_member: ts_decls.PropertyDecl) -> tuple[KtNode, KtNode] | None:
    cache = convert_env.cache
    member_id = ts_member.get_decorator_key("id")
    member_id = member_id.value if isinstance(member_id, ts_decls.StringValue) else None
    if member_id is not None: # ExportField.id字段存在
        # ExportClass.id必然存在，使用（class_id, member_id）作为key获取kt字段
        kt_member = cache.kt_share_class_field_id_to_prop_map.get((class_id, member_id))
        # 优先使用kt相同字段名和类型
        ts_member_name, ts_member_type = kt_member.name, kt_member.type
    else: # ExportField.id字段不存在
        # git优先使用ExportField.name和ExportField.type字段，如果都不存在则使用ts字段名和类型
        ts_member_deco_name = ts_member.get_decorator_key("name")
        ts_member_name = ts_member_deco_name.value if isinstance(ts_member_deco_name, ts_decls.StringValue) else ts_member.name
        ts_member_name = f"`{ts_member_name}`" if ts_member_name in kotlin_keyword else ts_member_name
        ts_member_deco_type = ts_member.get_decorator_key("type")
        ts_member_type = kt_type_parser.parse_kt_type(ts_member_deco_type.value) if isinstance(ts_member_deco_type, ts_decls.StringValue) else to_default_kt_type(convert_env, ts_member.type)
        # ExportField.id字段不存在时，优先使用ExportClass.id作为key获取kt字段，如果都不存在则使用类名作为key获取kt字段
        members = cache.kt_id_class_map.get(class_id)[0].members if class_id is not None else cache.kt_name_to_decl_and_pkg_map.get(real_class_name)[0].members
        # Kotlin实体类没有任何字段
        if members is None or len(members) <= 0:
            warnings.warn(f"Generate to/from real func: Kotlin class {real_class_name} has no member", UserWarning)
            return None
        
        kt_member = next((m for m in members if m.name == ts_member_name), None)
    return generate_assign_code(convert_env, kt_member, real_class_name, proxy_class_name, ts_member_name, ts_member_type)

def generate_assign_code(convert_env: env.Env, kt_member: kt_decls.Decl | None, real_class_name: str, proxy_class_name: str, ts_member_name: str, ts_member_type: kt.Type) -> tuple[KtNode, KtNode] | None:
    cache = convert_env.cache
    # 1. 基础校验：检查是否找到了对应的 Kotlin 字段，且该字段必须是属性（Property）
    if kt_member is None or not isinstance(kt_member, kt_decls.PropertyDecl):
        warnings.warn(f"Generate to/from real func: Proxy field {proxy_class_name}.{ts_member_name}: {ts_member_type} has no corresponding member in {real_class_name}", UserWarning)
        return None
    
    # 2. 可变性校验：如果 Kotlin 字段是只读的（val），则无法从代理对象同步数据到真实对象
    if kt_decls.PropertyModifier.VAL in kt_member.modifier:
        warnings.warn(f"Generate to/from real func: Kotlin field {real_class_name}.{kt_member.name}: {kt_member.type} is not mutable", UserWarning)
        return None
    
    # 3. 类型匹配校验：仅当 ArkTS 代理字段的类型与 Kotlin 真实字段的类型完全一致时才生成赋值代码
    elif kt_member.type.real_obj_equal_to_proxy_obj(cache, ts_member_type):
        # 处理可空类型的特殊情况：如果是 Nullable，需要解包基础类型以便后续处理访问符（?.）
        is_nullable = isinstance(kt_member.type, kt.NullableType) and isinstance(ts_member_type, kt.NullableType)
        kt_member_type = kt_member.type.base_type if is_nullable else kt_member.type
        ts_member_type = ts_member_type.base_type if is_nullable else ts_member_type

        visit_proxy_obj_field, visit_real_obj_field = generate_visit_field_code(convert_env, kt_member_type, f"obj.{kt_member.name}", ts_member_type, is_nullable)
        # 返回赋值语句对：(代理到真实的赋值, 真实到代理的赋值)
        return (
            f"obj.{kt_member.name} = this.{ts_member_name}{visit_proxy_obj_field}",
            f"proxy.{ts_member_name} = {visit_real_obj_field}"
        )
    else:
        # 类型不匹配时发出警告，不生成转换逻辑
        warnings.warn(f"Generate to/from real func: Proxy field {proxy_class_name}.{ts_member_name}: {ts_member_type} and Kotlin field {real_class_name}.{kt_member.name}: {kt_member.type} type mismatch", UserWarning)
        return None

def generate_visit_field_code(convert_env: env.Env, kt_member_type: kt.Type, visit_kt_member: str, ts_member_type: kt.Type, is_nullable: bool) -> tuple[str, str]:
    cache = convert_env.cache
    # 根据是否可空决定使用安全调用符 (?.) 还是普通成员访问符 (.)
    prefix = "?." if is_nullable else "."
    # 针对可空类型在调用工厂方法时可能需要的非空断言或默认处理
    suffix = "!!" if is_nullable else ""
    
    match (kt_member_type, ts_member_type):
        # 原生类型或原生类型数组：无需特殊转换，直接互相赋值
        case (kt.PrimType(), kt.PrimType()) | (kt.PrimArrayType(), kt.PrimArrayType()):
            return (
                "",
                f"{visit_kt_member}"
            )
        # 数组类型：Array<T>，递归处理数组元素的转换逻辑
        case (kt.ArrayType(kt_element_type), kt.ArrayType(ts_element_type)):
            visit_real_obj_field, visit_proxy_obj_field = generate_visit_field_code(convert_env, kt_element_type, "it", ts_element_type, False)
            return (
                f"{prefix}map{{ it{visit_real_obj_field} }}",
                f"{visit_kt_member}{prefix}map{{ {visit_proxy_obj_field} }}"
            )
        # 引用类型：RefType，递归处理引用对象的转换逻辑
        case (kt.RefType(kt_type_name), kt.RefType()):
            proxy_type_name = cache.real_obj_and_proxy_obj_name_map.get(kt_member_type.name)
            return (
                f"{prefix}toRealObject()",
                (f"if ({visit_kt_member} == null) null else " if is_nullable else "") + f"{proxy_type_name}.fromRealObject({visit_kt_member}{suffix})"
            )
        # 泛型应用类型：AppType<T1, T2, …>，递归处理泛型参数的转换逻辑，仅处理List<T>和Map<K, V>
        # List<T>
        case (kt.AppType(kt.RefType("List"), [kt_type_arg]), kt.AppType(kt.RefType("List"), [ts_type_arg])):
            # 递归处理列表元素的转换逻辑
            visit_real_obj_field, visit_proxy_obj_field = generate_visit_field_code(convert_env, kt_type_arg, "it", ts_type_arg, False)
            return (
                f"{prefix}map{{ it{visit_real_obj_field} }}",
                f"{visit_kt_member}{prefix}map{{ {visit_proxy_obj_field} }}"
            )
        # Map<K, V>
        case (kt.AppType(kt.RefType("Map"), [kt_key_type, kt_value_type]), kt.AppType(kt.RefType("Map"), [ts_key_type, ts_value_type])):
            if not kt_key_type.real_obj_equal_to_proxy_obj(cache, kt.string_type):
                warnings.warn(f"Generate to/from real func: AppType unexpected map key type ts {ts_member_type}, ts {ts_member_type}", UserWarning)
            visit_real_obj_field, visit_proxy_obj_field = generate_visit_field_code(convert_env, kt_value_type, "it", ts_value_type, False)
            return (
                f"{prefix}mapValue{{ it{visit_real_obj_field} }}",
                f"{visit_kt_member}{prefix}mapValue{{ {visit_proxy_obj_field} }}"
            )
        # 可空类型：Nullable<T>，递归处理基础类型的转换逻辑
        case (kt.NullableType(kt_base_type), kt.NullableType(ts_base_type)):
            return generate_visit_field_code(convert_env, kt_base_type, visit_kt_member, ts_base_type, True)
        # 限定名类型：QualifiedType，递归处理基础类型的转换逻辑
        case (kt.QualifiedType(), _) | (_, kt.QualifiedType()):
            kt_base_type = kt_member_type.base_type if isinstance(kt_member_type, kt.QualifiedType) else kt_member_type
            ts_base_type = ts_member_type.base_type if isinstance(ts_member_type, kt.QualifiedType) else ts_member_type
            return generate_visit_field_code(convert_env, kt_base_type, visit_kt_member, ts_base_type, False)
        case _:
            warnings.warn(f"Generate to/from real func: Unsupported type conversion: {kt_member_type} / {ts_member_type}", UserWarning)
            return None
