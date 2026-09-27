IAPData = {}

function IAPData.GetProductName()
  if LuaHelper.CheckDefine("VN") then
    return IAPData.GetVNProduct();
  end

  return {};
end

function IAPData.GetVNProduct()
  local result
  if LuaHelper.IsAndroidPlatform then
    result = {
      "gz06.and.001",
      "gz06.and.002",
      "gz06.and.003",
      "gz06.and.004",
      "gz06.and.005",
      "gz06.and.006",
      "gz06.and.007",
      "gz06.and.008",
      "gz06.and.009",
      "gz06.and.010",
      "gz06.and.011",
      "gz06.and.012",
      "gz06.and.013",
      "gz06.and.014",
      "gz06.and.015",
      "gz06.and.016",
      "gz06.and.017",
      "gz06.and.018",
      "gz06.and.019",
      "gz06.and.020",
      "gz06.and.021",
      "gz06.and.022",
      "gz06.and.023",
      "gz06.and.024",
      "gz06.and.025",
      "gz06.and.026",
      "gz06.and.027",
      "gz06.and.028",
      "gz06.and.029",
      "gz06.and.030",
      "gz06.and.031",
      "gz06.and.032",
      "gz06.and.033",
      "gz06.and.034",
      "gz06.and.035",
    };
  elseif LuaHelper.IsApplePlatform then
    result = {
      "gz06.ios.001",
      "gz06.ios.002",
      "gz06.ios.003",
      "gz06.ios.004",
      "gz06.ios.005",
      "gz06.ios.006",
      "gz06.ios.007",
      "gz06.ios.008",
      "gz06.ios.009",
      "gz06.ios.010",
      "gz06.ios.011",
      "gz06.ios.012",
      "gz06.ios.013",
      "gz06.ios.014",
      "gz06.ios.015",
      "gz06.ios.016",
      "gz06.ios.017",
      "gz06.ios.018",
      "gz06.ios.019",
      "gz06.ios.020",
      "gz06.ios.021",
      "gz06.ios.022",
      "gz06.ios.023",
      "gz06.ios.024",
      "gz06.ios.025",
      "gz06.ios.026",
      "gz06.ios.027",
      "gz06.ios.028",
      "gz06.ios.029",
      "gz06.ios.030",
      "gz06.ios.031",
      "gz06.ios.032",
      "gz06.ios.033",
      "gz06.ios.034",
      "gz06.ios.035",
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
      [8] = 114,
      [9] = 102,
      [10] = 103,
      [11] = 104,
      [12] = 105,
      [13] = 106,
      [14] = 115,
      [15] = 107,
      [16] = 108,
      [17] = 109,
      [18] = 110,
      [19] = 116,
      [20] = 117,
      [21] = 118,
      [22] = 119,
      [23] = 111,
      [24] = 112,
      [25] = 120,
      [26] = 113,
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
      [8] = 114,
      [9] = 102,
      [10] = 103,
      [11] = 104,
      [12] = 105,
      [13] = 106,
      [14] = 115,
      [15] = 107,
      [16] = 108,
      [17] = 109,
      [18] = 110,
      [19] = 116,
      [20] = 117,
      [21] = 118,
      [22] = 119,
      [23] = 111,
      [24] = 112,
      [25] = 120,
      [26] = 113,
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
  if LuaHelper.CheckDefine("VN") then
    publicKey = "BAQADIQ2UYQOaHV0oMAhCDrgpV2ada2dt+/tV88I1b4RZjQG9sDVJiLDd0JXKqthKBUpsq+fe2JR4xOIq0jNqyO++1TD9+VkY6PArzt+KgUIW2qgP+FBXWRZX3p4B7GKZ65kdbh34y5zo8y/jgoS+NSi6F8x/j1PhAwTayJ6D6OqvLy08GDOesV2NYYza1Bagrgv++9JfnBaGTv14fbMbeQjvkbol9VNx9BIygV0P5qA4YLVEC34Ol4PXlvDvSTlj4GsyokCU5Q8yHD4FrfCj1Nzi15Hd6YlqSfrR9lIoXDhGEKLa9YWC8Y2oZ4MMH/ZJwiZ8SkrP363mgIVeDrlJRpjAfjvAEQACKgCBIIMA8QACOAAFEQAB0w9GikhqkgBNAjIBIIM";
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