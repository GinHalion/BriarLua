local bindClass = luajava.bindClass
local LinearLayoutCompat = bindClass "androidx.appcompat.widget.LinearLayoutCompat"
local AppCompatTextView = bindClass "androidx.appcompat.widget.AppCompatTextView"
local AppCompatSeekBar = bindClass "androidx.appcompat.widget.AppCompatSeekBar"
local TextInputLayout = bindClass "com.google.android.material.textfield.TextInputLayout"
local TextInputEditText = bindClass "com.google.android.material.textfield.TextInputEditText"

return {
  LinearLayoutCompat,
  layout_width = -1,
  layout_height = -2,
  orientation = "vertical",
  padding = "20dp",

  {
    AppCompatTextView,
    id = "preview",
    layout_width = -1,
    layout_height = "60dp",
  },

  {
    TextInputLayout,
    layout_width = -1,
    layout_height = -2,
    layout_marginTop = "16dp",
    hint = "HEX 颜色 (如 #FF0000)",
    {
      TextInputEditText,
      id = "editText",
      layout_width = -1,
      layout_height = -2,
      inputType = "text",
      singleLine = true,
    }
  },

  {
    LinearLayoutCompat,
    layout_width = -1,
    layout_height = -2,
    orientation = "horizontal",
    gravity = "center_vertical",
    layout_marginTop = "16dp",
    {
      AppCompatTextView,
      text = "R",
      textColor = "#FF5252",
      textSize = "14sp",
      layout_width = "24dp",
    },
    {
      AppCompatSeekBar,
      id = "seekR",
      layout_width = 0,
      layout_weight = 1,
      layout_height = -2,
    }
  },

  {
    LinearLayoutCompat,
    layout_width = -1,
    layout_height = -2,
    orientation = "horizontal",
    gravity = "center_vertical",
    layout_marginTop = "8dp",
    {
      AppCompatTextView,
      text = "G",
      textColor = "#69F0AE",
      textSize = "14sp",
      layout_width = "24dp",
    },
    {
      AppCompatSeekBar,
      id = "seekG",
      layout_width = 0,
      layout_weight = 1,
      layout_height = -2,
    }
  },

  {
    LinearLayoutCompat,
    layout_width = -1,
    layout_height = -2,
    orientation = "horizontal",
    gravity = "center_vertical",
    layout_marginTop = "8dp",
    {
      AppCompatTextView,
      text = "B",
      textColor = "#448AFF",
      textSize = "14sp",
      layout_width = "24dp",
    },
    {
      AppCompatSeekBar,
      id = "seekB",
      layout_width = 0,
      layout_weight = 1,
      layout_height = -2,
    }
  },
}