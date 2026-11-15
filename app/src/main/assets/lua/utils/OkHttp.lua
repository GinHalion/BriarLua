local _M = {}
local bindClass = luajava.bindClass
local TimeUnit = bindClass "java.util.concurrent.TimeUnit"
local Dispatcher = bindClass "okhttp3.Dispatcher"
local RequestBody = bindClass "okhttp3.RequestBody"
local MediaType = bindClass "okhttp3.MediaType"
local OkHttpClient = bindClass "okhttp3.OkHttpClient"
local Callback = bindClass "okhttp3.Callback"
local MultipartBody = bindClass "okhttp3.MultipartBody"
local Request = bindClass "okhttp3.Request"
local File = bindClass "java.io.File"
local FileOutputStream = bindClass "java.io.FileOutputStream"
local Runnable = bindClass "java.lang.Runnable"
local System = bindClass "java.lang.System"
local cjson = require "cjson"
local ProgressMaterialAlertDialog = require "dialogs.ProgressMaterialAlertDialog"

local pendingCalls = {}
local requestIdCounter = 0

local function uiAlive()
  local ok, finishing = pcall(function() return activity.isFinishing() end)
  if ok and finishing then return false end
  local ok2, destroyedFlag = pcall(function() return activity.isDestroyed() end)
  if ok2 and destroyedFlag then return false end
  return true
end

local function postToUi(fn)
  activity.runOnUiThread(Runnable {
    run = function()
      if uiAlive() then pcall(fn) end
    end
  })
end

function _M.print(str)
  if pcall(_M.decode, str) then
    print(dump(_M.decode(str)))
  else
    print(str)
  end
end

if not _M.client then
  _M.client = OkHttpClient.Builder()
  .connectTimeout(10, TimeUnit.SECONDS)
  .readTimeout(30, TimeUnit.SECONDS)
  .writeTimeout(30, TimeUnit.SECONDS)
  .dispatcher(Dispatcher().setMaxRequests(5))
  .dispatcher(Dispatcher().setMaxRequestsPerHost(2))
  .build()
end

_M.decode = cjson.decode
_M.encode = cjson.encode
_M.cecode = cjson.encode

function get(url, header, followRedirects, callback)
  header = header or {}
  followRedirects = followRedirects == nil and true or followRedirects
  callback = callback or nil

  local clientBuilder = _M.client.newBuilder()
  clientBuilder.followRedirects(followRedirects)
  local client = clientBuilder.build()
  local requestBuilder = Request.Builder().url(url).get()

  if not header["User-Agent"] then
    header["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
  end

  for key, value in pairs(header) do
    requestBuilder.header(key, value)
  end

  local request = requestBuilder.build()
  local call = client.newCall(request)

  requestIdCounter = requestIdCounter + 1
  local currentRequestId = requestIdCounter
  pendingCalls[currentRequestId] = call
  local startNs = System.nanoTime()

  call.enqueue(Callback{
    onFailure = function(call, e)
      pendingCalls[currentRequestId] = nil
      local ms = math.floor((System.nanoTime() - startNs) / 1e6)
      postToUi(function()
        if dialog_code == true then
          dialog_okhttp.dismiss()
        end
        callback(-1, e, ms)
      end)
    end,
    onResponse = function(call, response)
      pendingCalls[currentRequestId] = nil
      local ms = math.floor((System.nanoTime() - startNs) / 1e6)
      local code = response.code()
      local content = response.body().string()
      local headers = luajava.astable(response.headers())
      postToUi(function()
        if dialog_code == true then
          dialog_okhttp.dismiss()
        end
        callback(code, content, ms)
      end)
    end
  })
end

local function post(url, postParams, header, callback)
  local postParams = postParams or {}
  local header = header or {}
  local callback = callback or nil
  local json = cjson.encode(postParams)
  local requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json)
  local requestBuilder = Request.Builder().url(url).post(requestBody)

  for key, value in pairs(header) do
    requestBuilder.header(key, value)
  end

  local request = requestBuilder.build()
  local call = _M.client.newCall(request)

  requestIdCounter = requestIdCounter + 1
  local currentRequestId = requestIdCounter
  pendingCalls[currentRequestId] = call
  local startNs = System.nanoTime()

  call.enqueue(Callback {
    onFailure = function(call, e)
      pendingCalls[currentRequestId] = nil
      local ms = math.floor((System.nanoTime() - startNs) / 1e6)
      postToUi(function()
        if dialog_code == true then
          dialog_okhttp.dismiss()
        end
        callback(404, e, ms)
      end)
    end,

    onResponse = function(call, response)
      pendingCalls[currentRequestId] = nil
      local ms = math.floor((System.nanoTime() - startNs) / 1e6)
      local code = response.code()
      local content = response.body().string()
      local headers = luajava.astable(response.headers())
      postToUi(function()
        if dialog_code == true then
          dialog_okhttp.dismiss()
        end
        callback(code, content, ms)
      end)
    end
  })
