local _M = {}

local bindClass = luajava.bindClass
local Context = bindClass "android.content.Context"
local ColorStateList = bindClass "android.content.res.ColorStateList"
local GradientDrawable = bindClass "android.graphics.drawable.GradientDrawable"

local bound = false
local visible = false
local caseSensitive = false
local wholeWord = false
local regexMode = false
local pendingRunnable = nil
local searchListenerProxy = nil
local launcherProxy = nil

local function dp(v)
  local r = activity.Resources.DisplayMetrics
  return math.floor(v * r.density + 0.5)
end

local function getKeyword()
  local ok, s = pcall(function() return mySearchInput.getText().toString() end)
  return (ok and s) and tostring(s) or ""
end

local function getReplacement()
  local ok, s = pcall(function() return myReplaceInput.getText().toString() end)
  return (ok and s) and tostring(s) or ""
end

local function isEditorReady()
  if not myLuaEditor then return false end
  local ok, v = pcall(function() return myLuaEditor.getVisibility() end)
  return ok and v == 0
end

local function focusInput()
  pcall(function()
    mySearchInput.requestFocus()
    mySearchInput.post(luajava.createProxy("java.lang.Runnable", {
      run = function()
        pcall(function()
          local imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE)
          if imm then imm.showSoftInput(mySearchInput, 0) end
        end)
      end,
    }))
  end)
end

local function hideIme()
  pcall(function()
    local imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE)
    if imm then imm.hideSoftInputFromWindow(mySearchInput.getWindowToken(), 0) end
  end)
end

local function loadOptions()
  caseSensitive = activity.getSharedData("search_case_sensitive") == true
  wholeWord = activity.getSharedData("search_whole_word") == true
  regexMode = activity.getSharedData("search_regex") == true
end

local function styleToggle(view, active)
  if not view then return end
  pcall(function()
    local gd = GradientDrawable()
    gd.setCornerRadius(dp(16))
    if active then
      gd.setColor(MyColor.colorPrimaryContainer)
      view.setTextColor(MyColor.colorOnPrimaryContainer)
     else
      gd.setColor(MyColor.colorSurfaceContainerHigh)
      view.setTextColor(MyColor.colorOnSurfaceVariant)
    end
    view.setBackground(gd)
  end)
end

local function refreshToggles()
  styleToggle(mySearchOptCase, caseSensitive)
  styleToggle(mySearchOptWord, wholeWord)
  styleToggle(mySearchOptRegex, regexMode)
end

local function checkKeyword()
  local kw = getKeyword()
  if kw == "" then
    MyToast.short("输入搜索内容")
    return nil
  end
  if regexMode then
    local ok, err = pcall(function() return myLuaEditor.searchCodeRegexError(kw) end)
    if ok and err and err ~= "" then
      pcall(function() mySearchCounter.setText("正则错误") end)
      MyToast.short("正则表达式无效")
      return nil
    end
  end
  return kw
end

local function doSearch(fromInput)
  if not visible or not isEditorReady() then return end
  local kw = getKeyword()
  if kw == "" then
    pcall(function() mySearchCounter.setText("") end)
    pcall(function() myLuaEditor.clearCodeSearchSelection() end)
    return
  end
  if regexMode then
    local ok, err = pcall(function() return myLuaEditor.searchCodeRegexError(kw) end)
    if ok and err and err ~= "" then
      pcall(function() mySearchCounter.setText("正则错误") end)
      return
    end
  end
  pcall(function()
    if fromInput then
      myLuaEditor.searchCodeInput(kw, caseSensitive, wholeWord, regexMode)
     else
      myLuaEditor.searchCodeNext(kw, caseSensitive, wholeWord, regexMode)
    end
  end)
end

local function nextMatch()
  local kw = checkKeyword()
  if not kw then return end
  pcall(function() myLuaEditor.searchCodeNext(kw, caseSensitive, wholeWord, regexMode) end)
