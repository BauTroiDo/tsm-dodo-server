IAPData = {}

--[[
Googel / IOS
]]--
local productName = {};
function IAPData.GetProductName()

  if LuaHelper.CheckDefine("TW") then
    return IAPData.GetTWProduct();
  elseif LuaHelper.CheckDefine("HK") then
    return IAPData.GetHKProduct();
  end

  return {};
end

function IAPData.GetTWProduct()
  local result
  if LuaHelper.IsAndroidPlatform then
    result = {
      "net.chinesegamer.tsm.gold01",
      "net.chinesegamer.tsm.gold02",
      "net.chinesegamer.tsm.gold03",
      "net.chinesegamer.tsm.gold04",
      "net.chinesegamer.tsm.gold05",
      "net.chinesegamer.tsm.gold06",
      "net.chinesegamer.tsm.item01",
      "net.chinesegamer.tsm.item02",
      "net.chinesegamer.tsm.item03",
      "net.chinesegamer.tsm.item04",
      "net.chinesegamer.tsm.item05",
      "net.chinesegamer.tsm.item06",
      "net.chinesegamer.tsm.item07",
      "net.chinesegamer.tsm.item08",
      "net.chinesegamer.tsm.item09",
      "net.chinesegamer.tsm.item10",
      "net.chinesegamer.tsm.item11",
      "net.chinesegamer.tsm.item12",
      "net.chinesegamer.tsm.item13",
      "net.chinesegamer.tsm.item14",
      "net.chinesegamer.tsm.item15",
      "net.chinesegamer.tsm.item16",
      "net.chinesegamer.tsm.item17",
      "net.chinesegamer.tsm.item18",
      "net.chinesegamer.tsm.item19",
      "net.chinesegamer.tsm.item20",
      "net.chinesegamer.tsm.item21",
      "net.chinesegamer.tsm.item22",
      "net.chinesegamer.tsm.item23",
      "net.chinesegamer.tsm.item24",
      "net.chinesegamer.tsm.item25",
      "net.chinesegamer.tsm.item26",
      "net.chinesegamer.tsm.item27",
      "net.chinesegamer.tsm.item28",
      "net.chinesegamer.tsm.item29",
    };
  elseif LuaHelper.IsApplePlatform then
    result = {
      "net.chinesegamer.tsm.gold01",
      "net.chinesegamer.tsm.gold02",
      "net.chinesegamer.tsm.gold03",
      "net.chinesegamer.tsm.gold04",
      "net.chinesegamer.tsm.gold05",
      "net.chinesegamer.tsm.gold06",
      "net.chinesegamer.tsm.item01",
      "net.chinesegamer.tsm.item02",
      "net.chinesegamer.tsm.item03",
      "net.chinesegamer.tsm.item04",
      "net.chinesegamer.tsm.item05",
      "net.chinesegamer.tsm.item06",
      "net.chinesegamer.tsm.item07",
      "net.chinesegamer.tsm.item08",
      "net.chinesegamer.tsm.item09",
      "net.chinesegamer.tsm.item10",
      "net.chinesegamer.tsm.item11",
      "net.chinesegamer.tsm.item12",
      "net.chinesegamer.tsm.item13",
      "net.chinesegamer.tsm.item14",
      "net.chinesegamer.tsm.item15",
      "net.chinesegamer.tsm.item16",
      "net.chinesegamer.tsm.item17",
      "net.chinesegamer.tsm.item18",
      "net.chinesegamer.tsm.item19",
      "net.chinesegamer.tsm.item20",
      "net.chinesegamer.tsm.item21",
      "net.chinesegamer.tsm.item22",
      "net.chinesegamer.tsm.item23",
      "net.chinesegamer.tsm.item24",
      "net.chinesegamer.tsm.item25",
      "net.chinesegamer.tsm.item26",
      "net.chinesegamer.tsm.item27",
      "net.chinesegamer.tsm.item28",
      "net.chinesegamer.tsm.item29",
    };
  end
  return result;
end

