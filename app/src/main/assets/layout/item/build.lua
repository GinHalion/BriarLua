local bindClass = luajava.bindClass
local LinearLayoutCompat = bindClass "androidx.appcompat.widget.LinearLayoutCompat"
local AppCompatTextView = bindClass "androidx.appcompat.widget.AppCompatTextView"
local MaterialCardView = bindClass "com.google.android.material.card.MaterialCardView"

return {
  LinearLayoutCompat,
  layout_width = -1,
  layout_height = "wrap_content",
  padding = "4dp",
  {
    MaterialCardView,
    id = "card",
    layout_width = -1,
    layout_height = "wrap_content",
    layout_marginLeft = "6dp",
    layout_marginRight = "6dp",
    radius = "8dp",
    strokeWidth = "1.5dp",
    {
      AppCompatTextView,
      id = "text",
      layout_width = "match",
      layout_height = "wrap_content",
      textStyle = "bold",
      textColor = MyColor.colorOnBackground,
      padding = "12dp",
      textSize = "13sp",
    },
  },
}