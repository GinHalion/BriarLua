local _M = {}

_M.BASE = {
  { title = "tab", content = "\t" },
  { title = "function()", content = "function ()\n\nend" },
  { title = "print()", content = "print()" },
  { title = "(", content = "(" }, { title = ")", content = ")" },
  { title = "[", content = "[" }, { title = "]", content = "]" },
  { title = "{", content = "{" }, { title = "}", content = "}" },
  { title = "\"", content = "\"" }, { title = "=", content = "=" },
  { title = ":", content = ":" }, { title = ".", content = "." },
  { title = ",", content = "," }, { title = ";", content = ";" },
  { title = "_", content = "_" }, { title = "+", content = "+" },
  { title = "-", content = "-" }, { title = "*", content = "*" },
  { title = "/", content = "/" }, { title = "\\", content = "\\" },
  { title = "%", content = "%" }, { title = "#", content = "#" },
  { title = "^", content = "^" }, { title = "$", content = "$" },
  { title = "?", content = "?" }, { title = "&", content = "&" },
  { title = "|", content = "|" }, { title = "<", content = "<" },
  { title = ">", content = ">" }, { title = "~", content = "~" },
  { title = "'", content = "'" },
}


function _M.escape(s)
  s = tostring(s or "")
  s = s:gsub("\\", "\\\\")
  s = s:gsub("\t", "\\t")
  s = s:gsub("\n", "\\n")
  s = s:gsub("|", "\\p")
  return s
end

function _M.unescape(s)
  s = tostring(s or "")
  s = s:gsub("\\p", "|")
  s = s:gsub("\\t", "\t")
  s = s:gsub("\\n", "\n")
  s = s:gsub("\\\\", "\\")
  return s
end

function _M.serialize(items)
  local lines = {}
  for _, it in ipairs(items or {}) do
    local title = _M.escape(it.title or it.content or "")
    local content = _M.escape(it.content or it.title or "")
    if title == content then
      lines[#lines + 1] = title
     else
      lines[#lines + 1] = title .. "|" .. content
    end
  end
  return table.concat(lines, "\n")
end

function _M.parse(text)
  text = tostring(text or "")
  if text:match("^%s*$") then return nil end
  local items = {}
  for line in text:gmatch("[^\r\n]+") do
    line = line:match("^%s*(.-)%s*$")
    if line ~= "" and not line:match("^#") then
      local title, content = line:match("^(.-)%|(.*)$")
      if title then
        title = _M.unescape(title)
        content = _M.unescape(content)
       else
        title = _M.unescape(line)
        content = title
      end
      if title ~= "" then
        items[#items + 1] = { title = title, content = content }
      end
    end
  end
  if #items == 0 then return nil end
  return items
end


function _M.getBase()
  local saved = tostring(activity.getSharedData("symbols_base") or "")
  local items = _M.parse(saved)
  if items then return items end
  local copy = {}
  for i, v in ipairs(_M.BASE) do copy[i] = { title = v.title, content = v.content } end
  return copy
end

function _M.isBaseCustomized()
  local saved = tostring(activity.getSharedData("symbols_base") or "")
  return _M.parse(saved) ~= nil
end

function _M.getCustom()
  local custom = tostring(activity.getSharedData("custom_symbols") or "")
  if custom:match("^%s*$") then return {} end
  local items = {}
  for tok in custom:gmatch("[^,，、;；%s]+") do
    items[#items + 1] = { title = tok, content = tok }
  end
  return items
end

function _M.getEffective()
  local base = _M.getBase()
  local custom = _M.getCustom()
  for _, v in ipairs(custom) do base[#base + 1] = v end
  return base
end

function _M.reset()
  activity.setSharedData("symbols_base", "")
  activity.setSharedData("custom_symbols", "")
end

return _M
