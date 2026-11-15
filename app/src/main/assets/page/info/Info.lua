require "env"
local newInstance = luajava.newInstance
local bindClass = luajava.bindClass
local WindowManager = bindClass "android.view.WindowManager"
local ProjectUtil = require "utils.Project"
local LuaRecyclerAdapter = require "utils.LuaRecyclerAdapter"
local MyGlide = require "utils.Glide"
local LinearLayoutManager = bindClass "androidx.recyclerview.widget.LinearLayoutManager"
local File = bindClass "java.io.File"
local Handler = bindClass "android.os.Handler"
local Looper = bindClass "android.os.Looper"
local Intent = bindClass "android.content.Intent"

local projectDir = ...
if type(projectDir) == "table" then projectDir = projectDir[1] end
if not projectDir or projectDir == "" then
  MyToast.short("缺少项目路径")
  return
end

setStatus()
applyTheme()

activity
.setContentView(loadlayout("layout.info"))
.setSupportActionBar(myToolbar)
.getSupportActionBar()
.setDisplayHomeAsUpEnabled(true)
.setTitle("项目属性")

activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

MyToast.setActivity(activity)

myTabLayout.setupWithViewPager(myViewPager)

local cfg, err = ProjectUtil.readInit(projectDir)
if not cfg then
  MyToast.short("读取配置失败: " .. tostring(err))
  return
end

local cancelled = false
local mainHandler = Handler(Looper.getMainLooper())
local searchHandler = Handler()
local permissionTask = nil

local ICON_REQUEST_CODE = 1002
local pendingIconPath = nil

function onDestroy()
  cancelled = true

  pcall(function()
    mainHandler.removeCallbacksAndMessages(nil)
    searchHandler.removeCallbacksAndMessages(nil)
  end)

  if permissionTask then
    pcall(function() permissionTask.cancel(true) end)
    permissionTask = nil
  end

  cleanupTempIcon()

  pcall(function()
    permissionRecycler.setAdapter(nil)
    skipRecycler.setAdapter(nil)
  end)

  allPermissions = {}
  permissionList = {}
  allLuaFiles = {}
  skipList = {}
  selectedMap = {}
  skipSelectedMap = {}

  pcall(function()
    if appIcon then appIcon.setImageDrawable(nil) end
  end)

  mainHandler = nil
  searchHandler = nil
end

function onKeyDown(code, event)
  if code == 4 then
    cancelled = true
    pcall(function() activity.finish() end)
    return true
  end
end

function onOptionsItemSelected(item)
  if item.getItemId() == android.R.id.home then
    cancelled = true
    activity.finish()
    return true
  end
end

local function isAlive()
  if cancelled then return false end
  local ok, finishing = pcall(function() return activity.isFinishing() end)
  if ok and finishing then return false end
  local ok2, destroyed = pcall(function() return activity.isDestroyed() end)
  if ok2 and destroyed then return false end
  return true
end

local function safeStrokeColor(checked)
  return checked and MyColor.colorPrimary or MyColor.colorOutlineVariant
end

local function postDelayed(fn, delay)
  local runnable = luajava.createProxy("java.lang.Runnable", {
    run = function()
      if not isAlive() then return end
      pcall(fn)
    end
  })
  mainHandler.postDelayed(runnable, delay)
end

local function copyUriToTempImage(uri)
  local ok, result = pcall(function()
    local context = activity
    local inputStream = context.getContentResolver().openInputStream(uri)
    if not inputStream then error("openInputStream nil") end

    local dir = context.getCacheDir().getAbsolutePath() .. "/icon_tmp"
    local d = File(dir)
    if not d.exists() then d.mkdirs() end

    local path = dir .. "/icon_" .. tostring(os.time()) .. ".png"
    local fos = luajava.newInstance("java.io.FileOutputStream", path)
    local buf = byte[8192]
    while true do
      local n = inputStream.read(buf)
      if n == -1 then break end
      fos.write(buf, 0, n)
    end
    fos.flush()
    fos.close()
    inputStream.close()
    return path
  end)
  if not ok then return nil, tostring(result) end
  return result
