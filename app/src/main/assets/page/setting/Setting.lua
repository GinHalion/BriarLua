require "env"
local bindClass = luajava.bindClass
local MaterialAlertDialogBuilder = bindClass "com.google.android.material.dialog.MaterialAlertDialogBuilder"
local LinearLayoutManager = bindClass "androidx.recyclerview.widget.LinearLayoutManager"
local LuaCustRecyclerAdapter = bindClass "com.lua.custrecycleradapter.LuaCustRecyclerAdapter"
local LuaCustRecyclerHolder = bindClass "com.lua.custrecycleradapter.LuaCustRecyclerHolder"
local AdapterCreator = bindClass "com.lua.custrecycleradapter.AdapterCreator"
local Intent = bindClass "android.content.Intent"
local Uri = bindClass "android.net.Uri"
local Build = bindClass "android.os.Build"
local MyGlide = require "utils.Glide"
local SettingItem = require "layout.item.setting"
local FormDialog = require "dialogs.FormDialog"
local Symbols = require "utils.Symbols"

local ITYPE_TITLE = 0
local ITYPE_ARROW = 1
local ITYPE_SWITCH = 2

setStatus()
applyTheme()

activity
.setContentView(loadlayout("layout.setting"))
.setSupportActionBar(myToolbar)
.getSupportActionBar()
.setDisplayHomeAsUpEnabled(true)
.setTitle("软件设置")

MyToast.setActivity(activity)

local VERSION_NAME = "未知"
local VERSION_CODE = "0"
pcall(function()
  local BuildConfig = bindClass "com.briarlua.BuildConfig"
  VERSION_NAME = tostring(BuildConfig.VERSION_NAME)
  VERSION_CODE = tostring(BuildConfig.VERSION_CODE)
end)

local DARK_MODE_NAMES = { system = "跟随系统", light = "浅色", dark = "深色" }
local SORT_NAMES = { [1] = "按名称", [2] = "按修改时间" }

local data_setting = {
  { itemtype = "TITLE", title = "界面" },
  { itemtype = "ARROW", itype = ITYPE_ARROW, title = "主题颜色", image = "color", key = "theme_color" },
  { itemtype = "SWITCH", itype = ITYPE_SWITCH, title = "动态取色", content = "适配 Android 12 新特性", image = "color_pick", key = "dynamic_color" },
  { itemtype = "ARROW", itype = ITYPE_ARROW, title = "深色模式", image = "theme_light_dark", key = "dark_mode" },
  { itemtype = "TITLE", title = "编辑器" },
  { itemtype = "ARROW", itype = ITYPE_ARROW, title = "符号栏", content = "显示开关 · 编辑基础符号 · 追加自定义", image = "symbol", key = "symbol" },
  { itemtype = "ARROW", itype = ITYPE_ARROW, title = "文件排序", image = "sort", key = "file_sort" },
  { itemtype = "SWITCH", itype = ITYPE_SWITCH, title = "显示隐藏文件", content = "文件树中显示点开头的文件", image = "hidden", key = "show_hidden" },
  { itemtype = "TITLE", title = "软件" },
  { itemtype = "ARROW", itype = ITYPE_ARROW, title = "开发文档", content = "辅助项目开发", image = "doc", key = "doc" },
  { itemtype = "ARROW", itype = ITYPE_ARROW, title = "项目源码", content = "GitHub", image = "github", key = "source_code" },
  { itemtype = "ARROW", itype = ITYPE_ARROW, title = "关于软件", image = "about", key = "about" },
}

local function save(key, value)
  activity.setSharedData(key, value)
end

local function getBool(key, dv)
  local v = activity.getSharedData(key)
  if v == true or v == false then return v end
  return dv
end

local function num(key, dv)
  return tonumber(activity.getSharedData(key)) or dv
end

local function customSymbolCount()
  return #Symbols.getCustom()
end

local function getContent(item)
  local key = item.key
  if key == "theme_color" then
    if isDynamicColorEnabled() then
      return "动态取色已开启"
    end
    return currentThemeLabel()
   elseif key == "dark_mode" then
    local v = tostring(activity.getSharedData("dark_mode") or "system")
    return DARK_MODE_NAMES[v] or "跟随系统"
   elseif key == "symbol" then
    local shown = getBool("show_symbol", true)
    local baseN = #Symbols.getBase()
    local baseTag = Symbols.isBaseCustomized() and ("基础" .. baseN .. "（已改）") or ("基础" .. baseN)
    local c = customSymbolCount()
    local customTag = c > 0 and (" + 追加" .. c .. "个") or ""
    return (shown and "已显示" or "已隐藏") .. " · " .. baseTag .. customTag
   elseif key == "file_sort" then
    return SORT_NAMES[num("file_sort", 1)] or "按名称"
   elseif key == "about" then
    return "当前版本：" .. VERSION_NAME .. " (" .. VERSION_CODE .. ")"
  end
  return item.content or ""
