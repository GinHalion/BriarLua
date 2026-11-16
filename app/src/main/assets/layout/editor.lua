local bindClass = luajava.bindClass
local TabLayout = bindClass "com.google.android.material.tabs.TabLayout"
local SwipeRefreshLayout = bindClass "androidx.swiperefreshlayout.widget.SwipeRefreshLayout"
local CoordinatorLayout = bindClass "androidx.coordinatorlayout.widget.CoordinatorLayout"
local LinearLayoutCompat = bindClass "androidx.appcompat.widget.LinearLayoutCompat"
local AppCompatImageView = bindClass "androidx.appcompat.widget.AppCompatImageView"
local AppCompatTextView = bindClass "androidx.appcompat.widget.AppCompatTextView"
local LuaEditor = bindClass "com.androlua.LuaEditor"
local FrameLayout = bindClass "android.widget.FrameLayout"
local DrawerLayout = bindClass "androidx.drawerlayout.widget.DrawerLayout"
local FileTreeView = bindClass "com.briarlua.views.FileTreeView"
local MaterialDivider = bindClass "com.google.android.material.divider.MaterialDivider"
local RecyclerView = bindClass "androidx.recyclerview.widget.RecyclerView"

return {
  CoordinatorLayout,
  layout_width = -1,
  layout_height = -1,

  require "layout.appbar"(3, {
    TabLayout,
    id = "myTabLayout",
    layout_width = -1,
    layout_height = "wrap_content",
    tabMode = TabLayout.MODE_SCROLLABLE,
    clipToPadding = false,
    inlineLabel = true,
  }),

  {
    DrawerLayout,
    id = "myDrawerLayout",
    layout_width = -1,
    layout_height = -1,
    layout_behavior = "appbar_scrolling_view_behavior",

    {
      LinearLayoutCompat,
      id = "myEditorMain",
      layout_width = -1,
      layout_height = -1,
      orientation = "vertical",

      {
        FrameLayout,
        layout_width = -1,
        layout_height = 0,
        layout_weight = 1,
        LayoutTransition = newLayoutTransition(),
        {
          LuaEditor,
          id = "myLuaEditor",
          layout_width = "match",
          layout_height = "match",
          visibility = 8,
        },
        {
          LinearLayoutCompat,
          id = "myNoFileTip",
          layout_width = "match",
          layout_height = "wrap_content",
          layout_gravity = "center",
          gravity = "center",
          orientation = "vertical",
          visibility = 0,
          {
            AppCompatImageView,
            id = "myNoFileImage",
            layout_width = "70dp",
            layout_height = "70dp",
            colorFilter = MyColor.colorPrimary,
            layout_gravity = "center",
          },
          {
            AppCompatTextView,
            id = "myNoFileText",
            layout_margin = "10dp",
            text = "需要打开文件",
            textSize = "14sp",
            textColor = MyColor.colorPrimary,
            layout_gravity = "center",
          },
        },
      },

      {
        LinearLayoutCompat,
        id = "mySymbolBar",
        layout_width = -1,
        layout_height = "wrap_content",
        orientation = "vertical",
        backgroundColor = MyColor.colorBackground,
        visibility = 8,
        LayoutTransition = newLayoutTransition(),
        {
          MaterialDivider,
          layout_width = -1,
        },
        {
          RecyclerView,
          id = "mySymbolRecycler",
          layout_width = -1,
          layout_height = "45dp",
        },
      },

      require "layout.editor_search",
    },

    {
      LinearLayoutCompat,
      id = "mySidebar",
      layout_width = "280dp",
      layout_height = -1,
      orientation = "vertical",
      layout_gravity = "start",
      backgroundColor = MyColor.colorBackground,
      {
        SwipeRefreshLayout,
        id = "mySwipeRefresh",
        layout_width = -1,
        layout_height = -1,
        {
          FileTreeView,
          id = "myFileTree",
          layout_width = -1,
          layout_height = -1,
        },
      },
    },
  },
}