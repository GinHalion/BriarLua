local bindClass = luajava.bindClass
local CoordinatorLayout = bindClass "androidx.coordinatorlayout.widget.CoordinatorLayout"
local RecyclerView = bindClass "androidx.recyclerview.widget.RecyclerView"

return {
  CoordinatorLayout,
  layout_width = -1,
  layout_height = -1,
  require "layout.appbar"(1),
  {
    RecyclerView,
    id = "myBuildRecyclerView",
    layout_width = -1,
    layout_height = -1,
    layout_behavior = "appbar_scrolling_view_behavior",
  },
}