end

local function prevMatch()
  local kw = checkKeyword()
  if not kw then return end
  pcall(function() myLuaEditor.searchCodePrev(kw, caseSensitive, wholeWord, regexMode) end)
end

local function replaceOne()
  local kw = checkKeyword()
  if not kw then return end
  pcall(function()
    myLuaEditor.replaceCode(kw, getReplacement(), caseSensitive, wholeWord, regexMode)
  end)
end

local function replaceAll()
  local kw = checkKeyword()
  if not kw then return end
  local n = 0
  pcall(function()
    n = myLuaEditor.replaceCodeAll(kw, getReplacement(), caseSensitive, wholeWord, regexMode) or 0
  end)
  if n > 0 then
    MyToast.short("已替换 " .. tostring(n) .. " 处")
   else
    MyToast.short("未找到可替换内容")
  end
  doSearch(true)
end

function _M.isVisible()
  return visible
end

function _M.show()
  if not mySearchBar or not myLuaEditor then return end
  if not isEditorReady() then
    MyToast.short("请先打开文件")
    return
  end
  local sel = ""
  pcall(function() sel = tostring(myLuaEditor.getSelectedText() or "") end)
  if visible then
    if sel ~= "" then
      pcall(function() mySearchInput.setText(sel) end)
      pcall(function() mySearchInput.selectAll() end)
      doSearch(true)
     else
      pcall(function() mySearchInput.requestFocus() end)
      pcall(function() mySearchInput.selectAll() end)
      focusInput()
    end
    return
  end
  _M.bind()
  visible = true
  pcall(function() myLuaEditor.stopCodeSearch() end)
  pcall(function() myLuaEditor.selectText(false) end)
  if sel ~= "" then
    pcall(function() mySearchInput.setText(sel) end)
    pcall(function() mySearchInput.selectAll() end)
  end
  pcall(function() mySearchBar.setVisibility(0) end)
  refreshToggles()
  focusInput()
  doSearch(true)
end

function _M.hide()
  if not visible then return end
  visible = false
  pcall(function() mySearchBar.setVisibility(8) end)
  pcall(function() myLuaEditor.clearCodeSearchSelection() end)
  hideIme()
  pcall(function() mySearchInput.clearFocus() end)
  pcall(function() myReplaceInput.clearFocus() end)
  pcall(function() mySearchCounter.setText("") end)
end

function _M.toggle()
  if visible then _M.hide() else _M.show() end
end

function _M.onActivityDestroy()
  if pendingRunnable and mySearchInput then
    pcall(function() mySearchInput.removeCallbacks(pendingRunnable) end)
  end
  visible = false
  bound = false
  pendingRunnable = nil
  searchListenerProxy = nil
  launcherProxy = nil
end

function _M.onSearchResult(keyword, found, matchIndex, matchCount)
  if not mySearchCounter then return end
  pcall(function()
    if found then
      mySearchCounter.setText(tostring(matchIndex) .. "/" .. tostring(matchCount))
     else
      mySearchCounter.setText("0 结果")
    end
  end)
end

local function bindSearchListener()
  pcall(function()
    searchListenerProxy = luajava.createProxy("com.androlua.LuaEditor$SearchListener", {
      onSearchResult = function(keyword, found, selStart, selEnd, matchIndex, matchCount)
        _M.onSearchResult(keyword, found, matchIndex, matchCount)
      end,
    })
    myLuaEditor.setLuaSearchListener(searchListenerProxy)
  end)
end

local function bindLauncher()
  pcall(function()
    launcherProxy = luajava.createProxy("com.androlua.LuaEditor$SearchLauncher", {
      onOpenSearchBar = function() _M.show() end,
    })
    myLuaEditor.setCodeSearchLauncher(launcherProxy)
  end)
end

