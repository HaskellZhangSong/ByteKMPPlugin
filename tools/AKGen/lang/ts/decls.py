from abc import ABC
from lang.ts.ts_types import *
from enum import Enum

class Modifier(Enum):
    READONLY = "readonly"
    STATIC = "static"
    PUBLIC = 'public'
    PRIVATE = 'private'
    PROTECTED = 'protected'

class TsValue:
    pass

class StringValue(TsValue):
    def __init__(self, value: str):
        self.value = value

class NumberValue(TsValue):
    def __init__(self, value: str):
        self.value = value

class BooleanValue(TsValue):
    def __init__(self, value: bool):
        self.value = value
     
class UnaryExpressionValue(TsValue):
    def __init__(self, value: str):
        self.value = value

class MapValue(TsValue):
    def __init__(self, dict: dict[str, TsValue]):
        self.dict = dict

class ListValue(TsValue):
    def __init__(self, list: list[TsValue]):
        self.list = list

class Decorator:
    def __init__(self, name: str, args: list[TsValue] | None = None):
        self.name = name
        self.args = args

def get_ts_string_value(tsv: TsValue | None) -> str | None:
    if tsv is None:
        return None
    elif isinstance(tsv, StringValue):
        return tsv.value
    else:
        raise TypeError(f"Expected StringValue, got {type(tsv)}")

def g_get_decorators(decos : list[Decorator] | None, deco_name : str) -> Decorator | None:
    """Return the @ExportClass decorator if present."""
    for deco in decos:
        if deco.name == deco_name:
            return deco
    return None

def g_has_decorator(decos : list[Decorator] | None, deco_name : str) -> bool:
    if not decos:
        return False
    for deco in decos:
        if deco.name == deco_name:
            return True
    return False

def g_get_decorator_value(deco : Decorator | None, key : str) -> TsValue | None:
    if (deco is not None) and (deco.args is not None) and (len(deco.args) > 0):
        tsv = deco.args[0]
        if isinstance(tsv, MapValue):
            return tsv.dict.get(key, None)
    return None

class Decl(ABC):
    def __init__(self, name: str, decorators: list[Decorator] | None = None):
        self.name = name
        self.decorators = decorators or []

@dataclass
class SourceFile:
    path : str
    module : str
    decls : list[Decl]

@dataclass(frozen=False)
class NamedDecl(Decl):
    name: str

class InterfaceDecl(Decl):
    def __init__(self, name: str, members: list[Decl] | None = None, decorators: list[Decorator] | None = None):
        super().__init__(name, decorators)
        self.members = members or []
        self.deco = self.get_decorators()

    def get_decorators(self) -> Decorator | None:
        return g_get_decorators(self.decorators, 'ExportClass')

    def get_decorator_key(self, k: str):
        return g_get_decorator_value(self.deco, k)

    def has_export_decorator(self) -> bool:
        return self.deco is not None

class ClassDecl(Decl):
    def __init__(self, name: str, members: list[Decl] | None = None, decorators: list[Decorator] | None = None):
        super().__init__(name, decorators)
        self.members = members or []
        self.deco = self.get_decorators()

    def get_decorators(self) -> Decorator | None:
        return g_get_decorators(self.decorators, 'ExportClass')

    def get_decorator_key(self, k: str):
        return g_get_decorator_value(self.deco, k)

    def has_export_decorator(self) -> bool:
        return self.deco is not None

class PropertyDecl(Decl):
    def __init__(self, mod: list[Modifier], name: str, type: Type, decorators: list[Decorator] | None = None):
        super().__init__(name, decorators)
        self.mod = mod
        self.type = type
        self.deco = self.get_decorators()

    def get_decorators(self) -> Decorator | None:
        deco_export = g_get_decorators(self.decorators, 'ExportField')
        deco_no_export = g_get_decorators(self.decorators, 'NoExportField')
        return deco_export or deco_no_export

    def get_decorator_key(self, k: str):
        return g_get_decorator_value(self.deco, k)

    def has_export_decorator(self) -> bool:
        return self.deco is not None

    def is_mutable(self) -> bool:
        return not self.mod.__contains__(Modifier.READONLY) and not self.mod.__contains__(Modifier.STATIC)

class MethodType(Enum):
    CONSTRUCTOR = "constructor"
    METHOD = "method"

class ClassFuncDecl(Decl):
    def __init__(
        self,
        modifier: list[Modifier],
        func_type: MethodType,
        name: str,
        return_type: Type | None,
        params: list[tuple[str, Type]] | None = None,
        decorators: list[Decorator] | None = None,
    ):
        super().__init__(name, decorators)
        self.modifier = modifier
        self.func_type = func_type
        self.return_type = return_type
        self.params = params
        self.deco = self.get_decorators()

    def get_decorators(self) -> Decorator | None:
        deco_export = g_get_decorators(self.decorators, 'ExportMethod')
        deco_no_export = g_get_decorators(self.decorators, 'NoExportMethod')
        return deco_export or deco_no_export


class EnumDecl(Decl):
    def __init__(self, name: str, members: list[tuple[str, int | None]] | None = None):
        super().__init__(name, [])
        self.members = members or []

@dataclass(frozen=False)
class NamespaceDecl(NamedDecl):
    declarations: list[Decl]