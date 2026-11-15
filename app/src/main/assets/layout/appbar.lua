local bindClass = luajava.bindClass
local AppBarLayout = bindClass "com.google.android.material.appbar.AppBarLayout"
local CollapsingToolbarLayout = bindClass "com.google.android.material.appbar.CollapsingToolbarLayout"
local MaterialToolbar = bindClass "com.google.android.material.appbar.MaterialToolbar"
local SubtitleCollapsingToolbarLayout = bindClass "com.google.android.material.appbar.SubtitleCollapsingToolbarLayout"
local UiUtil = bindClass "com.briarlua.utils.UiUtil"

return function (code, extras)
  local toolbarHeight = UiUtil.getActionBarSize(activity) + MyUI.dp2px(8)
  local function newToolbar(extra)
    local t = {
      MaterialToolbar,
      id = "myToolbar",
      layout_width = - 1,
      layout_height = toolbarHeight,
    }
    if extra then
      for k, v in pairs(extra) do t[k] = v end
    end
    return t
  end
  local function newAppBar(child, extraChildren)
    local t = {
      AppBarLayout,
      fitsSystemWindows = true,
      layout_width = - 1,
      id = "myAppbar",
      liftOnScroll = true,
      child,
    }
    if extraChildren then
      for _, v in ipairs(extraChildren) do
        t[#t + 1] = v
      end
    end
    return t
  end

  local extraChildren = extras
  if extraChildren and extraChildren.id then
    extraChildren = { extraChildren }
  end

  if code == 1 then
    return newAppBar({
      CollapsingToolbarLayout,
      layout_width = - 1,
      layout_height = "140dp",
      ExpandedTitleTextAppearance = R.style.TextAppearance_SubTitle2,
      CollapsedTitleTextAppearance = R.style.TextAppearance_SubTitle,
      layout_scrollFlags = 3,
      TitleCollapseMode = 2,
      newToolbar { layout_collapseMode = "pin" },
    }, extraChildren)
   elseif code == 2 then
    return newAppBar({
      SubtitleCollapsingToolbarLayout,
      layout_width = - 1,
      layout_height = "200dp",
      ExpandedTitleMarginStart = "16dp",
      ContentScrimColor = MyColor.colorSurfaceContainer,
      ExpandedTitleTextAppearance = R.style.TextAppearance_SubTitle2,
      CollapsedTitleTextAppearance = R.style.TextAppearance_SubTitle,
      ExpandedSubtitleTextAppearance = R.style.TextAppearance_SubTitle4,
      CollapsedSubtitleTextAppearance = R.style.TextAppearance_SubTitle4,
      layout_scrollFlags = 3,
      newToolbar { layout_collapseMode = "pin" },
    }, extraChildren)
   else
    local base = {
      AppBarLayout,
      fitsSystemWindows = true,
      layout_width = - 1,
      layout_height = - 2,
      id = "myAppbar",
      newToolbar {
        layout_height = - 2,
        backgroundColor = MyColor.colorBackground,
        layout_scrollFlags = 3,
      },
    }
    if extraChildren then
      for _, v in ipairs(extraChildren) do
        base[#base + 1] = v
      end
    end
    return base
  end
end