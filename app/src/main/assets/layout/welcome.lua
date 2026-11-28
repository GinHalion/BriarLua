local bindClass = luajava.bindClass
local CoordinatorLayout = bindClass "androidx.coordinatorlayout.widget.CoordinatorLayout"
local LinearLayoutCompat = bindClass "androidx.appcompat.widget.LinearLayoutCompat"
local MaterialButton = bindClass "com.google.android.material.button.MaterialButton"
local LinearProgressIndicator = bindClass "com.google.android.material.progressindicator.LinearProgressIndicator"
local ViewPager = bindClass "androidx.viewpager.widget.ViewPager"
local UiUtil = bindClass "com.briarlua.utils.UiUtil"

local MDC_R = bindClass "com.google.android.material.R"

local function MyViewPager()
  return luajava.override(ViewPager, {
    onInterceptTouchEvent = function(super, event)
      return false
    end,
    onTouchEvent = function(super, event)
      return false
    end
  })
end

return {
  LinearLayoutCompat,
  layout_width = -1,
  layout_height = -1,
  orientation = "vertical",
  LayoutTransition = newLayoutTransition(),
  {
    MyViewPager,
    id = "myWelcomePager",
    layout_marginTop = UiUtil.getStatusBarHeight(activity),
    layout_width = -1,
    layout_height = -1,
    layout_weight = 1,
    pages = {
      "layout.welcome_first",
      "layout.welcome_second",
    },
  },
  {
    LinearLayoutCompat,
    layout_width = -1,
    layout_margin = "16dp",
    {
      MaterialButton,
      id="previouGo",
      layout_gravity = "left|center",
      style = MDC_R.attr.materialButtonOutlinedStyle,
      text = "上一步",
      enabled = false,
    },
    {
      LinearLayoutCompat,
      layout_weight = 1,
      layout_height = -1,
      layout_width = "fill",
      gravity = "center",
      {
        LinearProgressIndicator,
        layout_marginLeft = "18dp",
        layout_marginRight = "18dp",
        layout_width = -1,
        progress = 0,
        id = "indicator",
        layout_gravity = "center",
      }
    },
    {
      MaterialButton,
      id = "nextGo",
      layout_gravity = "right|center",
      text = "下一步",
    },
  }
}