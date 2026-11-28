require "env"

local welcome = not(activity.getSharedData("BriarWelcome"))

if welcome then
  MyActivity.new("welcome")
 else
  MyActivity.new("home")
end

activity.finish()