function IAPData.GetHKProduct()
  local result
  if LuaHelper.IsAndroidPlatform then
    result = {
      "hk.chinesegamer.tsmhk.gold01",
      "hk.chinesegamer.tsmhk.gold02",
      "hk.chinesegamer.tsmhk.gold03",
      "hk.chinesegamer.tsmhk.gold04",
      "hk.chinesegamer.tsmhk.gold05",
      "hk.chinesegamer.tsmhk.gold06",
      "hk.chinesegamer.tsmhk.item01",
      "hk.chinesegamer.tsmhk.item02",
      "hk.chinesegamer.tsmhk.item03",
      "hk.chinesegamer.tsmhk.item04",
      "hk.chinesegamer.tsmhk.item05",
      "hk.chinesegamer.tsmhk.item06",
      "hk.chinesegamer.tsmhk.item07",
      "hk.chinesegamer.tsmhk.item08",
      "hk.chinesegamer.tsmhk.item09",
      "hk.chinesegamer.tsmhk.item10",
      "hk.chinesegamer.tsmhk.item11",
      "hk.chinesegamer.tsmhk.item12",
      "hk.chinesegamer.tsmhk.item13",
      "hk.chinesegamer.tsmhk.item14",
      "hk.chinesegamer.tsmhk.item15",
      "hk.chinesegamer.tsmhk.item16",
      "hk.chinesegamer.tsmhk.item17",
      "hk.chinesegamer.tsmhk.item18",
      "hk.chinesegamer.tsmhk.item19",
      "hk.chinesegamer.tsmhk.item20",
      "hk.chinesegamer.tsmhk.item21",
      "hk.chinesegamer.tsmhk.item22",
      "hk.chinesegamer.tsmhk.item23",
      "hk.chinesegamer.tsmhk.item24",
      "hk.chinesegamer.tsmhk.item25",
      "hk.chinesegamer.tsmhk.item26",
      "hk.chinesegamer.tsmhk.item27",
      "hk.chinesegamer.tsmhk.item28",
      "hk.chinesegamer.tsmhk.item29",
    };
  elseif LuaHelper.IsApplePlatform then
    result = {
      "hk.chinesegamer.tsmhk.gold01",
      "hk.chinesegamer.tsmhk.gold02",
      "hk.chinesegamer.tsmhk.gold03",
      "hk.chinesegamer.tsmhk.gold04",
      "hk.chinesegamer.tsmhk.gold05",
      "hk.chinesegamer.tsmhk.gold06",
      "hk.chinesegamer.tsmhk.item01",
      "hk.chinesegamer.tsmhk.item02",
      "hk.chinesegamer.tsmhk.item03",
      "hk.chinesegamer.tsmhk.item04",
      "hk.chinesegamer.tsmhk.item05",
      "hk.chinesegamer.tsmhk.item06",
      "hk.chinesegamer.tsmhk.item07",
      "hk.chinesegamer.tsmhk.item08",
      "hk.chinesegamer.tsmhk.item09",
      "hk.chinesegamer.tsmhk.item10",
      "hk.chinesegamer.tsmhk.item11",
      "hk.chinesegamer.tsmhk.item12",
      "hk.chinesegamer.tsmhk.item13",
      "hk.chinesegamer.tsmhk.item14",
      "hk.chinesegamer.tsmhk.item15",
      "hk.chinesegamer.tsmhk.item16",
      "hk.chinesegamer.tsmhk.item17",
      "hk.chinesegamer.tsmhk.item18",
      "hk.chinesegamer.tsmhk.item19",
      "hk.chinesegamer.tsmhk.item20",
      "hk.chinesegamer.tsmhk.item21",
      "hk.chinesegamer.tsmhk.item22",
      "hk.chinesegamer.tsmhk.item23",
      "hk.chinesegamer.tsmhk.item24",
      "hk.chinesegamer.tsmhk.item25",
      "hk.chinesegamer.tsmhk.item26",
      "hk.chinesegamer.tsmhk.item27",
      "hk.chinesegamer.tsmhk.item28",
      "hk.chinesegamer.tsmhk.item29",
    };
  end
  return result;
end

