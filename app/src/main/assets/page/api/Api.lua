require "env"
local bindClass = luajava.bindClass
local LinearLayoutManager = bindClass "androidx.recyclerview.widget.LinearLayoutManager"
local LuaRecyclerAdapter = require "utils.LuaRecyclerAdapter"
local Context = bindClass "android.content.Context"
local ClipData = bindClass "android.content.ClipData"

setStatus()
applyTheme()

activity
.setContentView(loadlayout("layout.api"))
.setSupportActionBar(myToolbar)
.getSupportActionBar()
.setDisplayHomeAsUpEnabled(true)
.setTitle("API阅览器")

MyToast.setActivity(activity)

mySearchLayout.setVisibility(0)
myApiRecyclerView.setLayoutManager(LinearLayoutManager(activity))

local listView = myApiRecyclerView

pcall(function()
  luajava.newInstance("me.zhanghai.android.fastscroll.FastScrollerBuilder", listView).useMd2Style()
  .setPadding(0, MyUI.dp2px(8), MyUI.dp2px(2), MyUI.dp2px(8))
  .build()
end)

local CATEGORY_ORDER_BASE = { "All", "BriarLua", "Android", "AndroidX", "AndroLua", "Material", "Glide", "GitHub", "Java", "Kotlin", "Okhttp3", "Okio", "Other" }

local buckets = {}
local lowerBuckets = {}
local categoryOrder = {}
local currentCategory = "All"
local searchText = ""
local displayData = {}
local apiAdapter = nil
local destroyed = false

local function isAlive()
  if destroyed then return false end
  local ok, finishing = pcall(function() return activity.isFinishing() end)
  if ok and finishing then return false end
  local ok2, destroyedFlag = pcall(function() return activity.isDestroyed() end)
  if ok2 and destroyedFlag then return false end
  return true
end

