local bindClass = luajava.bindClass
local LuaCustRecyclerHolder = bindClass "com.lua.custrecycleradapter.LuaCustRecyclerHolder"
local LuaCustRecyclerAdapter = bindClass "com.lua.custrecycleradapter.LuaCustRecyclerAdapter"
local AdapterCreator = bindClass "com.lua.custrecycleradapter.AdapterCreator"
local loadlayout = require "loadlayout"

function LuaRecyclerAdapter(data, list_item, method)
  return LuaCustRecyclerAdapter(AdapterCreator({
    getItemCount = function()
      return #data
    end,

    getItemViewType = function(position)
      return 0
    end,

    onCreateViewHolder = function(parent, viewType)
      local views = {}
      local view = loadlayout(list_item, views)
      if not view then
        error("LuaRecyclerAdapter: loadlayout failed for " .. tostring(list_item))
      end
      local holder = LuaCustRecyclerHolder(view)
      holder.view.setTag(views)
      if method.setViews then
        method.setViews(views, viewType)
      end
      return holder
    end,

    onBindViewHolder = function(holder, position)
      if method.onBindViewHolder then
        local views = holder.view.getTag()
        if views then
          method.onBindViewHolder(holder, position, views, data[position + 1])
        end
      end
    end
  }))
end

return LuaRecyclerAdapter