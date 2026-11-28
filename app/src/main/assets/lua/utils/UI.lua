local _M = {}
local bindClass = luajava.bindClass
local TypedValue = bindClass "android.util.TypedValue"
local Resources = bindClass "android.content.res.Resources"

function _M.setTitle(title, fallback)
  local text = (title ~= nil and tostring(title) ~= "") and tostring(title) or (fallback or "BriarLua")
  local ok = pcall(function()
    local ab = activity.getSupportActionBar()
    if ab then ab.setTitle(text) end
  end)
  return ok, text
end

function _M.isNightMode()
  local Configuration = bindClass "android.content.res.Configuration"
  local currentNightMode = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK
  return currentNightMode == Configuration.UI_MODE_NIGHT_YES
end

function _M.getStatusBarHeight()
  local resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android")
  return activity.getResources().getDimensionPixelSize(resourceId)
end

function _M.dp2px(dp)
  return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().getDisplayMetrics())
end

function _M.sp2px(sp)
  return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, Resources.getSystem().getDisplayMetrics())
end

function _M.isEdgeToEdgeEnabled()
  local Build = bindClass "android.os.Build"
  return (Build.VERSION.SDK_INT >= 29)
end

function _M.applyEdgeToEdgePreference(window)
  pcall(function()
    local EdgeToEdgeUtils = bindClass "com.google.android.material.internal.EdgeToEdgeUtils"
    EdgeToEdgeUtils.applyEdgeToEdge(window, _M.isEdgeToEdgeEnabled())
    EdgeToEdgeUtils = nil
  end)
end

function _M.setColorAlpha(color, alpha)
  if alpha <= 1.0 then
    alpha = math.floor(alpha * 255 + 0.5)
  end
  alpha = math.min(255, math.max(0, alpha))
  local rgb = color & 0x00FFFFFF
  return (alpha << 24) | rgb
end

function _M.isColorLight(i, z)
  local MaterialColors = bindClass "com.google.android.material.color.MaterialColors"
  return MaterialColors.isColorLight(i) or (i == 0 and z)
end

function _M.statusBarAndNavigationBarColor(window, color, z)
  pcall(function()
    local WindowCompat = bindClass "androidx.core.view.WindowCompat"
    local insetsController = WindowCompat.getInsetsController(window, window.getDecorView())
    local myIsColorLight = _M.isColorLight(color, z)

    insetsController.setAppearanceLightStatusBars(myIsColorLight)
    insetsController.setAppearanceLightNavigationBars(myIsColorLight)

    window.setStatusBarColor(color)
    window.setNavigationBarColor(color)

    WindowCompat, insetsController, myIsColorLight = nil, nil, nil
  end)
end

function _M.applyRippleEffect(color, color2, topLeft, topRight, bottomLeft, bottomRight)
  local GradientDrawable = bindClass "android.graphics.drawable.GradientDrawable"
  local gradientDrawable = GradientDrawable()
    .setShape(0)
    .setColor(color)
    .setCornerRadii{
      _M.dp2px(topLeft), _M.dp2px(topLeft), _M.dp2px(topRight), _M.dp2px(topRight),
      _M.dp2px(bottomRight), _M.dp2px(bottomRight), _M.dp2px(bottomLeft), _M.dp2px(bottomLeft)
    }
  local RippleDrawable = bindClass "android.graphics.drawable.RippleDrawable"
  local ColorStateList = bindClass "android.content.res.ColorStateList"
  local rippleDrawable = RippleDrawable(ColorStateList.valueOf(color2), gradientDrawable, nil)
  return rippleDrawable
end

return _M