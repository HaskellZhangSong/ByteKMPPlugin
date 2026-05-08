import unittest
from pathlib import Path

from lang.ts.decls import *
from typing import cast

from lang.ts.parser_ts import ParserTs

class TsParserCasesTest(unittest.TestCase):
    BASE_DIR = Path(__file__).resolve().parent
    TS_CASES_DIR = BASE_DIR / "cases" / "ts"

    def parse(self, file_name: str):
        parser = ParserTs(self.TS_CASES_DIR / file_name)
        return parser.parse()

    def test_class0(self) -> None:
        sf = self.parse("class0.ts")
        self.assertEqual(sf[0].name, "com_example_awemem")

    def test_class1(self) -> None:
        sf = self.parse("class1.ts")
        self.assertEqual(sf[0].name, "com_example_awemem")
        self.assertEqual(len(sf[0].decorators), 1)
        self.assertEqual(len(cast(ClassDecl, sf[0]).members) , 1)

    def test_class2(self) -> None:
        sf = self.parse("class2.ts")
        self.assertEqual(sf[0].name, "com_example_awemem")
        self.assertEqual(len(sf[0].decorators), 1)

    def test_class3(self) -> None:
        try:
            sf = self.parse("class3.ts")
            self.fail("Expected parsing to fail due to syntax error")
        except SyntaxError:
            pass

    def test_class4(self) -> None:
        sf = self.parse("class4.ts")
        self.assertEqual(sf[0].name, "com_example_awemem")
        self.assertEqual(len(sf[0].decorators), 1)
        clazz = cast(ClassDecl, sf[0])
        self.assertEqual(len(clazz.members), 0)
        self.assertEqual(len(clazz.decorators[0].args), 1)
        args = cast(MapValue, clazz.decorators[0].args[0])
        d = args.dict
        keys = set(d.keys())
        self.assertEqual(keys, {"arkts_module_path", "export_all", "name"})

    def test_class5(self) -> None:
        sf = self.parse("class5.ts")
        self.assertEqual(sf[0].name, "com_example_awemem")
        self.assertEqual(len(sf[0].decorators), 1)
        clazz = cast(ClassDecl, sf[0])
        self.assertEqual(len(clazz.members), 1)
        self.assertEqual(len(clazz.decorators[0].args), 1)
        args = cast(MapValue, clazz.decorators[0].args[0])
        d = args.dict
        keys = set(d.keys())
        self.assertEqual(keys, {"arkts_module_path", "export_all", "name"})
        # TODO see the fields

    # TODO add more test cases covering various TypeScript features and edge cases.
    def test_class6(self):
        self.parse("class6.ts")

    def test_class7(self):
        self.parse("class7.ts")

    def test_class8(self):
        try:
            sf = self.parse("class8.ts")
            self.fail("Expected parsing to fail due to syntax error")
        except ValueError:
            pass

    def test_class9(self):
        self.parse("class9.ts")

    def test_class10(self):
        self.parse("class10.ts")

    def test_class11(self):
        sf = self.parse("class11.ts")

    def test_class12(self):
        sf = self.parse("class12.ts")

    def test_class13(self):
        sf = self.parse("class13.ts")
        self.assertTrue(isinstance(sf[0], NamespaceDecl))


    def test_class14(self):
        sf = self.parse("class14.ts")
        # TODO check decorators
        self.assertEqual(sf[0].name, "Foo")
        clazz = cast(ClassDecl, sf[0])
        self.assertEqual(len(clazz.members), 4)
        self.assertTrue(all(isinstance(m, ClassFuncDecl) for m in clazz.members))

    def test_class15(self):
        sf = self.parse("class15.ts")
        self.assertEqual(sf[0].name, "sicily_u_PoiSearchScene")
        self.assertTrue(isinstance(sf[0], EnumDecl))

    def test_class16(self):
        sf = self.parse("class16.ts")
        self.assertEqual(sf[0].name, "ArrType")
        clazz = cast(ClassDecl, sf[0])
        self.assertEqual(len(clazz.members), 1)
        prop = cast(PropertyDecl, clazz.members[0])
        self.assertTrue(isinstance(prop.type, ArrayType))

    def test_class17(self):
        sf = self.parse("class17.ts")
        self.assertEqual(sf[0].name, "Employee")
        clazz = cast(ClassDecl, sf[0])
        self.assertEqual(len(clazz.members), 1)
        prop = cast(PropertyDecl, clazz.members[0])
        self.assertEqual(prop.mod, [Modifier.STATIC])

    def test_class18(self):
        sf = self.parse("class18.ts")
        self.assertEqual(sf[0].name, "Foo")
        clazz = cast(ClassDecl, sf[0])
        prop = cast(PropertyDecl, clazz.members[0])
        match prop.type:
            case NullableType(AppType(RefType("List"), args)):
                self.assertEqual(isinstance(args[0], NullableType), True)
            case _:
                self.assertFalse(prop.type)

    def test_ns(self):
        sf = self.parse("ns.ts")
        self.assertEqual(sf[0].name, "A")

    def test_enum(self):
        sf = self.parse("enum0.ts")
        self.assertEqual(len(sf), 5)

        # TODO see the fields
    def test_class19(self):
        sf = self.parse("class19.ts")
        self.assertEqual(sf[0].name, "Foo")
        clazz = cast(ClassDecl, sf[0])
        self.assertEqual(len(clazz.members), 5)

        # TODO see the fields
    def test_class20(self):
        sf = self.parse("class20.ts")
        self.assertEqual(sf[0].name, "Foo")
        clazz = cast(ClassDecl, sf[0])
        self.assertEqual(len(clazz.members), 12)

    def test_class21(self):
        sf = self.parse("class21.ts")
        self.assertEqual(len(sf), 4)

    # interface
    def test_interface0(self):
        sf = self.parse("interface0.ts")
        self.assertEqual(sf[0].name, "IRoomModel")
        intf = cast(InterfaceDecl, sf[0])
        self.assertEqual(len(intf.members), 87)

    def test_interface1(self):
        sf = self.parse("interface1.ts")
        self.assertEqual(sf[0].name, "IRoomModel")
        intf = cast(InterfaceDecl, sf[0])
        self.assertEqual(len(intf.members), 3)

    def test_interface2(self):
        sf = self.parse("interface2.ts")
        self.assertEqual(sf[0].name, "IRoomModel")
        intf = cast(InterfaceDecl, sf[0])
        self.assertEqual(len(intf.members), 3)
if __name__ == "__main__":
    unittest.main()
