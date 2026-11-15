local _M = {}
local bindClass = luajava.bindClass
local MaterialAlertDialogBuilder = bindClass "com.google.android.material.dialog.MaterialAlertDialogBuilder"
local FileClass = bindClass "java.io.File"
local PopupMenu = bindClass "androidx.appcompat.widget.PopupMenu"
local Context = bindClass "android.content.Context"
local ClipData = bindClass "android.content.ClipData"

local ProjectUtil = require "utils.Project"
local FileHistory = require "utils.FileHistory"
local FilesTabManager = require "dialogs.FilesTabManager"

local MyGlide = nil
local FormDialog = nil

local asyncFileOk, AsyncFile = pcall(bindClass, "com.briarlua.utils.AsyncFile")

local loadSeq = 0

_M.currentFilePath = nil
_M.currentDirPath = nil
_M.projectDir = nil
_M.projectFolderName = nil
_M.projectName = nil
_M.TEXT_EXTS = {
  lua = true, aly = true, txt = true, md = true, json = true,
  java = true, kt = true, js = true, ts = true, py = true,
  php = true, xml = true, html = true, css = true, gradle = true,
  yml = true, yaml = true, ini = true, conf = true, sh = true,
}

function _M.copyToClipboard(text)
  local ok = pcall(function()
    local cm = activity.getSystemService(Context.CLIPBOARD_SERVICE)
    cm.setPrimaryClip(ClipData.newPlainText("path", text))
  end)
  MyToast.short(ok and "已复制" or "复制失败")
end

function _M.syncEditorStateByPath(path)
  if not path then return end
  local ext = (path:match("%.(%w+)$") or ""):lower()
  if ext == "lua" or ext == "aly" then
    myLuaEditor.setEnableDrawingErrMsg(true)
   else
    myLuaEditor.setEnableDrawingErrMsg(false)
  end
end

function _M.checkSyntax(filePath)
  if not filePath then
    MyToast.short("请选择文件")
    return false
  end

  local ext = (filePath:match("%.(%w+)$") or ""):lower()
  if ext ~= "lua" and ext ~= "aly" then
    MyToast.short("只支持检查 .lua / .aly 文件")
    return false
  end

  local src = myLuaEditor.getText().toString()
  if type(src) ~= "string" then src = tostring(src) end

  if ext == "aly" then
    src = "return " .. src
  end

  local fn, err = loadstring(src)
  if fn then return true end

  local line, msg = tostring(err):match(":(%d+):%s*(.+)$")
  if line then
    local ln = tonumber(line)
    pcall(function() myLuaEditor.gotoLine(ln) end)
    pcall(function() myLuaEditor.requestLayout() end)
    if ln == 1 then
      pcall(function() myLuaEditor.moveCaret(0) end)
    end
    return line .. "：" .. msg
  end
  return tostring(err)
end

function _M.removeComments(str)
  local result = {}
  local i = 1
  local len = #str
  local in_string = false
  local string_char = nil
  local in_long_string = false
  local in_comment = false
  local long_level = 0
  while i <= len do
    local char = str:sub(i, i)
    local next_char = str:sub(i + 1, i + 1)
    if in_comment then
      if char == ']' then
        local eq_count = 0
        while i + eq_count + 1 <= len and str:sub(i + eq_count + 1, i + eq_count + 1) == '=' do
          eq_count = eq_count + 1
        end
        if i + eq_count + 1 <= len and str:sub(i + eq_count + 1, i + eq_count + 1) == ']' then
          if eq_count == long_level then
            in_comment = false
            i = i + eq_count + 1
          end
        end
      end
     elseif in_long_string then
      table.insert(result, char)
      if char == ']' then
        local eq_count = 0
        while i + eq_count + 1 <= len and str:sub(i + eq_count + 1, i + eq_count + 1) == '=' do
          eq_count = eq_count + 1
        end
        if eq_count == long_level and i + eq_count + 1 <= len and str:sub(i + eq_count + 1, i + eq_count + 1) == ']' then
          in_long_string = false
          local closing = str:sub(i + 1, i + eq_count + 1)
          table.insert(result, closing)
          i = i + eq_count + 1
        end
      end
     elseif in_string then
      table.insert(result, char)

      if char == '\\' then
        if i < len then
          table.insert(result, next_char)
          i = i + 1
        end
       elseif char == string_char then
        in_string = false
      end
     else
      if char == '"' or char == "'" then
        in_string = true
        string_char = char
        table.insert(result, char)
       elseif char == '[' then
        local eq_count = 0
        while i + eq_count + 1 <= len and str:sub(i + eq_count + 1, i + eq_count + 1) == '=' do
          eq_count = eq_count + 1
        end
        if i + eq_count + 1 <= len and str:sub(i + eq_count + 1, i + eq_count + 1) == '[' then
          in_long_string = true
          long_level = eq_count
          local opening = str:sub(i, i + eq_count + 1)
          table.insert(result, opening)
          i = i + eq_count + 1
         else
          table.insert(result, char)
        end
       elseif char == '-' and next_char == '-' then
        local j = i + 2
        if str:sub(j, j) == '[' then
          local eq_count = 0
          while j + eq_count + 1 <= len and str:sub(j + eq_count + 1, j + eq_count + 1) == '=' do
            eq_count = eq_count + 1
          end
          if j + eq_count + 1 <= len and str:sub(j + eq_count + 1, j + eq_count + 1) == '[' then
            in_comment = true
            long_level = eq_count
            i = j + eq_count + 1
           else
            while i <= len and str:sub(i, i) ~= '\n' and str:sub(i, i) ~= '\r' do
              i = i + 1
            end
            i = i - 1
          end
         else
          while i <= len and str:sub(i, i) ~= '\n' and str:sub(i, i) ~= '\r' do
            i = i + 1
          end
          i = i - 1
        end
        if #result > 0 and result[#result] ~= " " and result[#result] ~= "\n" and result[#result] ~= "\r" then
          table.insert(result, " ")
        end
       else
        table.insert(result, char)
      end
    end
    i = i + 1
  end
  return table.concat(result)
