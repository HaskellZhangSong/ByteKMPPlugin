
@ExportClass({ name : "SendableParent", package : "com.abc.def" })
class SendableParent {
  boolean_value?: boolean;
  int_value?: number;
  @ExportField({type : "Long?"})
  long_value?: number;
  @ExportField({type : "Float?"})
  float_value?: number;

  @ExportField({type : "Double?"})
  double_value?: number;
  string_value?: string;
  object_value?: SendableChild;

  @ExportField({type : "IntArray?"})
  int_array?: collections.Array<number>;
  @ExportField({type : "LongArray?"})
  long_array?: collections.Array<number>;

  boolean_list?: collections.Array<boolean>;

  @ExportField({type : "List<Int>?"})
  int_list?: collections.Array<number>;

  @ExportField({type : "List<Long>?"})
  long_list?: collections.Array<number>;
  string_list?: collections.Array<string>;
  object_list?: collections.Array<SendableChild>;

  boolean_map?: collections.Map<string, boolean>;

  @ExportField({type : "Map<String, Int>?"})
  int_map?: collections.Map<string, number>;
  @ExportField({type : "Map<String, Long>?"})
  long_map?: collections.Map<string, number>;

  string_map?: collections.Map<string, string>;
  object_map?: collections.Map<string, SendableChild>;

  @ExportField({ type : "List<List<Int>>?"})
  list_list_int?: collections.Array<collections.Array<number>>;

  @ExportField({ type : "List<Map<String, String>>?"})
  list_map_string?: collections.Array<collections.Map<string, string>>;

  map_list_string?: collections.Map<string, collections.Array<string>>;

  @ExportField({ type : "Map<String, Map<String, Int>>?" })
  map_map_int?: collections.Map<string, collections.Map<string, number>>;

}