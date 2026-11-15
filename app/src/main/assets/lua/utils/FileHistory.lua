local _M = {}
local bindClass = luajava.bindClass
local FileClass = bindClass "java.io.File"
local ProjectUtil = require "utils.Project"

local historyDir = ProjectUtil.history_path
local historyFile = historyDir .. "/file_history.lua"

local CACHE = nil

_M.dirty = false

local function ensureDir()
  local ok, dir = pcall(function() return FileClass(historyDir) end)
  if ok and dir and not dir.exists() then dir.mkdirs() end
end

local function loadAll()
  if CACHE ~= nil then return CACHE end
  ensureDir()
  local content = ProjectUtil.readFile(historyFile)
  local data = {}
  if content and content ~= "" then
    local chunk = load("return " .. content, "@history", "t", {})
    if chunk then
      local ok, d = pcall(chunk)
      if ok and type(d) == "table" then data = d end
    end
  end
  CACHE = data
  return data
end

local function saveAll(data)
  data = data or CACHE
  if data == nil then return false end
  ensureDir()
  local parts = { "{" }
  for projName, proj in pairs(data) do
    parts[#parts + 1] = string.format("  [%q] = {", projName)

    parts[#parts + 1] = "    files = {"
    for _, p in ipairs(proj.files or {}) do
      parts[#parts + 1] = string.format("      %q,", p)
    end
    parts[#parts + 1] = "    },"

    if proj.lastFilePath then
      parts[#parts + 1] = string.format("    lastFilePath = %q,", proj.lastFilePath)
    end

    if proj.lines and next(proj.lines) then
      parts[#parts + 1] = "    lines = {"
      for p, line in pairs(proj.lines) do
        parts[#parts + 1] = string.format("      [%q] = %d,", p, tonumber(line) or 1)
      end
      parts[#parts + 1] = "    },"
    end

    parts[#parts + 1] = "  },"
  end
  parts[#parts + 1] = "}"
  return ProjectUtil.writeFile(historyFile, table.concat(parts, "\n"))
end

local function ensureProject(all, projectName)
  local p = all[projectName]
  if not p or type(p) ~= "table" then
    all[projectName] = { files = {}, lastFilePath = nil, lines = {} }
    return all[projectName]
  end
  if p[1] and not p.files then
    all[projectName] = { files = p, lastFilePath = nil, lines = {} }
    return all[projectName]
  end
  if not p.files then p.files = {} end
  if not p.lines then p.lines = {} end
  return p
end

function _M.get(projectName)
  if not projectName or projectName == "" then return {} end
  local all = loadAll()
  local proj = ensureProject(all, projectName)
  return proj.files
end

function _M.put(projectName, list)
  if not projectName or projectName == "" then return false end
  local all = loadAll()
  local proj = ensureProject(all, projectName)
  proj.files = list
  _M.dirty = true
  return true
end

function _M.remove(projectName, path)
  local list = _M.get(projectName)
  for i = #list, 1, -1 do
    if list[i] == path then table.remove(list, i) end
  end
  _M.put(projectName, list)
  _M.removeLine(projectName, path)
end

function _M.addUnique(projectName, path)
  if not path or path == "" then return end
  local list = _M.get(projectName)
  for _, p in ipairs(list) do
    if p == path then return end
  end
  list[#list + 1] = path
  _M.put(projectName, list)
end

function _M.setLastFile(projectName, path)
  if not projectName or projectName == "" then return end
  local all = loadAll()
  local proj = ensureProject(all, projectName)
  proj.lastFilePath = path
  _M.dirty = true
end

function _M.getLastFile(projectName)
  if not projectName or projectName == "" then return nil end
  local all = loadAll()
  local proj = ensureProject(all, projectName)
  return proj.lastFilePath
end

function _M.clearLastFile(projectName)
  if not projectName or projectName == "" then return end
  local all = loadAll()
  local proj = ensureProject(all, projectName)
  proj.lastFilePath = nil
  _M.dirty = true
end

function _M.setLine(projectName, path, line)
  if not projectName or projectName == "" or not path or path == "" then return end
  line = tonumber(line) or 1
  if line < 1 then line = 1 end
  local all = loadAll()
  local proj = ensureProject(all, projectName)
  proj.lines[path] = line
  _M.dirty = true
end

function _M.getLine(projectName, path)
  if not projectName or projectName == "" or not path or path == "" then return 1 end
  local all = loadAll()
  local proj = ensureProject(all, projectName)
  return tonumber(proj.lines[path]) or 1
end

function _M.removeLine(projectName, path)
  if not projectName or projectName == "" or not path or path == "" then return end
  local all = loadAll()
  local proj = ensureProject(all, projectName)
  proj.lines[path] = nil
  _M.dirty = true
end

function _M.flush()
  if not _M.dirty then return true end
  local all = loadAll()
  if all == nil then return false end
  local ok = saveAll(all)
  if ok then _M.dirty = false end
  return ok
end

return _M
