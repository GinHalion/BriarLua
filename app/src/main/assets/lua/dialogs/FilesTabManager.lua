local _M = {}

local bindClass = luajava.bindClass
local PopupMenu = bindClass "androidx.appcompat.widget.PopupMenu"
local ClipData = bindClass "android.content.ClipData"
local Context = bindClass "android.content.Context"
local FileClass = bindClass "java.io.File"

local FileHistory = require "utils.FileHistory"

_M.projectName = nil
_M.projectDir = nil
_M.getTabs = function() return nil end
_M.onTabCountChanged = function(count) end
_M.onBeforeRemove = function(path) return true end

_M.tabData = {}
_M.tabOrder = {}

local fileIconMap = {
  lua = R.drawable.ic_language_lua,
  aly = R.drawable.ic_code_braces,
  json = R.drawable.ic_code_json,
  png = R.drawable.ic_file_image_outline,
  jpg = R.drawable.ic_file_image_outline,
  gif = R.drawable.ic_file_image_outline,
  java = R.drawable.ic_language_java,
  kt = R.drawable.ic_language_kotlin,
  gradle = R.drawable.ic_language_gradle,
  js = R.drawable.ic_language_javascript,
  py = R.drawable.ic_language_python,
  apk = R.drawable.ic_android,
  zip = R.drawable.ic_zip_box_outline,
  _default = R.drawable.ic_file_outline,
}

local function currentTabs()
  return _M.getTabs()
end

local function copyText(text)
  local ok = pcall(function()
    local cm = activity.getSystemService(Context.CLIPBOARD_SERVICE)
    cm.setPrimaryClip(ClipData.newPlainText("path", text))
  end)
  MyToast.short(ok and "已复制" or "复制失败")
end

local function showTabMenu(tabView, path)
  local tabs = currentTabs()
  if not tabs then return end

  local fileName = path:match("[^/]+$") or path

  local popup = PopupMenu(activity, tabView)
  local menu = popup.getMenu()

  menu.add("关闭")
  menu.add("关闭其他")
  menu.add("复制文件名")
  menu.add("复制完整路径")

  popup.setOnMenuItemClickListener({
    onMenuItemClick = function(item)
      local title = tostring(item.getTitle())

      if title == "关闭" then
        _M.remove(path)

      elseif title == "关闭其他" then
        local toRemove = {}
        for p in pairs(_M.tabData) do
          if p ~= path then toRemove[#toRemove + 1] = p end
        end
        for _, p in ipairs(toRemove) do _M.remove(p) end

      elseif title == "复制文件名" then
        copyText(fileName)

      elseif title == "复制完整路径" then
        copyText(path)
      end

      return true
    end,
  })

  popup.show()
end

function _M.indexOf(path)
  for i, p in ipairs(_M.tabOrder) do
    if p == path then return i - 1 end
  end
  return -1
end

function _M.add(path)
  local tabs = currentTabs()
  if not tabs then return end
  if not path or path == "" then return end
  if _M.tabData[path] then return end

  local fileName = path:match("[^/]+$") or path
  local ext = (path:match("%.(%w+)$") or ""):lower()

  local tab = tabs.newTab()
  tab.setText(fileName)
  tab.setIcon(fileIconMap[ext] or fileIconMap._default)

  _M.tabData[path] = { tab = tab, path = path }
  _M.tabOrder[#_M.tabOrder + 1] = path

  tabs.addTab(tab)

  activity.onLongClick(tab.view, function(v)
    showTabMenu(v, path)
  end)

  FileHistory.addUnique(_M.projectName, path)
end

function _M.remove(path)
  local tabs = currentTabs()
  if not tabs then return end

  local data = _M.tabData[path]
  if not data then return end

  if _M.onBeforeRemove then
    local ok = _M.onBeforeRemove(path)
    if ok == false then return end
  end

  local wasActive = (tabs.getSelectedTabPosition() == _M.indexOf(path))

  tabs.removeTab(data.tab)
  _M.tabData[path] = nil

  for i, p in ipairs(_M.tabOrder) do
    if p == path then
      table.remove(_M.tabOrder, i)
      break
    end
  end

  FileHistory.remove(_M.projectName, path)

  local remaining = tabs.getTabCount()

  if wasActive then
    local newActive = _M.getActivePath()
    if newActive then
      FileHistory.setLastFile(_M.projectName, newActive)
    end
  end

  if remaining == 0 then
    FileHistory.clearLastFile(_M.projectName)
  end

  if _M.onTabCountChanged then
    _M.onTabCountChanged(remaining)
  end
end

function _M.removeAll()
  local paths = {}
  for p in pairs(_M.tabData) do paths[#paths + 1] = p end
  for _, p in ipairs(paths) do _M.remove(p) end
end

function _M.rename(oldPath, newPath)
  local data = _M.tabData[oldPath]
  if not data then return end

  data.path = newPath
  data.tab.setText(newPath:match("[^/]+$") or newPath)

  _M.tabData[newPath] = data
  _M.tabData[oldPath] = nil

  for i, p in ipairs(_M.tabOrder) do
    if p == oldPath then
      _M.tabOrder[i] = newPath
      break
    end
  end

  FileHistory.remove(_M.projectName, oldPath)
  FileHistory.addUnique(_M.projectName, newPath)

  local line = FileHistory.getLine(_M.projectName, oldPath)
  FileHistory.removeLine(_M.projectName, oldPath)
  if line and line > 1 then
    FileHistory.setLine(_M.projectName, newPath, line)
  end

  if FileHistory.getLastFile(_M.projectName) == oldPath then
    FileHistory.setLastFile(_M.projectName, newPath)
  end
end

function _M.restoreHistory()
  local list = FileHistory.get(_M.projectName)
  if #list == 0 then return false, nil end

  local count = 0
  for _, p in ipairs(list) do
    if FileClass(p).isFile() then
      _M.add(p)
      count = count + 1
    end
  end

  if count == 0 then return false, nil end

  local lastFile = FileHistory.getLastFile(_M.projectName)
  if not lastFile or not FileClass(lastFile).isFile() then
    lastFile = _M.tabOrder[1]
  end

  return true, lastFile
end

function _M.persist()
  local list = {}
  for _, p in ipairs(_M.tabOrder) do
    list[#list + 1] = p
  end
  FileHistory.put(_M.projectName, list)

  local active = _M.getActivePath()
  if active then
    FileHistory.setLastFile(_M.projectName, active)
  end

  FileHistory.flush()
end

function _M.getActivePath()
  local tabs = currentTabs()
  if not tabs then return nil end
  local pos = tabs.getSelectedTabPosition()
  if pos < 0 then return nil end
  return _M.tabOrder[pos + 1]
end

return _M