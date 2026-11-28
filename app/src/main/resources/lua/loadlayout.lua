local require = require
local _G = _G
local pairs = pairs
local ipairs = ipairs
local tonumber = tonumber
local tostring = tostring
local type = type
local rawset = rawset
local rawget = rawget
local error = error
local assert = assert
local pcall = pcall
local setmetatable = setmetatable
local getmetatable = getmetatable
local string = string
local table = table
local luajava = luajava

local stringMatch = string.match
local stringGsub = string.gsub
local stringFind = string.find
local stringUpper = string.upper
local stringSub = string.sub
local stringFormat = string.format
local tableUnpack = table.unpack
local tableInsert = table.insert
local tableConcat = table.concat
local tableSort = table.sort

local bindClass = luajava.bindClass
local newInstance = luajava.newInstance
local override = luajava.override
local instanceof = luajava.instanceof

local String = bindClass "java.lang.String"
local AndroidR = bindClass "android.R"
local View = bindClass "android.view.View"
local ViewGroup = bindClass "android.view.ViewGroup"
local Color = bindClass "android.graphics.Color"
local Typeface = bindClass "android.graphics.Typeface"
local ScaleType = bindClass "android.widget.ImageView$ScaleType"
local ArrayListAdapter = bindClass "android.widget.ArrayListAdapter"
local TruncateAt = bindClass "android.text.TextUtils$TruncateAt"
local ContextThemeWrapper = bindClass "androidx.appcompat.view.ContextThemeWrapper"
local TooltipCompat = bindClass "androidx.appcompat.widget.TooltipCompat"
local PagerAdapter = bindClass "androidx.viewpager.widget.PagerAdapter"
local MaterialColors = bindClass "com.google.android.material.color.MaterialColors"
local MaterialR = bindClass "com.google.android.material.R"
local NineBitmapDrawable = bindClass "com.androlua.NineBitmapDrawable"
local LuaBitmapDrawable = bindClass "com.androlua.LuaBitmapDrawable"
local Glide = bindClass "com.bumptech.glide.Glide"
local DiskCacheStrategy = bindClass "com.bumptech.glide.load.engine.DiskCacheStrategy"
local RequestOptions = bindClass "com.bumptech.glide.request.RequestOptions"
local MarginLayoutParams = ViewGroup.MarginLayoutParams

local okArrayList, JavaArrayList = pcall(bindClass, "java.util.ArrayList")
if not okArrayList then JavaArrayList = nil end
local okGridLayout, GridLayout = pcall(bindClass, "android.widget.GridLayout")
if not okGridLayout then GridLayout = nil end
local GridLayoutUndefined = GridLayout and GridLayout.UNDEFINED or -2147483648

local loadlayout
local initialContext = activity or service
local metrics = initialContext.getResources().getDisplayMetrics()
local widthPixels = metrics.widthPixels
local heightPixels = metrics.heightPixels
local density = metrics.density
local scaledDensity = metrics.scaledDensity
local xdpi = metrics.xdpi
local scaleTypeEnum = ScaleType.values()

local AndroidRAttr = nil
local function getAndroidRAttr()
  if AndroidRAttr == nil then
    local ok, v = pcall(function() return AndroidR.attr end)
    AndroidRAttr = (ok and v) or false
  end
  return AndroidRAttr
end

local LUA_STANDARD_TYPES = {
  ["nil"] = true, ["boolean"] = true, ["number"] = true, ["string"] = true,
  ["table"] = true, ["function"] = true, ["thread"] = true, ["userdata"] = true,
}
local function isJavaObject(v)
  return not LUA_STANDARD_TYPES[type(v)]
end

local function toLuaNumber(v)
  local t = type(v)
  if t == "number" then return v end
  if t == "userdata" or isJavaObject(v) then
    local ok, s = pcall(tostring, v)
    if ok and type(s) == "string" then
      return tonumber(s:match("^%-?%d+$"))
    end
  end
  return nil
end

local excludeAttributes = {
  [1] = true,
  layout_width = true,
  layout_height = true,
  layout_margin = true,
  layout_marginHorizontal = true,
  layout_marginVertical = true,
  layout_marginLeft = true,
  layout_marginTop = true,
  layout_marginRight = true,
  layout_marginBottom = true,
  padding = true,
  paddingHorizontal = true,
  paddingVertical = true,
  paddingLeft = true,
  paddingTop = true,
  paddingRight = true,
  paddingBottom = true,
  paddingStart = true,
  paddingEnd = true,
  theme = true,
  style = true,
  id = true,
  viewId = true,
  layout_row = true,
  layout_column = true,
  layout_rowSpan = true,
  layout_columnSpan = true,
}

local ruleConstants = {
  layout_above = 2,
  layout_alignBaseline = 4,
  layout_alignBottom = 8,
  layout_alignEnd = 19,
  layout_alignLeft = 5,
  layout_alignParentBottom = 12,
  layout_alignParentEnd = 21,
  layout_alignParentLeft = 9,
  layout_alignParentRight = 11,
  layout_alignParentStart = 20,
  layout_alignParentTop = 10,
  layout_alignRight = 7,
  layout_alignStart = 18,
  layout_alignTop = 6,
  layout_alignWithParentIfMissing = 0,
  layout_below = 3,
  layout_centerHorizontal = 14,
  layout_centerInParent = 13,
  layout_centerVertical = 15,
  layout_toEndOf = 17,
  layout_toLeftOf = 0,
  layout_toRightOf = 1,
  layout_toStartOf = 16
}

local sizeConstants = {
  match = -1, wrap = -2, match_parent = -1, fill_parent = -1, wrap_content = -2, fill = -1
}

local scaleTypeConstants = {
  matrix = 0, fitXY = 1, fitStart = 2, fitCenter = 3, fitEnd = 4, center = 5, centerCrop = 6, centerInside = 7
}

local gravityConstants = {
  axis_clip = 8, axis_pull_after = 4, axis_pull_before = 2, axis_specified = 1,
  axis_x_shift = 0, axis_y_shift = 4, bottom = 80, center = 17, center_horizontal = 1,
  center_vertical = 16, clip_horizontal = 8, clip_vertical = 128,
  display_clip_horizontal = 16777216, display_clip_vertical = 268435456, fill = 119,
  fill_horizontal = 7, fill_vertical = 112, horizontal_gravity_mask = 7, left = 3,
  no_gravity = 0, relative_horizontal_gravity_mask = 8388615,
  relative_layout_direction = 8388608, right = 5, start = 8388611, top = 48,
  vertical_gravity_mask = 112, ["end"] = 8388613
}

