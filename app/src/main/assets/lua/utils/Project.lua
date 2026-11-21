local _M = {}
local bindClass = luajava.bindClass
local LuaUtil = bindClass "com.androlua.LuaUtil"
local Environment = bindClass "android.os.Environment"
local File = bindClass "java.io.File"
local Array = bindClass "java.lang.reflect.Array"

_M.sdcard_path = Environment.getExternalStorageDirectory().toString()

_M.app_root = _M.sdcard_path .. "/BriarLua"
_M.project_path = _M.app_root .. "/project"
_M.backup_path = _M.app_root .. "/backup"
_M.history_path = _M.app_root .. "/history"
_M.bin_path = _M.app_root .. "/bin"
_M.template_icon = activity.getLuaDir("icon.png")
_M.template_alp = activity.getLuaDir("res/template/default.alp")


function _M.dirExists(path)
  if not path or path == "" then return false end
  local ok, f = pcall(function() return File(path) end)
  if not ok or not f then return false end
  return f.exists() and f.isDirectory()
end

function _M.fileExists(path)
  if not path or path == "" then return false end
  local ok, f = pcall(function() return File(path) end)
  if not ok or not f then return false end
  return f.exists() and f.isFile()
end

function _M.ensureDir(path)
  local ok, f = pcall(function() return File(path) end)
  if not ok or not f then
    return false, "invalid path: " .. tostring(path)
  end
  if f.exists() then
    if f.isDirectory() then return true end
    return false, "path exists but is not a directory: " .. path
  end
  local made = f.mkdirs()
  if not made and not f.isDirectory() then
    return false, "mkdirs failed: " .. path
  end
  return true
end

function _M.initDirs()
  local dirs = {
    _M.app_root,
    _M.project_path,
    _M.backup_path,
    _M.history_path,
    _M.bin_path,
  }
  for _, p in ipairs(dirs) do
    local ok, err = _M.ensureDir(p)
    if not ok then return false, err end
  end
  return true
end

function _M.writeFile(path, content)
  local fos, writer
  local ok, err = pcall(function()
    local parent = File(path).getParentFile()
    if parent and not parent.exists() then parent.mkdirs() end
    fos = luajava.newInstance("java.io.FileOutputStream", path)
    writer = luajava.newInstance("java.io.OutputStreamWriter", fos, "UTF-8")
    writer.write(tostring(content))
    writer.flush()
  end)
  if writer then pcall(function() writer.close() end) end
  if fos then pcall(function() fos.close() end) end
  if not ok then return false, tostring(err) end
  return true
end

function _M.readFile(path)
  local fis
  local ok, result = pcall(function()
    fis = luajava.newInstance("java.io.FileInputStream", path)
    local baos = luajava.newInstance("java.io.ByteArrayOutputStream")
    local buf = byte[4096]
    while true do
      local n = fis.read(buf)
      if n == -1 then break end
      baos.write(buf, 0, n)
    end
    return baos.toString("UTF-8")
  end)
  if fis then pcall(function() fis.close() end) end
  if not ok then return nil, tostring(result) end
  return result
end

function _M.writeFile2(path, content)
  local ok, err = pcall(function()
    local parent = File(path).getParentFile()
    if parent and not parent.exists() then parent.mkdirs() end
    local fos = luajava.newInstance("java.io.FileOutputStream", path)
    local writer = luajava.newInstance("java.io.OutputStreamWriter", fos, "UTF-8")
    writer.write(tostring(content))
    writer.flush()
    writer.close()
    fos.close()
  end)
  if not ok then return false, tostring(err) end
  return true
end

function _M.readFile2(path)
  local ok, result = pcall(function()
    local fis = luajava.newInstance("java.io.FileInputStream", path)
    local baos = luajava.newInstance("java.io.ByteArrayOutputStream")
    local buf = byte[4096]
    while true do
      local n = fis.read(buf)
      if n == -1 then break end
      baos.write(buf, 0, n)
    end
    fis.close()
    return baos.toString("UTF-8")
  end)
  if not ok then return nil, tostring(result) end
  return result
end

function _M.copyFile(from, to)
  local ok, err = pcall(function()
    local dst = File(to)
    local parent = dst.getParentFile()
    if parent and not parent.exists() then parent.mkdirs() end
    local fis = luajava.newInstance("java.io.FileInputStream", from)
    local fos = luajava.newInstance("java.io.FileOutputStream", to)
    local buf = byte[8192]
    while true do
      local n = fis.read(buf)
      if n == -1 then break end
      fos.write(buf, 0, n)
    end
    fos.flush()
    fos.close()
    fis.close()
  end)
  if not ok then return false, tostring(err) end
  return true
end

function _M.deleteRecursive(path)
  local ok, f = pcall(function() return File(path) end)
  if not ok or not f then return false, "invalid path" end
  if not f.exists() then return true end
  if f.isDirectory() then
    local children = f.listFiles()
    if children then
      local len = Array.getLength(children)
      for i = 0, len - 1 do
        local ok2, err2 = _M.deleteRecursive(children[i].getAbsolutePath())
        if not ok2 then return false, err2 end
      end
    end
  end
  if not f.delete() then
    return false, "delete failed: " .. path
  end
  return true
end


function _M.makePackageNameByCount(prefix)
  local count = 0
  local ok, list = pcall(function() return _M.listProjects({ simple = true }) end)
  if ok and type(list) == "table" then count = #list end
  local n = count + 1
  local base = (prefix or "myapplication") .. tostring(n)
  return "com.example." .. base
end

function _M.makePackageName(name)
  local s = tostring(name or ""):lower():gsub("[^%w]", "")
  if s == "" then
    local count = 0
    local ok, list = pcall(function() return _M.listProjects({ simple = true }) end)
    if ok and type(list) == "table" then count = #list end
    s = "app" .. tostring(count + 1)
  end
  if s:match("^%d") then
    s = "app" .. s
  end
  return "com.example." .. s