function IAPData.GetProductIndex()
  local result = {};

  if LuaHelper.IsAndroidPlatform then
    result = {
      [1] = 1,
      [2] = 2,
      [3] = 3,
      [4] = 4,
      [5] = 5,
      [6] = 6,
      [7] = 101,
      [8] = 102,
      [9] = 103,
      [10] = 104,
      [11] = 105,
      [12] = 106,
      [13] = 107,
      [14] = 108,
      [15] = 109,
      [16] = 110,
      [17] = 111,
      [18] = 112,
      [19] = 113,
      [20] = 114,
      [21] = 115,
      [22] = 116,
      [23] = 117,
      [24] = 118,
      [25] = 119,
      [26] = 120,
      [27] = 121,
      [28] = 122,
      [29] = 123,
      [30] = 124,
      [31] = 125,
      [32] = 126,
      [33] = 127,
      [34] = 128,
      [35] = 133,
    };
  elseif LuaHelper.IsApplePlatform then
    result = {
      [1] = 1,
      [2] = 2,
      [3] = 3,
      [4] = 4,
      [5] = 5,
      [6] = 6,
      [7] = 101,
      [8] = 102,
      [9] = 103,
      [10] = 104,
      [11] = 105,
      [12] = 106,
      [13] = 107,
      [14] = 108,
      [15] = 109,
      [16] = 110,
      [17] = 111,
      [18] = 112,
      [19] = 113,
      [20] = 114,
      [21] = 115,
      [22] = 116,
      [23] = 117,
      [24] = 118,
      [25] = 119,
      [26] = 120,
      [27] = 121,
      [28] = 122,
      [29] = 123,
      [30] = 124,
      [31] = 125,
      [32] = 126,
      [33] = 127,
      [34] = 128,
      [35] = 133,
    };
  end

  return result;
end

local publicKey = "";
function IAPData.GetPublicKey()
  if LuaHelper.CheckDefine("TW") then
    publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmXCBKdI14w3us0YyFmVeKPcvaHrQYxTAwylpv6mCYjHVkkKiJZE2Ss9dHE4fJ2jrIQBa4Kz+EpCfvk8T810RD4FI9aEjGdkhaRDOG2nBO6UGOrm/hNgJ4zELa7F0zsW/UZHJw4w+JQRh3mvLOPNMZmcfB9AzlvVNyLlmHrUUylslyh1Z85TJhAE3MUyuIeiQX3O9e+kgSBrvWCkkHYa9G8G2gVcjYABor1IudI1Cwpldnu5LvxkIxHNfVXqB16hpd+qJZJvkocwyPgiOxXVhw+FgPG2V/qhVpSJv0Ualacsn2eNHnrXNeAaa51jBHC/YvEr65piUD7drpa8pghWmwQIDAQAB";
  end

  if LuaHelper.CheckDefine("HK") then
    publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnR4Zr/QG3a3xMibIR86HDl//7pfG9p+uA67MzRnyDqbbjVo53mHANg6KQHBDmRiaFM8fU6R1qo76ab1ggbm6a2OrS7/qu62vaM/2r140eZvxWQ+gskT9xdEyyWzt+S3bgijiJ+vJkDGU5FBacmuyzS+y+210g+NbFXwDmhru3BO3ImXlTwfe87uAU8B9DGX4dR69bZPm6ub8xVap0m/WbDfiAEaXgV+xKFgtlcU23Ew56O1e1kj5UO0MAd3QrKr/vxmoB80kSIUkO86LG5shso3UMmPsqjHrsDb782tW7kUfy4RfBZaZ+TYdpV1IiaifL/b4jqgB1y/Gh8NThsePCQIDAQAB";
  end

  return publicKey;
end

local reciptSite = "";
function IAPData.GetReciptSite()
  if LuaHelper.IsAndroidPlatform then
    if Define.IsInternal() then
      --測試網址
      reciptSite = "http://googlemoney1.chinesegamer.net/googleaddpoint.aspx";
    elseif LuaHelper.CheckDefine("ReleaseTest") then
      --外部測試網址
      reciptSite = "http://googlemoney2.chinesegamer.net/googleaddpoint.aspx";
    else
      --正式網址
      reciptSite = "http://googlemoney.chinesegamer.net/googleaddpoint.aspx";
    end
  elseif LuaHelper.IsApplePlatform then
    if Define.IsDebugMode() then
      --測試網址
      reciptSite = "http://applemoney1.chinesegamer.net/appleaddpoint.aspx";
    else
      --正式網址
      reciptSite = "https://applemoney.chinesegamer.net/appleaddpoint.aspx";
    end
  end

  return reciptSite;