local layoutBehaviorConstants = {
  appbar_scrolling_view_behavior = "com.google.android.material.appbar.AppBarLayout$ScrollingViewBehavior",
  bottom_sheet_behavior = "com.google.android.material.bottomsheet.BottomSheetBehavior",
  fab_transformation_scrim_behavior = "com.google.android.material.transformation.FabTransformationScrimBehavior",
  fab_transformation_sheet_behavior = "com.google.android.material.transformation.FabTransformationSheetBehavior",
  hide_bottom_view_on_scroll_behavior = "com.google.android.material.behavior.HideBottomViewOnScrollBehavior"
}

local viewConstants = {
  ltr = 0, rtl = 1, inherit = 2, locale = 3,
  insideOverlay = 0x0, insideInset = 0x01000000, outsideOverlay = 0x02000000, outsideInset = 0x03000000,
  none = 0x00, web = 0x01, email = 0x02, phon = 0x04, map = 0x08, all = 0x0f,
  text = 0x00000001, textCapCharacters = 0x00001001, textCapWords = 0x00002001,
  textCapSentences = 0x00004001, textAutoCorrect = 0x00008001, textAutoComplete = 0x00010001,
  textMultiLine = 0x00020001, textImeMultiLine = 0x00040001, textNoSuggestions = 0x00080001,
  textUri = 0x00000011, textEmailAddress = 0x00000021, textEmailSubject = 0x00000031,
  textShortMessage = 0x00000041, textLongMessage = 0x00000051, textPersonName = 0x00000061,
  textPostalAddress = 0x00000071, textPassword = 0x00000081, textVisiblePassword = 0x00000091,
  textWebEditText = 0x000000a1, textFilter = 0x000000b1, textPhonetic = 0x000000c1,
  textWebEmailAddress = 0x000000d1, textWebPassword = 0x000000e1, number = 0x00000002,
  numberSigned = 0x00001002, numberDecimal = 0x00002002, numberPassword = 0x00000012,
  phone = 0x00000003, datetime = 0x00000004, date = 0x00000014, time = 0x00000024,
  normal = 0x00000000, actionUnspecified = 0x00000000, actionNone = 0x00000001,
  actionGo = 0x00000002, actionSearch = 0x00000003, actionSend = 0x00000004,
  actionNext = 0x00000005, actionDone = 0x00000006, actionPrevious = 0x00000007,
  flagNoFullscreen = 0x2000000, flagNavigatePrevious = 0x4000000, flagNavigateNext = 0x8000000,
  flagNoExtractUi = 0x10000000, flagNoAccessoryAction = 0x20000000,
  flagNoEnterAction = 0x40000000, flagForceAscii = 0x80000000,
  noScroll = 0, scroll = 1, exitUntilCollapsed = 2, enterAlways = 4,
  enterAlwaysCollapsed = 8, snap = 16, snapMargins = 32,
}

local unitConstants = { px = 0, dp = 1, sp = 2, pt = 3, ["in"] = 4, mm = 5 }

local dimensionConverterMap = {
  px = function(v) return v end,
  dp = function(v) return density * v end,
  sp = function(v) return scaledDensity * v end,
  pt = function(v) return xdpi * v * 0.013888889 end,
  ["in"] = function(v) return xdpi * v end,
  mm = function(v) return xdpi * v * 0.03937008 end,
  ["%w"] = function(v) return widthPixels * v * 0.01 end,
  ["%h"] = function(v) return heightPixels * v * 0.01 end
}

local function alyloader(path)
  local alypath = stringGsub(package.path, "%.lua;", ".aly;")
  local path, msg = package.searchpath(path, alypath)
  if msg then return msg end
  local f = io.open(path)
  local s = f:read("*a")
  f:close()
  if stringSub(s, 1, 4) == "\27Lua" then
    return assert(loadfile(path)), path
  else
    local f, st = load("return " .. s, stringMatch(path, "[^/]+/[^/]+$"), "bt")
    if st then
      error(stringGsub(st, "%b[]", path, 1), 0)
    end
    return f, st
  end
end
tableInsert(package.searchers, alyloader)

local function debugLog(...)
  if loadlayout and loadlayout.debug then
    pcall(print, "[loadlayout] " .. stringFormat(...))
  end
end

local function warnLog(...)
  pcall(print, "[loadlayout 警告] " .. stringFormat(...))
end

local function dump(t)
  local _t = { tostring(t), "\t{" }
  local first = rawget(t, 1)
  tableInsert(_t, "\t\t[1] = " .. tostring(first) .. ((isJavaObject(first) or type(first) == "userdata") and " (Java类)" or ""))
  for k, v in pairs(t) do
    if k == 1 then goto _continue_ end
    if type(v) == "table" then
      tableInsert(_t, "\t\t" .. tostring(k) .. " = { " .. tostring(rawget(v, 1)) .. " ... }")
    else
      tableInsert(_t, "\t\t" .. tostring(k) .. " = " .. tostring(v))
    end
    ::_continue_::
  end
  tableInsert(_t, "\t}")
  return tableConcat(_t, "\n")
end

local dimensionCache = {}

local function parseDimension(value)
  local result = tonumber(value) or dimensionCache[value]
  if result then return result end
  local num, unit = stringMatch(value, "([%+%-]?[%d%.]+)([%%%a]+)")
  num = assert(tonumber(num), "Unknown dimension " .. value)
  local converter = dimensionConverterMap[unit]
  if converter then
    result = converter(num)
  else
    error("Unknown dimension " .. value, 2)
  end
  dimensionCache[value] = result
  return result
end

local function parseSize(value)
  return sizeConstants[value] or parseDimension(value)
end

local appContext = initialContext
local function resolveContext()
  local ctx = _G.activity or _G.service
  if ctx then appContext = ctx end
  return appContext
end

local function getColorAttr(color)
  local success, result = pcall(function() return MaterialR.attr[color] end)
  if success then return result end
  success, result = pcall(function() return AndroidR.attr[color] end)
  if success then return result end
  error("Unknown color " .. color, 2)
end

local function parseColor(value)
  local result = tonumber(value)
  if result then return result end
  if type(value) == "string" then
    if stringSub(value, 1, 1) == "?" then
      return MaterialColors.getColorOrNull(resolveContext(), getColorAttr(stringSub(value, 2)))
    else
      return Color.parseColor(value)
    end
  end
  error("Unknown color: " .. value, 2)
