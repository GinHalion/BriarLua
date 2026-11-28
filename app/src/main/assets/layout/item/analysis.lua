local bindClass = luajava.bindClass
local LinearLayoutCompat = bindClass "androidx.appcompat.widget.LinearLayoutCompat"
local MaterialCardView = bindClass "com.google.android.material.card.MaterialCardView"
local AppCompatTextView = bindClass "androidx.appcompat.widget.AppCompatTextView"

return {
  LinearLayoutCompat,
  layout_width = -1,
  layout_height = "wrap_content",
  orientation = "vertical",
  {
    MaterialCardView,
    id = "card",
    layout_width = -1,
    layout_height = "wrap_content",
    layout_margin = "6dp",
    strokeWidth = "1dp",
    strokeColor = MyColor.colorOutlineVariant,
    cardBackgroundColor = MyColor.colorSurface,
    clickable = true,
    focusable = true,
    checkable = true,
    {
      LinearLayoutCompat,
      layout_width = -1,
      layout_height = "wrap_content",
      padding = "14dp",
      {
        AppCompatTextView,
        id = "title",
        layout_width = "match",
        layout_height = "wrap_content",
        textColor = MyColor.colorOnSurface,
        textSize = "14sp",
      },
    },
  },
}