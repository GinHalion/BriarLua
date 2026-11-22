require "env"
local bindClass = luajava.bindClass
local LinearLayoutManager = bindClass "androidx.recyclerview.widget.LinearLayoutManager"
local Context = bindClass "android.content.Context"
local ClipData = bindClass "android.content.ClipData"
local IconDrawable = require "utils.IconDrawable"
local LuaRecyclerAdapter = require "utils.LuaRecyclerAdapter"
local ClassRegistry = require "utils.ClassRegistry"
local ProjectUtil = require "utils.Project"

local args = ...
local path = args
if type(path) == "table" then path = path[1] end

setStatus()
applyTheme()
activity
.setContentView(loadlayout("layout.analysis"))
.setSupportActionBar(myToolbar)
.getSupportActionBar()
.setDisplayHomeAsUpEnabled(true)
.setTitle("导入分析")

MyToast.setActivity(activity)
myAnalysisRecycler.setLayoutManager(LinearLayoutManager(activity))

local data = {}
local SelectedState = {}
local adapter = nil

local function copyToClipboard(text)
  local ok = pcall(function()
    local cm = activity.getSystemService(Context.CLIPBOARD_SERVICE)
    cm.setPrimaryClip(ClipData.newPlainText("path", text))
  end)
  MyToast.short(ok and "已复制" or "复制失败")
end

local function initUI()
  if #data == 0 then
    MyToast.short("未检测到需要导入的类")
    return
  end

  adapter = LuaRecyclerAdapter(data, "layout.item.analysis", {
    onBindViewHolder = function(holder, pos, views, className)
      views.title.setText(className)

      local isChecked = SelectedState[className] or false
      views.card.setChecked(isChecked)
      views.card.setStrokeColor(isChecked and MyColor.colorPrimary or MyColor.colorOutlineVariant)

      views.card.setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
        onClick = function(v)
          v.setChecked(not v.isChecked())
        end
      }))

      views.card.setOnCheckedChangeListener(luajava.createProxy("com.google.android.material.card.MaterialCardView$OnCheckedChangeListener", {
        onCheckedChanged = function(v, checked)
          if v.isPressed() then
            SelectedState[className] = checked
            v.setStrokeColor(checked and MyColor.colorPrimary or MyColor.colorOutlineVariant)
          end
        end
      }))

      views.card.setOnLongClickListener(luajava.createProxy("android.view.View$OnLongClickListener", {
        onLongClick = function(v)
          copyToClipboard(string.format('import "%s"', className))
          return true
        end
      }))
    end,
  })
  myAnalysisRecycler.setAdapter(adapter)
end

local function extractCodeIdentifiers(src)
  local identifiers = {}
  local len = #src
  local i = 1
  local state = 0
  local quoteChar
  local longLevel = 0
  local codeBuf = {}

  local function countEq(pos)
    local n = 0
    local p = pos + 1
    while p <= len and src:sub(p, p) == "=" do
      n = n + 1
      p = p + 1
    end
    return n, p
  end

  while i <= len do
    local c = src:sub(i, i)
    local n = src:sub(i + 1, i + 1)

    if state == 0 then
      if c == "-" and n == "-" then
        local j = i + 2
        if src:sub(j, j) == "[" then
          local eq, p = countEq(j)
          if src:sub(p, p) == "[" then
            state = 2
            longLevel = eq
            i = p
          else
            state = 1
            i = j
          end
         else
          state = 1
          i = j
        end
       elseif c == '"' or c == "'" then
        state = 3
        quoteChar = c
       elseif c == "[" then
        local eq, p = countEq(i)
        if src:sub(p, p) == "[" then
          state = 4
          longLevel = eq
          i = p
         else
          codeBuf[#codeBuf + 1] = c
        end
       else
        codeBuf[#codeBuf + 1] = c
      end
     elseif state == 1 then
      if c == "\n" then
        state = 0
        codeBuf[#codeBuf + 1] = "\n"
      end
     elseif state == 2 then
      if c == "]" then
        local eq, p = countEq(i)
        if eq == longLevel and src:sub(p, p) == "]" then
          state = 0
          i = p
        end
      end
     elseif state == 3 then
      if c == "\\" then
        i = i + 1
       elseif c == quoteChar then
        state = 0
      end
     elseif state == 4 then
      if c == "]" then
        local eq, p = countEq(i)
        if eq == longLevel and src:sub(p, p) == "]" then
          state = 0
          i = p
        end
      end
    end
    i = i + 1
  end

  local code = table.concat(codeBuf)
  for word in code:gmatch("[%a_][%w_]*") do
    identifiers[word] = true
  end
  return identifiers
end

local function analyze()
  if not path or path == "" then
    MyToast.short("无效的文件路径")
    return
  end

  local content, err = ProjectUtil.readFile(path)
  if not content then
    MyToast.short("无法读取文件: " .. tostring(err))
    return
  end

  local identifiers = extractCodeIdentifiers(content)

  local allClasses = ClassRegistry.all() or {}
  local classMap = {}

  for _, fullName in ipairs(allClasses) do
    if not fullName:match("%.R$") and not fullName:match("%.R%$") and not fullName:match("%.R%.") then
      local simple = fullName:match("([^%.%$]+)$")
      if simple and simple ~= "R" then
        classMap[simple] = classMap[simple] or {}
        table.insert(classMap[simple], fullName)
      end
    end
  end

  local resultMap = {}
  for ident in pairs(identifiers) do
    local candidates = classMap[ident]
    if candidates then
      for _, cls in ipairs(candidates) do
        resultMap[cls] = true
      end
    end
  end

  data = {}
  for cls in pairs(resultMap) do
    table.insert(data, cls)
  end
  table.sort(data)

  for _, cls in ipairs(data) do
    SelectedState[cls] = true
  end
  initUI()
end

analyze()

function onCreateOptionsMenu(menu)
  menu.add("复制").setShowAsAction(2).setIcon(IconDrawable("copy", MyColor.colorOnSurfaceVariant)).onMenuItemClick = function()
    if #data > 0 then
      local selected = {}
      for _, v in ipairs(data) do
        if SelectedState[v] then
          table.insert(selected, string.format('import "%s"', v))
        end
      end
      if #selected == 0 then
        MyToast.short("未选择任何类")
        return
      end
      copyToClipboard(table.concat(selected, "\n"))
    end
  end
  menu.add("反选").onMenuItemClick = function()
    if #data > 0 and adapter then
      for _, v in ipairs(data) do
        SelectedState[v] = not SelectedState[v]
      end
      adapter.notifyDataSetChanged()
    end
  end
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
    if myAnalysisRecycler then myAnalysisRecycler.setAdapter(nil) end
  end)
  collectgarbage("collect")
  collectgarbage("step")
end

local onThemeResume = makeThemeResumeChecker()
function onResume()
  onThemeResume()
end
