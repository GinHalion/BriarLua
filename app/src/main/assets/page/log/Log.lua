require "env"
local bindClass = luajava.bindClass
local LinearLayoutManager = bindClass "androidx.recyclerview.widget.LinearLayoutManager"
local LuaRecyclerAdapter = require "utils.LuaRecyclerAdapter"
local IconDrawable = require "utils.IconDrawable"
local Context = bindClass "android.content.Context"
local ClipData = bindClass "android.content.ClipData"
local Thread = bindClass "java.lang.Thread"
local Handler = bindClass "android.os.Handler"
local Looper = bindClass "android.os.Looper"

local asyncShellOk, AsyncShell = pcall(bindClass, "com.briarlua.utils.AsyncShell")

setStatus()
applyTheme()

activity
.setContentView(loadlayout("layout.log"))
.setSupportActionBar(myToolbar)
.getSupportActionBar()
.setDisplayHomeAsUpEnabled(true)
.setTitle("日志")

MyToast.setActivity(activity)
myLogsRecycler.setLayoutManager(LinearLayoutManager(activity))

local mainHandler = Handler(Looper.getMainLooper())
local tabNames = {"All", "Lua", "Test", "Tcc", "Error", "Warning", "Info", "Debug", "Verbose"}
local tabFilters = {"", "lua:* *:S", "test:* *:S", "tcc:* *:S", "*:E", "*:W", "*:I", "*:D", "*:V"}

local originalData = {}
local currentData = {}
local searchText = ""
local adapter = nil
local restoring = true
local isLoading = false
local destroyed = false

local function isAlive()
  if destroyed then return false end
  local ok, finishing = pcall(function() return activity.isFinishing() end)
  if ok and finishing then return false end
  local ok2, destroyedFlag = pcall(function() return activity.isDestroyed() end)
  if ok2 and destroyedFlag then return false end
  return true
end

local function copyToClipboard(text)
  local ok = pcall(function()
    local cm = activity.getSystemService(Context.CLIPBOARD_SERVICE)
    cm.setPrimaryClip(ClipData.newPlainText("path", text))
  end)
  MyToast.short(ok and "已复制" or "复制失败")
end

local function initAdapter()
  adapter = LuaRecyclerAdapter(currentData, "layout.item.log", {
    onBindViewHolder = function(holder, pos, views, text)
      local tv = views.logText
      local logtype = views.logType
      local card = views.card
      if not tv or not card then return end

      if text:find(" E/") or text:find(" E ") then
        tv.setTextColor(0xFFFF0000)
        logtype.setText("错误")
       elseif text:find(" W/") or text:find(" W ") then
        tv.setTextColor(0xFFFF9800)
        logtype.setText("警告")
       else
        tv.setTextColor(MyColor.colorOnSurface)
        logtype.setText("普通")
      end

      tv.setText(text)

      card.onClick = function(v)
        copyToClipboard(text)
      end
    end
  })
  myLogsRecycler.setAdapter(adapter)
  pcall(function()
    luajava.newInstance("me.zhanghai.android.fastscroll.FastScrollerBuilder", myLogsRecycler).useMd2Style()
    .setPadding(0, MyUI.dp2px(8), MyUI.dp2px(2), MyUI.dp2px(8))
    .build()
  end)
end

