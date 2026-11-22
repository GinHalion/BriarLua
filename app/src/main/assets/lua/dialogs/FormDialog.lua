local _M = {}
local bindClass = luajava.bindClass

local MaterialAlertDialogBuilder = bindClass "com.google.android.material.dialog.MaterialAlertDialogBuilder"
local TextInputLayout = bindClass "com.google.android.material.textfield.TextInputLayout"
local TextInputEditText = bindClass "com.google.android.material.textfield.TextInputEditText"
local LinearLayout = bindClass "android.widget.LinearLayout"
local LinearLayoutLayoutParams = bindClass "android.widget.LinearLayout$LayoutParams"
local ColorStateList = bindClass "android.content.res.ColorStateList"
local Typeface = bindClass "android.graphics.Typeface"

local function dp2px(context, v)
  local dm = context.getResources().getDisplayMetrics()
  return math.floor(v * dm.density + 0.5)
end

local function trim(s)
  return tostring(s):gsub("^%s+", ""):gsub("%s+$", "")
end

local function resolveButton(spec, defaultText, defaultHandler)
  if type(spec) == "table" then
    return spec.text or defaultText, spec.onClick or defaultHandler
  end
  return spec or defaultText, defaultHandler
end

function _M.show(opts)
  opts = opts or {}
  local context = opts.context or activity
  local fields = opts.fields or {}
  local title = opts.title or "输入"
  local strokeColor = opts.strokeColor

  local container = LinearLayout(context)
  container.setOrientation(LinearLayout.VERTICAL)
  local pad = dp2px(context, 20)
  container.setPadding(pad, math.floor(pad / 2), pad, 0)

  local edits = {}
  for i, f in ipairs(fields) do
    local til = TextInputLayout(context)
    til.setHint(f.hint or f.key)
    til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE)
    til.setBoxCornerRadii(dp2px(context, 8), dp2px(context, 8), dp2px(context, 8), dp2px(context, 8))
    if strokeColor ~= nil then
      til.setBoxStrokeColor(strokeColor)
      til.setHintTextColor(ColorStateList.valueOf(strokeColor))
    end

    local et = TextInputEditText(context)
    et.setText(f.initial or "")
    if f.multiline then
      et.setSingleLine(false)
      et.setMinLines(f.minLines or 8)
      et.setGravity(0x30)
      et.setTypeface(Typeface.MONOSPACE)
     else
      et.setSelection(et.getText().length())
    end
    til.addView(et)

    local lp = LinearLayoutLayoutParams(-1, -2)
    lp.topMargin = dp2px(context, i == 1 and 0 or 8)
    container.addView(til, lp)
    edits[f.key] = et
  end

  local builder = MaterialAlertDialogBuilder(context)
  builder.setTitle(title)
  builder.setView(container)

  local positiveText, positiveHandler = resolveButton(opts.positive, "确定", function(values)
    if opts.onConfirm then opts.onConfirm(values) end
  end)
  local negativeText, negativeHandler = resolveButton(opts.negative, "取消", function()
    if opts.onCancel then opts.onCancel() end
  end)

  builder.setPositiveButton(positiveText, {
    onClick = function()
      local values = {}
      for _, f in ipairs(fields) do
        if f.multiline then
          values[f.key] = tostring(edits[f.key].getText())
         else
          values[f.key] = trim(edits[f.key].getText())
        end
      end
      if positiveHandler then positiveHandler(values) end
    end
  })
  builder.setNegativeButton(negativeText, {
    onClick = function()
      if negativeHandler then negativeHandler() end
    end
  })

  builder.show()
end

function _M.input(opts)
  opts = opts or {}
  local positive = opts.positive
  local onConfirm = opts.onConfirm

  local positiveSpec
  if type(positive) == "table" then
    local userOnClick = positive.onClick
    positiveSpec = {
      text = positive.text or "确定",
      onClick = function(values)
        if userOnClick then userOnClick(values.input) end
      end
    }
  else
    positiveSpec = {
      text = positive or "确定",
      onClick = function(values)
        if onConfirm then onConfirm(values.input) end
      end
    }
  end

  local negative = opts.negative
  local onCancel = opts.onCancel

  local negativeSpec
  if type(negative) == "table" then
    negativeSpec = negative
  else
    negativeSpec = {
      text = negative or "取消",
      onClick = function()
        if onCancel then onCancel() end
      end
    }
  end

  _M.show({
    context = opts.context,
    title = opts.title,
    strokeColor = opts.strokeColor,
    fields = {
      {
        key = "input",
        hint = opts.hint or "请输入",
        initial = opts.initial or "",
        multiline = opts.multiline,
        minLines = opts.minLines,
      },
    },
    positive = positiveSpec,
    negative = negativeSpec,
  })
end

return _M