@ExportClass({name : "ClassName0", package: 'bd.dy',
            custom_code: `
            line1
                line2
                line3
                    line4`})
class ClassNameTs0 {
    @ExportField({custom_getter: `
            line1
                line2
                line3
                    line4`})
    field3? : number; // this is comment
}