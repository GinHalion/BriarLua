local _M = {}
local bindClass = luajava.bindClass
local MaterialAlertDialogBuilder = bindClass "com.google.android.material.dialog.MaterialAlertDialogBuilder"
local WindowManager = bindClass "android.view.WindowManager"

function _M.show()
  dialog = dialog.show()
  local window = dialog.create().getWindow()
  local layoutParams = WindowManager.LayoutParams()
  layoutParams.copyFrom(window.getAttributes())
  layoutParams.width = dp2px(200)
  layoutParams.height = dp2px(315)
  dialog.show().getWindow().setAttributes(layoutParams)
  return _M
end

function _M.dismiss()
  dialog.dismiss()
  return _M
end

setmetatable(_M,{
  __index = lambda(self,...):dialog[...]
})

return function(...)
  dialog = MaterialAlertDialogBuilder(...)
  .setView(loadlayout("layout.progress"))
  return _M
end