end

local function parseConstants(value, constants)
  local result = tonumber(value)
  if result then return result end
  result = 0
  for item in (value .. "|"):gmatch("(.-)|") do
    local newItem = constants[item] or tonumber(item)
    assert(newItem, "Unknown value " .. item)
    result = result | newItem
  end
  return result
end

local function parseValue(value)
  local valueType = type(value)
  if valueType == "string" then
    local result = tonumber(value)
    if result then
      return result
    elseif value == "true" then
      return true
    elseif value == "false" then
      return false
    elseif value == "nil" then
      return nil
    elseif stringFind(value, "^#[%da-fA-F]+$") then
      return Color.parseColor(value)
    end
    local num, unit = stringMatch(value, "([%+%-]?[%d%.]+)([%%%a]+)")
    num = tonumber(num)
    if num and unit then
      result = dimensionCache[value]
      if result then return result end
      local converter = dimensionConverterMap[unit]
      if converter then result = converter(num) end
      if result then
        dimensionCache[value] = result
        return result
      end
    end
    result = 0
    for item in (value .. "|"):gmatch("(.-)|") do
      local newItem = viewConstants[item] or tonumber(item)
      if not newItem then
        result = nil
        break
      end
      result = result | newItem
    end
    if result then
      return result
    end
  end
  return value
end

local function parseValues(value)
  for k, v in ipairs(value) do
    value[k] = parseValue(v)
  end
  return tableUnpack(value)
end

local attrIdCache = {}
local appAttrIdCache = {}
local styleResIdCache = {}
local appPackageName = nil

local function getPackageName()
  if appPackageName == nil then
    local ok, name = pcall(function() return resolveContext().getPackageName() end)
    appPackageName = (ok and type(name) == "string" and name) or false
  end
  return appPackageName
end

local function getAndroidAttrId(name)
  local cached = attrIdCache[name]
  if cached ~= nil then return cached or nil end
  local id = nil
  local rAttr = getAndroidRAttr()
  if rAttr then
    local ok, v = pcall(function() return rAttr[name] end)
    if ok then id = toLuaNumber(v) end
  end
  if not id or id == 0 then
    local ok, v = pcall(function()
      return resolveContext().getResources().getIdentifier(name, "attr", "android")
    end)
    if ok then
      local n = toLuaNumber(v)
      if n and n ~= 0 then id = n end
    end
  end
  if not id or id == 0 then
    local ok, v = pcall(function()
      return resolveContext().getResources().getIdentifier("android:attr/" .. name, "attr", "android")
    end)
    if ok then
      local n = toLuaNumber(v)
      if n and n ~= 0 then id = n end
    end
  end
  attrIdCache[name] = id or false
  return id
end

local function getAppAttrId(name)
  local cached = appAttrIdCache[name]
  if cached ~= nil then return cached or nil end
  local id = nil
  local pkg = getPackageName()
  if pkg then
    local ok, v = pcall(function()
      return resolveContext().getResources().getIdentifier(name, "attr", pkg)
    end)
    if ok then
      local n = toLuaNumber(v)
      if n and n ~= 0 then id = n end
    end
  end
  if not id then
    local ok, v = pcall(function() return MaterialR.attr[name] end)
    if ok then
      local n = toLuaNumber(v)
      if n and n ~= 0 then id = n end
    end
  end
  if not id then
    id = getAndroidAttrId(name)
  end
  appAttrIdCache[name] = id or false
  return id
end

local function getStyleResId(name, packageName)
  local cacheKey = packageName .. ":" .. name
  local cached = styleResIdCache[cacheKey]
  if cached ~= nil then return cached or nil end
  local id = nil
  local ok, v = pcall(function()
    return resolveContext().getResources().getIdentifier(name, "style", packageName)
  end)
  if ok then
    local n = toLuaNumber(v)
    if n and n ~= 0 then id = n end
  end
  if not id and stringFind(name, "%.") then
    local alt = stringGsub(name, "%.", "_")
    ok, v = pcall(function()
      return resolveContext().getResources().getIdentifier(alt, "style", packageName)
    end)
    if ok then
      local n = toLuaNumber(v)
      if n and n ~= 0 then id = n end
    end
  end
  styleResIdCache[cacheKey] = id or false
  return id
end

local function resolveStyleSpec(styleValue)
  local name = stringMatch(styleValue, "^%?android:attr/([%w_]+)$")
  if name then
    local id = getAndroidAttrId(name)
    if id then return id, nil end
    error(stringFormat("未知 style 属性: %s (android.R.attr.%s 解析失败)", styleValue, name), 0)
  end
  name = stringMatch(styleValue, "^%?attr/([%w_]+)$")
  if name then
    local id = getAppAttrId(name)
    if id then return id, nil end
    error(stringFormat("未知 style 属性: %s (attr/%s 解析失败)", styleValue, name), 0)
  end
  local pkg, pname = stringMatch(styleValue, "^%?([%w_]+):attr/([%w_]+)$")
  if pkg then
    local realPkg = (pkg == "app") and getPackageName() or pkg
    local id = nil
    if realPkg then
      for _, form in ipairs({ pkg .. ":attr/" .. pname, pname }) do
        local ok, v = pcall(function()
          return resolveContext().getResources().getIdentifier(form, "attr", realPkg)
        end)
        if ok then
          local n = toLuaNumber(v)
          if n and n ~= 0 then id = n break end
        end
      end
    end
    if id then return id, nil end
    error(stringFormat("未知 style 属性: %s (%s:attr/%s 解析失败)", styleValue, pkg, pname), 0)
  end
  name = stringMatch(styleValue, "^@?android:style/([%w_%.]+)$")
  if name then
    local id = getStyleResId(name, "android")
    if id then return nil, id end
    error(stringFormat("未知 style 资源: %s (android:style/%s 解析失败)", styleValue, name), 0)
  end
  name = stringMatch(styleValue, "^@?style/([%w_%.]+)$") or stringMatch(styleValue, "^([%w_%.]+)$")
  if name then
    local appPkg = getPackageName()
    local id = appPkg and getStyleResId(name, appPkg) or nil
    if id then return nil, id end
    error(stringFormat("未知 style 资源: %s (style/%s 解析失败)", styleValue, name), 0)
  end
  error(stringFormat("无法识别的 style 格式: %s", tostring(styleValue)), 0)
end

