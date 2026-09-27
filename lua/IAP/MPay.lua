MPay = {};
local this = MPay;

local gameNo = 122;
local mtype = "P";

local function GetTimestamp()
  local times = {};
  times.year = CGTimer.serverTime.Year;
  times.month = CGTimer.serverTime.Month;
  times.day = CGTimer.serverTime.Day;
  times.hour = CGTimer.serverTime.Hour;
  times.min = CGTimer.serverTime.Minute;
  times.sec = CGTimer.serverTime.Second;

  return os.time(times);
end

local function GetHashKey()
  if Define.IsInternal() then
    return "12345678"
  elseif LuaHelper.CheckDefine("ReleaseTest") then
    return "12345678"
  else
    return "GJAI#KFNA@"
  end
end

local function GetUid()
  return tonumber(string.sub(Role.playerId, -10));
end

local function GetBaseUrl()
  if Define.IsInternal() then
    return "https://worldstorebilling2.chinesegamer.net/MPAY/";
  elseif LuaHelper.CheckDefine("ReleaseTest") then
    return "https://worldstorebilling1.chinesegamer.net/MPAY/";
  end

  return "https://worldstorebilling.chinesegamer.net/MPAY/";
end

function MPay.Buy(index, tradeType)
  local productList = IAPData.GetMPayProductName();
  local product = productList[index];
  if product == nil then return end

  local roleName = Role.player.name;
  local roleNForHash = LuaHelper.ToBase64String(roleName);

  local roleNameEncoded = string.EncodeURI(roleNForHash);
  local timestamp = GetTimestamp();
  local uid = GetUid();
  local loginId = Network.account;
  local serverId = Network.dbId;
  local flowNo = Network.serverId;
  local itemId = product[2];

  local hashInput = "";
  hashInput = string.format("%s%s%s%s%s%s%s%s%s%s",
    mtype,
    uid,
    serverId,
    flowNo,
    loginId,
    gameNo,
    itemId,
    timestamp,
    roleNForHash,
    GetHashKey()
  );

  local hash = string.lower(LuaHelper.MD5(hashInput));

  local postData = string.format(
    "?serverid=%s&flowno=%s&loginid=%s&uid=%s&rolename=%s&gameno=%s&ttime=%s&mtype=%s&itemid=%s&hash=%s",
    serverId,
    flowNo,
    loginId,
    uid,
    roleNameEncoded,
    gameNo,
    timestamp,
    mtype,
    itemId,
    hash
  )

  local url = string.Concat(GetBaseUrl(), postData);

  Application.OpenURL(url);
end
