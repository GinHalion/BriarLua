local bindClass = luajava.bindClass
local AppCompatImageView = bindClass "androidx.appcompat.widget.AppCompatImageView"
local LinearLayoutCompat = bindClass "androidx.appcompat.widget.LinearLayoutCompat"
local AppCompatTextView = bindClass "androidx.appcompat.widget.AppCompatTextView"
local MaterialCardView = bindClass "com.google.android.material.card.MaterialCardView"
local SwitchMaterial = bindClass "com.google.android.material.switchmaterial.SwitchMaterial"

return {
  TITLE = {
    LinearLayoutCompat,
    orientation = "vertical",
    layout_width = -1,
    layout_height = -2,
    padding = "8dp",
    paddingTop = "18dp",
    paddingLeft = "18dp",
    {
      AppCompatTextView,
      id = "title",
      textSize = "14sp",
      gravity = "center|left",
      textColor = MyColor.colorOnBackground,
    },
  },

  ARROW = {
    LinearLayoutCompat,
    id = "itemLayout",
    orientation = "vertical",
    layout_width = -1,
    layout_height = -2,
    padding = "3dp",
    paddingLeft = "8dp",
    paddingLeft = "8dp",
    {
      MaterialCardView,
      id = "card",
      layout_width = -1,
      layout_height = -2,
      radius = "16dp",
      StrokeWidth = 0,
      {
        LinearLayoutCompat,
        layout_height = -1,
        layout_width = -1,
        gravity = "center_vertical",
        {
          AppCompatImageView,
          layout_height = -1,
          layout_width = "45dp",
          layout_margin = "10dp",
          padding = "10dp",
          colorFilter = MyColor.colorOnBackground,
          id = "image",
        },
        {
          LinearLayoutCompat,
          orientation = "vertical",
          layout_width = 0,
          layout_weight = 1,
          layout_margin = "12dp",
          {
            AppCompatTextView,
            textColor = MyColor.colorOnBackground,
            singleLine = true,
            id = "title",
          },
          {
            AppCompatTextView,
            textSize = "12sp",
            maxLines = "1",
            ellipsize = "end",
            textColor = MyColor.colorOnSurface,
            singleLine = true,
            id = "content",
          },
        },
      },
    },
  },

  SWITCH = {
    LinearLayoutCompat,
    id = "itemLayout",
    orientation = "vertical",
    layout_width = -1,
    layout_height = -2,
    padding = "3dp",
    paddingLeft = "8dp",
    paddingLeft = "8dp",
    {
      MaterialCardView,
      id = "card",
      layout_width = -1,
      layout_height = -2,
      radius = "16dp",
      StrokeWidth = 0,
      {
        LinearLayoutCompat,
        layout_height = -1,
        layout_width = -1,
        gravity = "center_vertical",
        {
          AppCompatImageView,
          layout_height = -1,
          layout_width = "45dp",
          layout_margin = "10dp",
          padding = "10dp",
          colorFilter = MyColor.colorOnBackground,
          id = "image",
        },
        {
          LinearLayoutCompat,
          orientation = "vertical",
          layout_width = 0,
          layout_weight = 1,
          layout_margin = "12dp",
          {
            AppCompatTextView,
            textColor = MyColor.colorOnBackground,
            singleLine = true,
            id = "title",
          },
          {
            AppCompatTextView,
            textSize = "12sp",
            maxLines = "1",
            ellipsize = "end",
            textColor = MyColor.colorOnSurface,
            singleLine = true,
            id = "content",
          },
        },
        {
          SwitchMaterial,
          id = "switch",
          layout_marginEnd = "12dp",
          clickable = false,
          focusable = false,
        },
      },
    },
  },
}