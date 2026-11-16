local bindClass = luajava.bindClass
local CoordinatorLayout = bindClass "androidx.coordinatorlayout.widget.CoordinatorLayout"
local LuaWebView = bindClass "com.androlua.LuaWebView"

return {
  CoordinatorLayout,
  layout_width = -1,
  layout_height = -1,
  require "layout.appbar"(3),
  {
    LuaWebView,
    id = "myDocWebView",
    layout_width = -1,
    layout_height = -1,
    layout_behavior = "appbar_scrolling_view_behavior",
  },
}