local function resolveAttrRes(attrField)
  local ok, resId = pcall(function()
    local Rattr = bindClass "android.R$attr"
    local ta = activity.getTheme().obtainStyledAttributes({attrField})
    local id = ta.getResourceId(0, 0)
    ta.recycle()
    return id
  end)
  return ok and resId or 0
end

function _M.bind()
  if bound or not mySearchBar then return end
  bound = true

  pcall(function()
    local tint = ColorStateList.valueOf(MyColor.colorOnSurfaceVariant)
    mySearchPrev.setImageResource(R.drawable.ic_chevron_up)
    mySearchPrev.setImageTintList(tint)
    mySearchNext.setImageResource(R.drawable.ic_chevron_down)
    mySearchNext.setImageTintList(tint)
    mySearchClose.setImageResource(R.drawable.ic_close)
    mySearchClose.setImageTintList(tint)
    mySearchInput.setHintTextColor(MyColor.colorOnSurfaceVariant)
    myReplaceInput.setHintTextColor(MyColor.colorOnSurfaceVariant)

    local borderless = resolveAttrRes(
      (bindClass "android.R$attr").selectableItemBackgroundBorderless)
    if borderless ~= 0 then
      mySearchPrev.setBackgroundResource(borderless)
      mySearchNext.setBackgroundResource(borderless)
      mySearchClose.setBackgroundResource(borderless)
    end
    local bordered = resolveAttrRes(
      (bindClass "android.R$attr").selectableItemBackground)
    if bordered ~= 0 then
      myReplaceOne.setBackgroundResource(bordered)
      myReplaceAll.setBackgroundResource(bordered)
    end
  end)

  loadOptions()
  refreshToggles()

  mySearchInput.addTextChangedListener({
    beforeTextChanged = function(s, a, b, c) end,
    onTextChanged = function(s, a, b, c) end,
    afterTextChanged = function(s)
      local kw = tostring(s or "")
      if pendingRunnable then
        pcall(function() mySearchInput.removeCallbacks(pendingRunnable) end)
      end
      pendingRunnable = luajava.createProxy("java.lang.Runnable", {
        run = function()
          pendingRunnable = nil
          if not visible then return end
          if activity.isDestroyed() or activity.isFinishing() then return end
          doSearch(true)
        end,
      })
      pcall(function() mySearchInput.postDelayed(pendingRunnable, 120) end)
    end,
  })

  mySearchInput.setOnEditorActionListener(luajava.createProxy(
    "android.widget.TextView$OnEditorActionListener", {
      onEditorAction = function(v, actionId, event)
        local act = -1
        pcall(function() act = event and event.getAction() or -1 end)
        if event == nil or act == 1 then
          nextMatch()
        end
        return true
      end,
    }))

  myReplaceInput.setOnEditorActionListener(luajava.createProxy(
    "android.widget.TextView$OnEditorActionListener", {
      onEditorAction = function(v, actionId, event)
        local act = -1
        pcall(function() act = event and event.getAction() or -1 end)
        if event == nil or act == 1 then
          replaceOne()
        end
        return true
      end,
    }))

  mySearchPrev.onClick = function() prevMatch() end
  mySearchNext.onClick = function() nextMatch() end
  mySearchClose.onClick = function() _M.hide() end
  myReplaceOne.onClick = function() replaceOne() end
  myReplaceAll.onClick = function() replaceAll() end

  mySearchOptCase.onClick = function()
    caseSensitive = not caseSensitive
    activity.setSharedData("search_case_sensitive", caseSensitive)
    refreshToggles()
    doSearch(true)
  end
  mySearchOptWord.onClick = function()
    wholeWord = not wholeWord
    activity.setSharedData("search_whole_word", wholeWord)
    refreshToggles()
    doSearch(true)
  end
  mySearchOptRegex.onClick = function()
    regexMode = not regexMode
    activity.setSharedData("search_regex", regexMode)
    refreshToggles()
    doSearch(true)
  end

  bindSearchListener()
  bindLauncher()
end

return _M
