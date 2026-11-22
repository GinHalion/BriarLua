local _M = {}
local bindClass = luajava.bindClass
local GradientDrawable = bindClass "android.graphics.drawable.GradientDrawable"
local MaterialAlertDialogBuilder = bindClass "com.google.android.material.dialog.MaterialAlertDialogBuilder"

local function parseHexColor(hex)
  if not hex then return nil end
  hex = tostring(hex):gsub("#", ""):gsub("%s+", "")
  if #hex == 6 then hex = "FF" .. hex end
  if #hex ~= 8 then return nil end
  local ok, color = pcall(function()
    return bindClass("android.graphics.Color").parseColor("#" .. hex)
  end)
  if ok then return color end
  return nil
end

local function colorToHex(color)
  return string.format("%08X", color)
end

function _M.show(activity, initialColor, onColorSelected)
  if not initialColor or type(initialColor) ~= "number" then
    initialColor = 0xFF2196F3
  end

  local initR = (initialColor >> 16) & 0xFF
  local initG = (initialColor >> 8) & 0xFF
  local initB = initialColor & 0xFF
  local currentColor = initialColor

  local views = {}
  local view = loadlayout("layout.color_picker", views)
  local preview = views.preview
  local editText = views.editText
  local seekR = views.seekR
  local seekG = views.seekG
  local seekB = views.seekB

  local bgDrawable = GradientDrawable()
  bgDrawable.setShape(GradientDrawable.RECTANGLE)
  bgDrawable.setCornerRadius(MyUI.dp2px(8))
  bgDrawable.setColor(currentColor)
  preview.setBackgroundDrawable(bgDrawable)

  local isUpdating = false
  local function updateUI(r, g, b)
    currentColor = (0xFF << 24) | (r << 16) | (g << 8) | b
    bgDrawable.setColor(currentColor)
    preview.setBackgroundDrawable(bgDrawable)
    if not isUpdating then
      isUpdating = true
      editText.setText("#" .. string.format("%02X%02X%02X", r, g, b))
      isUpdating = false
    end
  end

  editText.setText("#" .. colorToHex(currentColor):sub(3))
  seekR.setMax(255)
  seekG.setMax(255)
  seekB.setMax(255)
  seekR.setProgress(initR)
  seekG.setProgress(initG)
  seekB.setProgress(initB)

  local seekListener = luajava.createProxy("android.widget.SeekBar$OnSeekBarChangeListener", {
    onProgressChanged = function(sb, progress, fromUser)
      if fromUser then
        local r = seekR.getProgress()
        local g = seekG.getProgress()
        local b = seekB.getProgress()
        updateUI(r, g, b)
      end
    end,
    onStartTrackingTouch = function(sb) end,
    onStopTrackingTouch = function(sb) end
  })

  seekR.setOnSeekBarChangeListener(seekListener)
  seekG.setOnSeekBarChangeListener(seekListener)
  seekB.setOnSeekBarChangeListener(seekListener)

  editText.addTextChangedListener(luajava.createProxy("android.text.TextWatcher", {
    beforeTextChanged = function(s, start, count, after) end,
    onTextChanged = function(s, start, before, count) end,
    afterTextChanged = function(s)
      if isUpdating then return end
      local color = parseHexColor(tostring(s))
      if color then
        seekR.setProgress((color >> 16) & 0xFF)
        seekG.setProgress((color >> 8) & 0xFF)
        seekB.setProgress(color & 0xFF)
        currentColor = color
        bgDrawable.setColor(currentColor)
        preview.setBackgroundDrawable(bgDrawable)
      end
    end,
  }))

  MaterialAlertDialogBuilder(activity)
  .setTitle("调色板")
  .setView(view)
  .setPositiveButton("确定", function()
    if onColorSelected then onColorSelected(currentColor) end
  end)
  .setNegativeButton("取消", nil)
  .show()
end

return _M