end

local function showAbout()
  MaterialAlertDialogBuilder(activity)
  .setTitle("关于")
  .setMessage("BriarLua " .. VERSION_NAME .. "\n基于：AndroLua，LuaAppX\n开发衍生的 Luaide\nminSdk 23 · targetSdk 35\nLua 5.3.3(定制)\n作者：GinHalion")
  .setPositiveButton("确定", nil)
  .show()
end

local function showThemeColorDialog()
  if isDynamicColorEnabled() then
    MaterialAlertDialogBuilder(activity)
    .setTitle("主题颜色")
    .setMessage("当前已开启「动态取色」，主题颜色由系统壁纸自动决定。\n\n如需手动选择主题颜色，请先关闭动态取色。")
    .setPositiveButton("关闭动态取色", {
      onClick = function()
        save("dynamic_color", false)
        bumpThemeVersion()
        activity.recreate()
      end
    })
    .setNegativeButton("取消", nil)
    .show()
    return
  end
  local cur = num("theme_color", 1)
  local idx = math.max(0, math.min(#ThemeNames, cur) - 1)
  MaterialAlertDialogBuilder(activity)
  .setTitle("主题颜色")
  .setSingleChoiceItems(ThemeNames, idx, {
    onClick = function(dialog, which)
      save("theme_color", tostring(which + 1))
      dialog.dismiss()
      bumpThemeVersion()
      activity.recreate()
    end
  })
  .setNegativeButton("取消", nil)
  .show()
end

local function showDarkModeDialog()
  local opts = { "跟随系统", "浅色", "深色" }
  local vals = { "system", "light", "dark" }
  local cur = tostring(activity.getSharedData("dark_mode") or "system")
  local idx = 0
  for i, v in ipairs(vals) do
    if v == cur then idx = i - 1 break end
  end

  MaterialAlertDialogBuilder(activity)
  .setTitle("深色模式")
  .setSingleChoiceItems(opts, idx, {
    onClick = function(dialog, which)
      save("dark_mode", vals[which + 1])
      dialog.dismiss()
      bumpThemeVersion()
      activity.recreate()
    end
  })
  .setNegativeButton("取消", nil)
  .show()
end

local adapter

local function refreshSettingList()
  if mySettingRecyclerView and adapter then
    pcall(function() adapter.notifyDataSetChanged() end)
  end
end

local function editBaseSymbols()
  local current = Symbols.serialize(Symbols.getBase())
  FormDialog.input({
    context = activity,
    title = "编辑基础符号栏",
    hint = "每行一个符号；格式 符号|插入内容，留空则两者相同；支持 \\t \\n 转义",
    initial = current,
    multiline = true,
    minLines = 12,
    positive = "保存",
    negative = "取消",
    onConfirm = function(text)
      if text:match("^%s*$") then
        save("symbols_base", "")
        MyToast.short("已恢复默认基础符号")
       else
        local items = Symbols.parse(text)
        if not items then
          MyToast.short("未解析到有效符号，请检查格式")
          return
        end
        save("symbols_base", Symbols.serialize(items))
        MyToast.short("已保存基础符号 " .. #items .. " 个")
      end
      bumpThemeVersion()
      refreshSettingList()
    end,
  })
end

local function appendCustomSymbols()
  local current = tostring(activity.getSharedData("custom_symbols") or "")
  FormDialog.input({
    context = activity,
    title = "追加自定义符号",
    hint = "用,或、分隔",
    initial = current,
    positive = "保存",
    negative = "取消",
    onConfirm = function(text)
      save("custom_symbols", text)
      bumpThemeVersion()
      refreshSettingList()
      MyToast.short(text:match("^%s*$") and "已清空追加符号" or "已保存，将追加在基础符号之后")
    end,
  })
end

local function resetSymbols()
  MaterialAlertDialogBuilder(activity)
  .setTitle("恢复默认")
  .setMessage("将基础符号与追加符号全部恢复为默认，确定吗？")
  .setPositiveButton("恢复", {
    onClick = function()
      Symbols.reset()
      bumpThemeVersion()
      refreshSettingList()
      MyToast.short("已恢复默认符号栏")
    end
  })
  .setNegativeButton("取消", nil)
  .show()
end

local function showSymbolDialog()
  local shown = getBool("show_symbol", true)
  local items = {
    shown and "隐藏符号栏" or "显示符号栏",
    "编辑基础符号（可修改默认符号）",
    "追加自定义符号（不覆盖默认）",
    "恢复默认符号栏",
  }
  MaterialAlertDialogBuilder(activity)
  .setTitle("符号栏")
  .setItems(items, {
    onClick = function(dialog, which)
      if which == 0 then
        save("show_symbol", not shown)
        bumpThemeVersion()
        refreshSettingList()
        MyToast.short(not shown and "已显示符号栏" or "已隐藏符号栏")
       elseif which == 1 then
        editBaseSymbols()
       elseif which == 2 then
        appendCustomSymbols()
       elseif which == 3 then
        resetSymbols()
      end
    end
  })
  .setNegativeButton("取消", nil)
  .show()
end

local function showFileSortDialog()
  local cur = num("file_sort", 1)
  local idx = cur == 2 and 1 or 0
  MaterialAlertDialogBuilder(activity)
  .setTitle("文件排序")
  .setSingleChoiceItems({ "按名称", "按修改时间" }, idx, {
    onClick = function(dialog, which)
      save("file_sort", which == 1 and 2 or 1)
      dialog.dismiss()
      bumpThemeVersion()
      refreshSettingList()
    end
  })
  .setNegativeButton("取消", nil)
  .show()
end

local function onItemClick(item, views)
  local key = item.key
  if key == "about" then
    showAbout()
   elseif key == "source_code" then
    local ok = openUrl("https://github.com/GinHalion/BriarLua")
    if not ok then MyToast.short("无法打开浏览器") end
   elseif key == "doc" then
    local ok = pcall(function() MyActivity.new("doc") end)
    if not ok then MyToast.short("开发文档不可用") end
   elseif key == "theme_color" then
    showThemeColorDialog()
   elseif key == "dark_mode" then
    showDarkModeDialog()
   elseif key == "symbol" then
    showSymbolDialog()
   elseif key == "file_sort" then
    showFileSortDialog()
   elseif key == "dynamic_color" then
    local on = getBool("dynamic_color", false)
    if not on and Build.VERSION.SDK_INT < 31 then
      MyToast.short("需要 Android 12 及以上")
      return
    end
    save("dynamic_color", not on)
    if views["switch"] then
      pcall(function() views["switch"].setChecked(not on) end)
    end
    bumpThemeVersion()
    activity.recreate()
   elseif key == "show_hidden" then
    local on = getBool("show_hidden", true)
    save("show_hidden", not on)
    if views["switch"] then
      pcall(function() views["switch"].setChecked(not on) end)
    end
    bumpThemeVersion()
  end
end

local function bindItem(views, item)
  if item.title and views.title then
    views.title.setText(tostring(item.title))
  end
  if views.content then
    views.content.setText(tostring(getContent(item)))
  end

  if item.image and views.image then
    pcall(function()
      MyGlide.set(backImage(item.image), views.image)
    end)
  end

  if item.itype == ITYPE_SWITCH and views["switch"] then
    local checked
    if item.key == "dynamic_color" then
      checked = getBool("dynamic_color", false)
     elseif item.key == "show_hidden" then
      checked = getBool("show_hidden", true)
     else
      checked = activity.getSharedData(item.key) == true
    end
    pcall(function() views["switch"].setChecked(checked) end)
  end

  if views.card then
    if item.key == "theme_color" and isDynamicColorEnabled() then
      pcall(function() views.card.setAlpha(0.5) end)
     else
      pcall(function() views.card.setAlpha(1) end)
    end
    if item.itype ~= ITYPE_TITLE then
      views.card.onClick = function()
        onItemClick(item, views)
      end
    end
  end
end

adapter = LuaCustRecyclerAdapter(AdapterCreator({
  getItemCount = function()
    return #data_setting
  end,
  getItemViewType = function(position)
    return data_setting[position + 1].itype or ITYPE_TITLE
  end,
  onCreateViewHolder = function(parent, viewType)
    local views = {}
    local layout = SettingItem.ARROW
    if viewType == ITYPE_TITLE then
      layout = SettingItem.TITLE
     elseif viewType == ITYPE_SWITCH then
      layout = SettingItem.SWITCH
    end
    local root = loadlayout(layout, views)
    local holder = LuaCustRecyclerHolder(root)
    holder.view.setTag(views)
    return holder
  end,
  onBindViewHolder = function(holder, position)
    if not holder or not holder.view then return end
    local views = holder.view.getTag()
    if not views then return end
    local item = data_setting[position + 1]
    if not item then return end
    bindItem(views, item)
  end,
}))

if mySettingRecyclerView then
  mySettingRecyclerView.setAdapter(adapter)
  mySettingRecyclerView.setLayoutManager(LinearLayoutManager(activity))
  pcall(function() mySettingRecyclerView.setHasFixedSize(true) end)
  pcall(function() mySettingRecyclerView.setItemViewCacheSize(10) end)
end

function onOptionsItemSelected(item)
  if item.getItemId() == android.R.id.home then
    activity.finish()
    return true
  end
  return false
end

local onThemeResume = makeThemeResumeChecker()
function onResume()
  if onThemeResume() then return end
  pcall(function()
    if adapter then adapter.notifyDataSetChanged() end
  end)
end

function onDestroy()
  pcall(function()
    if mySettingRecyclerView then mySettingRecyclerView.setAdapter(nil) end
  end)
  collectgarbage("collect")
  collectgarbage("step")
end
