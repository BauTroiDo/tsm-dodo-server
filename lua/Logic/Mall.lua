Mall = {}
local this = Mall;

this.EDoroKind = {
  Empty = 0,
  Warrior = 1,
  Card = 2,
  furnitureAdvanced = 3,
  furnitureNormal = 4,
  furnitureGold = 5,
  Elf = 6,
  Soul = 7,
  ExclusiveWeapon = 8,
  Outfit = 9,
  UniqueBlood = 10,
  Treasure = 11
}

this.EDoroItemId = {
  Warrior = 65415,
  Card = 65416,
}

this.maxMajorKind = 18; --虛寶分類主項的數量
this.maxSecondKind = 9; --虛寶分類次項(細項)的數量
this.maxNTD2List = 1; --虛寶最細項之下的最多列表數量種類

local buyThing = false;

local pageSellItemData = {};
local severBuyItemCount = {};

local doroKind = this.EDoroKind.Empty;
local doroData = {};

function Mall.ClearDoro()
  buyThing = false;
end

function Mall.Doro()
  buyThing = true;
end

function Mall.IsMallDoro()
  return buyThing;
end

function Mall.GetPageItemCount(storeKind, mainPageIndex, subPageIndex)
  if goodsSaleData[storeKind]== nil then
    return 0;
  end
  
  if goodsSaleData[storeKind][mainPageIndex] == nil then
    return 0;
  end
  
  local showKind = Mall.GetTagShowKind(storeKind, mainPageIndex, subPageIndex);
  if showKind  == nil then
    return 0;
  end
  
  if goodsSaleData[storeKind][mainPageIndex][showKind] == nil then
    return 0;
  end
  
  return table.Count(goodsSaleData[storeKind][mainPageIndex][showKind]);
end

function Mall.GetPageItemIndex(storeKind, mainPageIndex, subPageIndex, sellData)
  local showKind = Mall.GetTagShowKind(storeKind, mainPageIndex, subPageIndex);

  for k, v in pairs(goodsSaleData[storeKind][mainPageIndex][showKind]) do
    if v.Id == sellData.Id and v.saleCredit == sellData.saleCredit then
      return k;
    end
  end 

  return 0;
end

function Mall.ResetPageSellData(storeKind, mainPageIndex, subPageIndex)
  table.Clear(pageSellItemData);
  if this.GetPageItemCount(storeKind, mainPageIndex, subPageIndex) > 0 then
    local showKind = Mall.GetTagShowKind(storeKind, mainPageIndex, subPageIndex);
    for k, v in pairs(goodsSaleData[storeKind][mainPageIndex][showKind]) do
      if Network.CheckServerSwitch(EServerSwitch.Review) then --送審版上架
        if v.open and v:IsInSaleTime() and CheckFlag(v.bitFunction, 1) then
          table.insert(pageSellItemData, v);
        end
      else
        if v.open and v:IsInSaleTime() and CheckFlag(v.bitFunction, 2) then --正式版上架
          table.insert(pageSellItemData, v);
        end        
      end
    end    
  end
end

function Mall.GetTotalPageCount(storeKind)
  if storeKind == EStoreKind.NTD then
    return table.Count(pageSellItemData);
  elseif storeKind == EStoreKind.NTD2 then
    return math.ceil(table.Count(pageSellItemData) / this.maxNTD2List);
  elseif storeKind == EStoreKind.Doro then
    return table.Count(pageSellItemData);
  elseif storeKind == EStoreKind.Mall or storeKind == EStoreKind.Organization or storeKind == EStoreKind.PVP then
    return table.Count(pageSellItemData);
  end

  return 0;
end

function Mall.GetSellDataCount()
  return table.Count(pageSellItemData);
end

function Mall.GetSellData(itemIndex)
  if pageSellItemData[itemIndex] == nil then
    return nil;
  end

  return pageSellItemData[itemIndex];
end

function Mall.GetMallKind(mainPageIndex)
  if Network.CheckServerSwitch(EServerSwitch.Review) then
      if mainPageIndex == 1 then
        return EStoreKind.NTD;
      end     

      if mainPageIndex == 2 then
        return EStoreKind.Cash;
      end   
  else
    local index = 0;
    for k, v in pairs(goodsSaleTagData) do
      if v.subTag == 0 then
        index = index + 1;
      end 
    
      if index == mainPageIndex then
        return v.storeKind;
      end   
    end
  end

  return 0;
end

function Mall.GetMainPageIndex(mainPageIndex)
  local index = 0;
  for k, v in pairs(goodsSaleTagData) do
    if v.subTag == 0 then
      index = index + 1;
    end 
    
    if index == mainPageIndex then
      return v.mainTag;
    end   
  end
  
  return 0;
