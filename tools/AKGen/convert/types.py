import logging
from os import login_tty

from lang.kt.kt_types import QualifiedType, ArrayType
from lang.ts.ts_types import *
from lang.ts.decls import *
import lang.kt.kt_types as kt
import lang.ts.ts_types as ts
from lang.ts.ts_types import RefType
import convert.env as env
type KtNode = str | list[KtNode]

def to_default_kt_type(convert_env: env.Env, t: ts.Type) -> kt.Type:
    cache = convert_env.cache
    match t:
        case VoidType():
            return kt.uint_type
        case NullType() | UndefinedType():
            return kt.unit
        case StringType():
            return kt.string_type
        case NumberType():
            return kt.int_type
        case BigIntType():
            return kt.long_type
        case BooleanType():
            return kt.boolean
        # TODO map and array type
        case RefType(name=name):
            if cache.arkts_enum_type_set.__contains__(name):
                return kt.int_type
            return kt.RefType(env.get_class_name_and_package_name(convert_env, name)[0])
        case ArrayType(elem):
            match elem:
                case NumberType():
                    return kt.intarray
                case _:
                    return kt.AppType(kt.RefType("List"), [to_default_kt_type(convert_env, elem)])
        case NullableType(base_type):
            return kt.NullableType(to_default_kt_type(convert_env, base_type))
        case UnionType(types=types):
            # Keep the original semantics: drop null/undefined, require exactly one remaining type.
            base_types = [x for x in types if not isinstance(x, (NullType, UndefinedType))]
            if len(base_types) != 1:
                raise TypeError(f"Unrecognized type {t}")
            # Note: returning the remaining element preserves the original behavior.
            return kt.NullableType(to_default_kt_type(convert_env, base_types[0]))

        case AppType(RefType("Array"), args=[ts.NumberType()]):
            return kt.intarray

        case AppType(RefType("Array"), args):
            convert_func = lambda arg: to_default_kt_type(convert_env, arg)
            return kt.AppType(kt.RefType("List"), list(map(convert_func, args)))

        case AppType(QualifiedType(["collections"], RefType("Array")), args=[ts.NumberType()]):
            return kt.intarray

        case AppType(RefType("List"), args):
            convert_func = lambda arg: to_default_kt_type(convert_env, arg)
            return kt.AppType(kt.RefType("List"), list(map(convert_func, args)))

        case AppType(RefType("Map"), args):
            convert_func = lambda arg: to_default_kt_type(convert_env, arg)
            return kt.AppType(kt.RefType("Map"), list(map(convert_func, args)))

        case AppType(type=base_type, args=type_args):
            args = [to_default_kt_type(convert_env, arg) for arg in type_args]
            return kt.AppType(to_default_kt_type(convert_env, base_type), args)

        case QualifiedType(qualifiers=qualifiers, base_type=base_type):
            # convert collections.Map<string, number> into Kotlin Map
            if qualifiers and qualifiers[0] == "collections" and isinstance(base_type, RefType):
                if base_type.name == "Map":
                    return kt.RefType("Map")
                if base_type.name == "Array":
                    return kt.RefType("List")
            return to_default_kt_type(convert_env, base_type)
        case _:
            raise TypeError(f"Unrecognized type {t}")

def get_type_default_value(kt_ty: kt.Type) -> KtNode | None:
    match kt_ty:
        case kt.PrimType(prim_type):
            match prim_type:
                case kt.PrimTypeEnum.UNIT:
                    return "Unit"
                case kt.PrimTypeEnum.BOOLEAN:
                    return "false"
                case kt.PrimTypeEnum.BYTE:
                    return "0"
                case kt.PrimTypeEnum.UBYTE:
                    return "0u"
                case kt.PrimTypeEnum.SHORT:
                    return "0"
                case kt.PrimTypeEnum.USHORT:
                    return "0u"
                case kt.PrimTypeEnum.INT:
                    return "0"
                case kt.PrimTypeEnum.UINT:
                    return "0u"
                case kt.PrimTypeEnum.LONG:
                    return "0L"
                case kt.PrimTypeEnum.ULONG:
                    return "0uL"
                case kt.PrimTypeEnum.CHAR:
                    return "'\\u0000'"
                case kt.PrimTypeEnum.FLOAT:
                    return "0f"
                case kt.PrimTypeEnum.DOUBLE:
                    return "0.0"
                case kt.PrimTypeEnum.STRING:
                    return '""'

        case kt.AppType(kt.RefType("Array"), _):
            return "arrayOf()"

        case kt.AppType(kt.RefType("List"), _):
            return "listOf()"

        case kt.AppType(kt.RefType("MutableList"), _):
            return "mutableListOf()"

        case kt.AppType(kt.RefType("Map"), _):
            return "mapOf()"

        case kt.AppType(kt.RefType("MutableMap"), _):
            return "mutableMapOf()"

        case kt.AppType(kt.QualifiedType(qs, ty), _):
            return get_type_default_value(ty)

        case kt.QualifiedType(["kotlin"], base_type):
            return get_type_default_value(base_type)

        case kt.PrimArrayType(kt.PrimArrayTypeEnum.INTARRAY):
            return "intArrayOf()"

        case kt.PrimArrayType(kt.PrimArrayTypeEnum.LONGARRAY):
            return "longArrayOf()"

        case kt.NullableType(_):
            logging.warning(f"Nullable type default to null value")
            return "null"

        case _:
            return None
