local _M = {}
local bindClass = luajava.bindClass

local Glide = bindClass "com.bumptech.glide.Glide"
local DrawableTransitionOptions = bindClass "com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions"
local DiskCacheStrategy = bindClass "com.bumptech.glide.load.engine.DiskCacheStrategy"
local RequestOptions = bindClass "com.bumptech.glide.request.RequestOptions"
local GlideUrl = bindClass "com.bumptech.glide.load.model.GlideUrl"
local LazyHeaders = bindClass "com.bumptech.glide.load.model.LazyHeaders"

local DEFAULT_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1"

local function buildGlideUrl(path, headers)
  local builder = LazyHeaders.Builder()

  local ua = (headers and headers["User-Agent"]) or DEFAULT_UA
  builder.addHeader("User-Agent", ua)

  if headers then
    for k, v in pairs(headers) do
      if k ~= "User-Agent" then
        builder.addHeader(k, v)
      end
    end
  end

  return GlideUrl(path, builder.build())
end

local function isNetworkUrl(path)
  if type(path) ~= "string" then return false end
  return path:match("^https?://") ~= nil or path:match("^//") ~= nil
end

local function loadWithGlide(path, view, errorDrawable, headers)
  local requestOptions = RequestOptions()
  .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)

  if errorDrawable then
    requestOptions = requestOptions.error(errorDrawable)
  end

  local loadTarget
  if isNetworkUrl(path) then
    loadTarget = buildGlideUrl(path, headers)
   else
    loadTarget = path
  end

  Glide.with(activity)
  .load(loadTarget)
  .transition(DrawableTransitionOptions.withCrossFade())
  .apply(requestOptions)
  .into(view)
end

function _M.set(path, view, error, headers)
  loadWithGlide(path, view, error, headers)
  return _M
end

function _M.setFile(path, view, error, signatureKey)
  local requestOptions = RequestOptions()
  .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)

  if signatureKey ~= nil then
    local ObjectKey = bindClass "com.bumptech.glide.signature.ObjectKey"
    requestOptions = requestOptions.signature(ObjectKey(tostring(signatureKey)))
  end

  if error then
    requestOptions = requestOptions.error(error)
  end

  Glide.with(activity)
  .load(path)
  .transition(DrawableTransitionOptions.withCrossFade())
  .apply(requestOptions)
  .into(view)

  return _M
end

function _M.setNoCache(path, view, error, headers)
  local requestOptions = RequestOptions()
  .diskCacheStrategy(DiskCacheStrategy.NONE)
  .skipMemoryCache(true)

  if error then
    requestOptions = requestOptions.error(error)
  end

  local loadTarget
  if isNetworkUrl(path) then
    loadTarget = buildGlideUrl(path, headers)
  else
    loadTarget = path
  end

  Glide.with(activity)
  .load(loadTarget)
  .transition(DrawableTransitionOptions.withCrossFade())
  .apply(requestOptions)
  .into(view)

  return _M
end

function _M.loadDrawable(path, size, onReady, onFail, headers)
  local CustomTarget = bindClass "com.bumptech.glide.request.target.CustomTarget"
  local BitmapDrawable = bindClass "android.graphics.drawable.BitmapDrawable"
  local RequestOptions = bindClass "com.bumptech.glide.request.RequestOptions"

  local loadTarget
  if isNetworkUrl(path) then
    loadTarget = buildGlideUrl(path, headers)
   else
    loadTarget = path
  end

  local options = RequestOptions()
  .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
  .circleCrop()

  if size and size > 0 then
    options = options.override(size, size)
  end

  local function stillAlive()
    local ok, finishing = pcall(function() return activity.isFinishing() end)
    if ok and finishing then return false end
    local ok2, destroyedFlag = pcall(function() return activity.isDestroyed() end)
    if ok2 and destroyedFlag then return false end
    return true
  end

  pcall(function()
    Glide.with(activity)
    .asBitmap()
    .load(loadTarget)
    .apply(options)
    .into(CustomTarget({
      onResourceReady = function(bitmap, transition)
        activity.runOnUiThread(function()
          if stillAlive() and onReady then
            onReady(BitmapDrawable(activity.getResources(), bitmap))
          end
        end)
      end,
      onLoadFailed = function(e)
        activity.runOnUiThread(function()
          if stillAlive() and onFail then onFail() end
        end)
      end,
      onLoadCleared = function(placeholder) end,
    }))
  end)
  return _M
end

function _M.clear()
  Glide.get(activity).clearMemory()
  return _M
end

function _M.clearAll()
  Glide.get(activity).clearMemory()
  local glide = Glide.get(activity)
  local Thread = luajava.bindClass "java.lang.Thread"
  Thread(Runnable({
    run = function()
      pcall(function() glide.clearDiskCache() end)
    end
  })).start()
  return _M
end

return _M