local bindClass = luajava.bindClass
local LinearLayoutCompat = bindClass "androidx.appcompat.widget.LinearLayoutCompat"
local AppCompatTextView = bindClass "androidx.appcompat.widget.AppCompatTextView"
local MaterialCardView = bindClass "com.google.android.material.card.MaterialCardView"

return {
  LinearLayoutCompat,
  layout_width = -1,
  orientation = "vertical",
  {
    MaterialCardView,
    id = "card",
    layout_margin = "5dp",
    layout_width = -1,
    layout_height = "wrap_content",
    focusable = true,
    clickable = true,
    strokeWidth = 0,
    {
      LinearLayoutCompat,
      layout_width = -1,
      layout_height = "wrap_content",
      orientation = "vertical",
      padding = "12dp",
      {
        AppCompatTextView,
        id = "title",
        layout_width = "match",
        layout_height = "wrap_content",
        textColor = MyColor.colorOnBackground,
        textStyle = "bold",
        textSize = "15sp",
      },
      {
        AppCompatTextView,
        id = "content",
        layout_width = "match",
        layout_height = "wrap_content",
        textSize = "12sp",
        textColor = MyColor.colorOnSurfaceVariant,
        layout_marginTop = "6dp",
      },
    },
  },
}
