@ShareClass(id = "com.ss.StatStructV2")
public class StatStruct() : Message<Nothing>(ADAPTER,
    ByteString.EMPTY) {
    @field:WireField(
    tag = 1,
    adapter = "com.wire.Adapter#INT64",
  )
  @SerializedName(value = "count")
  @ShareField(id = "count")
  @JvmField
  public var count: Long = 0L
}