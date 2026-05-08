from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from enum import Enum

__all__ = [
    "PrimTypeEnum",
    "Type",
    "TypeVisitor",
    "PrimType",
    "NullableType",
    "RefType",
    "ArrayType",
    "AppType",
    "QualifiedType",
    "unit",
    "boolean",
    "byte_type",
    "ubyte_type",
    "short_type",
    "ushort_type",
    "int_type",
    "uint_type",
    "long_type",
    "ulong_type",
    "char_type",
    "float_type",
    "double_type",
    "string_type",
    "intarray",
    "longarray",
    "is_map_type",
    "is_list_type",
    "is_prim_array_type",
    "get_prim_array_type"
]

class PrimTypeEnum(Enum):
    UNIT = "Unit"
    BOOLEAN = "Boolean"
    BYTE = "Byte"
    UBYTE = "UByte"
    SHORT = "Short"
    USHORT = "UShort"
    INT = "Int"
    UINT = "UInt"
    LONG = "Long"
    ULONG = "ULong"
    CHAR = "Char"
    FLOAT = "Float"
    DOUBLE = "Double"
    STRING = "String"

number_type = {PrimTypeEnum.BYTE,
              PrimTypeEnum.UBYTE,
              PrimTypeEnum.SHORT,
              PrimTypeEnum.USHORT,
              PrimTypeEnum.INT,
              PrimTypeEnum.UINT,
              PrimTypeEnum.LONG,
              PrimTypeEnum.ULONG,
              PrimTypeEnum.FLOAT,
              PrimTypeEnum.DOUBLE}

class Type(ABC):
    '''env: ConvertCache'''
    def real_obj_equal_to_proxy_obj(self, env, other): # 实体类代理类判等
        raise TypeError(f"Empty Type equals no Type")
    @abstractmethod
    def accept(self, visitor: "TypeVisitor"):
        pass


class TypeVisitor(ABC):
    def visit_prim_type(self, t: "PrimType"):
        return t

    def visit_prim_array_type(self, t: "PrimArrayType"):
        return t

    def visit_nullable_type(self, t: "NullableType"):
        return NullableType(t.base_type.accept(self))

    def visit_ref_type(self, t: "RefType"):
        return t

    def visit_array_type(self, t: "ArrayType"):
        return ArrayType(t.element_type.accept(self))

    def visit_app_type(self, t: "AppType"):
        return AppType(t.base_type.accept(self), [arg.accept(self) for arg in t.type_args])

    def visit_qualified_type(self, t: "QualifiedType"):
        return QualifiedType(t.qualifiers, t.base_type.accept(self))

@dataclass(frozen=True)
class PrimType(Type):
    prim_type: PrimTypeEnum

    def real_obj_equal_to_proxy_obj(self, env, other):
        match other:
            case PrimType():  # 与原生类型比较，必须类型完全相同
                return self.prim_type == other.prim_type
            case QualifiedType(["kotlin"], base_type):  # 与kotlin限定名类型比较，忽略kotlin限定名，递归比较基础类型
                return self.real_obj_equal_to_proxy_obj(env, base_type)
            case _:  # 其他情况不匹配
                return False

    def accept(self, visitor: "TypeVisitor"):
        return visitor.visit_prim_type(self)

    def is_prim_number_type(self):
        return self.prim_type in number_type

    def __str__(self):
        return f"{self.prim_type.value}"

class PrimArrayTypeEnum(Enum):
    INTARRAY = "IntArray"
    LONGARRAY = "LongArray"


@dataclass(frozen=True)
class PrimArrayType(Type):
    prim_type: PrimArrayTypeEnum

    def real_obj_equal_to_proxy_obj(self, env, other):
        match other:
            case PrimArrayType():  # 与原生数组类型比较，必须类型完全相同
                return self.prim_type == other.prim_type
            case QualifiedType():  # 与限定名类型比较，限定名为kotlin.时忽略限定名并递归比较
                return other.qualifiers == ["kotlin"] and self.real_obj_equal_to_proxy_obj(env, other.base_type)
            case ArrayType(other_prim_type):  # 与数组类型比较，IntArray对应int[]，LongArray对应long[]
                return (self == intarray and other_prim_type == int_type) or \
                    (self == longarray and other_prim_type == long_type)
            case AppType(RefType("List"), [other_prim_type]):  # 与List泛型比较，IntArray对应List<Int>，LongArray对应List<Long>
                return (self == intarray and other_prim_type == int_type) or \
                    (self == longarray and other_prim_type == long_type)
            case _:  # 其他情况不匹配
                return False

    def accept(self, visitor: "TypeVisitor"):
        return visitor.visit_prim_array_type(self)

    def __str__(self):
        return f"{self.prim_type.value}"