end


local function toStr(v, dft)
  if v == nil then return dft or "" end
  local t = type(v)
  if t == "table" or t == "function" or t == "userdata" then
    return dft or ""
  end
  return tostring(v)
end

function _M.updateInit(projectDir, cfg)
  local initPath = projectDir .. "/init.lua"
  local content = _M.readFile(initPath)
  if not content then
    return false, "init.lua not found"
  end
  cfg = cfg or {}
  if cfg.appname then
    content = content:gsub('appname%s*=%s*"[^"]*"', "appname=" .. string.format("%q", tostring(cfg.appname)))
  end
  if cfg.packagename then
    content = content:gsub('packagename%s*=%s*"[^"]*"', "packagename=" .. string.format("%q", tostring(cfg.packagename)))
  end
  if cfg.appver then
    content = content:gsub('appver%s*=%s*"[^"]*"', 'appver="' .. tostring(cfg.appver) .. '"')
  end
  if cfg.appcode then
    content = content:gsub('appcode%s*=%s*"[^"]*"', 'appcode="' .. tostring(cfg.appcode) .. '"')
  end
  if cfg.minsdk then
    content = content:gsub('minsdk%s*=%s*"[^"]*"', 'minsdk="' .. tostring(cfg.minsdk) .. '"')
  end
  if cfg.appsdk then
    content = content:gsub('appsdk%s*=%s*"[^"]*"', 'appsdk="' .. tostring(cfg.appsdk) .. '"')
  end
  return _M.writeFile(initPath, content)
end

function _M.readInit(projectDir)
  local initPath = projectDir .. "/init.lua"
  if not _M.fileExists(initPath) then
    return nil, "init.lua not found"
  end

  local env = {}
  setmetatable(env, { __index = _G })

  local chunk, err = loadfile(initPath, "t", env)
  if not chunk then return nil, tostring(err) end

  local ok, runErr = pcall(chunk)
  if not ok then return nil, tostring(runErr) end

  local cfg = {
    appname = toStr(env.appname or env.app_name, ""),
    packagename = toStr(env.packagename or env.package or env.package_name, ""),
    appver = toStr(env.appver or env.versionName or env.version_name, "1.0"),
    appcode = toStr(env.appcode or env.versionCode or env.version_code, "1"),
    minsdk = toStr(env.minsdk or env.minSdkVersion or env.min_sdk, "23"),
    appsdk = toStr(env.appsdk or env.targetSdkVersion or env.target_sdk or env.sdk, "35"),
    debugmode = env.debugmode == nil and false or not not env.debugmode,
    user_permission = type(env.user_permission) == "table" and env.user_permission or {},
    skip_compilation = type(env.skip_compilation) == "table" and env.skip_compilation or {},
  }
  return cfg
end