local function LuaPagerAdapter(pageViews, pageTitles)
  local overrides = {
    getCount = function(super) return #pageViews end,
    instantiateItem = function(super, container, position)
      local pageView = pageViews[position + 1]
      container.addView(pageView)
      return pageView
    end,
    destroyItem = function(super, container, position, object)
      local pageView = pageViews[position + 1]
      container.removeView(pageView)
    end,
    isViewFromObject = function(super, view, object) return view == object end,
  }
  if pageTitles then
    overrides.getPageTitle = function(super, position) return pageTitles[position + 1] end
  end
  return override(PagerAdapter, overrides)
end

local function toJavaList(value)
  if type(value) ~= "table" or not JavaArrayList then return value end
  local ok, list = pcall(function()
    local list
    local okNew, inst = pcall(luajava.new, JavaArrayList)
    if okNew then
      list = inst
    else
      list = newInstance("java.util.ArrayList")
    end
    for _, item in ipairs(value) do list.add(item) end
    return list
  end)
  return ok and list or value
end

local attributeSetterMap = {
  items = function(view, value)
    local adapter = view.getAdapter()
    if adapter then
      pcall(function() adapter.addAll(toJavaList(value)) end)
    else
      adapter = ArrayListAdapter(resolveContext(), AndroidR.layout.simple_list_item_1, String(value))
      view.setAdapter(adapter)
    end
  end,
  pages = function(view, value, valueType, layoutParams, views)
    local pages = {}
    for k, v in ipairs(value) do
      local vType = type(v)
      pages[k] = (vType == "string" or vType == "table") and loadlayout(v, views) or v
    end
    view.setAdapter(LuaPagerAdapter(pages))
  end,
  pagesWithTitle = function(view, value, valueType, layoutParams, views)
    local pages = {}
    for k, v in ipairs(value[1]) do
      local vType = type(v)
      pages[k] = (vType == "string" or vType == "table") and loadlayout(v, views) or v
    end
    view.setAdapter(LuaPagerAdapter(pages, value[2]))
  end,
  background = function(view, value, valueType)
    if valueType == "string" then
      if stringSub(value, 1, 1) == "?" then
        local okId, resId = pcall(function()
          return resolveContext().getResources().getIdentifier(stringSub(value, 2), nil, nil)
        end)
        local idNum = okId and (toLuaNumber(resId) or tonumber(resId)) or nil
        if idNum and idNum ~= 0 then
          view.setBackgroundResource(idNum)
        else
          warnLog("background 资源解析失败，已保留原背景: %s", value)
        end
      elseif stringSub(value, 1, 1) == "#" then
        view.setBackgroundColor(parseColor(value))
      else
        if stringFind(value, "%.9%.png$") then
          view.setBackground(NineBitmapDrawable(value))
        else
          view.setBackground(LuaBitmapDrawable(resolveContext(), value))
        end
      end
    else
      view.setBackground(value)
    end
  end,
  onClick = function(view, value, valueType, layoutParams, views)
    view.onClick = valueType == "string" and function(v)
      (views[value] or _G[value])(v)
    end or value
  end,
  onLongClick = function(view, value, valueType, layoutParams, views)
    view.onLongClick = valueType == "string" and function(v)
      (views[value] or _G[value])(v)
    end or value
  end,
  src = function(view, value)
    local firstChar = stringSub(value, 1, 1)
    if firstChar ~= "/" and stringFind(value, "http") == nil then
      value = this.getLuaDir() .. "/" .. value
    end
    local options = RequestOptions().skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE)
    Glide.with(this).load(value).apply(options).into(view)
  end,
  gravity = function(view, value) view.setGravity(parseConstants(value, gravityConstants)) end,
  text = function(view, value) view.setText(value) end,
  tag = function(view, value) view.setTag(value) end,
  title = function(view, value) view.setTitle(value) end,
  subtitle = function(view, value) view.setSubtitle(value) end,
  hint = function(view, value) view.setHint(value) end,
  summary = function(view, value) view.setSummary(value) end,
  textAppearance = function(view, value)
    if not pcall(function() view.setTextAppearance(resolveContext(), value) end) then
      if not pcall(function() view.setTextAppearance(value) end) then
        warnLog("textAppearance 设置失败，已忽略: %s", tostring(value))
      end
    end
  end,
  url = function(view, value) view.loadUrl(value) end,
  tooltip = function(view, value) TooltipCompat.setTooltipText(view, value) end,
  textStyle = function(view, value)
    if value == "italic|bold" or value == "bold|italic" then
      view.setTypeface(nil, Typeface.BOLD_ITALIC)
    else
      view.setTypeface(nil, Typeface[stringUpper(value)])
    end
  end,
  scaleType = function(view, value)
    view.setScaleType(scaleTypeEnum[scaleTypeConstants[value]] or tonumber(value))
  end,
  ellipsize = function(view, value) view.setEllipsize(TruncateAt[stringUpper(value)]) end,
  layoutDirection = function(view, value) view.setLayoutDirection(viewConstants[value] or tonumber(value)) end,
  imeOptions = function(view, value) view.setImeOptions(viewConstants[value] or tonumber(value)) end,
  inputType = function(view, value) view.setInputType(viewConstants[value] or tonumber(value)) end,
  textAlignment = function(view, value) view.setTextAlignment(viewConstants[value] or tonumber(value)) end,
  autoLink = function(view, value) view.setAutoLink(viewConstants[value] or tonumber(value)) end,
  scrollbarStyle = function(view, value) view.setScrollbarStyle(viewConstants[value] or tonumber(value)) end,
  textSize = function(view, value)
    local size = tonumber(value)
    if size then
      view.setTextSize(unitConstants.px, size)
    else
      local num, unit = stringMatch(value, "([%+%-]?[%d%.]+)([%%%a]+)")
      view.setTextSize(unitConstants[unit], tonumber(num))
    end
  end,
  radius = function(view, value) view.setRadius(parseSize(value)) end,
  cardElevation = function(view, value) view.setCardElevation(parseSize(value)) end,
  elevation = function(view, value) view.setElevation(parseSize(value)) end,
  strokeWidth = function(view, value) view.setStrokeWidth(parseSize(value)) end,
  minHeight = function(view, value) view.setMinimumHeight(parseSize(value)) end,
  minWidth = function(view, value) view.setMinimumWidth(parseSize(value)) end,
  textColor = function(view, value) view.setTextColor(parseColor(value)) end,
  backgroundColor = function(view, value) view.setBackgroundColor(parseColor(value)) end,
  password = function(view, value)
    view.setInputType((value == true or value == "true") and 0x81 or 0x00)
  end,
  drawingCacheQuality = function(view, value)
    view.setDrawingCacheQuality(value == "auto" and 0 or value == "low" and 1 or value == "high" and 2 or tonumber(value))
  end,
  orientation = function(view, value)
    view.setOrientation(value == "vertical" and 1 or value == "horizontal" and 0 or tonumber(value))
  end,
  importantForAccessibility = function(view, value)
    view.setImportantForAccessibility(value == "auto" and 0 or value == "yes" and 1 or value == "no" and 2 or tonumber(value))
  end,
  layerType = function(view, value)
    view.setLayerType(value == "none" and 0 or value == "software" and 1 or value == "hardware" and 2 or tonumber(value))
  end,
  visibility = function(view, value)
    view.setVisibility(value == "visible" and 0 or value == "invisible" and 4 or value == "gone" and 8 or tonumber(value))
  end,
  layout_collapseMode = function(view, value, valueType, layoutParams)
    local ok = pcall(function()
      layoutParams.setCollapseMode(value == "pin" and 1 or value == "parallax" and 2 or tonumber(value))
    end)
    if not ok then
      warnLog("layout_collapseMode 不适用于 %s，已忽略", tostring(layoutParams.getClass().getName()))
    end
  end,
  layout_anchor = function(view, value, valueType, layoutParams, views, deferredSet)
    if type(value) == "number" then
      if not pcall(function() layoutParams.setAnchorId(value) end) then
        warnLog("layout_anchor 不适用于 %s，已忽略", tostring(layoutParams.getClass().getName()))
      end
      return
    end
    local target = rawget(views, value)
    if target then
      if not pcall(function() layoutParams.setAnchorId(target.getId()) end) then
        warnLog("layout_anchor 不适用于 %s，已忽略", tostring(layoutParams.getClass().getName()))
      end
    else
      deferredSet[#deferredSet + 1] = {
        value = value,
        layoutParams = layoutParams,
        apply = function(lp, targetId) lp.setAnchorId(targetId) end,
      }
    end
  end,
  layout_collapseParallaxMultiplier = function(view, value, valueType, layoutParams)
    pcall(function() layoutParams.setParallaxMultiplier(tonumber(value)) end)
  end,
  layout_behavior = function(view, value, valueType, layoutParams)
    local behavior = layoutBehaviorConstants[value]
    local ok = pcall(function()
      layoutParams.setBehavior(behavior and newInstance(behavior) or value)
    end)
    if not ok then
      warnLog("layout_behavior 不适用于 %s，已忽略", tostring(layoutParams.getClass().getName()))
    end
  end,
  layout_weight = function(view, value, valueType, layoutParams)
    if not pcall(function() layoutParams.weight = tonumber(value) end) then
      warnLog("layout_weight 不适用于 %s，已忽略", tostring(layoutParams.getClass().getName()))
    end
  end,
  layout_gravity = function(view, value, valueType, layoutParams)
    local gravity = parseConstants(value, gravityConstants)
    if not pcall(function() layoutParams.setGravity(gravity) end) then
      if not pcall(function() layoutParams.gravity = gravity end) then
        warnLog("layout_gravity 不适用于 %s，已忽略", tostring(layoutParams.getClass().getName()))
      end
    end
  end,
  layout_scrollFlags = function(view, value, valueType, layoutParams)
    local ok = pcall(function()
      layoutParams.setScrollFlags(parseConstants(value, viewConstants))
    end)
    if not ok then
      warnLog("layout_scrollFlags 不适用于 %s，已忽略", tostring(layoutParams.getClass().getName()))
    end
  end,
  layout_marginStart = function(view, value, valueType, layoutParams)
    if not pcall(function() layoutParams.setMarginStart(parseSize(value)) end) then
      warnLog("layout_marginStart 不适用于 %s，已忽略", tostring(layoutParams.getClass().getName()))
    end
  end,
  layout_marginEnd = function(view, value, valueType, layoutParams)
    if not pcall(function() layoutParams.setMarginEnd(parseSize(value)) end) then
      warnLog("layout_marginEnd 不适用于 %s，已忽略", tostring(layoutParams.getClass().getName()))
    end
  end,
  onCreate = function(view, value, valueType, layoutParams) value(view, layoutParams) end,
  alignmentMode = function(view, value)
    local mode = tonumber(value)
    if not mode then
      if value == "alignBounds" then mode = 0
      elseif value == "alignMargins" then mode = 1
      else
        warnLog("Unknown alignmentMode: %s", tostring(value))
        return
      end
    end
    local ok = pcall(function() view.setAlignmentMode(mode) end)
    if not ok then
      warnLog("alignmentMode 不适用于 %s，已忽略", tostring(view.getClass().getName()))
    end
  end,
}

