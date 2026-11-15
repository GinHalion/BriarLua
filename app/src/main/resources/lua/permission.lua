require "import"
import "android.content.pm.PackageManager"
local pm = activity.getPackageManager();
local pi = pm.getPackageInfo(activity.getPackageName(), 0);
local pkgName = pi.packageName;

local pkgInfo = pm.getPackageInfo(pkgName,
    PackageManager.GET_PERMISSIONS);
local sharedPkgList = pkgInfo.requestedPermissions;
permission = {}
permission_info = {}
permission_info["BIND_ACCESSIBILITY_SERVICE"] = "辅助功能"
for i = 0, #sharedPkgList - 1 do
    local permName = sharedPkgList[i];
    local s, tmpPermInfo = pcall(pm.getPermissionInfo, permName, 0);

    permName = permName:match("%.([%w_]+)$")
    table.insert(permission, permName)
    if s then
        permission_info[permName] = tmpPermInfo.loadLabel(pm)
    else
        permission_info[permName] = permName
    end
end
