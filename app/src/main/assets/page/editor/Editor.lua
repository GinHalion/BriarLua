require "env"
local bindClass = luajava.bindClass
local ActionBarDrawerToggle = bindClass "androidx.appcompat.app.ActionBarDrawerToggle"
local MaterialAlertDialogBuilder = bindClass "com.google.android.material.dialog.MaterialAlertDialogBuilder"
local WindowCompat = bindClass "androidx.core.view.WindowCompat"
local WindowManager = bindClass "android.view.WindowManager"
local LinearLayoutManager = bindClass "androidx.recyclerview.widget.LinearLayoutManager"
local PopupMenu = bindClass "androidx.appcompat.widget.PopupMenu"

local FilesTabManager = require "dialogs.FilesTabManager"
local ProjectUtil = require "utils.Project"
local IconDrawable = require "utils.IconDrawable"
local FileHistory = require "utils.FileHistory"
local LuaRecyclerAdapter = require "utils.LuaRecyclerAdapter"
local ClassInfo = require "utils.ClassInfo"
local EditorTool = require "page.editor.EditorTool"
local EditorSearch = require "page.editor.EditorSearch"
local UiUtil = bindClass "com.briarlua.utils.UiUtil"
local TooltipCompat = bindClass "androidx.appcompat.widget.TooltipCompat"


local args = ...
if type(args) == "table" then
  EditorTool.projectFolderName = args[1]
  EditorTool.projectDir = args[2]
 else
  EditorTool.projectDir = args
end

if not EditorTool.projectDir or EditorTool.projectDir == "" then
  MyToast.short("缺少项目路径")
  return
end
if not EditorTool.projectFolderName or EditorTool.projectFolderName == "" then
  EditorTool.projectFolderName = EditorTool.projectDir:match("[^/]+$")
end

EditorTool.currentDirPath = EditorTool.projectDir
setStatus()
applyTheme()

activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

activity
.setContentView(loadlayout("layout.editor"))
.setSupportActionBar(myToolbar)
.getSupportActionBar()
.setDisplayHomeAsUpEnabled(true)
.setTitle("编辑器")

pcall(function()
  UiUtil.keepAboveKeyboard(myEditorMain, mySidebar)
end)

MyToast.setActivity(activity)

if not ProjectUtil.dirExists(EditorTool.projectDir) then
  MyToast.short("找不到项目目录：" .. tostring(EditorTool.projectDir))
  return
end

local cfg, cerr = ProjectUtil.readInit(EditorTool.projectDir)
if not cfg then
  MyToast.short("读取配置失败: " .. tostring(cerr))
  cfg = {}
end

EditorTool.projectName = cfg.appname
MyUI.setTitle(EditorTool.projectName)

local drawerToggle = ActionBarDrawerToggle(
activity, myDrawerLayout, myToolbar,
R.string.open_drawer, R.string.close_drawer
)
myDrawerLayout.addDrawerListener(drawerToggle)
drawerToggle.syncState()

FilesTabManager.projectName = EditorTool.projectFolderName
FilesTabManager.projectDir = EditorTool.projectDir
FilesTabManager.getTabs = function() return myTabLayout end

EditorTool.initTabCallbacks()

local sortMode = tonumber(activity.getSharedData("file_sort")) or 1
pcall(function()
  myFileTree.setSortType(sortMode == 2 and myFileTree.SORT_BY_TIME or myFileTree.SORT_BY_NAME)
  myFileTree.setShowHidden(activity.getSharedData("show_hidden") ~= false)
end)

if not myFileTree.init(EditorTool.projectDir) then
  MyToast.short("无权打开目录")
end

local Symbols = require "utils.Symbols"

local pendingClasses = nil
local pendingClassesKey = nil
local symbolAdapter = nil
local selectionListener = nil
local symbolItems = {}

local function symbolBarEnabled()
  return activity.getSharedData("show_symbol") ~= false
end

local function currentSymbolItems()
  return Symbols.getEffective()
end

local function insertToEditor(text)
  if not myLuaEditor then return end
  local ok = pcall(function() myLuaEditor.replaceSelection(text) end)
  if not ok then pcall(function() myLuaEditor.paste(text) end) end
end

