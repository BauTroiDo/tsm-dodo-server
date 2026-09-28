
--未載入字串表前用到的字串
Str_DownloadSize = "Tải : %.2fMB\n Giải nén : %.2f MB\n. Nhớ lưu ý dung lượng của máy bạn.Xin cám ơn"
Str_DiskFull = "Máy không đủ dung lượng, vui lòng kiểm tra lại."
Str_DownloadResources = "Tải : %.2f MB / %.2f MB";
Str_DecompressResources = "Giải nén :  %d / %d";
Str_DownloadDatas = "Tải dữ liệu : %d / %d";
Str_DownloadImages = "Tải hình ảnh : %.2f MB / %.2f MB";
Str_DecompressImages = "Giải nén : %d / %d";
Str_LoadData = "Đang tải dữ liệu";
Str_LoadResource = "Đang tải hình ảnh";
Str_NeedDownLoadResource = "Cần cập nhật"

--GoogleAnalytics用到的字串
GALabel = {
  OpenUI = "開啟%s介面";
  CloseUI = "關閉%s介面";
  SwitchTag = "%s切換到%s頁籤";
  SwitchMainTag = "%s切換到%s主頁籤";
  SwitchSubTag = "%s切換到%s子頁籤";
  PrevPage = "前一頁";
  NextPage = "下一頁";
  Navigation = "導航";
  Mount = "坐騎";
  Buff = "Buff";
  Organization = "公會";
  Target = "目標";
  SwitchMission = "切換任務顯示";
  GetItem = "領取物品";
  OfficialSite = "官網";
  CustomerService = "客服中心";
  Atlas = "介面樣式";
  LogOut = "登出";
  Quit = "離開";
  PK = "PK";
  PlayOne = "團戰";
  PlayerInfo = "玩家資訊";
  NpcInfo = "武將資訊";
  Music = "音樂";
  Sound = "音效";
  StoredValue = "儲值";
  Action = "動作";
  Emotion = "表情";
  Cancel = "取消";
  Deliver = "快遞";
  Area = "分區";
  Show = "顯示";
  Hide = "隱藏";
  SendMessageboard = "寄送留言";
  Messageboard = "選擇留言";
  SelectItem = "選擇物品";
  AddBank = "擴充銀行";
  Save = "存入";
  Take = "拿出";
  SelectSever = "選擇伺服器";
  Connect = "連線";
  CreateRole = "創角";
  Rand = "隨機%s";
  Attr = "屬性";
  Element = "元素";
  Cancel = "取消";
  UISellSuccess = "介消使用成功";
  UISellFail = "介消使用失敗";
}

function string.IsNullOrEmpty(s)
  return s == nil or s == "";
end

function string.Count(str)
  local len = #str;
  local left = len;
  local cnt = 0;
  local arr = { 0, 0xc0, 0xe0, 0xf0, 0xf8, 0xfc };

  while left ~= 0 do
    local tmp = string.byte(str, -left);
    local i = #arr;

    while arr[i] do
      if tmp >= arr[i] then
        left = left - i;
        break;
      end

      i = i - 1;
    end

    cnt = cnt + 1;
  end

  return cnt;
end

function string.Sub(str, start, len)
  local initIndex = 1;
  local strResult = "";
  local endIndex;
  if len == nil then
    endIndex = string.Count(str);
  else
    endIndex = start + len - initIndex;
  end

  local index = initIndex;
  if endIndex < start then return strResult end;

  --[[
    https://www.cnblogs.com/SkyflyBird/p/9804282.html
    UTF8的编码规则：
    1. 字符的第一个字节范围： 0x00—0x7F(0-127),或者 0xC2—0xF4(194-244);
        UTF8 是兼容 ascii 的，所以 0~127 就和 ascii 完全一致
    2. 0xC0, 0xC1,0xF5—0xFF(192, 193 和 245-255)不会出现在UTF8编码中
    3. 0x80—0xBF(128-191)只会出现在第二个及随后的编码中(针对多字节编码，如汉字)
    ]]
  --Use gmatch from http://lua-users.org/wiki/LuaUnicode for now.
  for singleChar in string.gmatch(str, "([%z\1-\127\194-\244][\128-\191]*)") do
    if index >= start and index <= endIndex then
      strResult = string.Concat(strResult, singleChar);
    end
    index = index + 1;
  end
  return strResult;
end