local function applyFilter(scrollToBottom)
  for i = #currentData, 1, -1 do
    currentData[i] = nil
  end

  if #searchText == 0 then
    for _, v in ipairs(originalData) do currentData[#currentData + 1] = v end
   else
    local lowerSearch = searchText:lower()
    for _, v in ipairs(originalData) do
      if v:lower():find(lowerSearch, 1, true) then
        currentData[#currentData + 1] = v
      end
    end
    if #currentData == 0 then
      currentData[#currentData + 1] = "未找到包含 \"" .. searchText .. "\" 的日志"
    end
  end

  if adapter then
    adapter.notifyDataSetChanged()
    if scrollToBottom and #currentData > 0 then
      pcall(function() myLogsRecycler.scrollToPosition(#currentData - 1) end)
    end
  end
end

local function ingestContent(content)
  local tempData = {}
  if content and content ~= "" then
    for line in content:gmatch("[^\r\n]+") do
      if line:match("%S") then
        if not line:find("InterceptorFactory") then
          tempData[#tempData + 1] = line
        end
      end
    end
   else
    tempData[#tempData + 1] = "<没有读取到日志内容>"
  end

  for i = #originalData, 1, -1 do
    originalData[i] = nil
  end
  for _, v in ipairs(tempData) do
    originalData[#originalData + 1] = v
  end
end

local function loadLogs(filterStr)
  if isLoading then return end
  isLoading = true

  local cmd = "logcat -d -v time -t 500 " .. (filterStr or "")

  if asyncShellOk then
    AsyncShell.exec(cmd, function(content)
      if not isAlive() then
        isLoading = false
        return
      end
      pcall(function()
        ingestContent(content)
        applyFilter(true)
      end)
      isLoading = false
      local curIdx = myTabLayout.getSelectedTabPosition() + 1
      local curFilter = tabFilters[curIdx]
      if curFilter ~= nil and curFilter ~= (filterStr or "") then
        loadLogs(curFilter)
      end
    end)
   else
    Thread(luajava.createProxy("java.lang.Runnable", {
      run = function()
        local p = io.popen(cmd)
        local content = ""
        if p then
          content = p:read("*a")
          p:close()
        end

        mainHandler.post(luajava.createProxy("java.lang.Runnable", {
          run = function()
            if not isAlive() then
              isLoading = false
              return
            end
            pcall(function()
              ingestContent(content)
              applyFilter(true)
            end)
            isLoading = false
          end
        }))
      end
    })).start()
  end
end

myTabLayout.removeAllTabs()
for _, name in ipairs(tabNames) do
  myTabLayout.addTab(myTabLayout.newTab().setText(name))
end
myTabLayout.setVisibility(0)

myTabLayout.addOnTabSelectedListener({
  onTabSelected = function(tab)
    if restoring then return end
    local idx = tab.getPosition() + 1
    loadLogs(tabFilters[idx])
  end
})

local debounceRunnable = nil
mySearchEdit.addTextChangedListener({
  beforeTextChanged = function(s, start, count, after) end,
  onTextChanged = function(s, start, before, count) end,
  afterTextChanged = function(s)
    searchText = tostring(s or "")
    if debounceRunnable then mainHandler.removeCallbacks(debounceRunnable) end
    debounceRunnable = luajava.createProxy("java.lang.Runnable", {
      run = function()
        debounceRunnable = nil
        if not isAlive() then return end
        pcall(function() applyFilter(false) end)
      end,
    })
    mainHandler.postDelayed(debounceRunnable, 300)
  end,
})

function onCreateOptionsMenu(menu)
  menu.add("复制全部").setShowAsAction(2).setIcon(IconDrawable("copy", MyColor.colorOnSurfaceVariant)).onMenuItemClick = function()
    if #currentData == 0 then
      MyToast.short("当前列表为空")
      return
    end
    local allLogs = table.concat(currentData, "\n")
    copyToClipboard(allLogs)
  end

  menu.add("清空日志").setShowAsAction(2).setIcon(IconDrawable("delete", MyColor.colorOnSurfaceVariant)).onMenuItemClick = function()
    if asyncShellOk then
      AsyncShell.exec("logcat -c", function()
        if not isAlive() then return end
        MyToast.short("日志已清空")
        loadLogs(tabFilters[myTabLayout.getSelectedTabPosition() + 1])
      end)
     else
      local p = io.popen("logcat -c")
      if p then p:read("*a"); p:close() end
      MyToast.short("日志已清空")
      loadLogs(tabFilters[myTabLayout.getSelectedTabPosition() + 1])
    end
  end
end

function onOptionsItemSelected(item)
  if item.getItemId() == android.R.id.home then
    activity.finish()
    return true
  end
  return false
end

initAdapter()

if myTabLayout.getTabCount() > 0 then
  myTabLayout.getTabAt(0).select()
end

loadLogs("")
restoring = false

function onDestroy()
  destroyed = true
  pcall(function() mainHandler.removeCallbacksAndMessages(nil) end)
  pcall(function()
    if mySearchEdit and debounceRunnable then
      mySearchEdit.removeCallbacks(debounceRunnable)
    end
  end)
  pcall(function()
    if myLogsRecycler then myLogsRecycler.setAdapter(nil) end
  end)
  for i = #originalData, 1, -1 do originalData[i] = nil end
  for i = #currentData, 1, -1 do currentData[i] = nil end
  adapter = nil
  collectgarbage("collect")
end

local onThemeResume = makeThemeResumeChecker()
function onResume()
  onThemeResume()
end