local function makeConstraintSetter(field)
  return function(layoutParams, targetId) layoutParams[field] = targetId end
end

local constraintSetters = {
  layout_constraintStart_toStartOf = makeConstraintSetter("startToStart"),
  layout_constraintStart_toEndOf = makeConstraintSetter("startToEnd"),
  layout_constraintLeft_toLeftOf = makeConstraintSetter("leftToLeft"),
  layout_constraintLeft_toRightOf = makeConstraintSetter("leftToRight"),
  layout_constraintRight_toRightOf = makeConstraintSetter("rightToRight"),
  layout_constraintRight_toLeftOf = makeConstraintSetter("rightToLeft"),
  layout_constraintEnd_toEndOf = makeConstraintSetter("endToEnd"),
  layout_constraintEnd_toStartOf = makeConstraintSetter("endToStart"),
  layout_constraintTop_toTopOf = makeConstraintSetter("topToTop"),
  layout_constraintTop_toBottomOf = makeConstraintSetter("topToBottom"),
  layout_constraintBottom_toBottomOf = makeConstraintSetter("bottomToBottom"),
  layout_constraintBottom_toTopOf = makeConstraintSetter("bottomToTop"),
}

-- 性能优化：setterCache 改为按 Class 对象两级缓存。
-- 旧版每次 setAttribute 都要 view.getClass().getName() 拼字符串，
-- 两次 Java 调用 + 字符串拼接。改为 Class 对象（全局单例）作一级 key，
-- 属性名作二级 key，省掉 getName() 和字符串拼接。
local classSetterCache = {}

-- 性能优化：LayoutParams 嵌套类反射结果缓存。
-- getDeclaredClasses() 是重反射，旧版每个子 View 都调一次；
-- 同一父容器类的 LayoutParams 嵌套类不变，缓存后只反射一次。
local lpClassCache = {}

