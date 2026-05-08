from __future__ import annotations

import logging
from collections import deque
from pathlib import Path

from tree_sitter import Language, Parser as TsParserImpl, Tree, Node
from tree_sitter_typescript import language_typescript

from lang.parser import Parser
from lang.ts.decls import *
from utils.preprocessor import replace_jsfield_pattern

class_skip_until_node_types = {'public_field_definition', 'method_definition',
                               'index_signature', 'abstract_method_signature',
                               ';', '}'}

class ParserTs(Parser):
    def __init__(self, file_path: Path | str):
        self.language = Language(language_typescript())
        self.parser = TsParserImpl(self.language)
        path = Path(file_path).expanduser().resolve()
        self.source_code = replace_jsfield_pattern(path.read_text(encoding="utf-8"))
        self.tree: Tree = self.parser.parse(self.source_code.encode("utf-8"))
        super().__init__(deque(self.tree.root_node.children), path)

    def parse_string(self) -> StringValue:
        self.push_children()
        self.eat_choice({"\"", "\'"})
        str_value = self.parse_token_str_with(lambda node: self.peek().type in {'string_fragment', 'escape_sequence'})
        self.eat_choice({"\"", "\'"})
        return StringValue(str_value)

    def parse_identifier_name(self) -> str:
        node = self.peek()
        if node is None:
            raise SyntaxError("Unexpected end while parsing declaration name")

        match node.type:
            case 'identifier' | 'property_identifier' | 'type_identifier':
                return self.parse_identifier()
            case 'shorthand_property_identifier' | 'nested_identifier' | 'computed_property_name':
                return self.parse_text()
            case 'private_property_identifier':
                return self.parse_text().lstrip('#')
            case 'string':
                return self.parse_string().value
            case _:
                return self.parse_text()

    def parse_statement_block_decls(self) -> list[Decl]:
        self.push_children_type('statement_block')
        self.eat('{')
        decls: list[Decl] = []
        while self.peek() is not None and self.peek().type != '}':
            decl = self.parse_decl_node()
            if decl is not None:
                decls.append(decl)
        self.eat('}')
        return decls

    def parse_pair(self) -> tuple[str, TsValue]:
        # Here, the return type should be tuple[TsValue, TsValue]
        # so that it can represent values like {param: "xxx"} {"a": 1} or {1: "a"}.
        # However, we only consider identifier and string. We need not discriminate them.
        self.push_children_type('pair')
        node = self.peek()
        if node.type in {'identifier', 'property_identifier', 'type_identifier'}:
            key = self.parse_identifier()
        elif self.peek_type('string'):
            key = self.parse_string().value
        self.eat(":")
        value = self.parse_expression()
        return key, value

    def parse_object_value(self):
        self.push_children_type('object')
        self.eat("{")
        kv_list = self.sep_parse(",", self.parse_pair)
        self.eat("}")
        return MapValue(dict(kv_list))

    def parse_expression(self) -> TsValue:
        node = self.peek()
        match node.type:
            case 'string':
                return self.parse_string()
            case 'number':
                return NumberValue(self.parse_text())
            case 'true':
                self.eat('true')
                return BooleanValue(True)
            case 'false':
                self.eat('false')
                return BooleanValue(False)
            case 'object':
                return self.parse_object_value()
            case 'unary_expression':
                return UnaryExpressionValue(self.parse_text())
            case 'template_string':
                self.push_children()
                self.eat('`')
                str_value = self.parse_text()
                self.eat('`')
                return StringValue(str_value)
            case 'array':
                self.push_children()
                self.eat('[')
                items = self.sep_parse(',', self.parse_expression)
                self.eat(']')
                return ListValue(items)
            case e:
                raise SyntaxError(f"Unexpected expression type: {e}, parser at: {self.get_node_position(node)}")

    def parse_decorator(self):
        self.push_children_type('decorator')
        self.eat('@')
        peek = self.peek()
        match peek.type:
            case 'identifier':
                name = self.parse_identifier()
                return Decorator(name)
            case 'call_expression':
                self.push_children()
                name = self.parse_identifier()
                self.push_children_type('arguments')
                self.eat('(')
                args = self.sep_parse(',', self.parse_expression)
                self.eat(')')
                return Decorator(name, args)

    def parse_decorators(self) -> list[Decorator]:
        res = []
        while self.peek().type == 'decorator':
            dec = self.parse_decorator()
            res.append(dec)
        return res

    def parse_type(self) -> Type:
        n = self.peek()
        match n.type:
            case 'predefined_type':
                self.push_children()
                node = self.pop()
                node_span = self.get_node_span(node)
                match node.type:
                    case 'number':
                        return NumberType(span=node_span)
                    case 'boolean':
                        return BooleanType(span=node_span)
                    case 'string':
                        return StringType(span=node_span)
                    case 'object':
                        return ObjectType(span=node_span)
                    case 'any':
                        return AnyType()
            case 'literal_type':
                self.push_children()
                node = self.pop()
                match node.type:
                    case 'null':
                        return NullType(span=self.get_node_span(node))
                    case 'undefined':
                        return UndefinedType(span=self.get_node_span(node))
            case 'type_identifier':
                type_name = self.parse_identifier()
                if type_name == 'bigint':
                    return BigIntType(span=self.get_node_span(n))
                else:
                    return RefType(name=type_name, span=self.get_node_span(n))
            case 'generic_type':
                self.push_children_type('generic_type')
                base_type = self.parse_type()
                # parse type arguments
                self.push_children_type('type_arguments')
                self.eat('<')
                type_args = self.sep_parse(',', self.parse_type)
                self.eat('>')
                return AppType(base_type, type_args, span=self.get_node_span(n))
            case 'nested_type_identifier':
                self.push_children_type('nested_type_identifier')
                res = self.sep_parse('.', self.parse_identifier)
                span = self.get_node_span(n)
                # TODO split the location span info for qualifier and base_type
                return QualifiedType(span=self.get_node_span(n), qualifiers=res[:-1],
                                     base_type=RefType(name=res[-1],span=span))
            case 'array_type':
                self.push_children_type('array_type')
                ty = self.parse_type()
                span = self.get_node_span(n)
                self.eat('[')
                self.eat(']')
                return ArrayType(ty, span=span)
            case 'union_type':
                types = self.parse_union_type()
                # all union_type in this tool must be nullable type which means
                # it can contain only 1 non-null and non-undefined type
                non_null_types = list(filter(lambda x : not isinstance(x, (NullType, UndefinedType)), types))
                if len(non_null_types) == 1:
                    return NullableType(non_null_types[0], span=self.get_node_span(n))
                else:
                    logging.error(f'Union type {types} has too non-null many types')
                    raise ValueError(f'Union type {types} has too non-null many types')

        raise SyntaxError(f"Unsupported arkts type: {n.type}, parser at: {self.get_node_position(n)}")

    def parse_union_type(self) -> list[Type]:
        types = []
        self.push_children_type('union_type')
        if self.peek().type == 'union_type':
            types = self.parse_union_type()
            self.eat('|')
            types.append(self.parse_type())
            return types
        else:
            types.append(self.parse_type())
            self.eat('|')
            if self.peek().type == 'union_type':
                types.append(self.parse_union_type())
            else:
                types.append(self.parse_type())
            return types

    def parse_type_annotation(self) -> Type:
        self.push_children_type('type_annotation')
        self.eat(':')
        return self.parse_type()


    def parse_modifiers(self) -> list[Modifier]:
        mods = []
        while True:
            match self.peek().type:
                case 'readonly':
                    self.eat('readonly')
                    mods.append(Modifier.READONLY)
                case 'static':
                    self.eat('static')
                    mods.append(Modifier.STATIC)
                case _:
                    break
        return mods

    def parse_accessibility_modifier(self) -> Modifier:
        self.push_children_type('accessibility_modifier')
        match self.peek().type:
            case 'public':
                self.eat('public')
                return Modifier.PUBLIC
            case 'private':
                self.eat('private')
                return Modifier.PRIVATE
            case 'protected':
                self.eat('protected')
                return Modifier.PROTECTED
            case _:
                raise SyntaxError(f"Expected accessibility modifier, but got {self.peek().type}, parser at: {self.get_node_position(self.peek())}")

    def parse_public_field_definition(self) -> PropertyDecl:
        self.push_children_type('public_field_definition')
        mod = []
        if self.peek_type('accessibility_modifier'):
            mod.append(self.parse_accessibility_modifier())
        # 1. 解析装饰器 (如 @ExportField) 和修饰符 (如 readonly, static)
        deocs = self.parse_decorators()
        mod += self.parse_modifiers()
        node = self.peek()
        # 2. 处理抽象字段 (abstract) 的特殊逻辑
        if self.peek().type == 'abstract':
            nullable = False
            field_name = 'abstract'
            self.eat('abstract')
            ty = None
            
            # 2.1 错误恢复逻辑：如果解析器报错，尝试手动解析类型字符串
            if self.peek().type == 'ERROR':
                self.eat('ERROR')
                self.skip_until('property_identifier')
                type_str = self.parse_text()
                # 映射基础类型或引用类型
                match type_str:
                    case 'number':
                        ty =  NumberType(span=self.get_node_span(node))
                    case 'boolean':
                        ty = BooleanType(span=self.get_node_span(node))
                    case 'string':
                        ty = StringType(span=self.get_node_span(node))
                    case 'bigint':
                        ty = BigIntType(span=self.get_node_span(node))
                    case _:
                        ty = RefType(type_str,span=self.get_node_span(node))
            else:
                # 2.2 正常解析抽象属性标识符和可选标记
                self.eat('property_identifier')
                if self.peek().type == '?':
                    self.eat('?')
                    nullable = True
                ty = self.parse_type_annotation()

            self.skip_cond_util(lambda n: n in class_skip_until_node_types)
            if self.peek_type(';'):
                self.eat(';')
            
            # 处理可空类型封装
            if nullable:
                ty = NullableType(ty, span=self.get_node_span(node))
            return PropertyDecl(mod, field_name, ty, deocs)
        else:
            # 3. 解析常规字段
            # 3.1 解析字段名 (Identifier)
            field_name = self.parse_identifier()
            nullable = False
            
            # 3.2 检查是否为可选字段 (如: field?: string)
            if self.peek().type == '?':
                self.eat('?')
                nullable = True

            # 3.3 解析类型注解 (如: : string)
            ty = self.parse_type_annotation()
            
            # 3.4 寻找分号结束符
            self.skip_cond_util(lambda n: n in class_skip_until_node_types)
            if self.peek_type(';'):
                self.eat(';')
            
            # 如果是可选字段，将其类型封装为 NullableType
            if nullable:
                ty = NullableType(ty, span=self.get_node_span(node))
            return PropertyDecl(mod, field_name, ty, deocs)

    def parse_property_signature(self, deco: list[Decorator] = []) -> PropertyDecl:
        self.push_children_type('property_signature')

        # 解析装饰器 (如 @ExportField) 和修饰符 (如 readonly, static)
        deocs = deco if deco != [] else self.parse_decorators()
        mod = self.parse_modifiers()
        node = self.peek()

        # 解析常规字段
        # 解析字段名 (Identifier)
        field_name = self.parse_identifier()
        nullable = False

        # 检查是否为可选字段 (如: field?: string)
        if self.peek().type == '?':
            self.eat('?')
            nullable = True

        # 解析类型注解 (如: : string)
        ty = self.parse_type_annotation()

        # 寻找分号结束符
        self.skip_cond_util(lambda n: n in {'export_statement','property_signature','call_signature',
                                            'construct_signature','index_signature','method_signature',
                                            ';', '}'})
        if self.peek_type(';'):
            self.eat(';')

        # 如果是可选字段，将其类型封装为 NullableType
        if nullable:
            ty = NullableType(ty, span=self.get_node_span(node))
        return PropertyDecl(mod, field_name, ty, deocs)

    def parse_required_parameter(self) -> tuple[str, Type]:
        """解析单个必需参数，返回(参数名, 类型)"""
        self.push_children_type('required_parameter')

        # 解析参数名
        self.skip_until('identifier')
        param_name = self.parse_identifier()

        # 直接解析类型注解（已经在子节点中了）
        param_type = self.parse_type_annotation()            
        return (param_name, param_type)

    def parse_optional_parameter(self) -> tuple[str, Type]:
        """解析单个可选参数，返回(参数名, 类型)"""
        self.push_children_type('optional_parameter')

        # 解析参数名
        self.skip_until('identifier')
        param_name = self.parse_identifier()

        # 检查可选参数标记
        nullable = False
        if self.peek().type == '?':
            self.eat('?')
            nullable = True

        # 直接解析类型注解（已经在子节点中了）
        param_type = self.parse_type_annotation()

        # 如果是可选参数，封装为可空类型
        if nullable:
            param_type = NullableType(param_type)

        return (param_name, param_type)

    def parse_formal_parameters(self) -> list[tuple[str, Type]] | None:
        """解析形式参数列表，返回参数名和类型的元组列表"""
        self.push_children_type('formal_parameters')
        self.eat('(')

        params = []
        # 解析参数直到遇到右括号
        while self.peek().type != ')':
            if self.peek().type == 'required_parameter':
                param = self.parse_required_parameter()
                params.append(param)
            elif self.peek().type == 'optional_parameter':
                param = self.parse_optional_parameter()
                params.append(param)
            elif self.peek().type == ',':
                self.eat(',')
            else:
                # 跳过其他类型的参数节点（如可选参数等）
                self.skip()

        self.eat(')')
        return params

    def parse_method_definition(self, cached_decorators: list[Decorator] = []) -> ClassFuncDecl:
        modifiers = []
        self.push_children_type('method_definition')
        # 获取函数装饰器
        decorators = cached_decorators
        self.skip_token_in({'accessibility_modifier'})

        if self.peek().type == 'static':
            self.eat('static')
            modifiers.append(Modifier.STATIC)
        self.skip_until('property_identifier')
        # 获取函数名
        method_name = self.parse_identifier()

        # 确定函数类型
        func_type = MethodType.CONSTRUCTOR if method_name == 'constructor' else MethodType.METHOD
            
        # 解析参数列表
        params = self.parse_formal_parameters()

        # 解析返回类型（如果有）
        return_type = None
        if self.peek().type == 'type_annotation':
            self.push_children_type('type_annotation')
            self.eat(':')
            # 直接解析类型（已经在type_annotation的子节点中了）
            return_type = self.parse_type()
            
        # 跳过函数体，暂不解析
        self.eat('statement_block')
        return ClassFuncDecl(modifier = modifiers,
                             func_type=func_type,
                             decorators = decorators,
                             name = method_name,
                             return_type = return_type,
                             params = params)

    def parse_class_body(self) -> list[Decl]:
        res = []
        self.push_children_type('class_body')
        self.eat('{')
        
        # 用于缓存装饰器的临时列表
        cached_decorators = []
        
        while (node_type := self.peek().type) != '}':
            match node_type:
                case 'public_field_definition':
                    res.append(self.parse_public_field_definition())
                case 'method_definition':
                    # 将缓存的装饰器传递给方法定义
                    res.append(self.parse_method_definition(cached_decorators))
                    # 清空装饰器缓存
                    cached_decorators = []
                case 'decorator':
                    # 解析并缓存装饰器
                    decorator = self.parse_decorator()
                    cached_decorators.append(decorator)
                case _:
                    self.skip()
        self.eat('}')
        return res

    def parse_class_decl(self, decorators: list[Decorator] = []) -> ClassDecl | None:
        self.push_children_type('class_declaration')
        decos = self.parse_decorators() + decorators
        contains_export_class_deco = any(deco.name == 'ExportClass' for deco in decos)
        if not contains_export_class_deco:
            return None
        self.eat('class')
        class_name = self.parse_text()
        node = self.peek()
        if node.type == 'class_heritage':
            self.push_children()
            node1 = self.peek()
            if node1.type == 'extends_clause':
                raise SyntaxError(f"Extends clause not supported, parser at: {self.get_node_position(node1)}")
            self.skip_until('class_body')
        decls = self.parse_class_body()
        return ClassDecl(class_name, decls, decos)


    def parse_export_class_declaration(self) -> ClassDecl:
        self.push_children()
        decos = self.parse_decorators()
        self.eat('export')
        return self.parse_class_decl(decos)

    def parse_interface_body(self) -> list[Decl]:
        res = []
        self.push_children_type('interface_body')
        self.eat('{')

        # 用于缓存装饰器的临时列表
        cached_decorators = []

        while (node_type := self.peek().type) != '}':
            match node_type:
                case 'property_signature':
                    res.append(self.parse_property_signature(cached_decorators))
                    cached_decorators = []
                case 'decorator':
                    # 解析并缓存装饰器
                    decorator = self.parse_decorator()
                    cached_decorators.append(decorator)
                case 'ERROR':
                    self.push_children_type('ERROR')
                    decorator = self.parse_decorator()
                    cached_decorators.append(decorator)
                case _:
                    self.skip()
        self.eat('}')
        return res

    def parse_interface_decl(self, decorators: list[Decorator] = []) -> InterfaceDecl:
        self.push_children_type('interface_declaration')
        decos = self.parse_decorators() + decorators
        self.eat('interface')
        interface_name = self.parse_text()
        node = self.peek()
        if node.type == 'extends_type_clause':
            self.push_children()
            node1 = self.peek()
            if node1.type == 'extends':
                logging.warning(f"[Warn] Extends not supported, parser at: {self.get_node_position(node1)}")
            self.skip_until('interface_body')
        decls = self.parse_interface_body()
        return InterfaceDecl(interface_name, decls, decos)

    def parse_module_decl(self, node_type: str) -> NamespaceDecl:
        self.push_children_type(node_type)
        self.eat('module' if node_type == 'module' else 'namespace')
        if self.peek_type('string'):
            name = self.parse_string().value
        else:
            name = self.parse_identifier_name()

        declarations: list[Decl] = []
        if self.peek_type('statement_block'):
            declarations = self.parse_statement_block_decls()
        return NamespaceDecl(name=name, declarations=declarations)

    def parse_expression_statement_decl(self) -> Decl | None:
        self.push_children_type('expression_statement')
        node = self.peek()
        match (node.type):
            case 'internal_module':
                return self.parse_module_decl('internal_module')
            case 'module':
                return self.parse_module_decl('module')
            case _:
                self.skip()
                return None

    def parse_ambient_declaration(self) -> Decl:
        self.push_children_type('ambient_declaration')
        self.eat('declare')
        declaration = self.parse_decl_node()
        if declaration is None:
            raise SyntaxError("Expected declaration after declare")
        return declaration

    def parse_export_statement(self) -> Decl | None:
        self.push_children_type('export_statement')
        decos = self.many('decorator', self.parse_decorator)
        self.eat('export')
        node = self.peek()
        if node is None:
            raise SyntaxError("Unexpected end after export")
        match node.type:
            case 'class_declaration':
                contains_export_class_deco = any(deco.name == 'ExportClass' for deco in decos)
                if contains_export_class_deco:
                    return self.parse_class_decl(decos)
                return None
            case 'enum_declaration':
                return self.parse_enum_decl()
            case 'module':
                return self.parse_module_decl('module')
            case 'internal_module':
                return self.parse_module_decl('internal_module')
            case 'ambient_declaration':
                return self.parse_ambient_declaration()
            case 'type_alias_declaration':
                return self.skip()
            case 'function_declaration':
                return self.skip()
            case 'interface_declaration':
                return self.parse_interface_decl(decos)
            case _:
                return self.skip()

    def parse_decl_node(self) -> Decl | None:
        node = self.peek()
        if node is None:
            return None
        match node.type:
            case 'export_statement':
                return self.parse_export_statement()
            case 'class_declaration':
                return self.parse_class_decl()
            case 'module':
                return self.parse_module_decl('module')
            case 'internal_module':
                return self.parse_module_decl('internal_module')
            case 'expression_statement':
                return self.parse_expression_statement_decl()
            case 'ambient_declaration':
                return self.parse_ambient_declaration()
            case 'enum_declaration':
                return self.parse_enum_decl()
            case 'abstract_class_declaration':
                # will not handle abstract class
                return self.skip()
            case _:
                return self.skip()

    def parse_enum_member(self) -> tuple[str, int | None]:
        self.skip_cond_util(lambda x: x != 'comment')
        if self.peek().type == 'enum_assignment':
            self.push_children()

        name = self.parse_text()
        value = None
        if self.peek().type == '=':
            self.eat('=')
            item = self.parse_expression()
            if isinstance(item, NumberValue) or isinstance(item, UnaryExpressionValue):
                try:
                    value = int(item.value)
                except ValueError:
                    raise ValueError(f"Invalid enum value: {item.value}, parser at: {self.get_node_position(self.peek())}")
            else:
                raise ValueError(f"Invalid enum value: {item}, parser at: {self.get_node_position(self.peek())}")
        if self.peek().type == ',':
            self.eat(',')
        return name, value

    def parse_enum_members(self) -> list[tuple[str, int | None]]:
        self.skip_comments()
        res = self.many_in_table({'enum_assignment': self.parse_enum_member, 'property_identifier': self.parse_enum_member})
        return res
    
    def parse_export_enum_declaration(self) -> EnumDecl:
        self.push_children()
        self.eat('export')
        return self.parse_enum_decl()

    def parse_enum_body(self) -> list[tuple[str, int | None]]:
        self.push_children_type('enum_body')
        self.eat('{')
        members = self.parse_enum_members()
        self.eat('}')
        return members

    def parse_enum_decl(self) -> EnumDecl:
        self.push_children_type('enum_declaration')
        if self.peek().type == 'const':
            self.eat('const')
        self.eat('enum')
        name = self.parse_text()
        members = self.parse_enum_body()
        return EnumDecl(name, members)

    def parse(self) -> list[Decl]:
        res: list[Decl] = []
        while self.peek() is not None:
            decl = self.parse_decl_node()
            if decl is not None:
                res.append(decl)
        return res



def parse_ts_file(file_path: str | Path, module_name: str = "") -> SourceFile:
    p = Path(file_path).expanduser().resolve()
    parser = ParserTs(file_path)
    decls = parser.parse()
    return SourceFile(str(p), module_name, decls)


# Just for test
BASE_DIR = Path(__file__).parent.parent.parent

file_path = Path(f'{BASE_DIR}') / "test/cases/ts/class6.ts"
code = ''

if __name__ == "__main__":
    print('start')
    a = parse_ts_file(file_path)
    print('end')