end

--[[
MyCard
]]--

IAPData.TradeType = "1";
IAPData.Currency = "TWD";
IAPData.ObjName = "MyCardReceiver";

local myCardProductName = {};
function IAPData.GetMyCardProductName()

  if LuaHelper.CheckDefine("HK") then
    myCardProductName = {
      [1] = {30, "mycard.chinesegamer.tsmhk.gold30"},
      [2] = {90, "mycard.chinesegamer.tsmhk.gold90"},
      [3] = {150, "mycard.chinesegamer.tsmhk.gold150"},
      [4] = {300, "mycard.chinesegamer.tsmhk.gold300"},
      [5] = {450, "mycard.chinesegamer.tsmhk.gold450"},
      [6] = {750, "mycard.chinesegamer.tsmhk.gold750"},
      [7] = {1000, "mycard.chinesegamer.tsmhk.gold1000"},
      [8] = {1150, "mycard.chinesegamer.tsmhk.gold1150"},
      [9] = {2000, "mycard.chinesegamer.tsmhk.gold2000"},
      [10] = {3000, "mycard.chinesegamer.tsmhk.gold3000"},
      [11] = {5000, "mycard.chinesegamer.tsmhk.gold5000"},
      [12] = {10000, "mycard.chinesegamer.tsmhk.gold10000"},
      [101] = {30, "mycard.chinesegamer.tsmhk.item01"},
      [102] = {90, "mycard.chinesegamer.tsmhk.item02"},
      [103] = {90, "mycard.chinesegamer.tsmhk.item03"},
      [104] = {90, "mycard.chinesegamer.tsmhk.item04"},
      [105] = {90, "mycard.chinesegamer.tsmhk.item05"},
      [106] = {150, "mycard.chinesegamer.tsmhk.item06"},
      [107] = {300, "mycard.chinesegamer.tsmhk.item07"},
      [108] = {300, "mycard.chinesegamer.tsmhk.item08"},
      [109] = {300, "mycard.chinesegamer.tsmhk.item09"},
      [110] = {300, "mycard.chinesegamer.tsmhk.item10"},
      [111] = {750, "mycard.chinesegamer.tsmhk.item11"},
      [112] = {750, "mycard.chinesegamer.tsmhk.item12"},
      [113] = {1490, "mycard.chinesegamer.tsmhk.item13"},
      [114] = {30, "mycard.chinesegamer.tsmhk.item14"},
      [115] = {150, "mycard.chinesegamer.tsmhk.item15"},
      [116] = {300, "mycard.chinesegamer.tsmhk.item16"},
      [117] = {300, "mycard.chinesegamer.tsmhk.item17"},
      [118] = {510, "mycard.chinesegamer.tsmhk.item18"},
      [119] = {510, "mycard.chinesegamer.tsmhk.item19"},
      [120] = {900, "mycard.chinesegamer.tsmhk.item20"},
      [121] = {1500, "mycard.chinesegamer.tsmhk.item21"},
      [122] = {1500, "mycard.chinesegamer.tsmhk.item22"},
      [123] = {1500, "mycard.chinesegamer.tsmhk.item23"},
      [124] = {2400, "mycard.chinesegamer.tsmhk.item24"},
      [125] = {3000, "mycard.chinesegamer.tsmhk.item25"},
      [126] = {3000, "mycard.chinesegamer.tsmhk.item26"},
      [127] = {3000, "mycard.chinesegamer.tsmhk.item27"},
      [128] = {3000, "mycard.chinesegamer.tsmhk.item28"},
      [133] = {670, "mycard.chinesegamer.tsmhk.item29"},
    };
  else
    myCardProductName = {
      [1] = {30, "mycard.chinesegamer.tsm.gold30"},
      [2] = {90, "mycard.chinesegamer.tsm.gold90"},
      [3] = {150, "mycard.chinesegamer.tsm.gold150"},
      [4] = {300, "mycard.chinesegamer.tsm.gold300"},
      [5] = {450, "mycard.chinesegamer.tsm.gold450"},
      [6] = {750, "mycard.chinesegamer.tsm.gold750"},
      [7] = {1000, "mycard.chinesegamer.tsm.gold1000"},
      [8] = {1150, "mycard.chinesegamer.tsm.gold1150"},
      [9] = {2000, "mycard.chinesegamer.tsm.gold2000"},
      [10] = {3000, "mycard.chinesegamer.tsm.gold3000"},
      [11] = {5000, "mycard.chinesegamer.tsm.gold5000"},
      [12] = {10000, "mycard.chinesegamer.tsm.gold10000"},
      [101] = {30, "mycard.chinesegamer.tsm.item01"},
      [102] = {90, "mycard.chinesegamer.tsm.item02"},
      [103] = {90, "mycard.chinesegamer.tsm.item03"},
      [104] = {90, "mycard.chinesegamer.tsm.item04"},
      [105] = {90, "mycard.chinesegamer.tsm.item05"},
      [106] = {150, "mycard.chinesegamer.tsm.item06"},
      [107] = {300, "mycard.chinesegamer.tsm.item07"},
      [108] = {300, "mycard.chinesegamer.tsm.item08"},
      [109] = {300, "mycard.chinesegamer.tsm.item09"},
      [110] = {300, "mycard.chinesegamer.tsm.item10"},
      [111] = {750, "mycard.chinesegamer.tsm.item11"},
      [112] = {750, "mycard.chinesegamer.tsm.item12"},
      [113] = {1490, "mycard.chinesegamer.tsm.item13"},
      [114] = {30, "mycard.chinesegamer.tsmhk.item14"},
      [115] = {150, "mycard.chinesegamer.tsmhk.item15"},
      [116] = {300, "mycard.chinesegamer.tsmhk.item16"},
      [117] = {300, "mycard.chinesegamer.tsmhk.item17"},
      [118] = {510, "mycard.chinesegamer.tsmhk.item18"},
      [119] = {510, "mycard.chinesegamer.tsmhk.item19"},
      [120] = {900, "mycard.chinesegamer.tsmhk.item20"},
      [121] = {1500, "mycard.chinesegamer.tsmhk.item21"},
      [122] = {1500, "mycard.chinesegamer.tsmhk.item22"},
      [123] = {1500, "mycard.chinesegamer.tsmhk.item23"},
      [124] = {2400, "mycard.chinesegamer.tsmhk.item24"},
      [125] = {3000, "mycard.chinesegamer.tsmhk.item25"},
      [126] = {3000, "mycard.chinesegamer.tsmhk.item26"},
      [127] = {3000, "mycard.chinesegamer.tsmhk.item27"},
      [128] = {3000, "mycard.chinesegamer.tsmhk.item28"},
      [133] = {670, "mycard.chinesegamer.tsmhk.item29"},
    };
  end

  return myCardProductName;