end

local function addFileToRequestBody(fieldName, filePath, builder)
  local imageFile = File(filePath)
  if imageFile.exists() then
    local mediaType = MediaType.parse("*/*")
    local fileBody = RequestBody.create(mediaType, imageFile)
    builder.addFormDataPart(fieldName, imageFile.getName(), fileBody)
  end
end

local function upload(url, postParams, files, header, callback)
  local requestBodyBuilder = MultipartBody.Builder()
  requestBodyBuilder.setType(MultipartBody.FORM)

  if postParams and next(postParams) ~= nil then
    for key, value in pairs(postParams) do
      requestBodyBuilder.addFormDataPart(key, tostring(value))
    end
  end

  local function processFiles(fieldName, fileData, builder)
    if type(fileData) == "string" then
      addFileToRequestBody(fieldName, fileData, builder)
    elseif type(fileData) == "table" then
      for _, item in ipairs(fileData) do
        if type(item) == "string" then
          addFileToRequestBody(fieldName, item, builder)
        elseif type(item) == "table" then
          processFiles(fieldName, item, builder)
        end
      end
    end
  end

  if files and next(files) ~= nil then
    for fieldName, fileData in pairs(files) do
      processFiles(fieldName, fileData, requestBodyBuilder)
    end
  end

  local requestBody = requestBodyBuilder.build()

  local requestBuilder = Request.Builder()
  requestBuilder.url(url)

  if header and next(header) ~= nil then
    for key, value in pairs(header) do
      requestBuilder.addHeader(key, tostring(value))
    end
  end

  requestBuilder.post(requestBody)
  local request = requestBuilder.build()

  local client = OkHttpClient()
  local call = client.newCall(request)

  requestIdCounter = requestIdCounter + 1
  local currentRequestId = requestIdCounter
  pendingCalls[currentRequestId] = call
  local startNs = System.nanoTime()

  call.enqueue(Callback {
    onFailure = function(call, e)
      pendingCalls[currentRequestId] = nil
      local ms = math.floor((System.nanoTime() - startNs) / 1e6)
      postToUi(function()
        if dialog_code2 == true then
          dialog_okhttp2.dismiss()
        end
        callback(404, e.toString(), ms)
      end)
    end,

    onResponse = function(call, response)
      pendingCalls[currentRequestId] = nil
      local ms = math.floor((System.nanoTime() - startNs) / 1e6)
      local code = response.code()
      local content = response.body().string()
      postToUi(function()
        if dialog_code2 == true then
          dialog_okhttp2.dismiss()
        end
        callback(code, content, ms)
      end)
    end
  })
end

function _M.post(code, url, data, header, callback)
  dialog_code = code
  if code == true then
    dialog_okhttp = ProgressMaterialAlertDialog(activity).show()
  end
  post(url, data, header, callback)
end

function _M.upload(code, url, data, file, header, callback)
  dialog_code2 = code
  if code == true then
    dialog_okhttp2 = ProgressMaterialAlertDialog(activity).show()
  end
  upload(url, data, file, header, callback)
end

function _M.get(code, url, header, followRedirects, callback)
  dialog_code = code
  if code == true then
    dialog_okhttp = ProgressMaterialAlertDialog(activity).show()
  end
  get(url, header, followRedirects, callback)
end

function _M.cancelAllRequests()
  for requestId, call in pairs(pendingCalls) do
    if not call.isCanceled() then
      call.cancel()
    end
    pendingCalls[requestId] = nil
  end
  requestIdCounter = 0
end

function _M.cleanupDialogs()
  if dialog_okhttp and dialog_okhttp.isShowing() then
    dialog_okhttp.dismiss()
    dialog_okhttp = nil
  end
  if dialog_okhttp2 and dialog_okhttp2.isShowing() then
    dialog_okhttp2.dismiss()
    dialog_okhttp2 = nil
  end
end

return _M