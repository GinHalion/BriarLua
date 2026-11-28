local _M = {}

local activityPaths = {
  home = "page/home/Home",
  welcome = "page/welcome/Welcome",
  setting = "page/setting/Setting",
  build = "page/build/Build",
  info = "page/info/Info",
  log = "page/log/Log",
  doc = "page/doc/Doc",
  api = "page/api/Api",
  parsing = "page/api/Parsing",
  analysis = "page/analysis/Analysis",
  editor = "page/editor/Editor"
}

_M.new = function(name, data, extra)
  local path = activityPaths[name]
  if path then
    local fullPath = activity.getLuaDir() .. "/" .. path
    if data then
      if extra then
        local args = {}
        for i = 1, #data do
          args[i] = data[i]
        end
        args[#data + 1] = extra
        activity.newActivity(fullPath, args)
       else
        activity.newActivity(fullPath, data)
      end
     else
      activity.newActivity(fullPath)
    end
  end
end

return _M