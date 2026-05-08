@ExportClass({id: "Room", name: "Room", package: "com.bd.kmp.live.infra.common.model"})
export interface IRoomModel extends JsModel<IRoomModel, "webcast.data.Room"> {
  // only for dy, saas use id_str instead
  @ExportField({id: "id"})
  id?: bigint
  @ExportField({id: "id_str"})
  id_str?: JsField<string, "id_str">
  @ExportField({id: "status"})
  status?: number
}