@ExportClass({arkts_module_path: "@dy/common_dto", export_all : false, name : "AwemeStructV2Proxy"})
class com_example_awemem{
    @ExportField({ name : "aweme_ID", type : "kotlin.Int"})
    aweme_id? : string;

    @ExportMethod({ params : { "param1": "kotlin.Long", "param2" : "String"} , return_type : "kotlin.Long" })
    computeSomething(param1: number, param2: string): number {
        return param1 + param2.length;
    }
}