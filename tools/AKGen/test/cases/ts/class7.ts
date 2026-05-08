/**
* @param: number
* */
@ExportClass
class Foo {
    field1 : number = 0;
    field2 : boolean;
    field3 : string;
    field4 : bigint;
    field5 : null;
    field6 : undefined;
    field7 : collections.Array<string>;
    field8 : collections.Map<string, number>;
    field9 : RefType;
    field10 : string | null;
    field11 : number | undefined | null;
    @Deco({ name : "abc" })
    static readonly field12 : number;
    @Deco({ name : "abc" })
    static field13 : number;
    readonly field14 : number;
}