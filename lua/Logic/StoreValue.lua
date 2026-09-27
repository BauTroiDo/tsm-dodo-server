StoreValue = {}
local this = StoreValue;

this.pageItemCount = 6;
this.halfMonthCardItemId = 45619; --半月卡物品ID

local currentPlatform;
local init = false;

local halfMonthCard = nil;

local show = {};

if LuaHelper.IsAndroidPlatform then
  currentPlatform = EPlatform.Android;
elseif LuaHelper.IsApplePlatform then
  currentPlatform = EPlatform.IOS;
end

function StoreValue.Clear()
  halfMonthCard = nil;
end

--儲值顯示
function StoreValue.Init()
  if init then return end;

  this.InitShow();

  init = true;
end

function StoreValue.InitShow()
  if LuaHelper.CheckDefine("ThirdParty") then
    if LuaHelper.CheckDefine("MyCard") then
      StoreValue.InsertShowData(EPlatform.MyCard);
    end 

    if LuaHelper.CheckDefine("Alipay") then
      StoreValue.InsertShowData(EPlatform.AliPay);
      StoreValue.InsertShowData(EPlatform.NineCard);
      StoreValue.InsertShowData(EPlatform.ULong);
    end 
  else
    StoreValue.InsertShowData(this.GetCurrentPlatform());
  end
end