end

local plateFormName = "";
function IAPData.GetMyCardPlateFormName()
  if LuaHelper.IsAndroidPlatform then
    plateFormName = "A";
  elseif LuaHelper.IsApplePlatform then
    plateFormName = "I";
  end

  return plateFormName;
end

local myCardAddUrl = "";
function IAPData.GetMyCardAddUrl()
  if Define.IsDebugMode() then
    myCardAddUrl = "http://worldstorebilling1.chinesegamer.net/mycard/addpoint.aspx";
  else
    myCardAddUrl = "http://worldstorebilling.chinesegamer.net/mycard/addpoint.aspx";
  end

  return myCardAddUrl;
end

local myCardAuthUrl = "";
function IAPData.GetMyCardAuthUrl()
  if Define.IsDebugMode() then
    myCardAuthUrl = "https://worldstorebilling1.chinesegamer.net/mycard/getauthcode.aspx";
  else
    myCardAuthUrl = "https://worldstorebilling.chinesegamer.net/mycard/getauthcode.aspx";
  end

  return myCardAuthUrl;
end

local myCardGameType = "";
function IAPData.GetMyCardGameType()
  if LuaHelper.CheckDefine("HK") then
    myCardGameType = "94H";
  elseif LuaHelper.CheckDefine("TW") then
    myCardGameType = "94T";    
  end

  return myCardGameType;