end

function Mall.GetMainPageCount()
  local result = 0;

  for k, v in pairs(goodsSaleTagData) do
    if v.subTag == 0 then
      result = result + 1;
    end
  end

  return result;
end

function Mall.GetSubPageCount(mainPageIndex)
  local result = 0;
  local index = 0;

  for k, v in pairs(goodsSaleTagData) do
    if v.subTag == 0 then
      index = index + 1;
    end

    if index == mainPageIndex and v.subTag ~= 0 then
      result = result + 1;
    end
  end

  return result;
end

function Mall.GetSubPageCountWithKind(storeKind)
  local result = 0;

  for k, v in pairs(goodsSaleTagData) do
    if v.storeKind == storeKind and v.subTag ~= 0 then
      result = result + 1;
    end
  end

  return result;
end

function Mall.GetTagShowKind(storeKind, mainPageIndex, subPageIndex)
  local index = subPageIndex;

  if this.GetSubPageCountWithKind(storeKind) == 0 then
    index = 0
  end

  for k, v in pairs(goodsSaleTagData) do
    if v.storeKind == storeKind and v.mainTag == mainPageIndex and v.subTag == index then  
      return v.showKind;
    end   
  end

  return nil;
end

function Mall.GetMainTagName(mainPageIndex)
  local index = 0;
  for k, v in pairs(goodsSaleTagData) do
    if v.subTag == 0 then
      index = index + 1;
    end   
    
    if index == mainPageIndex then
      return string.Get(v.name);
    end    
  end
  
  return "";
end

function Mall.GetMainTagNameByKind(kind)
  for k, v in pairs(goodsSaleTagData) do
    if v.storeKind == kind then
      return string.Get(v.name);
    end 
  end
  
  return "";
end

function Mall.GetSubTagName(mainPageIndex, subPageIndex)
  local index = 0;
  for k, v in pairs(goodsSaleTagData) do
    if v.subTag == 0 then
      index = index + 1;
    end 
    
    if index == mainPageIndex and v.subTag == subPageIndex then
      return string.Get(v.name);
    end   
  end
  
  return "";
end

function Mall.IsMoneyEnough(sellData, count)
  return Role.CheckCurrency(sellData.currencyKind, sellData.saleCredit * count);
end

--抽卡
function Mall.GetDoroKind()
  return doroKind;
end

function Mall.SetDoroKind(kind)
  doroKind = kind
end

function Mall.GetDoroCount()
  return table.Count(doroData);
end

function Mall.GetDoroItem(index)
  return doroData[index].item;
end

function Mall.GetDoroItemCount(index)
  return doroData[index].count;
end

function Mall.ClearDoroData(index)
  doroKind = this.EDoroKind.Empty;
  doroData = {};
end

function Mall.IsDoroWarrior(kind)
  --物品表38攔 1武將 2 卡片 3高級家具扭蛋幣 4一般家具扭蛋幣
  return kind == 1;
end

function Mall.IsCuRare(npcData)
  return npcData.rare <= 3;
end

function Mall.IsAgRare(npcData)
  return npcData.rare == 4;
end

function Mall.IsAuRare(npcData)
  return npcData.rare == 5;
end

function Mall.IsCuItemRare(item)
  return item.quality <= 1;
end

function Mall.IsAgItemRare(item)
  return item.quality == 2;
end

function Mall.IsAuItemRare(item)
  return item.quality >= 3;
end

function Mall.GetEggCareerName(element)
  if element ~= nil then--無
    return "EggCareer_"..element;
  end

  return "";
end

function Mall.GetCardRareName(npcData)
  if npcData.rare <= 3 then
    return "CardSmallFrame_0";
  elseif npcData.rare == 4 then
    return "CardSmallFrame_1";
  elseif npcData.rare == 5 then
    return "CardSmallFrame_2";
  end

  return "";
end

function Mall.GetEggRareName(npcData)
  if npcData.rare <= 3 then
    return "EggStarLv_0";
  elseif npcData.rare == 4 then
    return "EggStarLv_1";
  elseif npcData.rare == 5 then
    return "EggStarLv_2";
  end

  return "";
end

function Mall.GetItemRareName(item)
  if item.quality <= 1 then
    return "EggStarLv_0";
  elseif item.quality == 2 then
    return "EggStarLv_1";
  elseif item.quality >= 3 then
    return "EggStarLv_2";
  end

  return "";
end

