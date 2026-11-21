local bindClass = luajava.bindClass
local FrameLayout = bindClass "android.widget.FrameLayout"
local AppCompatTextView = bindClass "androidx.appcompat.widget.AppCompatTextView"
local LinearLayoutCompat = bindClass "androidx.appcompat.widget.LinearLayoutCompat"
local AppCompatImageView = bindClass "androidx.appcompat.widget.AppCompatImageView"
local SwipeRefreshLayout = bindClass "androidx.swiperefreshlayout.widget.SwipeRefreshLayout"
local RecyclerView = bindClass "androidx.recyclerview.widget.RecyclerView"
local CoordinatorLayout = bindClass "androidx.coordinatorlayout.widget.CoordinatorLayout"
local FloatingActionButton = bindClass "com.google.android.material.floatingactionbutton.FloatingActionButton"

return {
  CoordinatorLayout,
  layout_width = - 1,
  layout_height = - 1,
  require "layout.appbar"(1),
  {
    SwipeRefreshLayout,
    id = "mySwipeRefreshLayout",
    layout_width = - 1,
    layout_height = - 1,
    layout_behavior = "appbar_scrolling_view_behavior",
    {
      FrameLayout,
      layout_width = - 1,
      layout_height = - 1,
      {
        RecyclerView,
        id = "myProjectRecyclerView",
        layout_width = - 1,
        layout_height = - 1,
      },
      {
        LinearLayoutCompat,
        id = "myNoProjectTip",
        layout_width = "match",
        layout_height = "wrap_content",
        layout_gravity = "center",
        gravity = "center",
        orientation = "vertical",
        visibility = 8,
        {
          AppCompatImageView,
          id = "myNoProjectImage",
          layout_width = "70dp",
          layout_height = "70dp",
          colorFilter = MyColor.colorPrimary,
          layout_gravity = "center",
        },
        {
          AppCompatTextView,
          id = "myNoProjectText",
          layout_margin = "10dp",
          text = "没有任何项目",
          textSize = "14sp",
          textColor = MyColor.colorPrimary,
          layout_gravity = "center",
        },
      },
    },
  },
  {
    FloatingActionButton,
    id = "myFab",
    src = backImage("add"),
    layout_gravity = "bottom|end",
    layout_margin = "16dp",
  },
}