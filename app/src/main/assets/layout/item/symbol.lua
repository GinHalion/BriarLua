local AppCompatTextView = luajava.bindClass "androidx.appcompat.widget.AppCompatTextView"

return {
  AppCompatTextView,
  layout_height = "45dp",
  gravity = "center",
  paddingRight = "16dp",
  paddingLeft = "16dp",
  textColor = MyColor.colorOnBackground,
  singleLine = true,
  id = "symbol",
}