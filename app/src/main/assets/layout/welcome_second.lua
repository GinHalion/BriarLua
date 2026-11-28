local bindClass = luajava.bindClass
local LinearLayoutCompat = bindClass "androidx.appcompat.widget.LinearLayoutCompat"
local AppCompatTextView = bindClass "androidx.appcompat.widget.AppCompatTextView"
local RecyclerView = bindClass "androidx.recyclerview.widget.RecyclerView"

return {
  LinearLayoutCompat,
  layout_width = -1,
  layout_height = -1,
  orientation = "vertical",
  gravity = "center",
  {
    AppCompatTextView,
    layout_margin = "16dp",
    text = "必要步骤",
    textSize = "34sp",
    textColor = MyColor.colorPrimary,
  },
  {
    RecyclerView,
    id = "myWelcomeRecyclerView",
    layout_width = -1,
    layout_height = -2,
  },
}