function _M.buildInit(cfg)
  cfg = cfg or {}
  local function q(s) return string.format("%q", tostring(s or "")) end
  local function dumpList(t)
    t = t or {}
    if #t == 0 then return "" end
    local parts = {}
    for _, v in ipairs(t) do
      parts[#parts + 1] = "  " .. q(v) .. ","
    end
    return "\n" .. table.concat(parts, "\n") .. "\n"
  end

  local lines = {
    "appname=" .. q(cfg.appname or "AppName"),
    "packagename=" .. q(cfg.packagename or "PackageName"),
    "appver=" .. q(cfg.appver or "1.0"),
    "appcode=" .. q(cfg.appcode or "1"),
    "minsdk=" .. q(cfg.minsdk or "23"),
    "appsdk=" .. q(cfg.appsdk or "35"),
    "debugmode=" .. tostring(cfg.debugmode and true or false),
    "user_permission={" .. dumpList(cfg.user_permission) .. "}",
    "skip_compilation={" .. dumpList(cfg.skip_compilation) .. "}",
    "",
  }
  return table.concat(lines, "\n")
end

function _M.writeInitFull(projectDir, cfg)
  return _M.writeFile(projectDir .. "/init.lua", _M.buildInit(cfg))
end


function _M.getProjectDir(name)
  if not name or name == "" then return nil end
  return _M.project_path .. "/" .. tostring(name)
end

function _M.getIconPath(projectDir)
  local p = projectDir .. "/icon.png"
  if _M.fileExists(p) then return p end
  return nil
end

function _M.getIconWithMtime(projectDir)
  local p = projectDir .. "/icon.png"
  if not _M.fileExists(p) then return nil end
  local ok, mtime = pcall(function() return tostring(File(p).lastModified()) end)
  return p, ok and mtime or "0"
end

function _M.getProjectInfo(name)
  local dir = _M.getProjectDir(name)
  if not dir or not _M.dirExists(dir) then return nil end

  local info = {
    name = tostring(name),
    dir = dir,
    lastModified = 0,
  }
  info.icon, info.iconMtime = _M.getIconWithMtime(dir)

  local ok, f = pcall(function() return File(dir) end)
  if ok and f then
    pcall(function() info.lastModified = tonumber(f.lastModified()) or 0 end)
  end

  local cfg = _M.readInit(dir)
  if cfg then
    info.appname = cfg.appname ~= "" and cfg.appname or info.name
    info.packagename = cfg.packagename
    info.appver = cfg.appver
    info.appcode = cfg.appcode
    info.minsdk = cfg.minsdk
    info.appsdk = cfg.appsdk
    info.debugmode = cfg.debugmode
    info.user_permission = cfg.user_permission
    info.skip_compilation = cfg.skip_compilation
    info.hasInit = true
   else
    info.appname = info.name
    info.packagename = ""
    info.appver = ""
    info.appcode = ""
    info.hasInit = false
  end

  info.hasMain = _M.fileExists(dir .. "/main.lua")
  return info
end

function _M.listProjects(opts)
  opts = opts or {}
  local result = {}
  local dir = File(_M.project_path)
  if not dir.exists() or not dir.isDirectory() then return result end

  local files = dir.listFiles()
  if not files then return result end

  local len = Array.getLength(files)
  for i = 0, len - 1 do
    local f = files[i]
    local okDir = false
    pcall(function() okDir = f.isDirectory() end)
    if okDir then
      local name = f.getName()
      if not tostring(name):match("^%.") then
        if opts.simple then
          result[#result + 1] = name
         else
          local info = _M.getProjectInfo(name)
          if info then result[#result + 1] = info end
        end
      end
    end
  end

  if not opts.simple then
    table.sort(result, function(a, b)
      local ta = tonumber(a.lastModified) or 0
      local tb = tonumber(b.lastModified) or 0
      if ta == tb then
        return tostring(a.name):lower() < tostring(b.name):lower()
      end
      return ta > tb
    end)
   else
    table.sort(result, function(a, b)
      return tostring(a):lower() < tostring(b):lower()
    end)
  end

  return result
end


function _M.isValidName(name)
  if type(name) ~= "string" or name == "" then return false end
  if name:match("[/\\:*?\"<>|]") then return false end
  if name == "." or name == ".." then return false end
  return true
end

function _M.newProject(name, cfg)
  if not _M.isValidName(name) then
    return false, "项目名称无效"
  end
  local projectDir = _M.getProjectDir(name)
  if _M.dirExists(projectDir) then
    return false, "项目已存在: " .. name
  end

  if not _M.fileExists(_M.template_alp) then
    return false, "模板不存在: " .. _M.template_alp
  end

  local ok, err = pcall(function()
    LuaUtil.unZip(_M.template_alp, projectDir)
  end)
  if not ok then
    _M.deleteRecursive(projectDir)
    return false, "解压模板失败: " .. tostring(err)
  end

  cfg = cfg or {}
  if not cfg.appname or cfg.appname == "" then cfg.appname = name end
  if not cfg.packagename or cfg.packagename == "" then
    cfg.packagename = _M.makePackageName(name)
  end

  local initPath = projectDir .. "/init.lua"
  local content, rerr = _M.readFile(initPath)
  if not content then
    _M.deleteRecursive(projectDir)
    return false, "读取模板 init.lua 失败: " .. tostring(rerr)
  end

  local q = string.format("%q", tostring(cfg.appname))
  local qpkg = string.format("%q", tostring(cfg.packagename))

  content = content:gsub('appname%s*=%s*"[^"]*"', "appname=" .. q)
  content = content:gsub('packagename%s*=%s*"[^"]*"', "packagename=" .. qpkg)

  if cfg.appver then
    content = content:gsub('appver%s*=%s*"[^"]*"', 'appver="' .. tostring(cfg.appver) .. '"')
  end
  if cfg.appcode then
    content = content:gsub('appcode%s*=%s*"[^"]*"', 'appcode="' .. tostring(cfg.appcode) .. '"')
  end
  if cfg.minsdk then
    content = content:gsub('minsdk%s*=%s*"[^"]*"', 'minsdk="' .. tostring(cfg.minsdk) .. '"')
  end
  if cfg.appsdk then
    content = content:gsub('appsdk%s*=%s*"[^"]*"', 'appsdk="' .. tostring(cfg.appsdk) .. '"')
  end

  local wok, werr = _M.writeFile(initPath, content)
  if not wok then
    _M.deleteRecursive(projectDir)
    return false, werr
  end

  local mainPath = projectDir .. "/main.lua"
  if _M.fileExists(mainPath) then
    local mainContent = _M.readFile(mainPath)
    if mainContent then
      mainContent = mainContent:gsub('setTitle%s*%(%s*"[^"]*"%s*%)', 'setTitle(' .. string.format("%q", tostring(cfg.appname)) .. ')')
      _M.writeFile(mainPath, mainContent)
    end
  end

  local iconDst = projectDir .. "/icon.png"
  if not _M.fileExists(iconDst) and _M.fileExists(_M.template_icon) then
    _M.copyFile(_M.template_icon, iconDst)
  end

  return true, projectDir
end

function _M.peekInitFromArchive(alpPath)
  if not alpPath or not _M.fileExists(alpPath) then
    return nil, "文件不存在"
  end

  local context = activity
  local tmpDir = context.getCacheDir().getAbsolutePath() .. "/import_peek_" .. tostring(os.time())
  local ok, err = pcall(function()
    LuaUtil.unZip(alpPath, tmpDir)
  end)
  if not ok then
    _M.deleteRecursive(tmpDir)
    return nil, "解压失败: " .. tostring(err)
  end

  local initPath = tmpDir .. "/init.lua"
  if not _M.fileExists(initPath) then
    _M.deleteRecursive(tmpDir)
    return nil, "压缩包内缺少 init.lua"
  end

  local cfg = _M.readInit(tmpDir)
  _M.deleteRecursive(tmpDir)

  if not cfg then
    return nil, "init.lua 解析失败"
  end
  return cfg
end

function _M.uniqueProjectName(baseName)
  if not baseName or baseName == "" then
    baseName = "ImportedProject"
  end
  if not _M.isValidName(baseName) then
    baseName = "ImportedProject"
  end
  if not _M.dirExists(_M.getProjectDir(baseName)) then
    return baseName, false
  end
  local n = 1
  while true do
    local candidate = baseName .. "_copy" .. (n > 1 and tostring(n) or "")
    if not _M.dirExists(_M.getProjectDir(candidate)) then
      return candidate, true
    end
    n = n + 1
  end
end

function _M.importProject(alpPath, destName)
  if not alpPath or not _M.fileExists(alpPath) then
    return false, "文件不存在: " .. tostring(alpPath)
  end
  if not destName or destName == "" then
    destName = alpPath:match("([^/]+)%.alp$") or alpPath:match("([^/]+)%.zip$")
  end
  if not _M.isValidName(destName) then
    return false, "项目名称无效"
  end

  local projectDir = _M.getProjectDir(destName)
  if _M.dirExists(projectDir) then
    return false, "项目已存在: " .. destName
  end

  local ok, err = pcall(function()
    LuaUtil.unZip(alpPath, projectDir)
  end)
  if not ok then
    _M.deleteRecursive(projectDir)
    return false, "解压失败: " .. tostring(err)
  end

  local initPath = projectDir .. "/init.lua"
  if not _M.fileExists(initPath) then
    _M.deleteRecursive(projectDir)
    return false, "不是有效的项目文件（缺少 init.lua）"
  end

  local mainPath = projectDir .. "/main.lua"
  if not _M.fileExists(mainPath) then
    _M.deleteRecursive(projectDir)
    return false, "不是有效的项目文件（缺少 main.lua）"
  end

  return true, projectDir
end

function _M.backupProject(name)
  if not name or name == "" then
    return false, "项目名称为空"
  end
  local projectDir = _M.getProjectDir(name)
  if not _M.dirExists(projectDir) then
    return false, "项目不存在: " .. name
  end

  local okDirs, errDirs = _M.ensureDir(_M.backup_path)
  if not okDirs then return false, errDirs end

  local timestamp = os.date("%Y%m%d_%H%M%S")
  local zipName = name .. "_" .. timestamp .. ".alp"
  local backupFile = _M.backup_path .. "/" .. zipName

  local ok, err = pcall(function()
    LuaUtil.zip(projectDir, _M.backup_path, zipName)
  end)
  if not ok then
    return false, "备份失败: " .. tostring(err)
  end

  if not _M.fileExists(backupFile) then
    return false, "备份文件未生成: " .. backupFile
  end

  return true, backupFile
end

function _M.renameProject(oldName, newName)
  if not _M.isValidName(oldName) or not _M.isValidName(newName) then
    return false, "名称无效"
  end
  if oldName == newName then return true end

  local oldDir = _M.getProjectDir(oldName)
  local newDir = _M.getProjectDir(newName)
  if not _M.dirExists(oldDir) then
    return false, "项目不存在: " .. oldName
  end
  if File(newDir).exists() then
    return false, "目标名称已存在: " .. newName
  end

  local cfgBefore = _M.readInit(oldDir)

  local ok = false
  pcall(function()
    ok = File(oldDir).renameTo(File(newDir))
  end)
  if not ok then return false, "重命名失败" end

  if cfgBefore and (cfgBefore.appname == oldName or cfgBefore.appname == "") then
    local initPath = newDir .. "/init.lua"
    local content = _M.readFile(initPath)
    if content then
      content = content:gsub('appname%s*=%s*"[^"]*"', "appname=" .. string.format("%q", newName))
      _M.writeFile(initPath, content)
    end
  end

  return true
end

function _M.deleteProject(name)
  if not name or name == "" then
    return false, "项目名称为空"
  end
  local projectDir = _M.getProjectDir(name)
  if not _M.dirExists(projectDir) then
    return false, "项目不存在: " .. name
  end
  return _M.deleteRecursive(projectDir)
end

function _M.cloneProject(srcName, destName, cfg)
  if not _M.isValidName(srcName) or not _M.isValidName(destName) then
    return false, "名称无效"
  end
  local srcDir = _M.getProjectDir(srcName)
  local destDir = _M.getProjectDir(destName)
  if not _M.dirExists(srcDir) then return false, "源项目不存在" end
  if File(destDir).exists() then return false, "目标已存在" end

  local function copyDir(from, to)
    local src = File(from)
    local dst = File(to)
    if src.isDirectory() then
      if not dst.exists() then dst.mkdirs() end
      local children = src.listFiles()
      if children then
        local len = Array.getLength(children)
        for i = 0, len - 1 do
          local child = children[i]
          copyDir(child.getAbsolutePath(), to .. "/" .. child.getName())
        end
      end
     else
      local parent = dst.getParentFile()
      if parent and not parent.exists() then parent.mkdirs() end
      local fis = luajava.newInstance("java.io.FileInputStream", from)
      local fos = luajava.newInstance("java.io.FileOutputStream", to)
      local buf = byte[8192]
      while true do
        local n = fis.read(buf)
        if n == -1 then break end
        fos.write(buf, 0, n)
      end
      fos.flush()
      fos.close()
      fis.close()
    end
  end

  local ok, err = pcall(function() copyDir(srcDir, destDir) end)
  if not ok then
    _M.deleteRecursive(destDir)
    return false, tostring(err)
  end

  cfg = cfg or {}
  local initPath = destDir .. "/init.lua"
  local content = _M.readFile(initPath)
  if not content then
    _M.deleteRecursive(destDir)
    return false, "读取 init.lua 失败"
  end

  local appname = cfg.appname
  if not appname or appname == "" then appname = destName end

  local packagename = cfg.packagename
  if not packagename or packagename == "" then
    packagename = _M.makePackageName(destName)
  end

  content = content:gsub('appname%s*=%s*"[^"]*"', "appname=" .. string.format("%q", tostring(appname)))
  content = content:gsub('packagename%s*=%s*"[^"]*"', "packagename=" .. string.format("%q", tostring(packagename)))

  if cfg.appver and cfg.appver ~= "" then
    content = content:gsub('appver%s*=%s*"[^"]*"', 'appver="' .. tostring(cfg.appver) .. '"')
  end
  if cfg.appcode and cfg.appcode ~= "" then
    content = content:gsub('appcode%s*=%s*"[^"]*"', 'appcode="' .. tostring(cfg.appcode) .. '"')
  end
  if cfg.minsdk and cfg.minsdk ~= "" then
    content = content:gsub('minsdk%s*=%s*"[^"]*"', 'minsdk="' .. tostring(cfg.minsdk) .. '"')
  end
  if cfg.appsdk and cfg.appsdk ~= "" then
    content = content:gsub('appsdk%s*=%s*"[^"]*"', 'appsdk="' .. tostring(cfg.appsdk) .. '"')
  end

  local wok, werr = _M.writeFile(initPath, content)
  if not wok then
    _M.deleteRecursive(destDir)
    return false, "写入 init.lua 失败: " .. tostring(werr)
  end

  local mainPath = destDir .. "/main.lua"
  if _M.fileExists(mainPath) then
    local mainContent = _M.readFile(mainPath)
    if mainContent then
      mainContent = mainContent:gsub('setTitle%s*%(%s*"[^"]*"%s*%)', 'setTitle(' .. string.format("%q", tostring(cfg.appname)) .. ')')
      _M.writeFile(mainPath, mainContent)
    end
  end

  return true, destDir
end

function _M.saveProjectIcon(projectDir, tmpImagePath)
  if not projectDir or not tmpImagePath then
    return false, "参数无效"
  end
  if not _M.fileExists(tmpImagePath) then
    return false, "临时图片不存在"
  end
  local dest = projectDir .. "/icon.png"
  local ok, err = _M.copyFile(tmpImagePath, dest)
  if not ok then
    return false, "保存图标失败: " .. tostring(err)
  end
  return true
end


function _M.uriToTempFile(uri, tempDir)
  if not uri then return nil, "uri is nil" end
  local ok, result = pcall(function()
    local context = activity
    local inputStream = context.getContentResolver().openInputStream(uri)
    if not inputStream then error("openInputStream returned nil") end

    local dirPath = tempDir or context.getCacheDir().getAbsolutePath() .. "/import"
    local dir = File(dirPath)
    if not dir.exists() then dir.mkdirs() end

    local ts = os.time()
    local tmpPath = dirPath .. "/import_" .. ts .. ".alp"

    local fos = luajava.newInstance("java.io.FileOutputStream", tmpPath)
    local buf = byte[8192]
    while true do
      local n = inputStream.read(buf)
      if n == -1 then break end
      fos.write(buf, 0, n)
    end
    fos.flush()
    fos.close()
    inputStream.close()

    return tmpPath
  end)
  if not ok then return nil, tostring(result) end
  return result
end

function _M.cleanupTempFile(path)
  if not path then return end
  pcall(function()
    File(path).delete()
  end)
end

function _M.suggestProjectName(uri, alpPath)
  local name = nil
  if uri then
    pcall(function()
      local last = uri.getLastPathSegment()
      if last then
        name = tostring(last):match("([^/]+)%.alp$") or tostring(last):match("([^/]+)%.zip$")
      end
    end)
  end
  if not name and alpPath then
    name = alpPath:match("([^/]+)%.alp$") or alpPath:match("([^/]+)%.zip$")
  end
  if not name or name == "" then
    name = "ImportedProject"
  end
  return name
end

function _M.uniqueProjectName(baseName)
  if not _M.isValidName(baseName) then
    baseName = "ImportedProject"
  end
  if not _M.dirExists(_M.getProjectDir(baseName)) then
    return baseName
  end
  local n = 1
  while true do
    local candidate = baseName .. "_" .. n
    if not _M.dirExists(_M.getProjectDir(candidate)) then
      return candidate
    end
    n = n + 1
  end
end

function _M.collectUsedPackageNames()
  local used = {}
  local ok, list = pcall(function() return _M.listProjects({ simple = true }) end)
  if ok and type(list) == "table" then
    for _, name in ipairs(list) do
      local dir = _M.getProjectDir(name)
      local cfg = _M.readInit(dir)
      if cfg and cfg.packagename and cfg.packagename ~= "" then
        used[cfg.packagename] = true
      end
    end
  end
  return used
end

function _M.makeClonePackageName(originalPkg)
  local used = _M.collectUsedPackageNames()
  local base = tostring(originalPkg or ""):gsub("[^%w_.]", "")
  if base == "" then base = "com.example.app" end

  local candidate = base .. "_copy"
  if not used[candidate] then
    return candidate
  end
  local i = 2
  while true do
    local c = base .. "_copy" .. tostring(i)
    if not used[c] then
      return c
    end
    i = i + 1
  end
end

function _M.makeCloneProjectName(baseName)
  if not baseName or baseName == "" then baseName = "ImportedProject" end
  baseName = tostring(baseName):gsub("[/\\:*?\"<>|]", "_")
  if baseName == "" then baseName = "ImportedProject" end

  if not _M.dirExists(_M.getProjectDir(baseName)) then
    return baseName
  end

  local candidate = baseName .. "_copy"
  if not _M.dirExists(_M.getProjectDir(candidate)) then
    return candidate
  end

  local n = 2
  while true do
    local c = baseName .. "_copy" .. tostring(n)
    if not _M.dirExists(_M.getProjectDir(c)) then
      return c
    end
    n = n + 1
  end
end

local console = {}

local function _consoleReadBin(path)
  local f = io.open(path, "rb")
  if not f then return nil, "无法读取文件: " .. tostring(path) end
  local s = f:read("*a")
  f:close()
  return s
end

local function _consoleWriteBin(path, s)
  local f = io.open(path, "wb")
  if not f then return false, "无法写入编译产物: " .. tostring(path) end
  local ok, err = pcall(f.write, f, s)
  if not ok then
    pcall(function() f:close() end)
    return false, tostring(err)
  end
  f:flush()
  f:close()
  return true
end

local function _consoleStripPrefix(s)
  if s:sub(1, 3) == "\239\187\191" then s = s:sub(4) end
  if s:sub(1, 1) == "#" then
    local nl = s:find("\n")
    s = nl and s:sub(nl + 1) or ""
  end
  return s
end

local _consoleSeq = 0
local _consoleBase

local function _consoleOutPath()
  if not _consoleBase then
    local ok, base = pcall(function()
      local dir = tostring(activity.getCacheDir().getAbsolutePath()) .. "/build_luac"
      local FileClass = luajava.bindClass("java.io.File")
      if not FileClass(dir).isDirectory() then FileClass(dir).mkdirs() end
      return dir
    end)
    if not ok then return nil, "创建编译缓存目录失败: " .. tostring(base) end
    _consoleBase = base
  end
  _consoleSeq = _consoleSeq + 1
  return _consoleBase .. "/out_" .. tostring(os.time()) .. "_" .. _consoleSeq .. ".luac"
end

function console.build(path)
  local s, err = _consoleReadBin(path)
  if not s then return nil, err end
  s = _consoleStripPrefix(s)
  local name = "@" .. tostring(path):match("([^/\\]+)$")
  local f, cerr = load(s, name, "bt")
  if not f then return nil, tostring(cerr) end
  local ok, dump = pcall(string.dump, f)
  if not ok then return nil, "字节码生成失败: " .. tostring(dump) end
  local out, oerr = _consoleOutPath()
  if not out then return nil, oerr end
  local wok, werr = _consoleWriteBin(out, dump)
  if not wok then return nil, werr end
  return out
end

function console.build_aly(path)
  local s, err = _consoleReadBin(path)
  if not s then return nil, err end
  local head = s:sub(1, 1)
  if head == "\27" or head == "\28" or head == "=" then
    return path
  end
  s = _consoleStripPrefix(s)
  local name = "@" .. tostring(path):match("([^/\\]+)$")
  local f, cerr = load("return " .. s, name, "bt")
  if not f then return nil, tostring(cerr) end
  local ok, dump = pcall(string.dump, f)
  if not ok then return nil, "字节码生成失败: " .. tostring(dump) end
  local out, oerr = _consoleOutPath()
  if not out then return nil, oerr end
  local wok, werr = _consoleWriteBin(out, dump)
  if not wok then return nil, werr end
  return out
end

function _M.compileLuaFile(srcPath)
  return console.build(srcPath)
end

function _M.compileAlyFile(srcPath)
  return console.build_aly(srcPath)
end


local function safeCall(fn, ...)
  local ok, result = pcall(fn, ...)
  if not ok then
    return false, tostring(result)
  end
  return true, result
end

local function buildPermissions(perms)
  local out = {}
  for _, p in ipairs(perms or {}) do
    local full = tostring(p)
    if not full:find("^android%.permission%.") then
      full = "android.permission." .. full
    end
    out[#out + 1] = full
  end

  local Array = bindClass "java.lang.reflect.Array"
  local StringClass = bindClass "java.lang.String"
  local arr = Array.newInstance(StringClass, #out)
  for i = 1, #out do
    Array.set(arr, i - 1, StringClass(out[i]))
  end
  return arr
end

local function copyStream(input, output)
  local buf = byte[8192]
  while true do
    local n = input.read(buf)
    if n == -1 then break end
    output.write(buf, 0, n)
  end
end

function _M._doBuildApk(projectName, opts)
  opts = opts or {}
  local onUpdate = opts.onUpdate or function() end
  local onFinish = opts.onFinish or function() end

  local projectDir = _M.getProjectDir(projectName)
  if not _M.dirExists(projectDir) then
    onFinish(false, "项目不存在: " .. tostring(projectName))
    return
  end

  local cfg = _M.readInit(projectDir)
  if not cfg then
    onFinish(false, "读取 init.lua 失败")
    return
  end

  local appname = tostring(cfg.appname ~= "" and cfg.appname or projectName)
  local packagename = tostring(cfg.packagename ~= "" and cfg.packagename or _M.makePackageName(projectName))
  local appver = tostring(cfg.appver or "1.0")
  local appcode = tostring(cfg.appcode or "1")
  local minsdkNum = tonumber(tostring(cfg.minsdk)) or 23
  if minsdkNum < 23 then
    onUpdate("minsdk " .. minsdkNum .. " 低于运行时最低要求,已按 23 打包")
    minsdkNum = 23
  end
  local appsdkNum = tonumber(tostring(cfg.appsdk)) or 35
  local userPermission = cfg.user_permission or {}
  local skipCompilation = cfg.skip_compilation or {}

  local apkName = appname:gsub("[/\\:*?\"<>|]", "_") .. "_" .. appver .. ".apk"
  local apkPath = _M.bin_path .. "/" .. apkName


  local LuaUtil = bindClass "com.androlua.LuaUtil"
  local File = bindClass "java.io.File"
  local FileInputStream = bindClass "java.io.FileInputStream"
  local FileOutputStream = bindClass "java.io.FileOutputStream"
  local ZipEntry = bindClass "java.util.zip.ZipEntry"
  local ZipInputStream = bindClass "java.util.zip.ZipInputStream"
  local ZipOutputStream = bindClass "java.util.zip.ZipOutputStream"
  local BufferedInputStream = bindClass "java.io.BufferedInputStream"
  local BufferedOutputStream = bindClass "java.io.BufferedOutputStream"
  local ApkEditor = bindClass "com.zzzmode.apkeditor.ApkEditor"

  local tmpBase = "build_tmp_" .. tostring(os.time()) .. "_" .. tostring(os.clock()):gsub("%.", "_")
  local tmpApk = activity.getLuaPath(tmpBase .. ".apk")
  local tmpApk2 = activity.getLuaPath(tmpBase .. "2.apk")

  pcall(function()
    File(apkPath).getParentFile().mkdirs()
  end)

  pcall(function()
    local System = bindClass "java.lang.System"
    local cutoff = System.currentTimeMillis() - 3600000
    local dir = File(activity.getLuaPath(""))
    for _, f in ipairs(luajava.astable(dir.listFiles() or {})) do
      local n = tostring(f.getName())
      if n:find("^build_tmp_") and f.lastModified() < cutoff then
        pcall(function() f.delete() end)
      end
    end
  end)

  pcall(function()
    local FileClass = luajava.bindClass("java.io.File")
    local cdir = FileClass(tostring(activity.getCacheDir().getAbsolutePath()) .. "/build_luac")
    if cdir.isDirectory() then
      for _, f in ipairs(luajava.astable(cdir.listFiles() or {})) do
        pcall(function() f.delete() end)
      end
    end
  end)

  local errbuffer = {}
  local md5s = {}
  local replace = {}
  local checked = {}
  local lualib = {}

  pcall(function()
    local libs = File(activity.ApplicationInfo.nativeLibraryDir).list()
    if libs then
      for _, v in ipairs(luajava.astable(libs)) do
        replace[v] = true
      end
    end
  end)
  replace["libluajava.so"] = false

  local function isSkipCompilation(relPath)
    for _, v in ipairs(skipCompilation) do
      if tostring(v) == relPath then
        return true
      end
    end
    return false
  end

  local function checklib(path)
    if checked[path] then return end
    checked[path] = true

    local ok, s = safeCall(function()
      local f = io.open(path)
      if not f then return nil end
      local c = f:read("*a")
      f:close()
      return c
    end)
    if not ok or not s then return end

    local function processModule(modulename)
      local parts = {}
      for part in modulename:gmatch("([%w_]+)") do
        parts[#parts + 1] = part
      end
      if #parts == 0 then return end
      local cp = string.format("lib%s.so", parts[1])
      local lp = "lua/" .. table.concat(parts, "/") .. ".lua"
      if replace[cp] then
        replace[cp] = false
      end
      if replace[lp] then
        replace[lp] = false
        lualib[lp] = activity.Application.MdDir .. "/" .. table.concat(parts, "/") .. ".lua"
      end
    end

    for modulename in s:gmatch("require%s*%(?%s*\"([%w%.%_]+)\"") do
      processModule(modulename)
    end
    for modulename in s:gmatch("import%s*%(?%s*\"([%w%.%_]+)\"") do
      processModule(modulename)
    end
  end

  local function addDir(zos, dir, f)
    onUpdate("处理目录: assets/" .. dir)
    local entry = ZipEntry("assets/" .. dir)
    zos.putNextEntry(entry)

    local ls = f.listFiles()
    if not ls then return end

    for i = 0, #ls - 1 do
      local child = ls[i]
      local name = child.getName()

      if dir == "" and name == "solibs" then
        onUpdate("跳过 solibs")
       elseif name == ".using" then
        checklib(projectDir .. "/" .. dir .. name)
       elseif name:find("%.apk$") or name:find("%.luac$") or name:find("^%.") then
       elseif child.isDirectory() then
        addDir(zos, dir .. name .. "/", child)
       elseif name:find("%.lua$") then
        local rel = dir .. name
        checklib(projectDir .. "/" .. rel)
        local srcPath = projectDir .. "/" .. rel
        local outPath = srcPath
        local needCompile = not isSkipCompilation(rel)

        if needCompile then
          onUpdate("编译 Lua: " .. rel)
          local ok, result = pcall(function()
            return console.build(srcPath)
          end)
          if ok and result then
            outPath = result
           else
            local why = ok and "编译器未返回产物" or tostring(result)
            errbuffer[#errbuffer + 1] = "编译失败: " .. rel .. "\n  " .. why
            onUpdate("编译失败: " .. rel .. " (" .. why .. ")")
            outPath = nil
          end
         else
          onUpdate("跳过编译: " .. rel)
        end

        if outPath then
          local dst = "assets/" .. rel
          if replace[dst] then
            errbuffer[#errbuffer + 1] = "重复文件: " .. rel
          end
          local e = ZipEntry(dst)
          zos.putNextEntry(e)
          replace[dst] = true
          copyStream(FileInputStream(File(outPath)), zos)
          table.insert(md5s, LuaUtil.getFileMD5(outPath))
          if needCompile and outPath ~= srcPath then
            pcall(function() File(outPath).delete() end)
          end
        end
       elseif name:find("%.aly$") then
        local rel = dir .. name
        checklib(projectDir .. "/" .. rel)
        local srcPath = projectDir .. "/" .. rel
        local outName = name:gsub("aly$", "lua")
        local needCompile = not isSkipCompilation(rel)
        local outPath = nil

        if needCompile then
          onUpdate("编译 ALY: " .. rel)
          local ok, result = pcall(function()
            return console.build_aly(srcPath)
          end)
          if ok and result then
            outPath = result
           else
            local why = ok and "编译器未返回产物" or tostring(result)
            errbuffer[#errbuffer + 1] = "ALY 编译失败: " .. rel .. "\n  " .. why
            onUpdate("ALY 编译失败: " .. rel .. " (" .. why .. ")")
          end
         else
          outPath = srcPath
          outName = name
        end

        if outPath then
          local dst = "assets/" .. dir .. outName
          if replace[dst] then
            errbuffer[#errbuffer + 1] = "重复文件: " .. dir .. outName
          end
          local e = ZipEntry(dst)
          zos.putNextEntry(e)
          replace[dst] = true
          copyStream(FileInputStream(File(outPath)), zos)
          table.insert(md5s, LuaUtil.getFileMD5(outPath))
          if needCompile and outPath ~= srcPath then
            pcall(function() File(outPath).delete() end)
          end
        end
       else
        onUpdate("添加资源: " .. dir .. name)
        local dst = "assets/" .. dir .. name
        local e = ZipEntry(dst)
        zos.putNextEntry(e)
        replace[dst] = true
        copyStream(FileInputStream(child), zos)
        table.insert(md5s, LuaUtil.getFileMD5(child.getAbsolutePath()))
      end
    end
  end

  local function customSolibs(p)
    onUpdate("处理自定义 so...")
    local ok, list = pcall(function()
      return luajava.astable(File(p).listFiles() or {})
    end)
    if not ok then return end

    for _, v in ipairs(list) do
      if v.isFile() and v.getName():find("%.so$") then
        local arch = v.getParentFile().getName()
        if arch == "arm64-v8a" or arch == "armeabi-v7a" then
          local spath = "lib/" .. arch .. "/" .. v.getName()
          local e = ZipEntry(spath)
          zos.putNextEntry(e)
          replace[spath] = true
          copyStream(FileInputStream(v), zos)
          table.insert(md5s, LuaUtil.getFileMD5(v.getAbsolutePath()))
        end
       elseif v.isDirectory() then
        customSolibs(v.getAbsolutePath())
      end
    end
  end

  local ok, err = pcall(function()
    local info = activity.getApplicationInfo()
    local zis = ZipInputStream(BufferedInputStream(FileInputStream(File(info.publicSourceDir))))
    local fos = FileOutputStream(tmpApk)
    local zos = ZipOutputStream(BufferedOutputStream(fos))

    addDir(zos, "", File(projectDir))

    local iconSrc = projectDir .. "/icon.png"
    if _M.fileExists(iconSrc) then
      onUpdate("添加应用图标")
      local e = ZipEntry("res/drawable/icon.png")
      zos.putNextEntry(e)
      replace["res/drawable/icon.png"] = true
      copyStream(FileInputStream(File(iconSrc)), zos)
    end

    if File(projectDir .. "/solibs").isDirectory() then
      customSolibs(projectDir .. "/solibs")
    end

    for name, path in pairs(lualib) do
      onUpdate("构建模块: " .. name)
      local ok2, result = pcall(function()
        return console.build(path)
      end)
      if ok2 and result then
        local e = ZipEntry(name)
        zos.putNextEntry(e)
        copyStream(FileInputStream(File(result)), zos)
        table.insert(md5s, LuaUtil.getFileMD5(result))
        pcall(function() File(result).delete() end)
       else
        local why = ok2 and "编译器未返回产物" or tostring(result)
        errbuffer[#errbuffer + 1] = "模块失败: " .. name .. "\n  " .. why
      end
    end

    local entry = zis.getNextEntry()
    while entry do
      local name = entry.getName()
      local lib = name:match("([^/]+%.so)$")
      local skip = false
      if replace[name] then skip = true
       elseif lib and replace[lib] then skip = true
       elseif name:find("^assets/") then skip = true
       elseif name:find("META%-INF") then skip = true
       elseif not name:find("%a") then skip = true
      end

      if not skip then
        local e = ZipEntry(name)
        zos.putNextEntry(e)
        if not entry.isDirectory() then
          local buf = byte[8192]
          while true do
            local n = zis.read(buf)
            if n == -1 then break end
            zos.write(buf, 0, n)
          end
        end
      end
      entry = zis.getNextEntry()
    end

    zos.setComment(table.concat(md5s))
    zis.close()
    zos.closeEntry()
    zos.close()
  end)

  if not ok then
    onFinish(false, "打包失败: " .. tostring(err))
    return
  end

  if #errbuffer > 0 then
    pcall(function() File(tmpApk).delete() end)
    onFinish(false, "打包错误:\n" .. table.concat(errbuffer, "\n"))
    return
  end

  onUpdate("修改 APK 信息...")
  local ok2, err2 = pcall(function()
    local editor = ApkEditor()
    .setOrigFile(tmpApk)
    .setOutFile(tmpApk2)
    .setAppName(appname)
    .setVersionName(appver)
    .setVersionCode(appcode)
    .setMinSdkVersion(minsdkNum)
    .setTargetSdkVersion(appsdkNum)
    .setPackageName(packagename)
    .setPermissions(buildPermissions(userPermission))
    .setCreateListener({
      onStart = function()
        onUpdate("开始修改 APK")
      end,
      onProgress = function(step, message)
        onUpdate(tostring(message))
      end,
      onError = function(e)
        onUpdate("APK 修改错误: " .. tostring(e))
      end,
      onComplete = function()
        onUpdate("APK 修改完成")
        editor.shutdown()
      end
    })
    editor.create()
  end)

  pcall(function() File(tmpApk).delete() end)

  if not ok2 then
    pcall(function() File(tmpApk2).delete() end)
    onFinish(false, "修改 APK 失败: " .. tostring(err2))
    return
  end

  onUpdate("签名 APK...")
  local keyDir = activity.getLuaDir("keys")
  if not keyDir or keyDir == "" then
    pcall(function() File(tmpApk2).delete() end)
    onFinish(false, "找不到 keys 目录")
    return
  end
  if not keyDir:match("/$") then
    keyDir = keyDir .. "/"
  end


  local okSign, signErr = pcall(function()
    local ApkSignerV2 = bindClass "com.androlua.sign.ApkSignerV2"
    ApkSignerV2.sign(tmpApk2, apkPath, keyDir, "testkey", minsdkNum, appsdkNum)
  end)

  pcall(function() File(tmpApk2).delete() end)

  if not okSign then
    onFinish(false, "签名失败（重试通常可恢复）：" .. tostring(signErr))
    return
  end

  if not _M.fileExists(apkPath) then
    onFinish(false, "APK 未生成: " .. apkPath)
    return
  end

  onFinish(true, apkPath)
end

function _M.buildApk(projectName, opts)
  opts = opts or {}
  local onFinish = opts.onFinish or function() end

  local Gate = nil
  local okGate = pcall(function()
    Gate = bindClass "com.androlua.sign.BuildGate"
  end)
  if okGate and Gate and not Gate.tryEnter() then
    onFinish(false, "已有打包任务正在进行，请等待完成后再试")
    return
  end

  local ok, err = pcall(_M._doBuildApk, projectName, opts)

  if okGate and Gate then
    pcall(function() Gate.exit() end)
  end

  if not ok then
    onFinish(false, "打包异常: " .. tostring(err))
  end
end

return _M