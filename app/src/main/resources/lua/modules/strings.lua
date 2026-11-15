require "utf8"

local find = utf8.find
local sub = utf8.sub
local insert = table.insert

table.slice = function(t, first, last, step)
  local sliced = {}
  for i = first or 1, last or #t, step or 1 do
    sliced[#sliced + 1] = t[i]
  end
  return sliced
end

string.split = function(str, regex, limit)
  limit = limit or 0
  local index = 1
  local matchLimited = limit > 0
  local matchList = {}

  local start, ends = find(str, regex)
  while start ~= nil do
    repeat
      if (not matchLimited or #matchList < limit) then
        if (index == 1 and index == start and start == ends) then
          start, ends = find(str, regex, ends + 1)
          index = 2
          break
        end
        local match = sub(str, index, start - 1)
        if match ~= "" then
          insert(matchList, match)
        end
        index = ends + 1
      elseif #matchList == limit - 1 then
        local match = sub(str, index, #str)
        insert(matchList, match)
        index = ends + 1
      end
    until true

    if start == nil then break end
    start, ends = find(str, regex, ends + 1)
  end

  if (not matchLimited or #matchList < limit) then
    local match = sub(str, index, #str)
    if match ~= "" then
      insert(matchList, match)
    end
  end

  local resultSize = #matchList
  if limit == 0 then
    while (resultSize > 0 and matchList[resultSize - 1] == "") do
      resultSize = resultSize - 1
    end
  end
  return table.slice(matchList, 1, resultSize)
end