local function showClassPopup(anchorView, classes)
  local popup = PopupMenu(activity, anchorView)
  local menu = popup.getMenu()
  local function addClassActions(targetMenu, fullName)
    local simpleName = fullName:match("([^%.%$]+)$") or fullName
    targetMenu.add("复制导入类").onMenuItemClick = function()
      EditorTool.copyToClipboard(string.format('local %s = luajava.bindClass "%s"', simpleName, fullName))
    end
    targetMenu.add("查看API").onMenuItemClick = function()
      local ok = pcall(function() MyActivity.new("parsing", {fullName}) end)
      if not ok then MyToast.short("API浏览器不可用") end
    end
  end
  if #classes == 1 then
    addClassActions(menu, classes[1])
   else
    local simpleName = classes[1]:match("([^%.%$]+)$") or classes[1]
    menu.add(simpleName .. " · " .. #classes .. " 个同名类").setEnabled(false)
    for _, fullName in ipairs(classes) do
      addClassActions(menu.addSubMenu(fullName), fullName)
    end
  end
  popup.show()
end

function EditorTool.refreshSymbols()
  if not mySymbolRecycler then return end
  if not symbolBarEnabled() then
    pcall(function() if mySymbolBar then mySymbolBar.setVisibility(8) end end)
    return
  end
  if not symbolAdapter then
    symbolAdapter = LuaRecyclerAdapter(symbolItems, "layout.item.symbol", {
      onBindViewHolder = function(holder, pos, views, item)
        local tv = views.symbol
        if item.isClassName then
          tv.setText(item.title)
          pcall(function() TooltipCompat.setTooltipText(tv, item.title) end)
          tv.setTextColor(MyColor.colorPrimary)
          tv.setTextSize(14)
          tv.onClick = function(v)
            if pendingClasses then showClassPopup(v, pendingClasses) end
          end
         else
          tv.setText(item.title)
          pcall(function() TooltipCompat.setTooltipText(tv, item.title) end)
          tv.setTextColor(MyColor.colorOnSurface)
          tv.setTextSize(14)
          tv.onClick = function()
            if item.insertText then insertToEditor(item.insertText)
           elseif item.content then insertToEditor(item.content) end
          end
        end
      end,
    })
    mySymbolRecycler.setAdapter(symbolAdapter)
    mySymbolRecycler.setLayoutManager(LinearLayoutManager(activity, 0, false))
  end

  for i = #symbolItems, 1, -1 do symbolItems[i] = nil end
  if pendingClasses then
    local title
    if #pendingClasses == 1 then
      title = pendingClasses[1]
     else
      title = (pendingClasses[1]:match("([^%.%$]+)$") or pendingClasses[1]) .. " (" .. #pendingClasses .. ")"
    end
    symbolItems[1] = {isClassName = true, title = title}
  end
  for _, v in ipairs(currentSymbolItems()) do symbolItems[#symbolItems + 1] = v end

  local ok = pcall(function() symbolAdapter.notifyDataSetChanged() end)
  if not ok then
    pcall(function()
      mySymbolRecycler.setAdapter(nil)
      mySymbolRecycler.setAdapter(symbolAdapter)
    end)
  end
  if pendingClasses then pcall(function() mySymbolRecycler.scrollToPosition(0) end) end
end

local function checkSelection()
  local sel = nil
  pcall(function() sel = myLuaEditor.getSelectedText() end)
  if sel and sel ~= "" then
    local trimmed = sel:match("^%s*(.-)%s*$") or ""
    if trimmed:match("^[%a_][%w_%.]*$") then
      local resolved = ClassInfo.resolveClassName(trimmed)
      local list = nil
      if type(resolved) == "string" then
        list = { resolved }
       elseif type(resolved) == "table" then
        local seen, dedup = {}, {}
        for _, full in ipairs(resolved) do
          if type(full) == "string" and full ~= "" and not seen[full] then
            seen[full] = true
            dedup[#dedup + 1] = full
          end
        end
        if #dedup > 0 then list = dedup end
      end
      if list then
        local key = table.concat(list, "\n")
        if pendingClassesKey ~= key then
          pendingClasses = list
          pendingClassesKey = key
          EditorTool.refreshSymbols()
        end
        return
      end
    end
  end
  if pendingClasses then
    pendingClasses = nil
    pendingClassesKey = nil
    EditorTool.refreshSymbols()
  end
end

local function postCheck()
  if not myLuaEditor then return end
  pcall(function()
    myLuaEditor.post(luajava.createProxy("java.lang.Runnable", {
      run = function() pcall(checkSelection) end
    }))
  end)
end

function EditorTool.bindSelectionListener()
  local ok = pcall(function()
    selectionListener = luajava.createProxy("com.androlua.LuaEditor$SelectionListener", {
      onSelectionChanged = function(active, selStart, selEnd) pcall(checkSelection) end,
    })
    myLuaEditor.setLuaSelectionListener(selectionListener)
  end)
  if not ok then selectionListener = nil end

  myLuaEditor.setOnTouchListener({
    onTouch = function(v, event)
      if event.getAction() == 1 then postCheck() end
      return false
    end
  })
  myLuaEditor.setOnKeyListener({
    onKey = function(v, keyCode, event)
      if event.getAction() == 1 then postCheck() end
      return false
    end
  })
end


pcall(function()
  myLuaEditor.post(luajava.createProxy("java.lang.Runnable", {
    run = function()
      pcall(function()
        if activity.isDestroyed() or activity.isFinishing() then return end
        EditorTool.refreshSymbols()
        EditorTool.bindSelectionListener()
        EditorSearch.bind()
      end)
    end,
  }))
end)

local restoring = true

myTabLayout.addOnTabSelectedListener({
  onTabSelected = function(tab)
    if restoring then return end

    local idx = tab.getPosition()
    local path = FilesTabManager.tabOrder[idx + 1]
    if not path or path == EditorTool.currentFilePath then return end

    if EditorTool.currentFilePath then
      EditorTool.saveCurrentFile(false)
      EditorTool.saveCurrentLine()
    end
    EditorTool.loadFileToEditor(path, true, false, false)
  end,
  onTabUnselected = function(tab) end,
  onTabReselected = function(tab) end,
})

local restored, lastFile = FilesTabManager.restoreHistory()
if restored and myTabLayout.getTabCount() > 0 then
  local targetIdx = 0
  if lastFile then
    local idx = FilesTabManager.indexOf(lastFile)
    if idx >= 0 then targetIdx = idx end
  end

  local targetPath = FilesTabManager.tabOrder[targetIdx + 1]
  if targetPath then
    EditorTool.loadFileToEditor(targetPath, true, false, false)
  end

  if myTabLayout.getSelectedTabPosition() ~= targetIdx then
    myTabLayout.getTabAt(targetIdx).select()
  end
end

restoring = false

EditorTool.updateEmptyState()

myFileTree.setOnNodeClickListener(function(view, position, dir, isExpanded)
  if dir.isDirectory() then
    EditorTool.currentDirPath = dir.getAbsolutePath()
  end
end)

myFileTree.setOnFileClickListener(function(view, path)
  if type(path) ~= "string" or path == "" then return end
  if EditorTool.currentFilePath then
    EditorTool.saveCurrentFile(false)
    EditorTool.saveCurrentLine()
  end
  local ext = (path:match("%.(%w+)$") or ""):lower()
  if not EditorTool.TEXT_EXTS[ext] then
    MyToast.short("不支持打开该类型文件")
    return
  end

  local idx = FilesTabManager.indexOf(path)
  if idx >= 0 then
    if myTabLayout.getSelectedTabPosition() ~= idx then
      myTabLayout.getTabAt(idx).select()
    end
   else
    EditorTool.loadFileToEditor(path)
  end
  if myDrawerLayout.isDrawerOpen(3) then
    myDrawerLayout.closeDrawer(3)
  end
end)

myFileTree.setOnLongFileClickListener(function(view, path)
  EditorTool.showFileTreeMenu(view, path)
  return true
end)

mySwipeRefresh.setColorSchemeColors({MyColor.colorPrimary})
mySwipeRefresh.setOnRefreshListener({
  onRefresh = function()
    local ok, rerr = pcall(function() myFileTree.refresh(EditorTool.projectDir) end)
    if not ok then MyToast.short("刷新失败：" .. tostring(rerr)) end
    pcall(function()
      if activity.isDestroyed() or activity.isFinishing() then return end
      activity.runOnUiThread(function()
        pcall(function() mySwipeRefresh.setRefreshing(false) end)
      end)
    end)
  end,
})

function onCreateOptionsMenu(menu)
  menu.add("撤销").setShowAsAction(2).setIcon(IconDrawable("undo", MyColor.colorOnSurfaceVariant)).onMenuItemClick = function() myLuaEditor.undo() end
  menu.add("重做").setShowAsAction(2).setIcon(IconDrawable("redo", MyColor.colorOnSurfaceVariant)).onMenuItemClick = function() myLuaEditor.redo() end
  menu.add("运行").setShowAsAction(2).setIcon(IconDrawable("play", 0xFF4CAF50)).onMenuItemClick = function()
    EditorTool.saveCurrentFile(false)
    EditorTool.runProject(EditorTool.projectDir)
  end

  local codeMenu = menu.addSubMenu("代码")
  codeMenu.add("搜索").onMenuItemClick = function() EditorSearch.show() end
  codeMenu.add("去除注释").onMenuItemClick = function()
    if not EditorTool.currentFilePath then
      MyToast.short("请选择文件")
      return
    end
    local content = myLuaEditor.getText().toString()
    local cleaned = EditorTool.removeComments(content)
    if content == cleaned then
      MyToast.short("没有注释")
      return
    end
    MyToast.custom("确定替换当前编辑内容吗？", 3000, "确定", function()
      myLuaEditor.setText(cleaned)
    end)
  end
  codeMenu.add("导入分析").onMenuItemClick = function()
    if not EditorTool.currentFilePath then
      MyToast.short("请先打开文件")
      return
    end
    EditorTool.saveCurrentFile(false)
    local ok = pcall(function()
      MyActivity.new("analysis", { EditorTool.currentFilePath })
    end)
    if not ok then MyToast.short("导入分析不可用") end
  end
  codeMenu.add("语法查错").onMenuItemClick = function()
    local result = EditorTool.checkSyntax(EditorTool.currentFilePath)
    if result == true then
      MyToast.short("没有语法错误")
     elseif result ~= false then
      MaterialAlertDialogBuilder(activity).setTitle("语法错误").setMessage(tostring(result)).setPositiveButton("确定", nil).show()
    end
  end
  codeMenu.add("格式化").onMenuItemClick = function() myLuaEditor.format() end

  local fileMenu = menu.addSubMenu("文件")
  fileMenu.add("关闭全部").onMenuItemClick = function()
    if EditorTool.currentFilePath then
      if not EditorTool.saveCurrentFile(false) then return end
    end
    FilesTabManager.removeAll()
  end
  fileMenu.add("关闭").onMenuItemClick = function()
    local active = FilesTabManager.getActivePath()
    if active then FilesTabManager.remove(active) end
  end
  fileMenu.add("编译").onMenuItemClick = function() EditorTool.compileCurrentFile(EditorTool.currentFilePath) end
  fileMenu.add("保存").onMenuItemClick = function() EditorTool.saveCurrentFile(true) end

  local toolMenu = menu.addSubMenu("工具")
  toolMenu.add("API阅览器").onMenuItemClick = function()
    local ok = pcall(function() MyActivity.new("api") end)
    if not ok then MyToast.short("API阅览器不可用") end
  end
  toolMenu.add("调色板").onMenuItemClick = function()
    local okP, picker = pcall(require, "utils.ColorPicker")
    if okP and picker then
      pcall(function()
        picker.show(activity, 0xFFFFFFFF, function(newColor)
          local color = string.format("#%08X", newColor)
          EditorTool.copyToClipboard(color)
        end)
      end)
     else
      MyToast.short("调色板不可用")
    end
  end
  toolMenu.add("日志").onMenuItemClick = function()
    MyActivity.new("log")
  end

  menu.add("打包").onMenuItemClick = function()
    if EditorTool.currentFilePath then
      EditorTool.saveCurrentFile(false)
      EditorTool.saveCurrentLine()
    end
    local ok = pcall(function()
      MyActivity.new("build", { EditorTool.projectFolderName })
    end)
    if not ok then
      MyToast.short("无法打包项目")
    end
  end
end

function onOptionsItemSelected(item)
  if drawerToggle.onOptionsItemSelected(item) then return true end
  return false
end

function onPause()
  if EditorTool.currentFilePath then
    EditorTool.saveCurrentFile(false)
    EditorTool.saveCurrentLine()
  end
  FilesTabManager.persist()
end

function onDestroy()
  if EditorTool.currentFilePath then
    EditorTool.saveCurrentFile(false)
    EditorTool.saveCurrentLine()
  end
  FilesTabManager.persist()

  pcall(function() EditorSearch.onActivityDestroy() end)

  pcall(function()
    if mySymbolRecycler then mySymbolRecycler.setAdapter(nil) end
  end)

  FilesTabManager.tabData = {}
  FilesTabManager.tabOrder = {}
  EditorTool.currentFilePath = nil
  EditorTool.currentDirPath = nil
  collectgarbage("collect")
  collectgarbage("step")
end

function onKeyDown(key)
  if key == 4 then
    if myDrawerLayout.isDrawerOpen(3) then myDrawerLayout.closeDrawer(3)
   else activity.finish() end
    return true
  end
end

local onThemeResume = makeThemeResumeChecker()

function onResume()
  onThemeResume()
end