function string.GetLength(s)
  if string.gmatch(s, "([%z\1-\127\194-\244][\128-\191]*)") then
    return TextChecker.GetLength(s);
  else
    --计算字符串宽度
    --可以计算出字符宽度，用于显示使用
    local lenInByte = #s;
    local width = 0;
    local i = 1;

    while i <= lenInByte do
      local curByte = string.byte(s, i);

      local byteCount = 1;
      if curByte > 0 and curByte <= 127 then
        --1字节字符
        byteCount = 1;
      elseif curByte >= 192 and curByte < 223 then
        --双字节字符
        byteCount = 2;
      elseif curByte >= 224 and curByte < 239 then
        --汉字
        byteCount = 2;  --原本是3  因為delphi一個中文字長度是2所以改2
      elseif curByte >= 240 and curByte <= 247 then
        --4字节字符
        byteCount = 4;
      end

      --重置下一字节的索引
      i = i + byteCount;

      --字符的个数（长度
      width = width + 1;
    end

    return width;
  end
end

function string.ChangeSymbol(s)
  s = string.gsub(s, ",", "，");
  s = string.gsub(s, "?", "？");
  s = string.gsub(s, "!", "！");
  
  return s;
end

function string.XOR(s, xor, offset)
  if offset == nil then
    offset = 0;
  end

  return string.gsub(
    s,
    ".",
    function(w)
      local result = bit.bxor(string.byte(w), xor) - offset;

      if result < 0 then
        result = result - (math.floor(result / DIV1) * SUBT1);
      end

      return string.char(result);
    end
  );
end

function string.GetChineseNumber(value)
  local result = "";
  local count = 0;
  local currentValue = 0;
  local currentResult = "";
  local extraResult = "";
  local lastIsZero = false;

  if value == 0 then
    return string.Get(22578);
  end
  
  while value ~= 0 do
    count = count + 1;
    currentValue = value % 10;
    value = math.floor(value / 10);
    
    if count % 4 == 1 then
      if count / 4 < 1 then
        extraResult = "";
      elseif count / 4 < 2 then
        extraResult = string.Get(22579);
      elseif count / 4 < 3 then
        extraResult = string.Get(22580);
      elseif count / 4 < 4 then
        extraResult = string.Get(22581);
      end
    elseif count % 4 == 2 then
      extraResult = string.Get(22582);
    elseif count % 4 == 3 then
      extraResult = string.Get(22583);
    elseif count % 4 == 0 then
      extraResult = string.Get(22584);
    end
    
    currentResult = "";
    if currentValue == 0 then
      if count % 4 == 1 then
        currentResult = "";
        lastIsZero = false;
      else
        if not lastIsZero then
          currentResult = string.Get(22578);
        end
        extraResult = "";
        lastIsZero = true;
      end
    elseif currentValue == 1 then
      if count % 4 == 2 and value == 0 then
        currentResult = "";
      else
        currentResult = string.Get(22585);
      end
      lastIsZero = false;
    elseif currentValue == 2 then
      if count == 1 or count % 4 == 2 then
        currentResult = string.Get(22586);
      else
        currentResult = string.Get(22587);
      end
      lastIsZero = false;
    elseif currentValue == 3 then
      currentResult = string.Get(22588);
      lastIsZero = false;
    elseif currentValue == 4 then
      currentResult = string.Get(22589);
      lastIsZero = false;
    elseif currentValue == 5 then
      currentResult = string.Get(22590);
      lastIsZero = false;
    elseif currentValue == 6 then
      currentResult = string.Get(22591);
      lastIsZero = false;
    elseif currentValue == 7 then
      currentResult = string.Get(22592);
      lastIsZero = false;
    elseif currentValue == 8 then
      currentResult = string.Get(22593);
      lastIsZero = false;
    elseif currentValue == 9 then
      currentResult = string.Get(22594);
      lastIsZero = false;
    end
    
    result = string.Concat(currentResult, extraResult, result);
  end
  
  --logError(result);
  return result;
end

function string.GetChineseNumberLower(value)
  if LuaHelper.CheckDefine("TH") then
    return value;
  else
    return string.GetChineseNumber(value);
  end
end