@dataclass(frozen=True)
class NullableType(Type):
    base_type: Type

    def real_obj_equal_to_proxy_obj(self, env, other):
        match other:
            case NullableType(other_base_type):  # 与可空类型比较，递归比较基础类型
                return self.base_type.real_obj_equal_to_proxy_obj(env, other_base_type)
            case _:  # 其他情况不匹配
                return False

    def accept(self, visitor: "TypeVisitor"):
        return visitor.visit_nullable_type(self)

    def __str__(self):
        return f"{self.base_type}?"

@dataclass(frozen=True)
class RefType(Type):
    name: str
    def real_obj_equal_to_proxy_obj(self, env, other):
        match other:
            case RefType(other_name): # 与引用类型比较，支持实体类与代理类映射关系：当前类名在映射表中且对方类名等于映射值，或对方类名在映射表中且当前类名等于映射值，或类名直接相等
                return (self.name in env.real_obj_and_proxy_obj_name_map and other_name == env.real_obj_and_proxy_obj_name_map[self.name]) or \
                (other_name in env.real_obj_and_proxy_obj_name_map and self.name == env.real_obj_and_proxy_obj_name_map[other.name]) or \
                self.name == other_name
            case _:  # 其他情况不匹配
                return False

    def accept(self, visitor: "TypeVisitor"):
        return visitor.visit_ref_type(self)

    def __str__(self):
        return f"{self.name}"

@dataclass(frozen=True)
class ArrayType(Type):
    element_type: Type

    def real_obj_equal_to_proxy_obj(self, env, other):
        match other:
            case ArrayType(other_array_type):  # 与数组类型比较，递归比较元素类型
                return self.element_type.real_obj_equal_to_proxy_obj(env, other_array_type)
            case PrimArrayType(INTARRAY):  # 与原生数组类型比较，IntArray对应Array<Int>
                return self.element_type == PrimType(PrimTypeEnum.INT)
            case PrimArrayType(LONGARRAY):  # 与原生数组类型比较，LongArray对应Array<Long>
                return self.element_type == PrimType(PrimTypeEnum.LONG)
            case AppType(RefType("List"), [other_element_type]):  # 与List泛型比较，递归比较元素类型
                return self.element_type.real_obj_equal_to_proxy_obj(env, other_element_type)
            case _:  # 其他情况不匹配
                return False

    def accept(self, visitor: "TypeVisitor"):
        return visitor.visit_array_type(self)

    def __str__(self):
        return f"{self.element_type}[]"

@dataclass(frozen=True)
class AppType(Type):
    base_type: Type
    type_args: list[Type]

    def real_obj_equal_to_proxy_obj(self, env, other):
        match other:
            case AppType(other_base_type, other_type_args):  # 与应用类型比较，递归比较基础类型和所有类型参数
                return self.base_type.real_obj_equal_to_proxy_obj(env, other_base_type) and \
                    all(arg.real_obj_equal_to_proxy_obj(env, other_arg) for arg, other_arg in zip(self.type_args, other.type_args))
            case ArrayType(other_element_type):  # 与数组类型比较，基础类型必须是List且类型参数与元素类型匹配
                return self.base_type == RefType("List") and self.type_args[0].real_obj_equal_to_proxy_obj(env, other_element_type)
            case PrimArrayType(INTARRAY):  # 与原生数组类型比较，基础类型必须是List且类型参数是int
                return self.base_type == RefType("List") and self.type_args == int_type
            case PrimArrayType(LONGARRAY):  # 与原生数组类型比较，基础类型必须是List且类型参数是long
                return self.base_type == RefType("List") and self.type_args == long_type
            case _:  # 其他情况不匹配
                return False

    def accept(self, visitor: "TypeVisitor"):
        return visitor.visit_app_type(self)

    def __str__(self):
        return f"{self.base_type}<{', '.join([str(arg) for arg in self.type_args])}>"

@dataclass(frozen=True)
class QualifiedType(Type):
    qualifiers: list[str]
    base_type: Type

    def real_obj_equal_to_proxy_obj(self, env, other):
        match other:
            case QualifiedType(other_qualifiers, other_base_type):  # 与限定名类型比较，必须限定名和基础类型都匹配
                return self.qualifiers == other_qualifiers and self.base_type.real_obj_equal_to_proxy_obj(env, other_base_type)
            case PrimType() | PrimArrayType():  # 与原生类型或原生数组类型比较，限定名为kotlin.时忽略限定名并递归比较
                return self.qualifiers == ["kotlin"] and self.base_type.real_obj_equal_to_proxy_obj(env, other)
            case _:  # 其他情况不匹配
                return False

    def accept(self, visitor: "TypeVisitor"):
        return visitor.visit_qualified_type(self)

    def __str__(self):
        return f"{'.'.join(self.qualifiers)}.{self.base_type}"