end

function cleanupTempIcon()
  if pendingIconPath then
    pcall(function()
      File(pendingIconPath).delete()
    end)
    pendingIconPath = nil
  end
end

local function loadProjectIcon()
  if not appIcon then return end
  local iconPath, iconMtime = ProjectUtil.getIconWithMtime(projectDir)
  if iconPath then
    pcall(function() MyGlide.setFile(iconPath, appIcon, nil, iconMtime) end)
  end
end

local function openIconPicker()
  local ok, err = pcall(function()
    local intent = Intent(Intent.ACTION_GET_CONTENT)
    intent.setType("image/*")
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    activity.startActivityForResult(intent, ICON_REQUEST_CODE)
  end)
  if not ok then
    MyToast.short("无法打开图片选择器: " .. tostring(err))
  end
end

function onActivityResult(requestCode, resultCode, data)
  if requestCode ~= ICON_REQUEST_CODE then return end
  if resultCode ~= -1 or not data then return end
  local uri = nil
  pcall(function() uri = data.getData() end)
  if not uri then return end

  local tmpPath, err = copyUriToTempImage(uri)
  if not tmpPath then
    MyToast.short("读取图片失败: " .. tostring(err))
    return
  end

  cleanupTempIcon()
  pendingIconPath = tmpPath

  pcall(function() MyGlide.set(tmpPath, appIcon) end)
end

if appIcon then
  appIcon.onClick = openIconPicker
end

loadProjectIcon()

local allPermissions = {}
local permissionList = {}
local selectedMap = {}
local permissionLoaded = false

for _, v in ipairs(cfg.user_permission or {}) do
  selectedMap[v] = true
end

local permClick = luajava.createProxy("android.view.View$OnClickListener", {
  onClick = function(v)
    if cancelled then return end
    local newState = not v.isChecked()
    v.setChecked(newState)
    selectedMap[tostring(v.getTag())] = newState
    local s = safeStrokeColor(newState)
    if s then v.setStrokeColor(s) end
  end
})
local permChecked = luajava.createProxy("com.google.android.material.card.MaterialCardView$OnCheckedChangeListener", {
  onCheckedChanged = function(v, isChecked)
    if cancelled then return end
    selectedMap[tostring(v.getTag())] = isChecked
    local s = safeStrokeColor(isChecked)
    if s then v.setStrokeColor(s) end
  end
})

local permissionAdapter = LuaRecyclerAdapter(permissionList, "layout.item.permission", {
  onBindViewHolder = function(holder, position, views, data)
    if cancelled or not data or not views then return end
    local perm = data.Text
    if views.text and perm then views.text.setText(perm) end
    if views.name then
      if data.name and data.name ~= "" then
        views.name.setVisibility(0)
        views.name.setText(data.name)
      else
        views.name.setVisibility(8)
      end
    end
    if views.card then
      local checked = selectedMap[perm] or false
      views.card.setOnCheckedChangeListener(nil)
      views.card.setChecked(checked)
      local stroke = safeStrokeColor(checked)
      if stroke then views.card.setStrokeColor(stroke) end
      views.card.setTag(perm)
      views.card.setOnClickListener(permClick)
      views.card.setOnCheckedChangeListener(permChecked)
    end
  end
})

permissionRecycler.setAdapter(permissionAdapter)
permissionRecycler.setLayoutManager(LinearLayoutManager(activity))

newInstance("me.zhanghai.android.fastscroll.FastScrollerBuilder", permissionRecycler).useMd2Style()
.setPadding(0, MyUI.dp2px(8), MyUI.dp2px(2), MyUI.dp2px(8))
.build()

local function refreshPermissionList()
  if isAlive() then
    pcall(function() permissionAdapter.notifyDataSetChanged() end)
  end
end

