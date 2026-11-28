require "env"
local bindClass = luajava.bindClass
local WindowManager = bindClass "android.view.WindowManager"
local ProjectUtil = require "utils.Project"
local LinearLayoutManager = bindClass "androidx.recyclerview.widget.LinearLayoutManager"
local MaterialAlertDialogBuilder = bindClass "com.google.android.material.dialog.MaterialAlertDialogBuilder"
local Handler = bindClass "android.os.Handler"
local System = bindClass "java.lang.System"
local Context = bindClass "android.content.Context"
local ClipData = bindClass "android.content.ClipData"

local projectName = ...
if type(projectName) == "table" then projectName = projectName[1] end
if not projectName or projectName == "" then
  MyToast.short("缺少项目名称")
  return
end

setStatus()
applyTheme()

activity
.setContentView(loadlayout("layout.build"))
.setSupportActionBar(myToolbar)
.getSupportActionBar()
.setDisplayHomeAsUpEnabled(true)
.setTitle("打包项目")

activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

MyToast.setActivity(activity)

local lastUpdateTime = System.currentTimeMillis()
local updateHandler = Handler()
local updateQueue = {}
local data = {}
local adapter = nil
local started = false
local buildTask = nil

local function safeToast(text)
  pcall(function() MyToast.short(text) end)
end

local function refreshMessage()
  if #updateQueue == 0 then return end

  local count = #updateQueue
  local startPos = #data

  for _, msg in ipairs(updateQueue) do
    data[#data + 1] = msg
  end
  updateQueue = {}

  if adapter then
    adapter.notifyItemRangeInserted(startPos, count)
  end
  if myBuildRecyclerView then
    myBuildRecyclerView.scrollToPosition(#data - 1)
  end
end

local destroyed = false

local refreshRunnable = luajava.createProxy("java.lang.Runnable", {
  run = function()
    if destroyed then return end
    pcall(refreshMessage)
  end
})

function update(message)
  message = tostring(message)
  local now = System.currentTimeMillis()
  updateQueue[#updateQueue + 1] = message

  if message:lower():find("error") or message:find("失败") or message:lower():find("failed") then
    refreshMessage()
    lastUpdateTime = now
    return
  end

  if now - lastUpdateTime > 1000 then
    refreshMessage()
    lastUpdateTime = now
   else
    updateHandler.removeCallbacksAndMessages(nil)
    updateHandler.postDelayed(refreshRunnable, 150)
  end
end

function onDestroy()
  destroyed = true
  updateHandler.removeCallbacksAndMessages(nil)
  if buildTask then
    pcall(function() buildTask.cancel(true) end)
    buildTask = nil
  end
  collectgarbage("collect")
end

function onOptionsItemSelected(item)
  if item.getItemId() == android.R.id.home then
    activity.finish()
    return true
  end
end

local function copyToClipboard(text)
  local ok = pcall(function()
    local cm = activity.getSystemService(Context.CLIPBOARD_SERVICE)
    cm.setPrimaryClip(ClipData.newPlainText("path", text))
  end)
  MyToast.short(ok and "已复制" or "复制失败")
end

local function setupList()
  local LuaRecyclerAdapter = require "utils.LuaRecyclerAdapter"
  adapter = LuaRecyclerAdapter(data, "layout.item.build", {
    onBindViewHolder = function(holder, position, views, text)
      if not text or not views then return end
      if views.text then views.text.setText(text) end
      if views.card then
        local isError = text:lower():find("error") or text:find("失败") or text:lower():find("failed")
        pcall(function()
          views.card.setStrokeColor(isError and MyColor.colorError or MyColor.colorOutlineVariant)
          views.text.setTextColor(isError and MyColor.colorError or MyColor.colorPrimary)
        end)
        views.card.onClick = function()
          copyToClipboard(text)
        end
      end
    end
  })
  if myBuildRecyclerView then
    myBuildRecyclerView.setAdapter(adapter)
    myBuildRecyclerView.setLayoutManager(LinearLayoutManager(activity))
    luajava.newInstance("me.zhanghai.android.fastscroll.FastScrollerBuilder", myBuildRecyclerView).useMd2Style()
    .setPadding(0, MyUI.dp2px(8), MyUI.dp2px(2), MyUI.dp2px(8))
    .build()
  end
end

function onError(title, msg)
  update(tostring(title) .. ": " .. tostring(msg))
end

local function doBuild()
  if started then return end
  started = true

  update("开始打包: " .. projectName)

  local function worker(prj)
    local ProjectUtil = require "utils.Project"
    local success, result = false, nil
    local ok, err = pcall(function()
      ProjectUtil.buildApk(prj, {
        onUpdate = function(msg)
          update(tostring(msg))
        end,
        onFinish = function(s, r)
          success = s
          result = r
        end,
      })
    end)
    if not ok then return false, tostring(err) end
    return success, result
  end

  local function callback(success, result)
    if success then
      update("打包成功: " .. tostring(result))
      local builder = MaterialAlertDialogBuilder(activity)
      builder.setTitle("打包成功")
      builder.setMessage(tostring(result))
      builder.setPositiveButton("安装", {
        onClick = function()
          pcall(function() activity.installApk(tostring(result)) end)
        end
      })
      builder.setNegativeButton("关闭", nil)
      builder.show()
     else
      update("打包失败: " .. tostring(result))
      safeToast("打包失败")
    end
  end

  buildTask = activity.newTask(worker, update, callback)
  buildTask.execute({projectName})
end

setupList()

local buildStarter = luajava.createProxy("java.lang.Runnable", {
  run = function()
    if destroyed then return end
    doBuild()
  end
})
Handler().postDelayed(buildStarter, 300)

local onThemeResume = makeThemeResumeChecker()
function onResume()
  onThemeResume()
end
