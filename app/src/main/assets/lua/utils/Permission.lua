local _M = {}
local bindClass = luajava.bindClass
local PackageManager = bindClass "android.content.pm.PackageManager"

local function cloneTable(t)
    local copy = {}
    for k, v in pairs(t) do copy[k] = v end
    return copy
end

function _M:with()
    local new = cloneTable(self)
    new.permissions = {}
    return new
end

function _M:permissionGroup(...)
    for _, p in ipairs({...}) do
        self:permission(p)
    end
    return self
end

function _M:permission(...)
    local args = {...}
    if #args == 1 and type(args[1]) == "table" then args = args[1] end
    for _, p in ipairs(args) do
        if not table.contains(self.permissions, p) then
            table.insert(self.permissions, p)
        end
    end
    return self
end

function _M:getDeniedPermissions(list)
    local perms = list or self.permissions
    local denied = {}
    for _, p in ipairs(perms) do
        if activity.checkCallingOrSelfPermission(p) ~= 0 then
            table.insert(denied, p)
        end
    end
    return denied
end

function _M:allPermission()
    local denied = self:getDeniedPermissions()
    return self:permission(denied)
end

function _M:callback(fn)
    self.callbackt = fn
    return self
end

function _M:start()
    self.permissions = self:getDeniedPermissions(self.permissions)
    if #self.permissions == 0 then
        task(20, function() pcall(self.callbackt, true, {}, {}) end)
        return self
    end

    activity.requestPermissions(self.permissions, 10000)

    function onRequestPermissionsResult(requestCode, permissions, grantResults)
        local granted, denied = {}, {}
        for i = 0, #permissions - 1 do
            if grantResults[i] == PackageManager.PERMISSION_GRANTED then
                table.insert(granted, permissions[i])
            else
                table.insert(denied, permissions[i])
            end
        end
        pcall(self.callbackt, #denied == 0, granted, denied)
    end

    return self
end

function _M.checkPermission(...)
    local args = {...}
    if #args == 1 then
        if type(args[1]) == "table" then
            return _M.checkPermission(table.unpack(args[1]))
        else
            return activity.checkCallingOrSelfPermission(args[1]) == 0
        end
    end

    local denied = {}
    for _, p in ipairs(args) do
        if activity.checkCallingOrSelfPermission(p) ~= 0 then
            table.insert(denied, p)
        end
    end
    return denied
end

function table.contains(tbl, val)
    for _, v in ipairs(tbl) do
        if v == val then return true end
    end
    return false
end

return _M