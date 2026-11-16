local bindClass = luajava.bindClass
local LinearLayoutCompat = bindClass "androidx.appcompat.widget.LinearLayoutCompat"
local AppCompatTextView = bindClass "androidx.appcompat.widget.AppCompatTextView"

return {
  LinearLayoutCompat,
  layout_width = -1,
  layout_height = -1,
  orientation = "vertical",
  gravity = "center",
  {
    AppCompatTextView,
    layout_marginTop = "24dp",
    text = "欢迎使用",
    textSize = "34sp",
    textColor = MyColor.colorPrimary,
  },
  {
    AppCompatTextView,
    layout_marginTop = "5dp",
    layout_margin = "24dp",
    text = "BriarLua",
    gravity = "center",
  },
}