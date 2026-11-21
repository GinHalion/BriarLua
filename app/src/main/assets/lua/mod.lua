local bindClass = luajava.bindClass
local Build = bindClass "android.os.Build"
local EdgeToEdge = bindClass "androidx.activity.EdgeToEdge"
local Configuration = bindClass "android.content.res.Configuration"
local AppCompatDelegate = bindClass "androidx.appcompat.app.AppCompatDelegate"
local DynamicColors = bindClass "com.google.android.material.color.DynamicColors"
local LayoutTransition = bindClass "android.animation.LayoutTransition"
local Uri = bindClass "android.net.Uri"
local ContentUris = bindClass "android.content.ContentUris"
local Environment = bindClass "android.os.Environment"
local DocumentsContract = bindClass "android.provider.DocumentsContract"
local MediaStore = bindClass "android.provider.MediaStore"
local JavaString = bindClass "java.lang.String"
local Context = bindClass "android.content.Context"
local ShareCompat = bindClass "androidx.core.app.ShareCompat"
local Intent = bindClass "android.content.Intent"

local appNameCache = nil

function getAppName()
  if appNameCache then return appNameCache end
  local ok, info = pcall(function()
    return activity.getPackageManager().getApplicationInfo(activity.getPackageName(), 0)
  end)
  if ok and info then
    appNameCache = tostring(activity.getPackageManager().getApplicationLabel(info))
  end
  return appNameCache
end

function openUrl(url)
  local ok = pcall(function()
    local intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    activity.startActivity(intent)
  end)
  return ok
end

function shareText(text)
  ShareCompat.IntentBuilder
  .from(activity)
  .setText(text)
  .setType("text/plain")
  .startChooser()
end

function copyText(content)
  pcall(function()
    activity.getSystemService(Context.CLIPBOARD_SERVICE).setText(content)
  end)
end

function backImage(str)
  return activity.getLuaDir("res/drawable/" .. str .. ".png")
end

function backHtml(str)
  return activity.getLuaDir("res/html/" .. str .. ".html")
end

function newLayoutTransition(code)
  local t = LayoutTransition()
  .enableTransitionType(LayoutTransition.CHANGING)
  .enableTransitionType(LayoutTransition.APPEARING)
  .enableTransitionType(LayoutTransition.DISAPPEARING)
  if code then
    t = t
    .disableTransitionType(LayoutTransition.CHANGE_APPEARING)
    .disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
  end
  return t.setDuration(250)
end

local function isDocUri(uri, authority)
  return uri.authority == authority
end

function uri2path(uri)
  if not uri then return nil end
  local selection, selectionArgs
  if Build.VERSION.SDK_INT >= 19 and DocumentsContract.isDocumentUri(activity, uri) then
    if isDocUri(uri, "com.android.externalstorage.documents") then
      local split = JavaString(DocumentsContract.getDocumentId(uri)).split(":")
      return Environment.externalStorageDirectory.toString() .. "/" .. split[1]
     elseif isDocUri(uri, "com.android.providers.downloads.documents") then
      local split = JavaString(DocumentsContract.getDocumentId(uri)).split(":")
      uri = ContentUris.withAppendedId(
      Uri.parse("content://downloads/public_downloads"),
      Long.valueOf(split[1])
      )
     elseif isDocUri(uri, "com.android.providers.media.documents") then
      local split = JavaString(DocumentsContract.getDocumentId(uri)).split(":")
      local kind = split[0]
      if kind == "image" then
        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
       elseif kind == "video" then
        uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
       elseif kind == "audio" then
        uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
      end
      selection = "_id=?"
      selectionArgs = { split[1] }
    end
  end
  local scheme = uri.scheme
  if JavaString("content").equalsIgnoreCase(scheme) then
    local result
    pcall(function()
      local cursor = activity.contentResolver.query(
      uri, { "_data" }, selection, selectionArgs, nil
      )
      if cursor then
        local idx = cursor.getColumnIndexOrThrow("_data")
        if cursor.moveToFirst() then
          result = cursor.getString(idx)
        end
        cursor.close()
      end
    end)
    return result
   elseif JavaString("file").equalsIgnoreCase(scheme) then
    return uri.path
  end
end

function isDark()
  local mode = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK
  return mode == Configuration.UI_MODE_NIGHT_YES
end

local function configureTheme()
  local themeMode = activity.getSharedData("dark_mode") or "system"
  if themeMode == "dark" then
    return AppCompatDelegate.MODE_NIGHT_YES
   elseif themeMode == "light" then
    return AppCompatDelegate.MODE_NIGHT_NO
   else
    return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
  end
end


function currentThemeName()
  local idx = tonumber(activity.getSharedData("theme_color"))
  return Themes[idx] or Themes[1] or "Blue"
end

function currentThemeLabel()
  local idx = tonumber(activity.getSharedData("theme_color"))
  return ThemeNames[idx] or ThemeNames[1] or "蓝色"
end

function isDynamicColorEnabled()
  return activity.getSharedData("dynamic_color") == true and Build.VERSION.SDK_INT >= 31
end

function applyTheme()
  if isDynamicColorEnabled() then
    activity.setTheme(R.style["Theme_Material3_Dynamic"])
    pcall(function()
      DynamicColors.applyToActivityIfAvailable(activity)
    end)
   else
    activity.setTheme(R.style["Theme_Material3_" .. currentThemeName() .. "_NoActionBar"])
  end
end

function bumpThemeVersion()
  local v = (tonumber(activity.getSharedData("theme_version")) or 0) + 1
  activity.setSharedData("theme_version", v)
  return v
end

function makeThemeResumeChecker()
  local created = tonumber(activity.getSharedData("theme_version")) or 0
  return function()
    local now = tonumber(activity.getSharedData("theme_version")) or 0
    if now > created then
      pcall(function() activity.recreate() end)
      return true
    end
    return false
  end
end

function setStatus()
  EdgeToEdge.enable(this)
  if Build.VERSION.SDK_INT >= 30 then
    activity.getWindow().setNavigationBarContrastEnforced(false)
  end
  AppCompatDelegate.setDefaultNightMode(configureTheme())
end