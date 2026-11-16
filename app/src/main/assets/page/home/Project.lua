local _M = {}
local bindClass = luajava.bindClass
local ProjectUtil = require "utils.Project"
local LuaRecyclerAdapter = require "utils.LuaRecyclerAdapter"
local MyGlide = require "utils.Glide"
local IconDrawable = require "utils.IconDrawable"
local FormDialog = require "dialogs.FormDialog"

local LinearLayoutManager = bindClass "androidx.recyclerview.widget.LinearLayoutManager"
local MaterialAlertDialogBuilder = bindClass "com.google.android.material.dialog.MaterialAlertDialogBuilder"
local Intent = bindClass "android.content.Intent"

_M.data = {}
_M.adapter = nil
_M.initialized = false

local IMPORT_REQUEST_CODE = 1001

local function safeText(v, fallback)
  if v == nil or v == "" or type(v) == "table" then
    return fallback or ""
  end
  return tostring(v)
end

local function uiCall(fn)
  if not fn then return end
  pcall(fn)
end

local function setRefreshing(on)
  if mySwipeRefreshLayout then
    uiCall(function() mySwipeRefreshLayout.setRefreshing(on) end)
  end
end

local function notifyAdapter()
  if _M.adapter then
    uiCall(function() _M.adapter.notifyDataSetChanged() end)
  end
end

local function toast(text)
  if MyToast and MyToast.onViewAction and myFab then
    MyToast.onViewAction(myFab, text)
   elseif MyToast and MyToast.short then
    MyToast.short(text)
  end
end

local function buildMeta(info)
  local ver = safeText(info.appver)
  if ver == "" then return "" end
  local v = "v" .. ver
  local code = safeText(info.appcode)
  if code ~= "" then v = v .. " (" .. code .. ")" end
  return v
end

local function updateEmptyState()
  local empty = #_M.data == 0
  if empty == _M._emptyShown then return end
  _M._emptyShown = empty
  uiCall(function()
    if empty then
      if myNoProjectImage and MyGlide and backImage then
        pcall(function() MyGlide.set(backImage("lua"), myNoProjectImage) end)
      end
      if myNoProjectTip then myNoProjectTip.setVisibility(0) end
     else
      if myNoProjectTip then myNoProjectTip.setVisibility(8) end
    end
  end)
end


