local _M = {}
local bindClass = luajava.bindClass

local CLASSES = nil
local BY_SIMPLE = nil
local BY_FULL = nil

local catalogOk, ClassCatalog = pcall(bindClass, "com.briarlua.utils.ClassCatalog")

local function loadFromCatalog()
  if not catalogOk then return false end
  local ctx = rawget(_G, "activity") or rawget(_G, "this") or rawget(_G, "service")
  if not ctx then return false end
  local ok, raw = pcall(function()
    return ClassCatalog.getRaw(ctx)
  end)
  if not ok or type(raw) ~= "string" or #raw == 0 then return false end
  local list = {}
  for name in raw:gmatch("[^%s]+") do
    if name ~= "" then list[#list + 1] = name end
  end
  if #list == 0 then return false end
  CLASSES = list
  return true
end

local function loadTable()
  if CLASSES then return end

  if loadFromCatalog() then
   else
    local ok, t = pcall(require, "utils.PublicClasses")
    if not ok or (type(t) ~= "table" and type(t) ~= "string") then
      CLASSES = {}
      BY_SIMPLE = {}
      BY_FULL = {}
      return
    end

    if type(t) == "string" then
      local list = {}
      for name in t:gmatch("[^%s]+") do
        if name ~= "" then list[#list + 1] = name end
      end
      CLASSES = list
     else
      CLASSES = t
    end
  end

  BY_SIMPLE = {}
  BY_FULL = {}
  for _, full in ipairs(CLASSES) do
    BY_FULL[full] = full
    local simple = full:match("([^%.%$]+)$")
    if simple then
      local list = BY_SIMPLE[simple]
      if not list then
        list = {}
        BY_SIMPLE[simple] = list
      end
      list[#list + 1] = full
    end
  end
end

function _M.resolve(name)
  if not name or name == "" then return nil end
  loadTable()

  if name:find(".", 1, true) then
    local ok = pcall(bindClass, name)
    if ok then return name end
    if BY_FULL[name] then return name end
  end

  local list = BY_SIMPLE[name]
  if list and #list > 0 then
    if #list == 1 then return list[1] end
    return list
  end

  if name:match("^%u") then
    local ok = pcall(bindClass, name)
    if ok then return name end
  end
  return nil
end

function _M.candidates(name)
  if not name or name == "" then return {} end
  loadTable()
  return BY_SIMPLE[name] or {}
end

function _M.all()
  loadTable()
  return CLASSES
end

function _M.search(keyword)
  loadTable()
  if not keyword or keyword == "" then return CLASSES end
  local kw = keyword:lower()
  local result = {}
  for _, full in ipairs(CLASSES) do
    if full:lower():find(kw, 1, true) then
      result[#result + 1] = full
    end
  end
  return result
end

return _M
