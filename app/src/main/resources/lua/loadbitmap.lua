local BitmapFactory = luajava.bindClass "android.graphics.BitmapFactory"

local cache = setmetatable({}, { __mode = "v" })

local function fromFile(path)
  local bmp = cache[path]
  if bmp then return bmp end
  bmp = BitmapFactory.decodeFile(path)
  if bmp then cache[path] = bmp end
  return bmp
end

local function fromUrl(url)
  local bmp = cache[url]
  if bmp then return bmp end
  local conn = luajava.newInstance("java.net.URL", url):openConnection()
  conn:setConnectTimeout(10000)
  conn:setReadTimeout(15000)
  conn:connect()
  local stream = conn:getInputStream()
  bmp = BitmapFactory.decodeStream(stream)
  pcall(function() stream:close() end)
  if bmp then cache[url] = bmp end
  return bmp
end

local function loadbitmap(path)
  if path:find("^https*://") then
    return fromUrl(path)
  elseif not path:find("^/") then
    return fromFile(string.format("%s/%s", luajava.luadir, path))
  else
    return fromFile(path)
  end
end

return loadbitmap