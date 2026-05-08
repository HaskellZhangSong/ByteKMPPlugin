@ExportClass({id: 'ClassId0', name : 'ClassName0Proxy', package: 'huawei.data', arkts_module_path: "@huawei.data", export_all_fields : true})
class ClassNameTs0 {
    @ExportField({id: "field0"})
    fieldStr0Ts?: string;
    fieldStr1?: string;
    fieldNum1?: number;
    @ExportField({name: "fieldNum2", type: "kotlin.Long?"})
    fieldNumTs2?: number = 0;
    @NoExportField
    fieldNum3?: number;
    @ExportField({type: "kotlin.Long?"})
    fieldNum4?: number;
    fieldNum5?: number;
    fieldNum6?: number;
    fieldNum6?: number;
}

@ExportClass({name : "ClassName1Proxy", package: 'huawei.data', arkts_module_path: "@huawei.data", export_all_fields : true})
class ClassName1 {
    fieldStr0?: string;
    fieldStr1?: string;
    fieldNum1?: number;
    @ExportField({type: "kotlin.Long?"})
    fieldNum2?: number = 0;
    fieldList1?: collections.Array<string>;
    fieldList2?: collections.Array<ClassName2>;
    fieldMap1?: collections.Map<string, ClassName2>;
}
@ExportClass
class ClassName2 {
    fieldStr0?: string;
    fieldStr1?: string;
}

