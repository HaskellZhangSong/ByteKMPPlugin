@ExportClass({name : "ClassName0", package: 'bd.dy', custom_import: ["abc.def", "bcd.def"],
            custom_code: 'foo() \n {}'})
class Foo {

    foo1(arg1: number): number {
        return a + 1
    }
    public foo2(arg1: number): number {
        return a + 1;
    }

    static foo3(arg1: number): number {
        return a + 1
    }

    public static foo4(arg1: number): number {
        return a + 1
    }
}