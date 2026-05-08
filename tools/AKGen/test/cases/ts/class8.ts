@ExportClass({arkts_module_path: '@huawei/data', export_all : false, name : 'ClassName2Proxy'})
class ClassName2 {
    @ExportField({SharedId: 1})
    field1?: string | number | boolean = null;
    field2: string | number | boolean =  1 + 2;
}

@ExportClass({arkts_module_path: '@huawei/data', export_all : false, name : 'ClassName2Proxy'})
class ClassName3 {
    @ExportField({SharedId: 1})
    field1: string | number | boolean = null;
    field2: string | number | boolean =  1 + 2;
}