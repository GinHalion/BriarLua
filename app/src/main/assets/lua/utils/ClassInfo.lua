local _M = {}
local bindClass = luajava.bindClass
local Modifier = bindClass "java.lang.reflect.Modifier"
local ClassRegistry = require "utils.ClassRegistry"

function _M.resolveClassName(name)
  return ClassRegistry.resolve(name)
end

function _M.loadClassInfo(fullName)
  local ok, cls = pcall(luajava.bindClass, fullName)
  if not ok or not cls then return nil end

  local info = {
    fullName = fullName,
    simpleName = cls.getSimpleName(),
    modifiers = Modifier.toString(cls.getModifiers()),
    constructors = {},
    methods = {},
    fields = {},
    superClass = nil,
    innerClasses = {}
  }

  local okSuper, superCls = pcall(function() return cls.getSuperclass() end)
  if okSuper and superCls then
    local okName, superName = pcall(function() return superCls.getName() end)
    if okName then info.superClass = superName end
  end

  local okInner, innerClasses = pcall(function() return cls.getDeclaredClasses() end)
  if okInner and innerClasses then
    for _, c in ipairs(luajava.astable(innerClasses)) do
      info.innerClasses[#info.innerClasses + 1] = {
        name = c.getSimpleName(),
        fullName = c.getName(),
        modifiers = Modifier.toString(c.getModifiers())
      }
    end
  end

  for _, ctor in ipairs(luajava.astable(cls.getDeclaredConstructors())) do
    local params = {}
    for _, p in ipairs(luajava.astable(ctor.getParameterTypes())) do
      params[#params + 1] = p.getSimpleName()
    end
    info.constructors[#info.constructors + 1] = {
      name = info.simpleName,
      params = params,
      modifiers = Modifier.toString(ctor.getModifiers())
    }
  end

  for _, m in ipairs(luajava.astable(cls.getDeclaredMethods())) do
    local mname = m.getName()
    if not mname:find("%$") then
      local params = {}
      for _, p in ipairs(luajava.astable(m.getParameterTypes())) do
        params[#params + 1] = p.getSimpleName()
      end
      info.methods[#info.methods + 1] = {
        name = mname,
        params = params,
        returnType = m.getReturnType().getSimpleName(),
        modifiers = Modifier.toString(m.getModifiers())
      }
    end
  end

  for _, f in ipairs(luajava.astable(cls.getDeclaredFields())) do
    if not f.getName():find("%$") then
      info.fields[#info.fields + 1] = {
        name = f.getName(),
        type = f.getType().getSimpleName(),
        modifiers = Modifier.toString(f.getModifiers())
      }
    end
  end

  return info
end

return _M