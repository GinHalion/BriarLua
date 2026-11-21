local _M = {}
local bindClass = luajava.bindClass
local Snackbar = bindClass "com.google.android.material.snackbar.Snackbar"

local currentActivity = nil

function _M.setActivity(activity)
    currentActivity = activity
end

local function getRootView()
    if currentActivity == nil then
        return nil
    end
    local window = currentActivity.getWindow()
    if window == nil then
        return nil
    end
    local decor = window.getDecorView()
    if decor == nil then
        return nil
    end
    return decor.findViewById(android.R.id.content)
end

local function makeOnView(view, text, duration, actionText, actionCallback)
    if view == nil then
        return nil
    end
    local sb = Snackbar.make(view, text, duration)
    if actionText ~= nil and actionCallback ~= nil then
        sb.setAction(actionText, luajava.createProxy("android.view.View$OnClickListener", {
            onClick = function(v)
                actionCallback()
            end
        }))
    end
    sb.show()
    return sb
end

local function show(text, duration, actionText, actionCallback)
    local root = getRootView()
    if root == nil then
        return nil
    end
    return makeOnView(root, text, duration, actionText, actionCallback)
end

function _M.short(text)
    return show(text, Snackbar.LENGTH_SHORT)
end

function _M.long(text)
    return show(text, Snackbar.LENGTH_LONG)
end

function _M.indefinite(text)
    return show(text, Snackbar.LENGTH_INDEFINITE)
end

function _M.action(text, actionText, callback)
    return show(text, Snackbar.LENGTH_LONG, actionText, callback)
end

function _M.onView(view, text)
    return makeOnView(view, text, Snackbar.LENGTH_SHORT)
end

function _M.onViewLong(view, text)
    return makeOnView(view, text, Snackbar.LENGTH_LONG)
end

function _M.onViewIndefinite(view, text)
    return makeOnView(view, text, Snackbar.LENGTH_INDEFINITE)
end

function _M.onViewAction(view, text, actionText, callback)
    return makeOnView(view, text, Snackbar.LENGTH_LONG, actionText, callback)
end

function _M.onViewCustom(view, text, duration, actionText, actionCallback, bgColor, textColor)
    local sb = makeOnView(view, text, duration, actionText, actionCallback)
    if sb == nil then
        return nil
    end
    local sbView = sb.getView()
    if bgColor ~= nil then
        sbView.setBackgroundColor(bgColor)
    end
    if textColor ~= nil then
        local tv = sbView.findViewById(com.google.android.material.R.id.snackbar_text)
        if tv ~= nil then
            tv.setTextColor(textColor)
        end
    end
    return sb
end

function _M.custom(text, duration, actionText, actionCallback, bgColor, textColor)
    local root = getRootView()
    if root == nil then
        return nil
    end
    return _M.onViewCustom(root, text, duration, actionText, actionCallback, bgColor, textColor)
end

function _M.dismiss(sb)
    if sb ~= nil then
        sb.dismiss()
    end
end

return _M