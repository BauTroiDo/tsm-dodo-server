GAEventAction = {
  ClickButton = "點擊按鈕",
  BuyItem = "購買物品",
}

local init = false;
local GAFunction = {};

GAFunction["Initialize"] = function()
  init = FirebaseAnalyticsSDK.Initialize();
end

GAFunction["SetUserId"] = function(userId)
  if userId == nil then logError("FirebaseAnalytics.SetUserId userId nil.") return end

  FirebaseAnalyticsSDK.SetUserId(userId);
end

GAFunction["SetCurrentScreen"] = function(name)
  if name == nil then logError("FirebaseAnalytics.LogScreen name nil.") return end

  FirebaseAnalyticsSDK.SetCurrentScreen(name);
end

GAFunction["LogEvent"] = function(eventName, paramName, paramValue)
  if eventName == nil then logError("FirebaseAnalytics.LogEvent name nil.") return end

  FirebaseAnalyticsSDK.LogEvent(eventName, paramName, paramValue);
end

local function Call(funName, ...)
  if funName == nil or GAFunction[funName] == nil then return end
  
  GAFunction[funName](...);
end

GoogleAnalytics = {}
local this = GoogleAnalytics;

function GoogleAnalytics.GameStart()
  Call("Initialize");
end

function GoogleAnalytics.ViewUI(name)
  if not init or Define.IsDebugMode() then return end
  Call("SetCurrentScreen", name);
end

function GoogleAnalytics.Login(id)
  if not init or Define.IsDebugMode() then return end
  Call("SetUserId", id);
  Call("LogEvent", FirebaseAnalyticsSDK.EventLogin);
end

function GoogleAnalytics.UISell(name)
  if not init or Define.IsDebugMode() then return end
  Call("LogEvent", FirebaseAnalyticsSDK.EventSpendVirtualCurrency, FirebaseAnalyticsSDK.ParamItemName, name);
end

function GoogleAnalytics.StartDownload(name)
  if not init or Define.IsDebugMode() then return end
  Call("LogEvent", name);
end

function GoogleAnalytics.DownloadDone(name)
  if not init or Define.IsDebugMode() then return end
  Call("LogEvent", name);
end