end

function _M.compileCurrentFile(filePath)
  if not filePath or filePath == "" then
    MyToast.short("请选择文件")
    return false
  end

  local ext = filePath:match("%.(%w+)$")
  if ext ~= "lua" and ext ~= "aly" then
    MyToast.short("只支持编译 .lua / .aly 文件")
    return false
  end

  if filePath == _M.currentFilePath then
    local content = myLuaEditor.getText().toString()
    local wok, werr = ProjectUtil.writeFile(filePath, content)
    if not wok then
      MyToast.short("保存失败：" .. tostring(werr))
      return false
    end
  end

  local tmpPath, cerr2
  if ext == "lua" then
    tmpPath, cerr2 = ProjectUtil.compileLuaFile(filePath)
   else
    tmpPath, cerr2 = ProjectUtil.compileAlyFile(filePath)
  end

  if not tmpPath then
    MaterialAlertDialogBuilder(activity)
    .setTitle("编译失败")
    .setMessage(tostring(cerr2 or "未知错误"))
    .setPositiveButton("确定", nil)
    .show()
    return false
  end

  if tmpPath == filePath then
    MaterialAlertDialogBuilder(activity)
    .setTitle("编译成功")
    .setMessage("源文件已是字节码，未重新生成：\n" .. filePath)
    .setPositiveButton("确定", nil)
    .show()
    return true
  end

  local dir = filePath:match("^(.*)/[^/]+$") or ""
  local base = filePath:match("([^/]+)%.[^.]+$") or "chunk"
  local finalPath = (dir ~= "" and dir .. "/" or "") .. base .. ".luac"

  local srcBin = io.open(tmpPath, "rb")
  if not srcBin then
    MyToast.short("读取编译产物失败")
    pcall(function() os.remove(tmpPath) end)
    return false
  end
  local data = srcBin:read("*a")
  srcBin:close()

  local dstBin = io.open(finalPath, "wb")
  if not dstBin then
    MyToast.short("写入产物失败：" .. finalPath)
    pcall(function() os.remove(tmpPath) end)
    return false
  end
  dstBin:write(data)
  dstBin:close()

  pcall(function() os.remove(tmpPath) end)
  myFileTree.refresh(_M.projectDir)

  MaterialAlertDialogBuilder(activity)
  .setTitle("编译成功")
  .setMessage("产物：" .. finalPath .. "\n字节数：" .. #data)
  .setPositiveButton("确定", nil)
  .show()
  return true
end

function _M.updateEmptyState()
  local count = myTabLayout and myTabLayout.getTabCount() or 0
  local empty = (count == 0)
  pcall(function()
    if activity.isDestroyed() or activity.isFinishing() then return end
    activity.runOnUiThread(function()
      if empty then
        if myNoFileImage then
          pcall(function()
            if not MyGlide then MyGlide = require "utils.Glide" end
            MyGlide.set(backImage("lua"), myNoFileImage)
          end)
        end
        if myNoFileTip then myNoFileTip.setVisibility(0) end
        if myLuaEditor then myLuaEditor.setVisibility(8) end
        if myTabLayout then myTabLayout.setVisibility(8) end
        if mySymbolBar then mySymbolBar.setVisibility(8) end

        if mySymbolRecycler and _M.refreshSymbols then _M.refreshSymbols() end
       else
        if myNoFileTip then myNoFileTip.setVisibility(8) end
        if myLuaEditor then myLuaEditor.setVisibility(0) end
        if myTabLayout then myTabLayout.setVisibility(0) end
        local showSymbol = activity.getSharedData("show_symbol") ~= false
        if mySymbolBar then mySymbolBar.setVisibility(showSymbol and 0 or 8) end
      end
    end)
  end)
end

function _M.getCurrentLineNumber()
  if not myLuaEditor then return 1 end
  local ok, line = pcall(function() return myLuaEditor.getCaretLine() end)
  if ok and type(line) == "number" and line >= 1 then
    return line
  end
  return 1
end

function _M.saveCurrentLine()
  if not _M.currentFilePath then return end
  FileHistory.setLine(_M.projectFolderName, _M.currentFilePath, _M.getCurrentLineNumber())
end

function _M.saveCurrentFile(silent)
  if not _M.currentFilePath then
    if silent then MyToast.short("请选择文件") end
    return true
  end
  local content = myLuaEditor.getText().toString()
  local ok, werr = ProjectUtil.writeFile(_M.currentFilePath, content)
  if not ok then
    MyToast.short("保存失败：" .. tostring(werr))
    return false
  end
  if silent then MyToast.short("已保存") end
  return true
end

local function applyLoadedContent(path, content, line, skipAddTab, skipLastFile)
  myLuaEditor.setText(content)
  _M.currentFilePath = path
  _M.syncEditorStateByPath(path)

  if not skipLastFile then
    FileHistory.setLastFile(_M.projectFolderName, path)
  end
  if line and line > 1 then
    pcall(function() myLuaEditor.gotoLine(line) end)
    pcall(function() myLuaEditor.requestLayout() end)
  end
  if not skipAddTab then
    FilesTabManager.add(path)
    local idx = FilesTabManager.indexOf(path)
    if idx >= 0 and myTabLayout.getSelectedTabPosition() ~= idx then
      myTabLayout.getTabAt(idx).select()
    end
  end
  _M.updateEmptyState()
  return true
end

function _M.loadFileToEditor(path, skipAddTab, skipLastFile, skipLineRestore)
  if not path or path == "" then return false end

  local line = nil
  if not skipLineRestore then
    line = FileHistory.getLine(_M.projectFolderName, path)
  end

  local seq = loadSeq + 1
  loadSeq = seq

  if asyncFileOk then
    local scheduled = false
    local ok2 = pcall(function()
      AsyncFile.read(path, 4 * 1024 * 1024, function(content, err)
        if seq ~= loadSeq then return end
        pcall(function()
          if activity.isDestroyed() or activity.isFinishing() then return end
          if content == nil then
            MyToast.short("打开失败：" .. tostring(err))
            return
          end
          applyLoadedContent(path, content, line, skipAddTab, skipLastFile)
        end)
      end)
      scheduled = true
    end)
    if ok2 and scheduled then return true end
    asyncFileOk = false
  end

  local content, rerr = ProjectUtil.readFile(path)
  if not content then
    MyToast.short("打开失败：" .. tostring(rerr))
    return false
  end
  return applyLoadedContent(path, content, line, skipAddTab, skipLastFile)
end

function _M.runProject(dir)
  if not dir then return end
  local ok, rerr = pcall(function()
    activity.newActivity(dir .. "/main.lua")
  end)
  if not ok then MyToast.short("无法运行项目: " .. tostring(rerr)) end
end

function _M.initTabCallbacks()
  FilesTabManager.onTabCountChanged = function(count)
    if count == 0 then
      myLuaEditor.setText("")
      _M.currentFilePath = nil
      _M.currentDirPath = _M.projectDir
      _M.updateEmptyState()
    end
  end

  FilesTabManager.onBeforeRemove = function(path)
    if _M.currentFilePath and _M.currentFilePath == path then
      if not FileClass(path).exists() then return true end
      _M.saveCurrentLine()
      return _M.saveCurrentFile(false)
    end
    return true
  end
end

local function copyPathMenu(path, isDir)
  local projectBase = _M.projectDir:match("[^/]+$") or ""
  if path == _M.projectDir then
    _M.copyToClipboard(projectBase)
    return
  end
  local relPath = path:sub(#_M.projectDir + 2)
  local baseName = path:match("[^/]+$") or path
  local formats = { projectBase .. "/" .. relPath, relPath }
  if not isDir and baseName ~= relPath then
    formats[#formats + 1] = baseName
  end
  MaterialAlertDialogBuilder(activity)
  .setTitle("复制路径")
  .setItems(formats, function(dialog, which)
    _M.copyToClipboard(formats[which + 1])
  end)
  .show()
end

function _M.showFileTreeMenu(view, path)
  if type(path) ~= "string" or path == "" then
    MyToast.short("无效路径")
    return
  end

  if not FormDialog then FormDialog = require "dialogs.FormDialog" end

  local name = path:match("[^/]+$") or path
  local isDir = FileClass(path).isDirectory()
  if isDir then _M.currentDirPath = path end

  local isRoot = (path == _M.projectDir)
  local popup = PopupMenu(activity, view)
  local menu = popup.getMenu()

  if isDir then
    menu.add("新建文件夹")
    menu.add("新建文件")
    menu.add("复制路径")
    if not isRoot then
      menu.add("重命名")
      menu.add("删除")
    end
    menu.add("刷新")
   else
    menu.add("复制路径")
    menu.add("重命名")
    menu.add("删除")
  end

  popup.setOnMenuItemClickListener({
    onMenuItemClick = function(item)
      local title = tostring(item.getTitle())
      if title == "新建文件" then
        FormDialog.input({
          title = "新建文件", hint = "文件名", strokeColor = MyColor.colorPrimary,
          positive = { text = "创建", onClick = function(newName)
              if newName == "" then return end
              local newPath = (_M.currentDirPath or _M.projectDir) .. "/" .. newName
              if FileClass(newPath).exists() then MyToast.short("文件已存在") return end
              local ok, werr = ProjectUtil.writeFile(newPath, "")
              if ok then MyToast.short("已创建"); myFileTree.refresh(_M.currentDirPath or _M.projectDir)
               else MyToast.short("创建失败：" .. tostring(werr)) end
            end }
          })
         elseif title == "新建文件夹" then
          FormDialog.input({
            title = "新建文件夹", hint = "文件夹名", strokeColor = MyColor.colorPrimary,
            positive = { text = "创建", onClick = function(newName)
                if newName == "" then return end
                local newPath = (_M.currentDirPath or _M.projectDir) .. "/" .. newName
                local ok, derr = ProjectUtil.ensureDir(newPath)
                if ok then MyToast.short("已创建"); myFileTree.refresh(_M.currentDirPath or _M.projectDir)
                 else MyToast.short("创建失败：" .. tostring(derr)) end
              end }
            })
           elseif title == "刷新" then
            myFileTree.refresh(path)
           elseif title == "重命名" then
            FormDialog.input({
              title = "重命名", hint = "新名称", initial = name, strokeColor = MyColor.colorPrimary,
              positive = { text = "确定", onClick = function(newName)
                  if newName == "" or newName == name then return end

                  local parent = FileClass(path).getParent()
                  local newPath = parent .. "/" .. newName
                  local ok = pcall(function() return FileClass(path).renameTo(FileClass(newPath)) end)

                  if ok and FileClass(newPath).exists() then
                    MyToast.short("已重命名")
                    if not isDir then FilesTabManager.rename(path, newPath) end
                    myFileTree.refresh(_M.projectDir)
                    if _M.currentFilePath == path then _M.currentFilePath = newPath end
                    if _M.currentDirPath == path then _M.currentDirPath = newPath end
                   else
                    MyToast.short("重命名失败: 目标已存在或权限不足")
                  end
              end }
            })
           elseif title == "复制路径" then
            copyPathMenu(path, isDir)
           elseif title == "删除" then
            MaterialAlertDialogBuilder(activity)
            .setTitle("确认删除")
            .setMessage("确定删除 " .. name .. " ？此操作不可恢复。")
            .setPositiveButton("删除", function()
              local ok, derr = ProjectUtil.deleteRecursive(path)
              if ok then
                MyToast.short("已删除")
                if not isDir then
                  FilesTabManager.remove(path)
                 else
                  local toRemove = {}
                  for p in pairs(FilesTabManager.tabData) do
                    if p == path or p:find("^" .. path .. "/") then toRemove[#toRemove + 1] = p end
                  end
                  for _, p in ipairs(toRemove) do FilesTabManager.remove(p) end
                end

                if _M.currentDirPath == path or _M.currentDirPath:find("^" .. path .. "/") then
                  _M.currentDirPath = FileClass(path).getParent() or _M.projectDir
                end
                if _M.currentFilePath and (_M.currentFilePath == path or _M.currentFilePath:find("^" .. path .. "/")) then
                  myLuaEditor.setText("")
                  _M.currentFilePath = nil
                end

                myFileTree.refresh(_M.projectDir)
                _M.updateEmptyState()
               else
                MyToast.short("删除失败：" .. tostring(derr))
              end
            end)
            .setNegativeButton("取消", nil)
            .show()
          end
          return true
        end,
      })
      popup.show()
    end

return _M