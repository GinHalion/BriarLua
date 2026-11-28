local require = require
local table = require "table"
luajava.ids = luajava.ids or { id = 0x7f000000 }
local ids = luajava.ids
local _G = _G
local insert = table.insert
local new = luajava.new
local bindClass = luajava.bindClass

local LuaDrawable = bindClass "com.androlua.LuaDrawable"
local BitmapDrawable = bindClass "android.graphics.drawable.BitmapDrawable"
local MenuItem = bindClass "android.view.MenuItem"
local SubMenu = bindClass "android.view.SubMenu"
require "loadbitmap"
local loadbitmap = loadbitmap

local textColorPrimary

local function loadmenu(menu, t, root, n)
  if not textColorPrimary then
    local actionBar = activity.getSupportActionBar()
    if actionBar then
      local arry = actionBar.getThemedContext().getTheme()
        .obtainStyledAttributes({ android.R.attr.textColorPrimary })
      textColorPrimary = arry.getColorStateList(0)
      arry.recycle()
    end
  end
  root = root or _G
  n = n or 0
  for k, v in ipairs(t) do
    local id = ids.id
    ids.id = ids.id + 1
    if v[1] == MenuItem then
      local item = menu.add(v.group or 0, id, v.order or 0, v.title)
      if v.id then
        rawset(root, v.id, item)
        ids[v.id] = id
      end
      if k <= n then
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
      end
      if v.icon then
        local bmp = loadbitmap(v.icon)
        if bmp then
          item.setIcon(BitmapDrawable(bmp))
        else
          print("[loadmenu] icon 加载失败: " .. tostring(v.icon))
        end
      end
      if v.enabled == false then
        item.setEnabled(v.enabled)
      end
      if v.visible == false then
        item.setVisible(v.visible)
      end
      if v.singleColor and textColorPrimary then
        item.setIconTintList(textColorPrimary)
      end
    elseif v[1] == SubMenu then
      local item = menu.addSubMenu(v.group or 0, id, v.order or 0, v.title)
      loadmenu(item, v.items or {}, root, n)
    end
  end
end

return loadmenu