-- 性能优化：deferredSet 回填时 views 查表结果缓存。
-- 同一目标被多个规则引用时，避免重复 rawget + getId()。
local pendingResolve = {}
local deferredSet = {}
local loadDepth = 0

local function setAttribute(view, attribute, value, layoutParams, views)
  local valueType = type(value)

  local rule = ruleConstants[attribute]
  if rule then
    if value == true or value == "true" then
      pcall(function() layoutParams.addRule(rule) end)
    elseif value == false or value == "false" then
      pcall(function() layoutParams.addRule(rule, 0) end)
    elseif type(value) == "number" then
      pcall(function() layoutParams.addRule(rule, value) end)
    else
      local target = rawget(views, value)
      if target then
        pcall(function() layoutParams.addRule(rule, target.getId()) end)
      else
        deferredSet[#deferredSet + 1] = { rule = rule, value = value, layoutParams = layoutParams }
      end
    end
    return
  end

  local constraintSetter = constraintSetters[attribute]
  if constraintSetter then
    if value == "parent" then
      pcall(constraintSetter, layoutParams, 0)
    elseif type(value) == "number" then
      pcall(constraintSetter, layoutParams, value)
    else
      local target = rawget(views, value)
      if target then
        pcall(constraintSetter, layoutParams, target.getId())
      else
        deferredSet[#deferredSet + 1] = { constraintSetter = constraintSetter, value = value, layoutParams = layoutParams }
      end
    end
    return
  end

  local setter = attributeSetterMap[attribute]
  if setter then
    setter(view, value, valueType, layoutParams, views, deferredSet)
    return
  end

  local originalAttribute = attribute
  local layoutParamsAttribute = stringMatch(attribute, "layout_(%a+)")
  if layoutParamsAttribute then
    attribute = layoutParamsAttribute
    view = layoutParams
  end

  if valueType == "table" then
    local okT, errT = pcall(function()
      view["set" .. stringGsub(attribute, "^(%a)", stringUpper)](parseValues(value))
    end)
    if not okT then
      error(stringFormat("table 属性 %s 的 setter 调用失败: %s", originalAttribute, tostring(errT)), 0)
    end
  else
    -- 性能优化：Class 对象作一级 key，属性名作二级 key，避免 getName() 字符串拼接
    local cls = view.getClass()
    local attrCache = classSetterCache[cls]
    if not attrCache then
      attrCache = {}
      classSetterCache[cls] = attrCache
    end
    local mode = attrCache[originalAttribute]
    if mode == nil then
      local ok = pcall(function()
        view["set" .. stringGsub(attribute, "^(%a)", stringUpper)](parseValue(value))
      end)
      mode = ok and "setter" or "assign"
      attrCache[originalAttribute] = mode
      if not ok then
        view[attribute] = parseValue(value)
      end
    elseif mode == "setter" then
      view["set" .. stringGsub(attribute, "^(%a)", stringUpper)](parseValue(value))
    else
      view[attribute] = parseValue(value)
    end
  end
end

local function setPadding(layout, view)
  local padding = layout.padding
  local paddingHorizontal = layout.paddingHorizontal or padding
  local paddingVertical = layout.paddingVertical or padding
  local paddingLeft = layout.paddingLeft or paddingHorizontal
  local paddingTop = layout.paddingTop or paddingVertical
  local paddingRight = layout.paddingRight or paddingHorizontal
  local paddingBottom = layout.paddingBottom or paddingVertical
  local paddingStart = layout.paddingStart
  local paddingEnd = layout.paddingEnd
  if paddingStart or paddingEnd then
    view.setPaddingRelative(
      parseSize(paddingStart or padding or 0),
      parseSize(paddingTop or 0),
      parseSize(paddingEnd or padding or 0),
      parseSize(paddingBottom or 0)
    )
  elseif paddingLeft or paddingTop or paddingRight or paddingBottom then
    view.setPadding(
      parseSize(paddingLeft or 0),
      parseSize(paddingTop or 0),
      parseSize(paddingRight or 0),
      parseSize(paddingBottom or 0)
    )
  end
end

local function setMargins(layout, layoutParams)
  local layoutMargin = layout.layout_margin
  local layoutMarginHorizontal = layout.layout_marginHorizontal or layoutMargin
  local layoutMarginVertical = layout.layout_marginVertical or layoutMargin
  local layoutMarginLeft = layout.layout_marginLeft or layoutMarginHorizontal
  local layoutMarginTop = layout.layout_marginTop or layoutMarginVertical
  local layoutMarginRight = layout.layout_marginRight or layoutMarginHorizontal
  local layoutMarginBottom = layout.layout_marginBottom or layoutMarginVertical
  if layoutMarginLeft or layoutMarginTop or layoutMarginRight or layoutMarginBottom then
    local ok = pcall(function()
      layoutParams.setMargins(
        parseSize(layoutMarginLeft or 0),
        parseSize(layoutMarginTop or 0),
        parseSize(layoutMarginRight or 0),
        parseSize(layoutMarginBottom or 0)
      )
    end)
    if not ok then
      warnLog("layout_margin 不适用于 %s，已忽略", tostring(layoutParams.getClass().getName()))
    end
  end
end

local function getNestedLayoutParamsClass(parentViewClass)
  local name = parentViewClass.getName()
  local cached = lpClassCache[name]
  if cached ~= nil then return cached or nil end
  local okList, classList = pcall(function()
    return luajava.astable(parentViewClass.getDeclaredClasses())
  end)
  if okList and type(classList) == "table" then
    for _, c in ipairs(classList) do
      local okName, simpleName = pcall(function() return c.getSimpleName() end)
      if okName and simpleName == "LayoutParams" then
        lpClassCache[name] = c
        return c
      end
    end
  end
  lpClassCache[name] = false
  return nil
end

local function createLayoutParams(layout, parentView, parentViewClass)
  local w = parseSize(layout.layout_width or -2)
  local h = parseSize(layout.layout_height or -2)

  -- GridLayout 专用：必须用 (Spec, Spec) 构造器，spec 在构造期传入才生效。
  -- 性能优化：先确认父容器确实是 GridLayout 才走此分支，避免对非 GridLayout
  -- 父容器做无谓的 pcall 失败。
  if GridLayout and (layout.layout_row ~= nil or layout.layout_column ~= nil
      or layout.layout_rowSpan ~= nil or layout.layout_columnSpan ~= nil) then
    local isGrid = false
    if parentView then
      isGrid = instanceof(parentView, GridLayout)
    elseif parentViewClass then
      local okIs, v = pcall(function()
        return parentViewClass.getName() == "android.widget.GridLayout"
      end)
      isGrid = okIs and v
    end
    if isGrid then
      local row, rowSpan = layout.layout_row, layout.layout_rowSpan
      local column, columnSpan = layout.layout_column, layout.layout_columnSpan
      local ok, lp = pcall(function()
        local rowSpec = GridLayout.spec(
          row and tonumber(row) or GridLayoutUndefined,
          rowSpan and tonumber(rowSpan) or 1)
        local colSpec = GridLayout.spec(
          column and tonumber(column) or GridLayoutUndefined,
          columnSpan and tonumber(columnSpan) or 1)
        local cls = parentViewClass
        if not cls and parentView then
          pcall(function() cls = parentView.getClass() end)
        end
        if not cls then cls = GridLayout end
        return cls.LayoutParams(rowSpec, colSpec)
      end)
      if ok and lp then
        pcall(function() lp.width = w end)
        pcall(function() lp.height = h end)
        return lp
      end
    end
  end

  if parentView then
    local ok, lp = pcall(function() return parentView.generateDefaultLayoutParams() end)
    if ok and lp then
      pcall(function() lp.width = w end)
      pcall(function() lp.height = h end)
      return lp
    end
    if parentViewClass == nil then
      local okC, cls = pcall(function() return parentView.getClass() end)
      if okC then parentViewClass = cls end
    end
  end

  if parentViewClass then
    local ok, lp = pcall(function() return parentViewClass.LayoutParams(w, h) end)
    if ok then return lp end
  end

  if parentViewClass then
    local nestedClass = getNestedLayoutParamsClass(parentViewClass)
    if nestedClass then
      local okLp, inst = pcall(function() return nestedClass(w, h) end)
      if okLp then return inst end
    end
  end

  return MarginLayoutParams(w, h)
end

local function constructView(viewClass, context, styleAttrId, styleResId)
  local styledErr = nil
  if styleAttrId then
    local ok, v = pcall(viewClass, context, nil, styleAttrId)
    if ok then
      debugLog("styled(defStyleAttr=%d) 构造成功: %s", styleAttrId, tostring(viewClass))
      return v
    end
    styledErr = v
  elseif styleResId then
    local ok, v = pcall(viewClass, context, nil, 0, styleResId)
    if ok then
      debugLog("styled(defStyleRes=%d) 构造成功: %s", styleResId, tostring(viewClass))
      return v
    end
    styledErr = v
  end

  local ok, v = pcall(viewClass, context)
  if ok then
    if styledErr then
      warnLog("style 构造失败，已回退为无样式构造:\n  类: %s\n  错误: %s", tostring(viewClass), tostring(styledErr))
    end
    return v
  end

  local ok2, v2 = pcall(viewClass)
  if ok2 then
    if styledErr then
      warnLog("style 构造失败，已回退为无参构造:\n  类: %s\n  错误: %s", tostring(viewClass), tostring(styledErr))
    end
    return v2
  end

  error(tostring(styledErr or v), 0)
end

local viewIdRegistry = {}

local function createView(layout, views, parentView, parentViewClass)
  local view = assert(layout[1], "missing first value in layout table (布局表[1]缺少视图类)")
  local theme = layout.theme
  local styleSpec = layout.style
  local context = resolveContext()

  if theme ~= nil then
    local themeId = theme
    if type(theme) == "string" then
      themeId = getStyleResId(stringGsub(theme, "^@?android:style/", ""), "android")
      if not themeId then
        local appPkg = getPackageName()
        themeId = appPkg and getStyleResId(theme, appPkg) or nil
      end
      assert(themeId, "Unknown theme " .. tostring(theme))
    else
      themeId = toLuaNumber(theme) or theme
    end
    local ok, wrapped = pcall(ContextThemeWrapper, context, themeId)
    if ok then
      context = wrapped
    else
      warnLog("theme 包装失败，使用原始 context: %s", tostring(wrapped))
    end
  end

  local styleAttrId, styleResId
  if styleSpec ~= nil then
    local t = type(styleSpec)
    if t == "number" then
      assert(styleSpec ~= 0, "style 数值不能为 0")
      styleAttrId = styleSpec
    elseif t == "string" then
      styleAttrId, styleResId = resolveStyleSpec(styleSpec)
    elseif t == "userdata" or isJavaObject(styleSpec) then
      styleAttrId = toLuaNumber(styleSpec)
      assert(styleAttrId and styleAttrId ~= 0, "style 值不是有效的资源 ID: " .. tostring(styleSpec))
    else
      error("style 属性的值必须是字符串或数字，收到 " .. t, 0)
    end
  end

  local viewClass
  if instanceof(view, View) then
    viewClass = view.getClass()
    if styleSpec ~= nil then
      warnLog("layout[1] 已是 View 实例，style 属性无法应用")
    end
  elseif isJavaObject(view) then
    viewClass = view
    view = constructView(viewClass, context, styleAttrId, styleResId)
  elseif type(view) == "string" then
    local ok, cls = pcall(bindClass, view)
    if not ok then
      error("Unknown view class: " .. tostring(view) .. "\n" .. tostring(cls), 0)
    end
    viewClass = cls
    view = constructView(cls, context, styleAttrId, styleResId)
  elseif type(view) == "function" then
    local ok, instance = pcall(view, context)
    if not ok then
      error("函数工厂创建视图失败: " .. tostring(instance), 0)
    end
    view = instance
    local okC, cls = pcall(function() return view.getClass() end)
    if okC then viewClass = cls end
  elseif type(view) == "table" then
    if rawget(view, "__class") then
      viewClass = rawget(view, "__class")
      view = constructView(viewClass, context, styleAttrId, styleResId)
    elseif getmetatable(view) and getmetatable(view).__call then
      viewClass = view
      view = constructView(view, context, styleAttrId, styleResId)
    else
      error("Invalid view type: table", 0)
    end
  else
    error("Invalid view type: " .. type(view), 0)
  end

  local viewId = layout.viewId
  if viewId ~= nil then
    viewId = toLuaNumber(viewId) or tonumber(viewId) or viewId
  end
  local okSetId, errSetId = pcall(function()
    view.setId(viewId or View.generateViewId())
  end)
  if not okSetId then
    warnLog("view.setId 失败（viewId=%s）: %s", tostring(layout.viewId), tostring(errSetId))
  end
  local id = layout.id
  if id ~= nil then
    views[id] = view
    local okId, numId = pcall(function() return view.getId() end)
    if okId then
      local n = toLuaNumber(numId)
      if n then viewIdRegistry[id] = n end
    end
  end

  local layoutParams = createLayoutParams(layout, parentView, parentViewClass)

  setMargins(layout, layoutParams)
  setPadding(layout, view)

  return view, layoutParams, viewClass
end

local function getLayoutTable(layout)
  if type(layout) == "string" then
    local ok, result = pcall(require, layout)
    if not ok or type(result) ~= "table" then
      error("加载布局文件失败: " .. layout .. "\n错误: " .. tostring(result), 2)
    end
    return result
  end
  if type(layout) == "table" then
    if rawget(layout, 1) == nil then
      local keys = {}
      for k in pairs(layout) do keys[#keys + 1] = tostring(k) end
      error("布局表[1]为空（视图类为 nil）。现有键: " .. tableConcat(keys, ", ")
        .. "。最常见原因: GridLayout 等类未 bindClass 或当前环境无此类", 2)
    end
    return layout
  elseif isJavaObject(layout) or type(layout) == "userdata" then
    local ok, result = pcall(luajava.astable, layout)
    if ok and type(result) == "table" and rawget(result, 1) ~= nil then
      return result
    end
  end
  error("bad argument #1 to 'loadlayout' (table expected, got " .. type(layout) .. ")", 2)
end

local loadlayoutImpl

local function loadlayoutInner(layout, views, parentView, parentViewClass)
  local view, layoutParams, viewClass = createView(layout, views, parentView, parentViewClass)

  for k, v in pairs(layout) do
    if excludeAttributes[k] or type(k) == "number" then
      goto _continue_
    end
    local e, s = pcall(setAttribute, view, k, v, layoutParams, views)
    if not e then
      local _, i = stringFind(s, ":%d+:")
      s = stringSub(s, i or 1)
      local _, du = pcall(dump, layout)
      error(stringFormat("loadlayout 错误: %s\n\t控件: %s\n\t属性: %s = %s\n%s",
        s, tostring(view), tostring(k), tostring(v), du or ""), 0)
    end
    ::_continue_::
  end

  local childKeys = {}
  for k in pairs(layout) do
    if type(k) == "number" and k ~= 1 then
      childKeys[#childKeys + 1] = k
    end
  end
  tableSort(childKeys)

  local isGridParent = GridLayout and instanceof(view, GridLayout)
  for _, k in ipairs(childKeys) do
    local v = layout[k]

    if isGridParent then
      if v.layout_row ~= nil then
        local row = tonumber(v.layout_row) or 0
        local rowSpan = tonumber(v.layout_rowSpan) or 1
        local needRows = row + rowSpan
        local curRows = 0
        pcall(function() curRows = view.getRowCount() end)
        if needRows > curRows then
          pcall(function() view.setRowCount(needRows) end)
          warnLog("GridLayout 自动扩展行数到 %d（layout_row=%d, rowSpan=%d）", needRows, row, rowSpan)
        end
      end
      if v.layout_column ~= nil then
        local col = tonumber(v.layout_column) or 0
        local colSpan = tonumber(v.layout_columnSpan) or 1
        local needCols = col + colSpan
        local curCols = 0
        pcall(function() curCols = view.getColumnCount() end)
        if needCols > curCols then
          pcall(function() view.setColumnCount(needCols) end)
          warnLog("GridLayout 自动扩展列数到 %d（layout_column=%d, colSpan=%d）", needCols, col, colSpan)
        end
      end
    end

    if instanceof(v, View) then
      local okA, errA = pcall(function() view.addView(v) end)
      if not okA then
        error(stringFormat("加载子视图在索引[%d]时出错:\n%s", k, tostring(errA)), 0)
      end
    else
      local okC, childView = pcall(loadlayoutImpl, v, views, view, viewClass)
      if not okC then
        error(stringFormat("加载子视图在索引[%d]时出错:\n%s", k, tostring(childView)), 0)
      end
      local okA, errA = pcall(function() view.addView(childView) end)
      if not okA then
        error(stringFormat("加载子视图在索引[%d]时出错:\n%s", k, tostring(errA)), 0)
      end
    end
  end

  view.setLayoutParams(layoutParams)
  return view
end

loadlayoutImpl = function(layout, views, parentView, parentViewClass)
  layout = getLayoutTable(layout)
  views = views or _G
  resolveContext()

  loadDepth = loadDepth + 1
  local ok, res = pcall(loadlayoutInner, layout, views, parentView, parentViewClass)
  loadDepth = loadDepth - 1

  if not ok then
    if loadDepth == 0 then
      deferredSet = {}
      pendingResolve = {}
    end
    error(res, 0)
  end

  if loadDepth == 0 and next(deferredSet) ~= nil then
    local items = deferredSet
    deferredSet = {}
    -- 性能优化：同一目标被多个规则引用时只解析一次 id
    for _, item in ipairs(items) do
      local targetId = pendingResolve[item.value]
      if targetId == nil then
        local target = rawget(views, item.value)
        if target then
          local okId, vid = pcall(function() return target.getId() end)
          if okId then targetId = toLuaNumber(vid) end
        end
        if not targetId then
          targetId = viewIdRegistry[item.value]
        end
        if not targetId then
          targetId = tonumber(item.value)
        end
        pendingResolve[item.value] = targetId or false
      end
      assert(targetId and targetId ~= false,
        "Unknown layout target " .. tostring(item.value) .. " (引用的控件尚未创建或 id 不存在)")
      if item.rule then
        local okR = pcall(function() item.layoutParams.addRule(item.rule, targetId) end)
        if not okR then
          warnLog("addRule(%d, %s) 失败，已忽略", item.rule, tostring(item.value))
        end
      elseif item.constraintSetter then
        local okC = pcall(item.constraintSetter, item.layoutParams, targetId)
        if not okC then
          warnLog("约束设置失败，目标: %s", tostring(item.value))
        end
      elseif item.apply then
        local okA = pcall(item.apply, item.layoutParams, targetId)
        if not okA then
          warnLog("延迟属性设置失败，目标: %s", tostring(item.value))
        end
      end
    end
    pendingResolve = {}
  end

  return res
end

loadlayout = setmetatable({
  _VERSION = "2.1.4",
  debug = false,
}, {
  __call = function(_, ...)
    return loadlayoutImpl(...)
  end,
})

return loadlayout