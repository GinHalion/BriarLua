require "env"
local bindClass = luajava.bindClass
local WebViewClient = bindClass "android.webkit.WebViewClient"
local Build = bindClass "android.os.Build"
local Intent = bindClass "android.content.Intent"
local Uri = bindClass "android.net.Uri"

local onThemeResume = makeThemeResumeChecker()

applyTheme()
setStatus()

activity
.setContentView(loadlayout("layout.doc"))
.setSupportActionBar(myToolbar)
.getSupportActionBar()
.setDisplayHomeAsUpEnabled(true)
.setTitle("开发文档")

MyToast.setActivity(activity)

local settings = myDocWebView.getSettings()
settings.setJavaScriptEnabled(true)
settings.setDomStorageEnabled(true)
settings.setAllowFileAccess(true)
settings.setAllowContentAccess(true)
settings.setDefaultTextEncodingName("UTF-8")

local dark = isDark()
myDocWebView.setBackgroundColor(dark and 0xFF18181B or 0xFFFFFFFF)

local docPath = backHtml("Doc")

if docPath and luajava.bindClass("java.io.File")(docPath).exists() then
  myDocWebView.loadUrl("file://" .. docPath .. (dark and "#theme=dark" or ""))
 else
  myDocWebView.loadDataWithBaseURL(nil,
  "<h2>文档未找到</h2>",
  "text/html", "UTF-8", nil)
end

local client = luajava.override(WebViewClient, {
  shouldOverrideUrlLoading = function(super, view, url)
    local u = tostring(url)
    if u:match("^file://") then
      return false
    end
    local ok = pcall(function()
      activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    end)
    return true
  end
})
myDocWebView.setWebViewClient(client)

function onKeyDown(key)
  if key == 4 then
    if myDocWebView and myDocWebView.canGoBack() then
      myDocWebView.goBack()
     else
      activity.finish()
    end
    return true
  end
end

function onResume()
  onThemeResume()
end

function onOptionsItemSelected(item)
  if item.getItemId() == android.R.id.home then
    activity.finish()
    return true
  end
  return false
end

function onDestroy()
  pcall(function()
    if myDocWebView then
      myDocWebView.stopLoading()
      myDocWebView.clearHistory()
      myDocWebView.clearCache(true)
      myDocWebView.destroy()
    end
  end)
  collectgarbage("collect")
  collectgarbage("step")
end