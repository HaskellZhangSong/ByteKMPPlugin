from lang.ts.ts_types import *
from lang.ts.decls import *

# get a value from decorator
def get_decorator(decos: list[Decorator] | None, deco_name: str) -> Decorator | None:
    if decos == None:
        return None
    is_deco = lambda x: x == deco_name
    return next((d for d in decos if is_deco(d.name)), None)


def get_value_from_ts_value(m: TsValue, key: list[str]) -> str | None | TsValue:
    if isinstance(m, StringValue) and len(key) == 0:
        return m.value
    elif len(key) == 0:
        return m
    elif isinstance(m, MapValue):
        if m.dict.get(key[0]) is None:
            return None
        return get_value_from_ts_value(m.dict[key[0]], key[1:])
    else:
        raise TypeError(f"Cannot get value from decorator {m} with key {key}")


def get_decorators_value(decos: list[Decorator], deco_name: str, key: list[str]) -> str | None:
    """
    :param decos: a list of decorators to search through
    :param deco_name: the name of the decorator to find
    :param key: the key to look up in the decorator's arguments, represented as a list of strings for nested keys
    :return: the string value associated with the key in the decorator's arguments if found, otherwise None
    """
    deco = get_decorator(decos, deco_name)
    if deco is not None:
        return get_value_from_ts_value(deco.args[0], key)
    return None


def get_decorator_value(deco: Decorator, key: list[str]) -> str | None:
    d: dict[str, TsValue] = deco.args[0].dict
    v: TsValue = d[key[0]]
    return get_value_from_ts_value(v, key[1:])


def has_decorator(decos: list[Decorator], deco_name: str) -> bool:
    is_deco = lambda x: x == deco_name
    return any(is_deco(d.name) for d in decos)


def is_exported(decos: list[Decorator]) -> bool:
    return has_decorator(decos, "ExportClass") or has_decorator(decos, "ExportField") or has_decorator(decos, "ExportMethod")