end

local myCardCheckCode = ""
function IAPData.GetMyCardCheckCode()
  if Define.IsDebugMode() then 
    myCardCheckCode = "abcd1234";
  else
    myCardCheckCode = "KEB*4lR_4dj@";
  end
  return myCardCheckCode;
end

local sandBoxMode = false;
function IAPData.GetMyCardSandBoxMode()
  if Define.IsDebugMode() then 
    sandBoxMode = true;
  else
    sandBoxMode = false;
  end
  return sandBoxMode;
end

--[[
Alipay
]]--

local alipayProductName = {};
function IAPData.GetAlipayProductName()
  if LuaHelper.CheckDefine("HK") then
    alipayProductName = {
      [1] = {34, "168Point"},
      [2] = {90, "460Point"},
      [3] = {179, "944Point"},
      [4] = {269, "1464Point"},
      [110] = {67, "MonthCard"},
      [112] = {157, "InTimeGift"}
    };
  end

  return alipayProductName;
end

local alipayGameType = "";
function IAPData.GetAlipayGameType()
  if LuaHelper.CheckDefine("HK") then
    alipayGameType = "94";
  end

  return alipayGameType;
end

local alipayCashUrl = "";
function IAPData.GetAlipayCashUrl()
  if Define.IsDebugMode() then 
    alipayCashUrl = "http://cnmoney1.chinesegamer.net/alipay/AliPay.aspx";
  else
    alipayCashUrl = "http://cnmoney.chinesegamer.net/alipay/AliPay.aspx";
  end

  return alipayCashUrl;
end

local alipayAPPID = "";
function IAPData.GetAlipayAPPID()
  if LuaHelper.CheckDefine("HK") then
    alipayAPPID = "2088121362660304";
  end

  return alipayAPPID;
end

local alipayKey = "";
function IAPData.GetAlipayKey()
  if LuaHelper.CheckDefine("HK") then
    alipayKey = "MIICXgIBAAKBgQDf/lCAqJljvp6il8VSP2geCudoIx3SpPyEfSXrOe2VjNr0XLoOt/NvMpYSgtuodZyNpaz3E1XaMem8eLkrrTXtjkBWwwjvr8fkqh1h9TmMuX9F2U3HoqpYwhEJxuW9HPIYHdWdVpc9Dunz6VsOyZw4o6UrAtxpLXRTCxz1Lk40YQIDAQABAoGAcsRQJQu9xBCIIVDYJmMcisAVfxL3FdW5GH0y74o3xDdufGQCZMY1sZTetc9ckrzoIN9xdwruIyUN2KaWYd2W0hImeh2Rkgur4lY8rg17bgSBiOGExoC5j++bQBXQ5CC3lQg8RPUZLP1df1qd7inLLQ+sUbm320bC/JRZNtK7BO0CQQD8S/nwt/26SEjP9QnXXr7hVTpDfcUaaTpG7ncuDKZxHduPhgEaMb3l4msp+Kx05bC72z7aXpI3Mxn8kmum6HKfAkEA40f8dMomxmBQH7mJmf9HvAFaoOR+so0AX47ss9BIknGnqgtKtjGIOn3rOyuEZ2GSfQmIVOk8g5cznlPtMgb4/wJBAPbCzLPvEP/4oRacoMKADYvQ0wqd4YEieHhV1kU45DXwsQVgWIWpLwW//XZwnSQ8Xy5tkrz8EGBi38DT6Oy3nxsCQQC7sNHp/Jrizdy9JjJdKzSpLJEUNz3t44VAZL5SjBmyXgtw4JxVMzhl3WCp33kbCre3bGzaejYQEYh+Ip7J5novAkEAjXViRwjS9xcYAXcuZa6IzOoAZVt6RSw0Y0GdJungjKHOXsXRNgxCY61ggg+2+VsTm+qIKnUxAToadwIffcuZdQ==";
  end

  return alipayKey;
end

local alipaySeller = "";
function IAPData.GetAlipaySeller()
  if LuaHelper.CheckDefine("HK") then
    alipaySeller = "payment@980x.com";
  end
  return alipaySeller;
end