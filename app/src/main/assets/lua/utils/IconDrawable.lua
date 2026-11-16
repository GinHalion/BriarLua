local bindClass = luajava.bindClass
local PorterDuffColorFilter = bindClass "android.graphics.PorterDuffColorFilter"
local PorterDuff = bindClass "android.graphics.PorterDuff"
local BitmapDrawable = bindClass "android.graphics.drawable.BitmapDrawable"
local Bitmap = bindClass "android.graphics.Bitmap"
local Matrix = bindClass "android.graphics.Matrix"
local TypedValue = bindClass "android.util.TypedValue"

local cache = {}

return function(image, color, viewSizeDp)
  local key = tostring(image) .. "|" .. tostring(color) .. "|" .. tostring(viewSizeDp)
  local hit = cache[key]
  if hit ~= nil then return hit end

  local viewSizeDp = viewSizeDp or 24
  local r = activity.Resources.DisplayMetrics
  local targetPx = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, viewSizeDp, r)

  local colorFilter
  if color ~= "none" and color ~= 0 and color ~= nil then
    colorFilter = PorterDuffColorFilter(color or 0xff5f6368, PorterDuff.Mode.SRC_ATOP)
  end

  local ok, result = pcall(function()
    local bitmap = LuaBitmap.getLocalBitmap(activity.LuaDir .. "/res/drawable/" .. tostring(image) .. ".png")
    local size = bitmap.Width
    local scale = targetPx / size
    local matrix = Matrix()
    matrix.postScale(scale, scale)

    local bitmap2 = Bitmap.createBitmap(bitmap, 0, 0, size, size, matrix, true)
    return BitmapDrawable(activity.Resources, bitmap2).setColorFilter(colorFilter)
  end)

  cache[key] = ok and result or nil
  return cache[key]
end