local function applyPermissionFilter()
  if not permissionLoaded or not isAlive() then return end
  local searchText = ""
  if searchPermission and searchPermission.getText then
    searchText = tostring(searchPermission.getText()):lower()
  end

  for i = #permissionList, 1, -1 do permissionList[i] = nil end
  if searchText == "" then
    for i = 1, #allPermissions do permissionList[i] = allPermissions[i] end
  else
    for _, perm in ipairs(allPermissions) do
      if perm.lowText:find(searchText, 1, true) or perm.lowName:find(searchText, 1, true) then
        permissionList[#permissionList + 1] = perm
      end
    end
  end
  refreshPermissionList()
end

local permissionSearchRunnable = luajava.createProxy("java.lang.Runnable", {
  run = function()
    pcall(applyPermissionFilter)
  end
})

local function loadPermissions()
  postDelayed(function()
    local function worker()
      local bindClass = luajava.bindClass
      local Class = bindClass "java.lang.Class"
      local Modifier = bindClass "java.lang.reflect.Modifier"
      local pm = activity.getPackageManager()

      local permissionClass = Class.forName("android.Manifest$permission")
      local fields = luajava.astable(permissionClass.getFields())

      local names = {}
      for _, field in ipairs(fields) do
        local modifiers = field.getModifiers()
        local typeName = field.getType().getName()
        if Modifier.isPublic(modifiers) and Modifier.isStatic(modifiers) and Modifier.isFinal(modifiers) and typeName == "java.lang.String" then
          local name = field.getName()
          if name then names[#names + 1] = name end
        end
      end

      local labels = {}
      local total = #names
      for i, v in ipairs(names) do
        local label = ""
        pcall(function()
          local p = v
          if not string.find(v, "^android%.permission%.") then
            p = "android.permission." .. v
          end
          local info = pm.getPermissionInfo(p, 0)
          local lb = tostring(info.loadLabel(pm) or "")
          if not lb:find("android.permission.", 1, true) then label = lb end
        end)
        labels[i] = label
        if i % 50 == 0 then update("加载权限: " .. i .. "/" .. total) end
      end

      return table.concat(names, "\1"), table.concat(labels, "\1")
    end

    local function onProgress(msg)
    end

    local function onResult(namesStr, labelsStr)
      if not isAlive() then return end
      local names, labels = {}, {}
      if namesStr and namesStr ~= "" then
        for part in namesStr:gmatch("[^\1]+") do names[#names + 1] = part end
      end
      if labelsStr and labelsStr ~= "" then
        for part in labelsStr:gmatch("[^\1]+") do labels[#labels + 1] = part end
      end

      local result = {}
      for i = 1, #names do
        local label = labels[i] or ""
        result[#result + 1] = { Text = names[i], name = label }
      end

      table.sort(result, function(a, b)
        local an, bn = a.name ~= "", b.name ~= ""
        if an ~= bn then return an end
        return a.Text < b.Text
      end)

      for _, item in ipairs(result) do
        item.lowText = item.Text:lower()
        item.lowName = item.name:lower()
      end

      allPermissions = result
      permissionLoaded = true
      applyPermissionFilter()
    end

    permissionTask = activity.newTask(worker, onProgress, onResult)
    permissionTask.execute({})
  end, 100)
end

if searchPermission then
  searchPermission.addTextChangedListener({
    afterTextChanged = function(editable)
      if not permissionLoaded or not isAlive() then return end
      searchHandler.removeCallbacksAndMessages(nil)
      searchHandler.postDelayed(permissionSearchRunnable, 150)
    end
  })
end

if btnSelectAll then
  btnSelectAll.onClick = function()
    if cancelled then return end
    for _, v in ipairs(permissionList) do
      selectedMap[v.Text] = true
    end
    refreshPermissionList()
  end
end

if btnClearAll then
  btnClearAll.onClick = function()
    if cancelled then return end
    for _, v in ipairs(permissionList) do
      selectedMap[v.Text] = false
    end
    refreshPermissionList()
  end
end

local function collectSelectedPermissions()
  local result = {}
  for _, v in ipairs(allPermissions) do
    if selectedMap[v.Text] then
      result[#result + 1] = v.Text
    end
  end
  return result
end


local allLuaFiles = {}
local skipList = {}
local skipSelectedMap = {}
local skipLoaded = false

for _, v in ipairs(cfg.skip_compilation or {}) do
  skipSelectedMap[v] = true
end

local skipClick = luajava.createProxy("android.view.View$OnClickListener", {
  onClick = function(v)
    if cancelled then return end
    local newState = not v.isChecked()
    v.setChecked(newState)
    skipSelectedMap[tostring(v.getTag())] = newState
    local s = safeStrokeColor(newState)
    if s then v.setStrokeColor(s) end
  end
})
local skipChecked = luajava.createProxy("com.google.android.material.card.MaterialCardView$OnCheckedChangeListener", {
  onCheckedChanged = function(v, isChecked)
    if cancelled then return end
    skipSelectedMap[tostring(v.getTag())] = isChecked
    local s = safeStrokeColor(isChecked)
    if s then v.setStrokeColor(s) end
  end
})

local skipAdapter = LuaRecyclerAdapter(skipList, "layout.item.permission", {
  onBindViewHolder = function(holder, position, views, data)
    if cancelled or not data or not views then return end
    local path = data.Text
    if views.text and path then views.text.setText(data.display or path) end
    if views.name then views.name.setVisibility(8) end
    if views.card then
      local checked = skipSelectedMap[path] or false
      views.card.setOnCheckedChangeListener(nil)
      views.card.setChecked(checked)
      local stroke = safeStrokeColor(checked)
      if stroke then views.card.setStrokeColor(stroke) end
      views.card.setTag(path)
      views.card.setOnClickListener(skipClick)
      views.card.setOnCheckedChangeListener(skipChecked)
    end
  end
})

skipRecycler.setAdapter(skipAdapter)
skipRecycler.setLayoutManager(LinearLayoutManager(activity))

newInstance("me.zhanghai.android.fastscroll.FastScrollerBuilder", skipRecycler).useMd2Style()
.setPadding(0, MyUI.dp2px(8), MyUI.dp2px(2), MyUI.dp2px(8))
.build()

local function refreshSkipList()
  if isAlive() then
    pcall(function() skipAdapter.notifyDataSetChanged() end)
  end
end

local function applySkipFilter()
  if not skipLoaded or not isAlive() then return end
  local searchText = ""
  if searchSkip and searchSkip.getText then
    searchText = tostring(searchSkip.getText()):lower()
  end

  for i = #skipList, 1, -1 do skipList[i] = nil end
  if searchText == "" then
    for i = 1, #allLuaFiles do skipList[i] = allLuaFiles[i] end
  else
    for _, item in ipairs(allLuaFiles) do
      if item.lowText:find(searchText, 1, true) then
        skipList[#skipList + 1] = item
      end
    end
  end
  refreshSkipList()
end

local skipSearchRunnable = luajava.createProxy("java.lang.Runnable", {
  run = function()
    pcall(applySkipFilter)
  end
})

local function collectLuaFiles(dir, baseDir, out)
  if cancelled then return end
  local f = File(dir)
  if not f.exists() or not f.isDirectory() then return end
  local children = f.listFiles()
  if not children then return end
  for _, child in ipairs(luajava.astable(children)) do
    if cancelled then return end
    if child.isDirectory() then
      collectLuaFiles(child.getAbsolutePath(), baseDir, out)
    else
      local name = child.getName()
      if name:match("%.lua$") then
        local abs = child.getAbsolutePath()
        local rel = abs:sub(#baseDir + 2)
        out[#out + 1] = {
          Text = rel,
          display = rel,
          name = abs,
        }
      end
    end
  end
end

local function loadSkipWorker()
  local out = {}
  collectLuaFiles(projectDir, projectDir, out)
  table.sort(out, function(a, b) return a.Text < b.Text end)
  for _, item in ipairs(out) do
    item.lowText = item.Text:lower()
  end
  return out
end

local function applySkip(result)
  if not isAlive() then return end
  allLuaFiles = result or {}
  skipLoaded = true
  applySkipFilter()
end

local function loadSkipFiles()
  postDelayed(function()
    local ok, result = pcall(loadSkipWorker)
    if ok then
      applySkip(result)
    else
      print("loadSkipFiles error:", tostring(result))
    end
  end, 200)
end

if searchSkip then
  searchSkip.addTextChangedListener({
    afterTextChanged = function(editable)
      if not skipLoaded or not isAlive() then return end
      searchHandler.removeCallbacksAndMessages(nil)
      searchHandler.postDelayed(skipSearchRunnable, 150)
    end
  })
end

if btnSkipSelectAll then
  btnSkipSelectAll.onClick = function()
    if cancelled then return end
    for _, v in ipairs(skipList) do
      skipSelectedMap[v.Text] = true
    end
    refreshSkipList()
  end
end

if btnSkipClearAll then
  btnSkipClearAll.onClick = function()
    if cancelled then return end
    for _, v in ipairs(skipList) do
      skipSelectedMap[v.Text] = false
    end
    refreshSkipList()
  end
end

local function collectSelectedSkips()
  local result = {}
  for _, v in ipairs(allLuaFiles) do
    if skipSelectedMap[v.Text] then
      result[#result + 1] = v.Text
    end
  end
  return result
end


local function setText(et, v)
  if et then
    et.setText(tostring(v or ""))
  end
end

local function getText(et, dft)
  if not et then return dft or "" end
  local t = tostring(et.getText()):gsub("^%s+", ""):gsub("%s+$", "")
  if t == "" then return dft or "" end
  return t
end

setText(inputName, cfg.appname ~= "" and cfg.appname or "")
setText(inputPackage, cfg.packagename)
setText(inputVersion, cfg.appver)
setText(inputVersionCode, cfg.appcode)
setText(inputMinSdk, cfg.minsdk)
setText(inputTargetSdk, cfg.appsdk)
if switchDebug then switchDebug.setChecked(cfg.debugmode) end

if getText(inputPackage, "") == "" then
  setText(inputPackage, ProjectUtil.makePackageName(getText(inputName, "App")))
end


local function doSave()
  local name = getText(inputName, "")
  local pkg = getText(inputPackage, "")
  if name == "" then
    MyToast.short("项目名称不能为空")
    return
  end
  if pkg == "" then
    MyToast.short("包名不能为空")
    return
  end

  local dir = projectDir
  local oldAppName = cfg.appname ~= "" and cfg.appname or nil
  local dirName = projectDir:match("[^/]+$") or ""
  local finalName = name

  if oldAppName and name ~= oldAppName then
    local ok, err = ProjectUtil.renameProject(dirName, name)
    if not ok then
      MyToast.short("重命名失败: " .. tostring(err))
      return
    end
    dir = ProjectUtil.getProjectDir(finalName)
  end

  local newCfg = {
    appname = finalName,
    packagename = pkg,
    appver = getText(inputVersion, "1.0"),
    appcode = getText(inputVersionCode, "1"),
    minsdk = getText(inputMinSdk, "21"),
    appsdk = getText(inputTargetSdk, "34"),
    debugmode = switchDebug and switchDebug.isChecked() or false,
    user_permission = collectSelectedPermissions(),
    skip_compilation = collectSelectedSkips(),
  }

  local ok, err = ProjectUtil.writeInitFull(dir, newCfg)
  if not ok then
    MyToast.short("保存失败: " .. tostring(err))
    return
  end

  if pendingIconPath then
    local iok, ierr = ProjectUtil.saveProjectIcon(dir, pendingIconPath)
    if not iok then
      MyToast.short("图标保存失败: " .. tostring(ierr))
    end
    cleanupTempIcon()
  end

  cancelled = true
  activity.finish()
end

if btnSave then btnSave.onClick = doSave end

loadPermissions()
loadSkipFiles()

local onThemeResume = makeThemeResumeChecker()
function onResume()
  onThemeResume()
end
