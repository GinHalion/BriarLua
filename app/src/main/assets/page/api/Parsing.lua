require "env"
local bindClass = luajava.bindClass
local LinearLayoutManager = bindClass "androidx.recyclerview.widget.LinearLayoutManager"
local LuaRecyclerAdapter = require "utils.LuaRecyclerAdapter"
local ClassInfo = require "utils.ClassInfo"
local Context = bindClass "android.content.Context"
local ClipData = bindClass "android.content.ClipData"

local args = ...
local fullName = args
if type(fullName) == "table" then fullName = fullName[1] end
if not fullName or fullName == "" then
  MyToast.short("缺少类名")
  return
end

setStatus()
applyTheme()

activity
.setContentView(loadlayout("layout.api"))
.setSupportActionBar(myToolbar)
.getSupportActionBar()
.setDisplayHomeAsUpEnabled(true)

MyToast.setActivity(activity)

mySearchLayout.setVisibility(8)
if myImportBar then myImportBar.setVisibility(8) end

local detailView = myApiRecyclerView
detailView.setLayoutManager(LinearLayoutManager(activity))

local info = ClassInfo.loadClassInfo(fullName)
if not info then
  MyToast.short("加载失败：" .. fullName)
  activity.finish()
  return
end

activity.getSupportActionBar().setTitle(info.simpleName)
activity.getSupportActionBar().setSubtitle(info.fullName)

local tabs = {}

local function copyToClipboard(text)
  local ok = pcall(function()
    local cm = activity.getSystemService(Context.CLIPBOARD_SERVICE)
    cm.setPrimaryClip(ClipData.newPlainText("path", text))
  end)
  MyToast.short(ok and "已复制" or "复制失败")
end

local function formatMod(m)
  if not m or m == "" then return "默认" end
  m = m:gsub("public", "公有"):gsub("private", "私有"):gsub("protected", "保护"):gsub("static", "静态"):gsub("final", "最终"):gsub("abstract", "抽象")
  return m
end

if #info.constructors > 0 then
  local ctorItems = {}
  for _, ctor in ipairs(info.constructors) do
    local text = ctor.name .. "(" .. table.concat(ctor.params, ", ") .. ")"
    ctorItems[#ctorItems + 1] = {
      text = text,
      copyText = text,
      modifier = formatMod(ctor.modifiers),
      returnType = ""
    }
  end
  tabs[#tabs + 1] = { name = "构建", count = #info.constructors, items = ctorItems }
end

if #info.methods > 0 then
  local methodItems = {}
  for _, m in ipairs(info.methods) do
    local sig = m.name .. "(" .. table.concat(m.params, ", ") .. ")"
    methodItems[#methodItems + 1] = {
      text = sig,
      copyText = sig,
      modifier = formatMod(m.modifiers),
      returnType = m.returnType
    }
  end
  tabs[#tabs + 1] = { name = "方法", count = #info.methods, items = methodItems }
end

if #info.fields > 0 then
  local fieldItems = {}
  for _, f in ipairs(info.fields) do
    fieldItems[#fieldItems + 1] = {
      text = f.name,
      copyText = info.simpleName .. "." .. f.name,
      modifier = formatMod(f.modifiers),
      returnType = f.type
    }
  end
  tabs[#tabs + 1] = { name = "字段", count = #info.fields, items = fieldItems }
end

if info.superClass then
  local superItems = {
    {
      text = info.superClass:match("([^%.]+)$") or info.superClass,
      copyText = info.superClass,
      modifier = "父类",
      returnType = ""
    }
  }
  tabs[#tabs + 1] = { name = "父类", count = 1, items = superItems }
end

if #info.innerClasses > 0 then
  local innerItems = {}
  for _, c in ipairs(info.innerClasses) do
    innerItems[#innerItems + 1] = {
      text = c.name,
      copyText = c.fullName,
      modifier = formatMod(c.modifiers),
      returnType = ""
    }
  end
  tabs[#tabs + 1] = { name = "内部类", count = #info.innerClasses, items = innerItems }
end

local function renderTab(index)
  local tab = tabs[index]
  if not tab then return end

  local a = LuaRecyclerAdapter(tab.items, "layout.item.parsing", {
    onBindViewHolder = function(holder, pos, views, item)
      views.title.setText(item.text)

      views.content.setText(item.modifier)

      if item.returnType and item.returnType ~= "" then
        views.returnType.setText(item.returnType)
        views.returnType.setVisibility(0)
       else
        views.returnType.setVisibility(8)
      end
      views.card.onClick = function(v)
        copyToClipboard(item.copyText)
      end
    end,
  })
  detailView.setAdapter(a)
end

if #tabs == 0 then
  myTabLayout.setVisibility(8)
  local empty = LuaRecyclerAdapter({ { text = "（无可显示的成员）" } }, "layout.item.symbol", {
    onBindViewHolder = function(holder, pos, views, item)
      views.symbol.setText(item.text)
      views.symbol.setTextColor(MyColor.colorOnSurfaceVariant)
      views.symbol.setOnClickListener(nil)
    end,
  })
  detailView.setAdapter(empty)
 else
  myTabLayout.removeAllTabs()
  for _, tab in ipairs(tabs) do
    local text = string.format("%s(%d)", tab.name, tab.count)
    myTabLayout.addTab(myTabLayout.newTab().setText(text))
  end
  myTabLayout.setVisibility(0)
  pcall(function() myTabLayout.clearOnTabSelectedListeners() end)
  myTabLayout.addOnTabSelectedListener({
    onTabSelected = function(tab)
      renderTab(tab.getPosition() + 1)
    end,
    onTabUnselected = function(tab) end,
    onTabReselected = function(tab) end,
  })
  if myTabLayout.getTabCount() > 0 then
    local t0 = myTabLayout.getTabAt(0)
    if t0 then t0.select() end
  end
  renderTab(1)
end

function onOptionsItemSelected(item)
  if item.getItemId() == android.R.id.home then
    activity.finish()
    return true
  end
  return false
end

function onDestroy()
  pcall(function()
    if detailView then detailView.setAdapter(nil) end
  end)
  collectgarbage("collect")
  collectgarbage("step")
end

local onThemeResume = makeThemeResumeChecker()
function onResume()
  onThemeResume()
end
