PluginManager = {}
local this = PluginManager;

local EPluginNotify = {
  GameStart = 1,
  Login = 2,
  ViewUI = 3,
  SetLevel = 4,
  StoreIAP = 5,
  QuitGame = 6,
  UISell = 7,
  StartDownLoadResource = 8, 
  DownLoadResourceDone = 9,
  SetServer = 10, 
};

local notify = {
  [EPluginNotify.GameStart] = {};
  [EPluginNotify.Login] = {};
  [EPluginNotify.SetServer] = {};
  [EPluginNotify.ViewUI] = {};
  [EPluginNotify.SetLevel] = {};
  [EPluginNotify.StoreIAP] = {};
  [EPluginNotify.QuitGame] = {};
  [EPluginNotify.UISell] = {};
  [EPluginNotify.StartDownLoadResource] = {};
  [EPluginNotify.DownLoadResourceDone] = {};
};

local init = false;

function PluginManager.Init()
  if init then return end

  if LuaHelper.CheckDefine("VN") then
    PluginManager.RegisterTW();
  end

  init = true;
end

function PluginManager.GameStart()
  for k, startFunction in pairs(notify[EPluginNotify.GameStart]) do
    startFunction();
  end
end

function PluginManager.Login(id)
  for k, loginFunction in pairs(notify[EPluginNotify.Login]) do
    loginFunction(id);
  end
end

function PluginManager.SetServer(serverId)
  for k, loginFunction in pairs(notify[EPluginNotify.SetServer]) do
    loginFunction(serverId);
  end
end

function PluginManager.ViewUI(name)
  for k, viewFunction in pairs(notify[EPluginNotify.ViewUI]) do
    viewFunction(name);
  end
end

function PluginManager.SetLevel(level)
  -- 玩家轉生，每一轉多200級
  local lv = Role.player:GetAttribute(EAttribute.Turn) * Role.playerMaxLv + level;

  for k, setFunction in pairs(notify[EPluginNotify.SetLevel]) do
    setFunction(lv);
  end
end

function PluginManager.StoreIAP(orderId, productId)
  local index = PluginManager.GetProductIndex(productId);

  local data = StoreValue.GetData(index);

  if data == nil then return end

  for k, IAPFunction in pairs(notify[EPluginNotify.StoreIAP]) do
    if data.flag ~= 0 and not BitFlag.Get(data.flag) then
      IAPFunction(orderId, productId, data.price, EurrencyType.TWD, (data.totalGold + data.gold), this.GetPaymentType());
    else
      IAPFunction(orderId, productId, data.price, EurrencyType.TWD, data.totalGold, this.GetPaymentType());
    end
  end
end

function PluginManager.GetProductIndex(productName)
  for k,v in pairs(IAPData.GetProductName()) do
    if productName == v then
      return k;
    end
  end

  return 0;
end

function PluginManager.QuitGame()
  for k, quitFunction in pairs(notify[EPluginNotify.QuitGame]) do
    quitFunction();
  end
end

function PluginManager.UISell(name)
  for k, func in pairs(notify[EPluginNotify.UISell]) do
    func(name);
  end
end

function PluginManager.StartDownload(name)
  for k, func in pairs(notify[EPluginNotify.StartDownLoadResource]) do
    func(name);
  end
end

function PluginManager.DownloadDone(name)
  for k, func in pairs(notify[EPluginNotify.DownLoadResourceDone]) do
    func(name);
  end
end

function PluginManager.GetPaymentType()
  if LuaHelper.IsAndroidPlatform then
    return EPaymenyType.GoogleIAP;
  elseif LuaHelper.IsApplePlatform then
    return EPaymenyType.IOSIAP;
  end

  return "";
end

function PluginManager.ReciveMessageToken(token)
  log(string.Concat("MessageToken : ", token));
end

function PluginManager.RegisterRemoteMessage()
  if LuaHelper.IsAndroidPlatform or LuaHelper.IsApplePlatform then
    LuaHelper.GetFirebaseMessagingToken(PluginManager.ReciveMessageToken);
  end
end

local registerTW = {};

registerTW[EPluginNotify.GameStart] = function ()
  local notifyReference = notify[EPluginNotify.GameStart];
end

registerTW[EPluginNotify.Login] = function ()
  local notifyReference = notify[EPluginNotify.Login];
end

registerTW[EPluginNotify.SetServer] = function ()
  local notifyReference = notify[EPluginNotify.SetServer];
end

registerTW[EPluginNotify.ViewUI] = function ()
  local notifyReference = notify[EPluginNotify.ViewUI];
end

registerTW[EPluginNotify.SetLevel] = function ()
  local notifyReference = notify[EPluginNotify.SetLevel];
end

registerTW[EPluginNotify.StoreIAP] = function ()
  local notifyReference = notify[EPluginNotify.StoreIAP];
end

registerTW[EPluginNotify.QuitGame] = function ()
  local notifyReference = notify[EPluginNotify.QuitGame];
end

registerTW[EPluginNotify.UISell] = function ()
  local notifyReference = notify[EPluginNotify.UISell];
end

registerTW[EPluginNotify.StartDownLoadResource] = function ()
  local notifyReference = notify[EPluginNotify.StartDownLoadResource];
end

registerTW[EPluginNotify.DownLoadResourceDone] = function ()
  local notifyReference = notify[EPluginNotify.DownLoadResourceDone];
end

function PluginManager.RegisterTW()
  for k, v in pairs(EPluginNotify) do
    if registerTW[v] ~= nil then
      registerTW[v]();
    end
  end
end