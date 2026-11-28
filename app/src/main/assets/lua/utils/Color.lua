local _M = {}
local bindClass = luajava.bindClass
local MDC_R = bindClass "com.google.android.material.R"
local MaterialColors = bindClass "com.google.android.material.color.MaterialColors"
local android_R = bindClass "android.R"

local DEFAULT_STATE = android_R.attr.state_enabled

_M.get = function(table, key)
  local val
  pcall(function() val = table[key] end)
  return val
end

local getAttr = function(name)
  return _M.get(MDC_R.attr, name) or _M.get(android_R.attr, name)
end

return setmetatable({}, {
  __index = function(self, key)
    local val = _M.get(MaterialColors, key) or MaterialColors.getColor(this, getAttr(key), DEFAULT_STATE)
    rawset(self, key, val)
    return val
  end
})