function Mall.IsCanBuy(currentStoreKind, sellData)
  if currentStoreKind == EStoreKind.NTD2 then
    if sellData.saleCredit == 1 then
      return false;
    elseif sellData.saleCredit == 110 then --暢遊不擋購買上限
      return true;
    elseif sellData.saleCredit == 112 then --半月卡
      return StoreValue.IsHalfMonthCardCanBuy();
    end
  end

  if sellData.memo == 6 and not StoreValue.IsHaveHalfMonthCard() then
    ShowCenterMessage( string.Get(21844) );
    return false;
  end

  if not Network.CheckServerSwitch(EServerSwitch.Review) and sellData.dayCount ~= 0 and RoleCount.Get(sellData.flag) >= sellData.dayCount then --不是送審才檢查每日限購 
    local itemData = itemDatas[sellData.Id];

    if itemData ~= nil then
      ShowCenterMessage(string.Concat(itemData:GetName(true), string.Get(20709)));
    end

    return false;
  end

  return true;
end

local checkDoro = false;
local checkMall = false;
function Mall.ClearDoroCheck()
  checkDoro = false;
  checkMall = false;
end

function Mall.SetDoroCheck()
  checkDoro = true;
end

function Mall.IsDoroCheck()
  return checkDoro;
end

function Mall.SetMallCheck()
  checkMall = true;
end

function Mall.IsMallCheck()
  if activityTimes[78] ~= nil and activityTimes[78][1] ~= nil and activityTimes[78][1]:IsActive() then
    return checkMall;
  else
    return true;
  end
end

function Mall.IsRedDot()
  if not Mall.IsDoroCheck() then
    RedDot.Add(ERedDot.Mall);
    return true;
  end

  if Mall.IsGSSavePointCanGet() then
    RedDot.Add(ERedDot.Mall);
    return true;
  end

  if not Mall.IsMallCheck() then
    RedDot.Add(ERedDot.Mall);
    return true;
  end

  RedDot.Remove(ERedDot.Mall);
end

function Mall.IsGSSavePointCanGet()
  local gsActivity = GSActivityData.GetActivityData(EGSActivityKind.SavePoint);
  if gsActivity == nil then return end

  for idx = 1, 4 do
    if gsActivity.buttons[idx].getKind == 1 then
      if RoleCount.Get(708) >= gsActivity.buttons[idx].value and not BitFlag.Get( gsActivity.buttons[idx].flag ) then
        return true;
      end
    end
  end

  return false;
end

--收送協定
function Mall.SendBuyItem(storeKind, mainPageIndex, subPageIndex, showItemIndex, count, doro, free)
  local sellData = this.GetSellData(showItemIndex);
  if sellData == nil then
    return;
  end
  
  local purchaseFree = 0;
  if (free ~= nil) and free then
    purchaseFree = 1;
  end

  if ((purchasFree == 1) and (not UIPurchaseFree.IsMoneyEnough(sellData.saleCredit*count))) 
    or ((purchasFree == 0) and (not Mall.IsMoneyEnough(sellData, count))) then    
    ShowCenterMessage(string.Concat(string.Get(90152), itemDatas[sellData.Id]:GetName(true), string.Get(90153)));
    return;
  end

  local pageItemIndex = this.GetPageItemIndex(storeKind, mainPageIndex, subPageIndex, sellData);

  local showKind = Mall.GetTagShowKind(storeKind, mainPageIndex, subPageIndex);

  if doro then
    Mall.Doro();
  end

  --C:066-001 <購買虛寶> +商店種類(1) +主項(1) +細項(1) +品項(1) +ID(2) +點數(2) +組(2)
  sendBuffer:Clear()
  sendBuffer:WriteByte(storeKind);
  sendBuffer:WriteByte(mainPageIndex);
  sendBuffer:WriteByte(showKind);
  sendBuffer:WriteByte(pageItemIndex);
  sendBuffer:WriteUInt16(sellData.Id);
  sendBuffer:WriteUInt16(sellData.saleCredit);
  sendBuffer:WriteUInt16(count);
  sendBuffer:WriteByte(purchaseFree);
  Network.Send(66, 1, sendBuffer);
end

function Mall.ReciveDoroData(data)
  if not Mall.IsMallDoro() then return end

  local buyState = buyThing; -- 關閉抽卡介面會清購買狀態

  if UI.IsVisible(UIDoro) then
    UI.Close(UIDoro);
  end

  buyThing = buyState;

  doroData = {};
  
  local count = data:ReadUInt16();
  local itemId = 0;
  local itemCount = 0;
  local item = nil;
  for i = 1, count do
    itemId = data:ReadUInt16();
    itemCount = data:ReadUInt32();
    item = itemDatas[itemId];
    doroData[i] = {};
    doroData[i].item = item;
    doroData[i].count = itemCount;
  end

  UI.Open(UIDoro);
end