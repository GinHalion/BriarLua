require "env"
local onThemeResume = makeThemeResumeChecker()
local bindClass = luajava.bindClass
local ProjectPage = require "page.home.Project"
local IconDrawable = require "utils.IconDrawable"
local WindowManager = bindClass "android.view.WindowManager"

setStatus()
applyTheme()

activity
.setContentView(loadlayout("layout.home"))
.setSupportActionBar(myToolbar)
.getSupportActionBar()

activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

MyToast.setActivity(activity)

ProjectPage.init()

function onCreateOptionsMenu(menu)
  menu.add("API").setShowAsAction(2).setIcon(IconDrawable("api", MyColor.colorOnSurfaceVariant)).onMenuItemClick = function()
    MyActivity.new("api")
  end
  menu.add("日志").setShowAsAction(2).setIcon(IconDrawable("bug", MyColor.colorOnSurfaceVariant)).onMenuItemClick = function()
    MyActivity.new("log")
  end
  menu.add("导入项目").onMenuItemClick = function()
    ProjectPage.importProject()
  end
  menu.add("软件设置").onMenuItemClick = function()
    MyActivity.new("setting")
  end
  menu.add("退出软件").onMenuItemClick = function()
    activity.finish()
  end
end

myFab.onClick = function(view)
  ProjectPage.newProject()
end

function onResume()
  onThemeResume()
  pcall(function() ProjectPage.refresh() end)
end

function onDestroy()
  pcall(function()
    if myProjectRecyclerView then
      myProjectRecyclerView.setAdapter(nil)
    end
  end)
  ProjectPage.initialized = false
  ProjectPage.adapter = nil
  ProjectPage.data = {}
  ProjectPage._emptyShown = nil
  pcall(function()
    if myNoProjectImage then myNoProjectImage.setImageDrawable(nil) end
  end)
end