local function buildImportMessage(cfg, isClone, destName, newPkg)
  local lines = {}
  lines[#lines + 1] = "应用名称：" .. safeText(cfg.appname, "未命名")
  lines[#lines + 1] = "包名：" .. safeText(cfg.packagename, "-")
  lines[#lines + 1] = "版本：" .. safeText(cfg.appver, "1.0") .. " (" .. safeText(cfg.appcode, "1") .. ")"
  lines[#lines + 1] = "SDK：" .. safeText(cfg.minsdk, "23") .. " / " .. safeText(cfg.appsdk, "35")
  lines[#lines + 1] = "权限数：" .. tostring(#(cfg.user_permission or {}))
  lines[#lines + 1] = ""
  if isClone then
    lines[#lines + 1] = "检测到同名项目已存在，将以克隆方式导入："
    lines[#lines + 1] = "新项目名：" .. destName
    if newPkg then
      lines[#lines + 1] = "新包名：" .. newPkg
    end
   else
    lines[#lines + 1] = "将导入为项目目录：" .. destName
  end
  return table.concat(lines, "\n")
end

local function confirmImport(tmpPath, cfg, destName, isClone, newPkg)
  local builder = MaterialAlertDialogBuilder(activity)
  builder.setTitle(isClone and "克隆导入项目" or "导入项目")
  builder.setMessage(buildImportMessage(cfg, isClone, destName, newPkg))
  builder.setPositiveButton(isClone and "克隆导入" or "导入", {
    onClick = function()
      local ok, result = ProjectUtil.importProject(tmpPath, destName)
      ProjectUtil.cleanupTempFile(tmpPath)
      if not ok then
        toast("导入失败: " .. tostring(result))
        return
      end

      if isClone then
        ProjectUtil.updateInit(result, {
          appname = destName,
          packagename = newPkg,
        })

        local mainPath = result .. "/main.lua"
        if ProjectUtil.fileExists(mainPath) then
          local mainContent = ProjectUtil.readFile(mainPath)
          if mainContent then
            mainContent = mainContent:gsub('setTitle%s*%(%s*"[^"]*"%s*%)', 'setTitle(' .. string.format("%q", tostring(cfg.appname)) .. ')')
            ProjectUtil.writeFile(mainPath, mainContent)
          end
        end
      end

      toast("导入成功: " .. destName)
      _M.refresh()
    end
  })
  builder.setNegativeButton("取消", {
    onClick = function()
      ProjectUtil.cleanupTempFile(tmpPath)
    end
  })
  builder.setOnCancelListener(luajava.createProxy("android.content.DialogInterface$OnCancelListener", {
    onCancel = function()
      ProjectUtil.cleanupTempFile(tmpPath)
    end
  }))
  builder.show()
end

local function handleImportUri(uri)
  if not uri then return end

  local tmpPath, err = ProjectUtil.uriToTempFile(uri)
  if not tmpPath then
    toast("读取文件失败: " .. tostring(err))
    return
  end

  local cfg, peekErr = ProjectUtil.peekInitFromArchive(tmpPath)
  if not cfg then
    ProjectUtil.cleanupTempFile(tmpPath)
    toast("无效项目文件: " .. tostring(peekErr))
    return
  end

  local baseName = cfg.appname
  if not baseName or baseName == "" then
    baseName = ProjectUtil.suggestProjectName(uri, tmpPath)
  end

  local baseDir = baseName:gsub("[/\\:*?\"<>|]", "_")
  local isClone = ProjectUtil.dirExists(ProjectUtil.getProjectDir(baseDir))
  local destName = ProjectUtil.makeCloneProjectName(baseName)
  local newPkg = isClone and ProjectUtil.makeClonePackageName(cfg.packagename) or nil

  confirmImport(tmpPath, cfg, destName, isClone, newPkg)
end

function onActivityResult(requestCode, resultCode, data)
  if requestCode ~= IMPORT_REQUEST_CODE then return end
  local uri = nil
  if resultCode == - 1 and data then
    pcall(function() uri = data.getData() end)
  end
  if uri then
    handleImportUri(uri)
  end
end

function _M.importProject()
  local ok, err = pcall(function()
    local intent = Intent(Intent.ACTION_GET_CONTENT)
    intent.setType("*/*")
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    activity.startActivityForResult(intent, IMPORT_REQUEST_CODE)
  end)
  if not ok then
    toast("无法打开文件选择器: " .. tostring(err))
  end
end


local function showProjectMenu(info)
  local items = { "运行", "打开", "属性", "重命名", "克隆", "备份", "打包", "删除" }
  local builder = MaterialAlertDialogBuilder(activity)
  builder.setTitle(safeText(info.appname or info.name, info.name))
  builder.setItems(items, {
    onClick = function(dialog, which)
      local action = items[which + 1]
      if action == "运行" then _M.runProject(info)
       elseif action == "打开" then _M.openProject(info)
       elseif action == "属性" then _M.showInfo(info)
       elseif action == "重命名" then _M.renameProject(info)
       elseif action == "克隆" then _M.cloneProject(info)
       elseif action == "备份" then _M.backupProject(info)
       elseif action == "打包" then _M.buildProject(info)
       elseif action == "删除" then _M.deleteProject(info)
      end
    end
  })
  builder.show()
end

function _M.runProject(info)
  if not info or not info.dir then return end
  local ok, err = pcall(function()
    activity.newActivity(info.dir .. "/main.lua")
  end)
  if not ok then
    toast("无法运行项目: " .. tostring(err))
  end
end

function _M.openProject(info)
  if not info or not info.dir then return end
  local ok = pcall(function() MyActivity.new("editor", { info.dir }) end)
  if not ok then
    toast("项目: " .. safeText(info.appname, info.name) .. "\n" .. info.dir)
  end
end

function _M.buildProject(info)
  if not info or not info.name then return end
  local ok = pcall(function()
    MyActivity.new("build", { info.name })
  end)
  if not ok then
    toast("无法打开打包页")
  end
end

function _M.showInfo(info)
  if not info or not info.dir then return end
  local ok = pcall(function()
    MyActivity.new("info", { info.dir })
  end)
  if not ok then
    toast("无法打开属性页: " .. safeText(info.dir))
  end
end

function _M.renameProject(info)
  FormDialog.input({
    context = activity,
    title = "重命名项目",
    hint = "项目名称",
    initial = info.name,
    strokeColor = MyColor.colorPrimary,
    positive = {
      text = "确定",
      onClick = function(newName)
        if newName == "" then
          toast("请输入项目名称")
          return
        end
        local ok, err = ProjectUtil.renameProject(info.name, newName)
        if ok then
          toast("重命名成功")
          _M.refresh()
         else
          toast("失败: " .. tostring(err))
        end
      end
    }
  })
end

function _M.cloneProject(info)
  local newNameDefault = ProjectUtil.makeCloneProjectName(info.name)
  local pkgDefault = ProjectUtil.makeClonePackageName(info.packagename)
  FormDialog.show({
    context = activity,
    title = "克隆项目",
    strokeColor = MyColor.colorPrimary,
    fields = {
      { key = "name", hint = "项目名称", initial = newNameDefault },
      { key = "packagename", hint = "包名", initial = pkgDefault },
      { key = "appver", hint = "版本号", initial = safeText(info.appver, "1.0") },
      { key = "appcode", hint = "版本代码", initial = safeText(info.appcode, "1") },
      { key = "minsdk", hint = "MinSDK", initial = safeText(info.minsdk, "23") },
      { key = "appsdk", hint = "TargetSDK", initial = safeText(info.appsdk, "35") },
    },
    positive = {
      text = "克隆",
      onClick = function(values)
        if values.name == "" then
          toast("请输入项目名称")
          return
        end
        if ProjectUtil.dirExists(ProjectUtil.getProjectDir(values.name)) then
          toast("项目已存在: " .. values.name)
          return
        end
        if values.packagename == "" then
          toast("包名不能为空")
          return
        end
        values.appname = values.name
        local ok, err = ProjectUtil.cloneProject(info.name, values.name, values)
        if ok then
          toast("克隆成功")
          _M.refresh()
         else
          toast("克隆失败: " .. tostring(err))
        end
      end
    }
  })
end

function _M.deleteProject(info)
  local builder = MaterialAlertDialogBuilder(activity)
  builder.setTitle("删除项目")
  builder.setMessage("确定删除「" .. safeText(info.appname, info.name) .. "」吗？\n\n" .. safeText(info.dir) .. "\n\n此操作不可恢复。")
  builder.setPositiveButton("删除", {
    onClick = function()
      local ok, err = ProjectUtil.deleteProject(info.name)
      if ok then
        toast("已删除")
        _M.refresh()
       else
        toast("删除失败: " .. tostring(err))
      end
    end
  })
  builder.setNegativeButton("取消", nil)
  builder.show()
end

function _M.backupProject(info)
  if not info then return end
  local ok, result = ProjectUtil.backupProject(info.name)
  if ok then
    toast("备份成功: " .. tostring(result))
   else
    toast("备份失败: " .. tostring(result))
  end
end

function _M.newProject()
  local base = "MyApplication"
  local n = 1
  while ProjectUtil.dirExists(ProjectUtil.getProjectDir(base .. n)) do n = n + 1 end
  local nameDefault = base .. n

  local pkgDefault = ProjectUtil.makePackageNameByCount("myapplication")

  FormDialog.show({
    context = activity,
    title = "新建项目",
    strokeColor = MyColor.colorPrimary,
    fields = {
      { key = "name", hint = "项目名称", initial = nameDefault },
      { key = "packagename", hint = "包名", initial = pkgDefault },
      { key = "appver", hint = "版本号", initial = "1.0" },
      { key = "appcode", hint = "版本代码", initial = "1" },
      { key = "minsdk", hint = "MinSDK", initial = "23" },
      { key = "appsdk", hint = "TargetSDK", initial = "35" },
    },
    positive = {
      text = "创建",
      onClick = function(values)
        if values.name == "" then
          toast("请输入项目名称")
          return
        end
        if ProjectUtil.dirExists(ProjectUtil.getProjectDir(values.name)) then
          toast("项目已存在: " .. values.name)
          return
        end
        if values.packagename == "" then
          toast("包名不能为空")
          return
        end
        values.appname = values.name
        local ok, err = ProjectUtil.newProject(values.name, values)
        if ok then
          toast("创建成功")
          _M.refresh()
         else
          toast("创建失败: " .. tostring(err))
        end
      end
    }
  })
end


function _M.loadData()
  local ok, list = pcall(function() return ProjectUtil.listProjects() end)
  for i = #_M.data, 1, - 1 do _M.data[i] = nil end
  if ok and type(list) == "table" then
    for i = 1, #list do _M.data[i] = list[i] end
  end
  return _M.data
end

function _M.refresh()
  if not _M.initialized then
    _M.init()
    return
  end
  _M.loadData()
  notifyAdapter()
  setRefreshing(false)
  updateEmptyState()
end

function _M.init()
  if _M.initialized then
    _M.refresh()
    return
  end

  pcall(function() ProjectUtil.initDirs() end)

  if not myProjectRecyclerView then return end

  _M.loadData()

  _M.adapter = LuaRecyclerAdapter(_M.data, "layout.item.project", {
    onBindViewHolder = function(holder, position, views, info)
      if not info or not views then return end
      if views.title then views.title.setText(safeText(info.appname or "未知")) end
      if views.packageName then views.packageName.setText(safeText(info.packagename, "未配置包名")) end
      if views.meta then
        local meta = buildMeta(info)
        if meta ~= "" then
          views.meta.setVisibility(0)
          views.meta.setText(meta)
         else
          views.meta.setVisibility(8)
        end
      end
      if views.icon then
        if info.icon then
          pcall(function() MyGlide.setFile(info.icon, views.icon, nil, info.iconMtime) end)
         else
          pcall(function() MyGlide.set(ProjectUtil.template_icon, views.icon) end)
        end
      end
      if views.card then
        views.card.onClick = function()
          _M.openProject(info)
        end
        views.card.onLongClick = function()
          showProjectMenu(info)
          return true
        end
      end
    end
  })

  myProjectRecyclerView.setAdapter(_M.adapter)
  pcall(function() myProjectRecyclerView.setLayoutManager(LinearLayoutManager(activity)) end)

  if mySwipeRefreshLayout then
    pcall(function()
      mySwipeRefreshLayout.setColorSchemeColors({ MyColor.colorPrimary })
      mySwipeRefreshLayout.setOnRefreshListener({
        onRefresh = function() _M.refresh() end
      })
    end)
  end

  _M.initialized = true
  updateEmptyState()
end

return _M