local function parsePayload(payload)
  buckets = {}
  lowerBuckets = {}
  categoryOrder = {}
  for _, name in ipairs(CATEGORY_ORDER_BASE) do
    buckets[name] = {}
  end
  for line in payload:gmatch("[^\n]+") do
    local sep = line:find("\t", 1, true)
    if sep then
      local cat = line:sub(1, sep - 1)
      local name = line:sub(sep + 1)
      local b = buckets[cat]
      if b then
        b[#b + 1] = name
      end
    end
  end
  for _, name in ipairs(CATEGORY_ORDER_BASE) do
    if #(buckets[name] or {}) > 0 then
      categoryOrder[#categoryOrder + 1] = name
    end
  end
end

local function parseFromRegistry()
  local ClassRegistry = require "utils.ClassRegistry"
  local all = ClassRegistry.all() or {}
  buckets = {}
  lowerBuckets = {}
  categoryOrder = {}
  for _, name in ipairs(CATEGORY_ORDER_BASE) do
    buckets[name] = {}
  end
  for _, full in ipairs(all) do
    buckets["All"][#buckets["All"] + 1] = full

    local matched = false
    local function put(cat)
      buckets[cat][#buckets[cat] + 1] = full
      matched = true
    end
    if full:find("android.", 1, true) == 1 then put("Android") end
    if full:find("androidx.", 1, true) == 1 then put("AndroidX") end
    if full:find("com.androlua.", 1, true) == 1
      or full:find("com.luajava.", 1, true) == 1
      or full:find("com.nirenr.", 1, true) == 1 then
      put("AndroLua")
    end
    if full:find("com.briarlua.", 1, true) == 1 then put("BriarLua") end
    if full:find("com.google.android.material.", 1, true) == 1 then put("Material") end
    if full:find("glide", 1, true) then put("Glide") end
    if full:find("github.", 1, true) == 1 then put("GitHub") end
    if full:find("java.", 1, true) == 1 or full:find("javax.", 1, true) == 1 then put("Java") end
    if full:find("kotlin.", 1, true) == 1 or full:find("kotlinx.", 1, true) == 1 then put("Kotlin") end
    if full:find("okhttp3.", 1, true) == 1 then put("Okhttp3") end
    if full:find("okio.", 1, true) == 1 then put("Okio") end
    if not matched then put("Other") end
  end
  for _, name in ipairs(CATEGORY_ORDER_BASE) do
    if #(buckets[name] or {}) > 0 then
      categoryOrder[#categoryOrder + 1] = name
    end
  end
end

local function loadData()
  local ok, payload = pcall(function()
    local ClassCatalog = bindClass "com.briarlua.utils.ClassCatalog"
    return ClassCatalog.getPayload(activity)
  end)
  if ok and type(payload) == "string" and #payload > 0 then
    parsePayload(payload)
    return true
  end
  pcall(parseFromRegistry)
  return #(buckets["All"] or {}) > 0
end

local function applyFilter()
  for i = #displayData, 1, -1 do
    displayData[i] = nil
  end

  local source = buckets[currentCategory] or {}
  if searchText == "" then
    for _, v in ipairs(source) do displayData[#displayData + 1] = v end
   else
    local lowerSource = lowerBuckets[currentCategory]
    if not lowerSource then
      lowerSource = {}
      for i, v in ipairs(source) do lowerSource[i] = v:lower() end
      lowerBuckets[currentCategory] = lowerSource
    end
    local kw = searchText:lower()
    for i, lower in ipairs(lowerSource) do
      if lower:find(kw, 1, true) then
        displayData[#displayData + 1] = source[i]
      end
    end
  end

  if apiAdapter then
    apiAdapter.notifyDataSetChanged()
  end
end

local function copyToClipboard(text)
  local ok = pcall(function()
    local cm = activity.getSystemService(Context.CLIPBOARD_SERVICE)
    cm.setPrimaryClip(ClipData.newPlainText("path", text))
  end)
  MyToast.short(ok and "已复制" or "复制失败")
end

local function initAdapter()
  apiAdapter = LuaRecyclerAdapter(displayData, "layout.item.api", {
    onBindViewHolder = function(holder, pos, views, full)
      local simple = full:match("([^%.%$]+)$") or full
      views.title.setText(simple)
      views.content.setText(full)
      views.card.onClick = function(v)
        MyActivity.new("parsing", { full })
      end
      views.card.onLongClick = function(v)
        copyToClipboard(full)
        return true
      end
    end,
  })
  listView.setAdapter(apiAdapter)
end

local debounceRunnable = nil

mySearchEdit.addTextChangedListener({
  afterTextChanged = function(s)
    searchText = tostring(s or "")
    if debounceRunnable then
      pcall(function() mySearchEdit.removeCallbacks(debounceRunnable) end)
    end
    debounceRunnable = luajava.createProxy("java.lang.Runnable", {
      run = function()
        debounceRunnable = nil
        if not isAlive() then return end
        pcall(applyFilter)
      end,
    })
    pcall(function() mySearchEdit.postDelayed(debounceRunnable, 300) end)
  end,
})

local restoring = true

local function buildTabs()
  myTabLayout.removeAllTabs()
  for _, name in ipairs(categoryOrder) do
    local count = #(buckets[name] or {})
    local text = string.format("%s(%d)", name, count)
    myTabLayout.addTab(myTabLayout.newTab().setText(text))
  end
  pcall(function() myTabLayout.clearOnTabSelectedListeners() end)
  myTabLayout.addOnTabSelectedListener({
    onTabSelected = function(tab)
      if restoring then return end
      local name = categoryOrder[tab.getPosition() + 1]
      if name then
        currentCategory = name
        pcall(applyFilter)
      end
    end,
    onTabUnselected = function(tab) end,
    onTabReselected = function(tab) end,
  })
end

loadData()
buildTabs()
initAdapter()
currentCategory = "All"
if myTabLayout.getTabCount() > 0 then
  local t0 = myTabLayout.getTabAt(0)
  if t0 then pcall(function() t0.select() end) end
end
restoring = false
applyFilter()

function onOptionsItemSelected(item)
  if item.getItemId() == android.R.id.home then
    activity.finish()
    return true
  end
  return false
end

function onDestroy()
  destroyed = true
  pcall(function()
    if mySearchEdit and debounceRunnable then
      mySearchEdit.removeCallbacks(debounceRunnable)
    end
  end)
  pcall(function()
    if listView then listView.setAdapter(nil) end
  end)
  buckets = nil
  lowerBuckets = nil
  displayData = nil
  collectgarbage("collect")
  collectgarbage("step")
end

local onThemeResume = makeThemeResumeChecker()
function onResume()
  onThemeResume()
end
