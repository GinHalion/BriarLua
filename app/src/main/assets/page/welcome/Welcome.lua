require "env"
local bindClass = luajava.bindClass
local ViewPager = bindClass "androidx.viewpager.widget.ViewPager"
local PackageManager = bindClass "android.content.pm.PackageManager"
local LinearLayoutManager = bindClass "androidx.recyclerview.widget.LinearLayoutManager"
local Intent = bindClass "android.content.Intent"
local Environment = bindClass "android.os.Environment"
local Uri = bindClass "android.net.Uri"
local Build = bindClass "android.os.Build"
local Settings = bindClass "android.provider.Settings"
local MyPermission = require "utils.Permission"
local LuaRecyclerAdapter = require "utils.LuaRecyclerAdapter"

setStatus()
applyTheme()

activity
.setContentView(loadlayout("layout.welcome"))

MyToast.setActivity(activity)

local max_page = 2
local REQ_SETTINGS = 2296

local step_data = {
  {
    title = "存储权限",
    text = "程序需要访问你的文件以便读取和处理，请允许此权限。",
    permission = {
      "android.permission.READ_EXTERNAL_STORAGE",
      "android.permission.WRITE_EXTERNAL_STORAGE"
    },
    ask = true,
    state = false,
  }
}

local function isStorageGranted()
  if Build.VERSION.SDK_INT >= 30 then
    return Environment.isExternalStorageManager()
  end
  return #MyPermission.checkPermission({
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE"
  }) == 0
end

local function checkAllCompleted()
  for _, step in ipairs(step_data) do
    if not step.state then
      return false
    end
  end
  return true
end

local function setBtnEnabled(btn, enabled)
  btn.setEnabled(enabled)
  btn.setAlpha(enabled and 1 or 0.5)
end

local function refreshNextGo()
  local cur = myWelcomePager.getCurrentItem()
  if cur == 0 then
    setBtnEnabled(nextGo, true)
   else
    setBtnEnabled(nextGo, checkAllCompleted())
  end
end

local function refreshStorageState()
  local ok = isStorageGranted()
  step_data[1].state = ok
  if adapter_permission then
    adapter_permission.notifyDataSetChanged()
  end
  refreshNextGo()
end

local function requestStorage()
  if Build.VERSION.SDK_INT >= 30 then
    local intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
    intent.setData(Uri.parse("package:" .. activity.getPackageName()))
    activity.startActivityForResult(intent, REQ_SETTINGS)
   else
    MyPermission:with():permission({
      "android.permission.READ_EXTERNAL_STORAGE",
      "android.permission.WRITE_EXTERNAL_STORAGE"
    }):allPermission():callback(function(success)
      refreshStorageState()
    end):start()
  end
end

local function initRecycler()
  if adapter_permission then
    return
  end
  adapter_permission = LuaRecyclerAdapter(step_data, "layout.item.authority", {
    onBindViewHolder = function(viewHolder, pos, views, currentData)
      views.title.setText(tostring(currentData.title or ""))
      views.text.setText(tostring(currentData.text or ""))
      if currentData.ask then
        local ok = isStorageGranted()
        currentData.state = ok
        views.card.setChecked(ok)
      end
      views.card.onClick = function()
        if currentData.ask then
          if isStorageGranted() then
            views.card.setChecked(true)
            currentData.state = true
            refreshNextGo()
            return
          end
          requestStorage()
        end
      end
    end
  })
  myWelcomeRecyclerView
  .setAdapter(adapter_permission)
  .setLayoutManager(LinearLayoutManager(activity))
end

myWelcomePager.setOnPageChangeListener(ViewPager.OnPageChangeListener {
  onPageSelected = function(position)
    indicator.setProgressCompat((position / (max_page - 1)) * 100, true)
    if position == 1 then
      setBtnEnabled(previouGo, true)
      refreshNextGo()
     else
      setBtnEnabled(previouGo, false)
      setBtnEnabled(nextGo, true)
    end
  end
})

previouGo.onClick = function()
  local cur = myWelcomePager.getCurrentItem()
  if cur > 0 then
    myWelcomePager.setCurrentItem(cur - 1)
  end
end

nextGo.onClick = function()
  if not nextGo.isEnabled() then return end
  local targetPage = myWelcomePager.getCurrentItem() + 1
  if targetPage < max_page then
    myWelcomePager.setCurrentItem(targetPage)
   else
    activity.setSharedData("BriarWelcome", true)
    MyActivity.new("home")
    activity.finish()
  end
end

function onActivityResult(req, resx, intent)
  if req == REQ_SETTINGS then
    refreshStorageState()
  end
end

initRecycler()
refreshStorageState()
setBtnEnabled(previouGo, false)
refreshNextGo()

function onDestroy()
  adapter_permission = nil
  luajava.clear()
  collectgarbage("collect")
  collectgarbage("step")
end

local onThemeResume = makeThemeResumeChecker()
function onResume()
  onThemeResume()
end
