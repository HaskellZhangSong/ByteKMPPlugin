@ShareClass(id = "com.abc.def")
class Video : Serializable {
    /**
     * <Aweme DTO Generator> 通用横封面
     */
    @SerializedName("universal_cover_horizontal")
    @JvmField
    @ShareField(id = "universal_cover_horizontal")
    var universalCoverHorizontal: com.ss.android.ugc.aweme.base.model.UrlModel? = null
    /**
     * <Aweme DTO Generator> 通用竖封面
     */
    @SerializedName("universal_cover_vertical")
    @JvmField
    @ShareField(id = "universal_cover_vertical")
    var universalCoverVertical: com.ss.android.ugc.aweme.base.model.UrlModel? = null
}