unit = PrimType(PrimTypeEnum.UNIT)
boolean = PrimType(PrimTypeEnum.BOOLEAN)

byte_type = PrimType(PrimTypeEnum.BYTE)
ubyte_type = PrimType(PrimTypeEnum.UBYTE)

short_type = PrimType(PrimTypeEnum.SHORT)
ushort_type = PrimType(PrimTypeEnum.USHORT)

int_type = PrimType(PrimTypeEnum.INT)
uint_type = PrimType(PrimTypeEnum.UINT)

long_type = PrimType(PrimTypeEnum.LONG)
ulong_type = PrimType(PrimTypeEnum.ULONG)

char_type = PrimType(PrimTypeEnum.CHAR)
float_type = PrimType(PrimTypeEnum.FLOAT)
double_type = PrimType(PrimTypeEnum.DOUBLE)

string_type = PrimType(PrimTypeEnum.STRING)

intarray = PrimArrayType(PrimArrayTypeEnum.INTARRAY)
longarray= PrimArrayType(PrimArrayTypeEnum.LONGARRAY)

_PRIM_BY_NAME: dict[str, PrimTypeEnum] = {e.value: e for e in PrimTypeEnum}

def is_nullable_type(t: Type) -> bool:
    return isinstance(t, NullableType)

def is_map_type(t: Type) -> bool:
    if isinstance(t, NullableType):
        return is_map_type(t.base_type)
    # Map<K, V>
    elif isinstance(t, AppType):
        return is_map_type(t.base_type)
    elif isinstance(t, QualifiedType):
        return is_map_type(t.base_type)
    elif isinstance(t, RefType):
        return t.name == "Map"
    else:
        return False

def is_list_type(t: Type) -> bool:
    if isinstance(t, NullableType):
        return is_list_type(t.base_type)
    # List<T>
    if isinstance(t, AppType):
        return is_list_type(t.base_type)
    elif isinstance(t, QualifiedType):
        return is_list_type(t.base_type)
    elif isinstance(t, RefType):
        return t.name == "List"
    else:
        return False

def is_array_type(t: Type) -> bool:
    if isinstance(t, NullableType):
        return is_array_type(t.base_type)
    # Array<T>
    if isinstance(t, AppType):
        return is_array_type(t.base_type)
    elif isinstance(t, QualifiedType):
        return is_array_type(t.base_type)
    elif isinstance(t, RefType):
        return t.name == "Array"
    else:
        return False

def is_primitive_type(t: Type) -> bool:
    if isinstance(t, PrimType):
        return True
    elif isinstance(t, NullableType):
        return is_primitive_type(t.base_type)
    elif isinstance(t, QualifiedType):
        if t.qualifiers == ["kotlin"]:
            return is_primitive_type(t.base_type)
        else:
            return False
    else:
        return False

def get_primitive_type(t: Type) -> PrimType:
    match t:
        case PrimType():
            return t
        case NullableType(base_type=base):
            return get_primitive_type(base)
        case QualifiedType(qualifiers=qualifiers, base_type=base):
            if qualifiers == ["kotlin"]:
                return get_primitive_type(base)
            else:
                raise TypeError(f"Type {t} is not a PrimType")
        case _:
            raise TypeError(f"Type {t} is not a PrimType")


def is_ref_type(t: Type) -> bool:
    if isinstance(t, RefType):
        return True
    elif isinstance(t, NullableType):
        return is_ref_type(t.base_type)
    return False

def get_ref_type_name(t: Type) -> str:
    if isinstance(t, RefType):
        return t.name
    elif isinstance(t, NullableType):
        return get_ref_type_name(t.base_type)
    else:
        raise TypeError(f"Type {t} is not a RefType")


def get_prim_array_type(t: Type) -> PrimArrayType:
    match t:
        case PrimArrayType():
            return t
        case NullableType(base_type=base):
            return get_prim_array_type(base)
        case QualifiedType(qualifiers=qualifiers, base_type=base):
            if qualifiers == ["kotlin"]:
                return get_prim_array_type(base)
            else:
                raise TypeError(f"Type {t} is not a PrimArrayType")
        case _:
            raise TypeError(f"Type {t} is not a PrimArrayType")

def is_prim_array_type(t: Type) -> bool:
    """Return True if `t` is a primitive array type (IntArray or LongArray).

    Handles nullable and `kotlin.` qualified forms, e.g. `IntArray`, `kotlin.IntArray?`.
    """

    if isinstance(t, PrimArrayType):
        return True
    if isinstance(t, NullableType):
        return is_prim_array_type(t.base_type)
    if isinstance(t, QualifiedType):
        # kotlin.IntArray / kotlin.LongArray
        if t.qualifiers == ["kotlin"]:
            return is_prim_array_type(t.base_type)
        return False
    return False