function StoreValue.InsertShowData(platform)
  if storeValueDatas[platform] == nil then return end

  for k, v in pairs(storeValueDatas[platform]) do
    if v.textureId ~= 0 then
      table.insert(show, v);
      show[#show].platform = platform;
    end
  end
end

function StoreValue.GetTotalPageCount()
  return math.ceil(table.Count(show) / this.pageItemCount);
end

function StoreValue.GetShowData(showIndex)
  if show[showIndex] == nil then 
    return nil;
  end

  return show[showIndex];
end

function StoreValue.GetStoreName(platform)
   if platform == EPlatform.MyCard then
    return string.Get(21571);
  elseif platform == EPlatform.AliPay then
    return string.Get(21574);
  elseif platform == EPlatform.NineCard then
    return string.Get(21573);
  elseif platform == EPlatform.ULong then
    return string.Get(21572);
  end

  return "";
end

function StoreValue.GetStoreName(platform)
   if platform == EPlatform.MyCard then
    return string.Get(21571);
  elseif platform == EPlatform.AliPay then
    return string.Get(21574);
  elseif platform == EPlatform.NineCard then
    return string.Get(21573);
  elseif platform == EPlatform.ULong then
    return string.Get(21572);
  end

  return "";
end

--儲值串表
function StoreValue.GetData(productIndex)
  if storeValueDatas[this.GetCurrentPlatform()] == nil then
    return nil;
  end

  for k, v in pairs(storeValueDatas[this.GetCurrentPlatform()]) do
    if productIndex == v.index then
      return v;
    end
  end

  return nil;
end

function StoreValue.GetCurrentPlatform()
  if LuaHelper.CheckDefine("ThirdParty") then
    if LuaHelper.CheckDefine("MyCard") then
      return EPlatform.MyCard;
    end
  end

  return currentPlatform;
end

function StoreValue.Buy(platform, index, tradeType)
  if LuaHelper.IsStandalone or LuaHelper.IsEditor then
    if Define.IsInternal() then
      if index == BattlePass.battlePassStorValueIndex then
        UI.Open(UIMyCardInput, platform, EMyCardInputKind.BattlePass);
      else
        UI.Open(UIMyCardInput, platform, EMyCardInputKind.Others);
      end
    else
      if tradeType > 1 then
        UI.Open(UIMallPaySelect, index);
      else
        if platform == EPlatform.MyCard then
          MyCard.PcBuy(index);
        elseif platform == EPlatform.NewebPay then
          MyCard.NewebPay(index);
        elseif platform == EPlatform.NineCard or platform == EPlatform.ULong then
          if index == BattlePass.battlePassStorValueIndex then
            UI.Open(UIMyCardInput, platform, EMyCardInputKind.BattlePass);
          else
            UI.Open(UIMyCardInput, platform, EMyCardInputKind.Others);
          end
        end
      end
    end
  else
    if platform == EPlatform.Android or platform == EPlatform.IOS then
      IAP.PurchaseProduct(StoreValue.GetIAPIndex(index));
    elseif platform == EPlatform.MyCard then
      if Define.IsDebugMode() then
        if index == BattlePass.battlePassStorValueIndex then
          UI.Open(UIMyCardInput, platform, EMyCardInputKind.BattlePass);
        else
          UI.Open(UIMyCardInput, platform, EMyCardInputKind.Others);
        end
      else
        MyCard.Buy(index, tradeType);
      end
    elseif platform == EPlatform.AliPay then
      AliPay.Buy(index);
    elseif platform == EPlatform.NineCard or platform == EPlatform.ULong or platform == EPlatform.NewebPay then
      if index == BattlePass.battlePassStorValueIndex then
        UI.Open(UIMyCardInput, platform, EMyCardInputKind.BattlePass);
      else
        UI.Open(UIMyCardInput, platform, EMyCardInputKind.Others);
      end
    end
  end
end

function StoreValue.BuyPointCard(platform, no, pas)
  if platform == EPlatform.MyCard then
    PointCard.Buy(PointCard.Kind.MyCard, no, pas);
  elseif platform == EPlatform.NineCard then
    PointCard.Buy(PointCard.Kind.NineCard, no, pas);
  elseif platform == EPlatform.ULong then
    PointCard.Buy(PointCard.Kind.ULong, no, pas);
  else
    logError("platform Error : "..platform);
  end
end

function StoreValue.GetIAPIndex(index)
  for k, v in pairs(IAPData.GetProductIndex()) do
    if index == v then
      return k;
    end
  end

  return 0;
end

function StoreValue.GetIAPName(productId)
  local index;
  for pk, pv in pairs(IAPData.GetProductName()) do
    if productId == pv then
      index = pk;
    end
  end

  local value;
  for ik, iv in pairs(IAPData.GetProductIndex()) do
    if index == ik then
      value = iv;
    end
  end

  if index > 6 then -- 7開始是禮包
    local itemId = nil;

    for ntdk, ntdv in pairs(goodsSaleData[EStoreKind.NTD][1][1]) do
      if value == ntdv.saleCredit then
        itemId = ntdv.Id;
        break;
      end
    end 

    if itemId == nil then
      for ntdk2, ntdv2 in pairs(goodsSaleData[EStoreKind.NTD2][1][1]) do
        if value == ntdv2.saleCredit then
          itemId = ntdv2.Id;
          break;
        end   
      end 
    end

    if itemId ~= nil then
      return itemDatas[itemId]:GetName();
    end
  else
    local storeData = StoreValue.GetData(index);

    return string.Get(10097).." "..storeData.totalGold;
  end

  return "";
end

--半月卡
function StoreValue.SetHalfMonthCardTime(d)
  halfMonthCard = System.DateTime.FromOADate(d);

  if UI.IsVisible( UIMall ) then
    UIMall.RefreshItemPage();
  end
end

function StoreValue.IsHalfMonthCardCanBuy()
  --不在活動時間內
  if not this.IsHalfMonthCardActive() then
    return false;
  end

  --沒有半月卡即可購買
  return not this.IsHaveHalfMonthCard();
end

function StoreValue.IsHalfMonthCardActive()
  if not this.IsHalfMonthCardVaild() then return false end
  
  return activityTimes[13][1]:IsActive(); 
end

function StoreValue.IsHalfMonthCardStart()
  if not this.IsHalfMonthCardVaild() then return false end

  return System.DateTime.Compare( CGTimer.serverTime, activityTimes[13][1]:GetStartDate() ) >= 0
end

function StoreValue.IsHalfMonthCardEnd()
  if not this.IsHalfMonthCardVaild() then return false end

  return System.DateTime.Compare( CGTimer.serverTime, activityTimes[13][1]:GetEndDate() ) >= 0
end

function StoreValue.IsHaveHalfMonthCard()
  return BitFlag.Get(1548) and StoreValue.GetHalfMonthCardTotalDay() <= 14 -- 購買永標
end

function StoreValue.GetHalfMonthCardTotalDay()
  if not this.IsHalfMonthCardVaild() or halfMonthCard == nil then return 0 end
  return math.floor( DateTime.New( CGTimer.serverTime.Year, CGTimer.serverTime.Month , CGTimer.serverTime.Day, 0, 0, 0 ):Subtract( DateTime.New( halfMonthCard.Year, halfMonthCard.Month , halfMonthCard.Day, 0, 0, 0 ) ).TotalDays ) + 1;
end

function StoreValue.GetHalfMonthCardEndDate()
  if not this.IsHalfMonthCardVaild() or halfMonthCard == nil then return 0 end
  return DateTime.New( halfMonthCard.Year, halfMonthCard.Month , halfMonthCard.Day, 0, 0, 0 ):AddDays(14);
end

function StoreValue.IsHalfMonthCardVaild()
  --沒有設定品項活動時間
  if activityTimes[13] == nil then
    logError("activityTimes[13] == nil");
    return false;
  end  

  --串表關閉此活動
  if activityTimes[13][1].switch == 0 then
    logError("activityTimes[13][1].switch == 0");
    return false;
  end  

  return true;
end