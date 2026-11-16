local bindClass = luajava.bindClass
local CoordinatorLayout = bindClass "androidx.coordinatorlayout.widget.CoordinatorLayout"
local LinearLayoutCompat = bindClass "androidx.appcompat.widget.LinearLayoutCompat"
local TabLayout = bindClass "com.google.android.material.tabs.TabLayout"
local TextInputLayout = bindClass "com.google.android.material.textfield.TextInputLayout"
local TextInputEditText = bindClass "com.google.android.material.textfield.TextInputEditText"
local RecyclerView = bindClass "androidx.recyclerview.widget.RecyclerView"

return {
  CoordinatorLayout,
  layout_width = -1,
  layout_height = -1,

  require "layout.appbar"(1, {
    TabLayout,
    id = "myTabLayout",
    layout_width = -1,
    layout_height = "wrap_content",
    tabMode = TabLayout.MODE_SCROLLABLE,
    visibility = 8,
  }),

  {
    LinearLayoutCompat,
    layout_width = -1,
    layout_height = -1,
    orientation = "vertical",
    layout_behavior = "appbar_scrolling_view_behavior",

    {
      TextInputLayout,
      id = "mySearchLayout",
      layout_width = -1,
      layout_height = "wrap_content",
      layout_margin = "8dp",
      hint = "搜索日志",
      {
        TextInputEditText,
        id = "mySearchEdit",
        layout_width = -1,
        layout_height = "wrap_content",
        inputType = "text",
        singleLine = true,
      },
    },

    {
      RecyclerView,
      id = "myLogsRecycler",
      layout_width = -1,
      layout_height = 0,
      layout_weight = 1,
    },
  },
}