function string.GetSecondText(seconds, shortText)
  local result = "";

  local day = math.floor(seconds / 86400);
  if day > 0 then
    result = string.Concat(result, day, string.Get(49031));
  end

  local hour = math.floor(seconds / 3600) % 24;
  if hour > 0 then
    if day > 0 then
      result = string.Concat(result, string.TenDigits(hour), string.Get(49032));
    else
      result = string.Concat(result, hour, string.Get(49032));
    end
  elseif day > 0 then
    result = string.Concat(result, "00", string.Get(49032));
  end

  local min = math.floor(seconds / 60) % 60;
  if min > 0 then
    if day > 0 or hour > 0 then
      result = string.Concat(result, string.TenDigits(min), string.Get(49033));
    else
      result = string.Concat(result, min, string.Get(49033));
    end
  elseif hour > 0 then
    result = string.Concat(result, "00", string.Get(49033));
  end

  if shortText == true and seconds >= 60 then return result end

  local second = seconds % 60;
  if second > 0 then
    if day > 0 or hour > 0 or min > 0 then
      result = string.Concat(result, string.TenDigits(second), string.Get(40145));
    else
      result = string.Concat(result, second, string.Get(40145));
    end
  elseif min > 0 then
    result = string.Concat(result, "00", string.Get(40145));
  end

  return result;
end

function string.GetSecondSymbol(seconds, noHour)
  local result = "";

  local hour = math.floor(seconds / 3600);
  if hour > 0 then
    result = string.Concat(result, string.TenDigits(hour), ":");
  else
    if not noHour then
      result = string.Concat(result, "00:");
    end
  end

  local min = math.floor(seconds / 60) % 60;
  if min > 0 then
    result = string.Concat(result, string.TenDigits(min), ":");
  else
    result = string.Concat(result, "00:");
  end

  local second = seconds % 60;
  if second > 0 then
    result = string.Concat(result, string.TenDigits(second));
  else
    result = string.Concat(result, "00");
  end

  return result;
end

function string.TenDigits(number)
   return string.Concat(math.floor(number / 10), number % 10);
end

function string.GetColorText(s, color)
  return string.format("<color=#%02x%02x%02x%02x>%s</color>", color.r * 255, color.g * 255, color.b * 255, color.a * 255, s);
end

function string.AddBrackets(s)
  return string.Concat("【", s, "】");
end

function string.Trim(s)
  return (s:gsub("^%s*(.-)%s*$", "%1"));
end

function string.CheckName(s, skipIllegal)
  
  if s == nil or s == "" then
    ShowCenterMessage(string.Get(50021));
    return false;
  end

  if not TextChecker.Check(s, TextChecker.Name, 1, 14) then
    ShowCenterMessage(string.Get(50022));
    return false;
  end

  if string.GetLength(s) > 14 then
    ShowCenterMessage(string.Get(49005));
    return false;
  end

  if skipIllegal then return true end

  for k, v in pairs(illegalDatas) do
    if string.match(s, string.GetNocase(v)) ~= nil then
      ShowCenterMessage(string.Get(50022));
      return false;
    end
  end

  return true;
end

