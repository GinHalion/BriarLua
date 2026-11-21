local bindClass = luajava.bindClass
local LinearLayoutCompat = bindClass "androidx.appcompat.widget.LinearLayoutCompat"
local AppCompatTextView = bindClass "androidx.appcompat.widget.AppCompatTextView"
local MaterialCardView = bindClass "com.google.android.material.card.MaterialCardView"

return {
  LinearLayoutCompat,
  layout_width = -1,
  layout_height = "wrap_content",
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
        LinearLayoutCompat,
        layout_width = "match",
        layout_height = "wrap_content",
        orientation = "horizontal",
        gravity = "center_vertical",
        layout_marginTop = "6dp",
        {
          MaterialCardView,
          layout_height = "wrap",
          StrokeWidth = 0,
          radius = "5dp",
          CardBackgroundColor = MyUI.setColorAlpha(MyColor.colorPrimary, 20),
          {
            AppCompatTextView,
            id = "content",
            layout_width = "wrap_content",
            layout_height = "wrap_content",
            textSize = "10sp",
            textColor = MyColor.colorPrimary,
            padding = "2dp",
            paddingLeft = "10dp",
            paddingRight = "10dp",
            gravity = "center",
          },
        },
        {
          AppCompatTextView,
          id = "returnType",
          layout_width = "wrap_content",
          layout_height = "wrap_content",
          textSize = "12sp",
          textColor = MyColor.colorOnSurfaceVariant,
          layout_marginLeft = "8dp",
        },
      },
    },
  },
}