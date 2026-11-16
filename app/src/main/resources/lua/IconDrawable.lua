local apply = luajava.bindClass
local PorterDuffColorFilter = apply "android.graphics.PorterDuffColorFilter"
local PorterDuff = apply "android.graphics.PorterDuff"
local BitmapDrawable = apply "android.graphics.drawable.BitmapDrawable"
local Bitmap = apply "android.graphics.Bitmap"
local Matrix = apply "android.graphics.Matrix"
local TypedValue = apply "android.util.TypedValue"

return function(image, color, viewSizeDp)
  local colorFilter
  if color ~= "none" and color ~= 0 and color ~= nil then
    colorFilter = PorterDuffColorFilter(
      color or 0xff5f6368, PorterDuff.Mode.SRC_ATOP)
  end

  local luaDir = activity.LuaDir
  if not luaDir then
    luaDir = activity.getLuaDir and activity.getLuaDir() or nil
  end
  if not luaDir then
    return nil
  end

  local path = luaDir .. "/" .. image
  local ok, bitmap = pcall(function()
    return LuaBitmap.getLocalBitmap(path)
  end)
  if not ok or not bitmap then
    return nil
  end

  local size = bitmap.getWidth()
  if size <= 0 then
    bitmap.recycle()
    return nil
  end

  local viewSizeDp = viewSizeDp or 24
  local r = activity.Resources.DisplayMetrics
  local scale = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, viewSizeDp, r) / size
  local matrix = Matrix()
  matrix.postScale(scale, scale)

  local scaled = Bitmap.createBitmap(bitmap, 0, 0, size, size, matrix, true)
  bitmap.recycle()

  local drawable = BitmapDrawable(activity.Resources, scaled)
  if colorFilter then
    drawable.setColorFilter(colorFilter)
  end
  return drawable
end