function string.ReomveSymbol(str, symbol)  
  local subStr = {}  
  while true do  
    local pos = string.find(str, symbol);
    if not pos then  
      subStr[#subStr+1] = str;
      break  
    end        
    subStr[#subStr+1] = string.sub(str, 1, pos-1);  
    str = string.sub(str, pos+1, #str)  
  end  
  return table.concat(subStr);
end

function string.Concat(...)
  return table.concat({ ... });
end

--字串表
local tempParameter = nil;
local tempText = "";
function string.Get(key, parameter)
  if key == nil or key == 0 then
    return "";
  end
  
  if textDatas == nil or textDatas[key] == nil then
    logWarn(string.Concat("Text[", key, "] is empty !!"));
    return "";
  end
  
  tempParameter = parameter;
  
  tempText = string.gsub(textDatas[key], "<%%(.-)%%>", string.Parse);
  
  return tempText;
end

function string.Parse(text)
  local key = tonumber(string.match(text, "(%d+)"));
  local value = tonumber(string.match(text, "=(%d+)"));
  
  if key == 1 then
    --玩家名稱
    if Role.player ~= nil then
      return Role.player.name;
    else
      return text;
    end
  elseif key == 2 then
    --現實日期
    return CGTimer.serverTime:ToShortDateString();
  elseif key == 3 then
    --現實時間
    return CGTimer.serverTime:ToShortTimeString();
  elseif key == 4 then
    --NPC名稱
    if npcDatas[value] ~= nil then
      return npcDatas[value].name;
    end
  elseif key == 5 then
    --物品名稱
    if itemDatas[value] ~= nil then
      return itemDatas[value]:GetName(true);
    end
  elseif key == 6 then
    --成就名稱
    if achievementDatas[value] ~= nil then
      return achievementDatas[value].name;
    end
  elseif key == 7 then
    --字串
    if textDatas[value] ~= nil then
      return textDatas[value];
    end
  elseif key == 8 then
    --中文數字
    return string.GetChineseNumber(value);
  elseif key == 9 then
    --GS名稱  
    for i = 1, table.Count(Network.servers) do
      if Network.servers[i].id == value then
        return Network.servers[i].name
      end
    end
  end
  
  return string.Concat("%", text, "%");
end

function string.GetAttribute(kind)
  if kind == 1 then
    return string.Get(20037);
  elseif kind == 2 then
    return string.Get(20038);
  elseif kind == 3 then
    return string.Get(20039);
  elseif kind == 4 then
    return string.Get(20042);
  elseif kind == 5 then
    return string.Get(20040);
  elseif kind == 6 then
    return string.Get(20041);
  end
  
  return "";
end

function string.GetMoral(kind, singleWord)
  if kind == 0 then
    return string.Get(20001);
  elseif kind == 1 then
    return string.Get(10086);
  elseif kind == 2 then
    return string.Get(10087);
  elseif kind == 3 then
    return string.Get(10088);
  elseif kind == 4 then
    if singleWord then
      return string.Get(21485);
    else
      return string.Get(10089);
    end
  elseif kind == 5 then
    if singleWord then
      return string.Get(21486)
    else
      return string.Get(10090);
    end
  end
  
  return "";
end

function string.GetFightWay(kind)
  if kind == 1 then
    return string.Get(20011);
  elseif kind == 2 then
    return string.Get(20012);
  elseif kind == 3 then
    return string.Get(20013);
  elseif kind == 4 then
    return string.Get(20014);
  elseif kind == 5 then
    return string.Get(20015);
  elseif kind == 6 then
    return string.Get(20016);
  elseif kind == 7 then
    return string.Get(20017);
  elseif kind == 8 then
    return string.Get(20018);
  elseif kind == 9 then
    return string.Get(20019);
  elseif kind == 10 then
    return string.Get(20020);
  elseif kind == 11 then
    return string.Get(20021);
  elseif kind == 12 then
    return string.Get(20022);
  elseif kind == 13 then
    return string.Get(20023);
  elseif kind == 14 then
    return string.Get(20024);
  elseif kind == 15 then
    return string.Get(20025);
  elseif kind == 16 then
    return string.Get(20026);
  elseif kind == 17 then
    return string.Get(20027);
  elseif kind == 18 then
    return string.Get(20028);
  elseif kind == 19 then
    return string.Get(20029);
  elseif kind == 20 then
    return string.Get(20030);
  elseif kind == 21 then 
    return string.Get(20011);
  elseif kind == 22 then
    return string.Get(23619);
  end
  
  return "";
end

function string.GetFightArea(kind)
  --1 一人, 2同列二人, 3行三人, 4跳三人, 5十字五人, 6六人, 7同行五人, 8全體 
  if kind == 1 then
    return string.Get(99662);
  elseif kind == 2 then
    return string.Get(99663);
  elseif kind == 3 then
    return string.Get(99664);
  elseif kind == 4 then
    return string.Get(99665);
  elseif kind == 5 then
    return string.Get(99666);
  elseif kind == 6 then
    return string.Get(99667);
  elseif kind == 7 then
    return string.Get(99668);
  elseif kind == 8 or kind == 255 then
    return string.Get(99669);
  end
  return "";
end

function string.GetCurrency(currency)
  if currency == ECurrency.Money then
    return TextParse.GetSpriteText("common0", "common0_icon018", 20, 20, 0, 2);
  elseif currency == ECurrency.Silver then
    return TextParse.GetSpriteText("common0", "common0_icon023", 20, 20, 0, 2);
  elseif currency == ECurrency.Gold then
    return TextParse.GetSpriteText("common0", "common0_icon022", 20, 20, 0, 2);
  elseif currency == ECurrency.OrgGold then
    return TextParse.GetSpriteText("common0", "common0_icon063", 20, 20, 0, 2);
  elseif currency == ECurrency.DataFight then
    return TextParse.GetSpriteText("common0", "common0_icon066", 20, 20, 0, 2);
  elseif currency == ECurrency.furnitureAdvanced then
    return TextParse.GetSpriteText("common0", "common0_icon136", 20, 20, 0, 2);
  elseif currency == ECurrency.furnitureNormal then
    return TextParse.GetSpriteText("common0", "common0_icon137", 20, 20, 0, 2);
  elseif currency == ECurrency.Elf then
    return TextParse.GetSpriteText("common0", "common0_icon155", 20, 20, 0, 2);
  elseif currency == ECurrency.Treasure then
    return TextParse.GetSpriteText("common0", "common0_icon202", 20, 20, 0, 2);
  elseif currency == ECurrency.TS then
    return TextParse.GetSpriteText("common0", "common0_icon154", 20, 20, 0, 2);
  elseif currency == ECurrency.Soul then
    return TextParse.GetSpriteText("common0", "common0_icon158", 20, 20, 0, 2);
  elseif currency == ECurrency.ColorSoul then
    return TextParse.GetSpriteText("common0", "common0_icon161", 20, 20, 0, 2);
  elseif currency == ECurrency.WarriorTicket then
    return TextParse.GetSpriteText("common0", "common0_icon165", 20, 20, 0, 2);
  elseif currency == ECurrency.CardTicket then
    return TextParse.GetSpriteText("common0", "common0_icon164", 20, 20, 0, 2);
  elseif currency == ECurrency.ExclusiveWeaponTicket then
    return TextParse.GetSpriteText("common0", "common0_icon163", 20, 20, 0, 2);
  elseif currency == ECurrency.OutfitTicket then
    return TextParse.GetSpriteText("common0", "common0_icon162", 20, 20, 0, 2);
  else
    return "";
  end
end

function string.GetCurrencyName(currency)
  if currency == ECurrency.Money then
    return string.Get(10099);
  elseif currency == ECurrency.Silver then
    return string.Get(10098);
  elseif currency == ECurrency.Gold then
    return string.Get(10097);
  elseif currency == ECurrency.OrgGold then
    return "";
  elseif currency == ECurrency.DataFight then
    return string.Get(22796);
  elseif currency == ECurrency.furnitureAdvanced then
    return string.Get(23572);
  elseif currency == ECurrency.furnitureNormal then
    return string.Get(23573);
  elseif currency == ECurrency.Elf then
    return string.Get(23736);
  elseif currency == ECurrency.TS then
    return string.Get(23737);
  elseif currency == ECurrency.Soul then
    return string.Get(23932);
  elseif currency == ECurrency.ColorSoul then
    return string.Get(23933);
  elseif currency == ECurrency.WarriorTicket then
    return string.Get(23939);
  elseif currency == ECurrency.CardTicket then
    return string.Get(23940);
  elseif currency == ECurrency.ExclusiveWeaponTicket then
    return string.Get(23941);
  elseif currency == ECurrency.OutfitTicket then
    return string.Get(23942);
  elseif currency == ECurrency.Treasure then
    return string.Get(24607);
  else
    return "";
  end
end

function string.GetUiSellFail(sellId, kind, value)
  if kind == 1 then
    return string.Get(10137); --無介消資料
  elseif kind == 2 then
    return string.fromat(string.Get(10138), value); --等級不足
  elseif kind == 3 then
    return string.Get(10139); --RoleCount索引錯誤
  elseif kind == 4 then
    return string.Get(10140); --價格錯誤
  elseif kind == 5 then
    return string.Get(24959); --貨幣不足
  elseif kind == 6 then
    return string.Get(10141); --扣金錢失敗
  elseif kind == 7 then
    return string.Get(10143); --扣物品失敗
  elseif kind == 10 then
    return string.Get(10142); --檢查使用物品錯誤
  elseif kind == 11 then
    if Contains(sellId, 1, 3 ,56) then
      return string.Get(20343); --背包介消次數已滿但成就取得物品還可使用的訊息
    elseif uisellDatas[sellId].customString > 0 then
      return string.Get(uisellDatas[sellId].customString);
    else
      return string.Get(40390); --已達最大上限
    end
  elseif kind == 12 then
    return string.Get(80360); --元寶不足
  elseif kind == 13 then
    return string.Get(20128); --綁元不足
  elseif kind == 14 then
    return string.Get(80099); --戰鬥中不可使用介消玩轉轉樂
  elseif kind == 15 then
    return string.Get(21272); --不可使用
  elseif kind == 16 then
    return string.Get(21273); --條件未完成
  elseif kind == 17 then
    return string.Get(21540); --所在場景不可傳送
  elseif kind == 18 then
    ShowCenterMessage(string.Get(22018));
    return string.Concat(string.Get(22006)); --藏龍寶箱時間未到
  elseif kind == 20 then
    return string.Get(22172); --吞食歡樂幣不足
  elseif kind == 21 then
    return string.Get(20763); --投幣達上限
  elseif kind == 26 then 
    return string.Get(22346); --商品已下架
  elseif kind == 35 then      --戳戳樂硬幣已滿
    return string.Get(22459);
  elseif kind == 254 then
    return string.Get(22173); --功能關閉中
  else
    return string.Get(10144); --未知錯誤
  end
end

function string.GetMissionKind(kind)
  if kind == 1 then
    return string.Get(20149);
  elseif kind == 2 then
    return string.Get(20150);
  elseif kind == 3 then
    return string.Get(20151);
  elseif kind == 4 then
    return string.Get(10118);
  elseif kind == 5 then
    return string.Get(10118);
  elseif kind == 6 then
    return string.Get(20066);
  elseif kind == 7 then
    return string.Get(20066);
  elseif kind == 8 then
    return string.Get(20066);
  end

  return "";
end

function string.GetElement(element)
  if element == 0 then
    return string.Get(20001);--無
  elseif element == 1 then
    return string.Get(20002);--地
  elseif element == 2 then
    return string.Get(20003);--水
  elseif element == 3 then
    return string.Get(20004);--火
  elseif element == 4 then
    return string.Get(20005);--風
  elseif element == 5 then
    return string.Get(20006);--心
  elseif element == 7 then
    return string.Get(20922);--光
  elseif element == 8 then
    return string.Get(20923);--暗
  else
    return "";
  end
end

function string.GetElementHint(element)
  if element == 1 then
   return string.Get(20007);
  elseif element == 2 then
    return string.Get(20008);
  elseif element == 3 then
    return string.Get(20009);
  elseif element == 4 then
    return string.Get(20010);
  end
end

function string.GetCareer(career)
  if career == 0 then
    return string.Get(20592); --轉
  elseif career == 1 then
    return string.Get(20962); --俠
  elseif career == 2 then
    return string.Get(20963); --霸
  elseif career == 3 then
    return string.Get(20964); --賢
  elseif career == 4 then
    return string.Get(20965); --仙
  end
end

function string.GetCareerHint(career)
  if career == 1 then
    return string.Get(22555); --俠
  elseif career == 2 then
    return string.Get(22553); --霸
  elseif career == 3 then
    return string.Get(22554); --賢
  elseif career == 4 then
    return string.Get(22556); --仙
  else
    return "";
  end
end

function string.GetDoroName(kind)
  if kind == 1 then
    return string.Get(98035) --武將
  elseif kind == 2 then
    return string.Get(21419) --卡片
  end
end

function string.GetCardName(warriorId)
  return string.Concat(warriorId, "_S");
end

function string.GetServerText(data)
  local result = "";
  
  local textKind = data:ReadByte();
  
  if textKind == 0 then
    local len = data:ReadInt16()
    result = data:ReadString(len);
    result = string.gsub(result, "<%%(.-)%%>", string.Parse);
  elseif textKind == 1 then
    result = string.Get(data:ReadInt32());
  elseif textKind == 2 then
    result = tostring(data:ReadInt32());
  elseif textKind == 3 then
    result = string.AddBrackets(string.Get(data:ReadInt32()));
  end
  
  local count = data:ReadByte();
  
  local argTexts = {};
  for i = 1, count do
    textKind = data:ReadByte();
    if textKind == 0 then
      argTexts[i] = data:ReadString(data:ReadInt16());
      argTexts[i] = string.gsub(argTexts[i], "<%%(.-)%%>", string.Parse);
    elseif textKind == 1 then
      argTexts[i] = string.Get(data:ReadInt32());
    elseif textKind == 2 then
      argTexts[i] = tostring(data:ReadInt32());
    elseif textKind == 3 then
      argTexts[i] = string.AddBrackets(string.Get(data:ReadInt32()));
    elseif textKind == 4 then
      local itemId = data:ReadUInt16();
      local itemData = itemDatas[itemId];
      if itemData ~= nil then
        argTexts[i] = itemData:GetName(true);
      else
        logError(" GetServerText itemId is nil : " .. itemId);
      end
    elseif textKind == 5 then
      local npcId = data:ReadUInt16();
      if npcId ~= nil then
        argTexts[i] = npcDatas[npcId].name;
      else
        logError(" GetServerText npcId is nil : " .. npcId);
      end
    end
  end
  
  local currentIndex = 0;
  result = string.gsub(
    result, 
    "%%s", 
    function(text)
      currentIndex = currentIndex + 1;
      return argTexts[currentIndex];
    end,
    count
  );
  
  return result;
end

function string.GetChannelText(channel)
  if channel == EChannel.System then
    return string.Get(20618);
  elseif channel == EChannel.World then
    return string.Get(20796);
  elseif channel == EChannel.Scene then
    return string.Get(20791);
  elseif channel == EChannel.Whisper then
    return string.Get(20792);
  elseif channel == EChannel.GM then
    return "GM";
  elseif channel == EChannel.Team then
    return string.Get(20793);
  elseif channel == EChannel.Organization then
    return string.Get(20794);
  elseif channel == EChannel.Ally then
    return string.Get(20795);
  else
    return "";
  end
end

function string.GetNocase(s)
  s = string.gsub(s, "%a",
    function (c)
      return string.format("[%s%s]", string.lower(c), string.upper(c))
    end
  )
  
  return s
end

function string.GetRoleKey()
  return string.format("_%d_%s", Network.serverId, Role.playerId);
end

function string.ReplaceSpecialSymbol(s)
  --( ) . % + - * ? [ ] ^ $
  --  s = string.gsub(s,  "%(", "%%(");
  --  s = string.gsub(s,  "%)", "%%)");
  s = string.gsub(s,  "%.", "%%.");
  --  s = string.gsub(s,  "%%", "%%%");
  s = string.gsub(s,  "%+", "%%+");
  s = string.gsub(s,  "%-", "%%-");
  s = string.gsub(s,  "%*", "%%*");
  --  s = string.gsub(s,  "%?", "%%?");
  --  s = string.gsub(s,  "%[", "%%[");
  --  s = string.gsub(s,  "%]", "%%]");
  --  s = string.gsub(s,  "%^", "%%^");
  --  s = string.gsub(s,  "%$", "%%$");
  return s;
end

function string.GetCommaValue(amount)
  local formatted = amount
  while true do  
    formatted, k = string.gsub(formatted, "^(-?%d+)(%d%d%d)", '%1,%2')
    if k == 0 then
      break
    end
  end
  return formatted
end

function string.GetProductionText(kind)
  if kind == EOrgProduction.Weapon then
    return string.Get(40245);
  elseif kind == EOrgProduction.Army then
    return string.Get(40244);
  end

  return "";
end

function string.GetCityLevelText(level)
  if level == 1 then
    return string.Get(40280);
  elseif level == 2 then
    return string.Get(40281);
  elseif level == 3 then
    return string.Get(40282);
  end

  return tostring(level);
end

function string.GetRandomName()
  local fullName;
  local min = 1;
  local max = table.Count(randomNameDatas);

  if math.random(2) == 1 then
    local name1, name2, name3;
    
    name1 = randomNameDatas[math.random(min, max)].firstName;
    if currentSex == 1 then
      name2 = randomNameDatas[math.random(min, max)].boyName;
      name3 = randomNameDatas[math.random(min, max)].boyName;
    else
      name2 = randomNameDatas[math.random(min, max)].girlName;
      name3 = randomNameDatas[math.random(min, max)].girlName;
    end

    fullName = string.Concat(name1, name2, name3);
  else
    local nameSp1, nameSp2;

    nameSp1 = randomNameDatas[math.random(min, max)].spNameA;
    nameSp2 = randomNameDatas[math.random(min, max)].spNameB;

    fullName = string.Concat(nameSp1, nameSp2);
  end

  return fullName;
end

function string.DecodeURI(s)
  s = string.gsub(s, '%%(%x%x)', function(h) return string.char(tonumber(h, 16)) end)
  return s
end

function string.EncodeURI(s)
  s = string.gsub(s, "([^%w%.%- ])", function(c) return string.format("%%%02X", string.byte(c)) end)
  return string.gsub(s, " ", "+")
end

function string.CheckNpcName(npcId, npcName)
  if string.IsNullOrEmpty(npcName) then
    local defaultName = npcDatas[npcId].name;
    defaultName = string.gsub(defaultName, '%d%d%d%d%d', '');
    return defaultName;
  else
    return npcName;
  end
end
