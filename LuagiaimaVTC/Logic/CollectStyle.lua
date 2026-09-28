StyleEquipData = {};
StyleEquipData._index = StyleEquipData;

function StyleEquipData.New(id, flag)
  local self = {};
  self.id = id;
  self.equiped = {};
  for i = 1, 5 do 
    self.equiped[i] = CheckFlag(flag, i);
  end
  return self;
end

CollectStyle = {}
CollectStyle._index = CollectStyle;
local this = CollectStyle;
local MAX_EQUIPCOUNT = 5;
local equipedData = {};
local nowPoint = 0;
local attributes = {};
local storeStyleId = 0;
local dataCount;

function CollectStyle.Clear()
  table.Clear(equipedData);
  nowPoint = 0;
  storeStyleId = 0;
  table.Clear(attributes);
end

function CollectStyle.InitData(data)
  table.Clear(equipedData);
  dataCount = data:ReadByte();
  for i = 1, dataCount do
    equipedData[i] = StyleEquipData.New(i, data:ReadByte());    
  end
  this.SaveOutfitCollecttionIntoBag();
  UICollectBook.CheckStyleRedDot();
  this.InitNowPoint(false);
end

function CollectStyle.CheckEquiped(id, index) --index = 部位
  if equipedData[id] == nil then 
    return false;
  end
  return equipedData[id].equiped[index];
end

function CollectStyle.InitNowPoint(showMessage)
  if showMessage == nil then 
    showMessage = true;
  end

  local lastPoint = nowPoint;
  nowPoint = 0;
  for k, v in pairs(equipedData)do 
    local allGet = true;
    for i = 1, MAX_EQUIPCOUNT do 
      if collectStyleDatas[v.id] ~= nil and collectStyleDatas[v.id].itemId[i] ~= 0 then 
        if v.equiped[i] == true then 
          nowPoint = nowPoint + collectStyleDatas[v.id].itemScore[i];
        else
          allGet = false;
        end
      end
    end
    if allGet then  --全蒐集加成
      nowPoint = nowPoint + collectStyleDatas[v.id].itemScore[6];
    end
  end
  
  if showMessage then 
    if lastPoint > nowPoint then 
      ShowCenterMessage(string.format(string.Get(21417), lastPoint-nowPoint));
    else
      ShowCenterMessage(string.format(string.Get(21412), nowPoint-lastPoint));
    end
  end

  this.UpdateAttribute();
  --todo 更新人物資料
  if UI.IsVisible(UICollectBook)then 
    UICollectBook.UpdateInfo();
  end
end

function CollectStyle.UpdateData(data)
  local index = data:ReadByte();
  local flag = data:ReadByte();

  equipedData[index] = StyleEquipData.New(index, flag);

  this.InitNowPoint();
  UICollectBook.CheckStyleRedDot();
end

function CollectStyle.GetNowPoint()  
  return nowPoint;
end

function CollectStyle.GetAttribute(kind)
  if attributes[kind] == nil then 
    return 0;
  else
    return attributes[kind];
  end  
end

function CollectStyle.GetAllAb()
  local ab = {};
  for k, v in pairs(attributes)do 
    if v ~= nil and k ~= 0 then 
      ab[k] = v;
    end
  end
  return ab;
end

function CollectStyle.GetCollectCount(id)
  local data = collectStyleDatas[id];
  if data == nil or equipedData[id] == nil then 
    return 0, 0;
  end

  local count = 0;
  local equipCount = 0;
  for k, v in pairs(equipedData[id].equiped)do
    if collectStyleDatas[id].itemId[k] ~= 0 then 
      if v == true then 
        equipCount = equipCount + 1;
      end    
      count = count + 1;
    end
  end  
  return equipCount, count;
end

function CollectStyle.UpdateAttribute()
  table.Clear(attributes);
  for k, v in pairs(collectStyleValueDatas)do
    if v.score <= nowPoint then 
      for i = 1, 4 do 
        if attributes[v.attribute[i].kind] == nil then 
          attributes[v.attribute[i].kind] = 0;
        end
        attributes[v.attribute[i].kind] = attributes[v.attribute[i].kind] + v.attribute[i].value;
      end      
    end    
  end

  if Role.player ~= nil then
    Role.player:SetAttribute(EAttribute.MaxHp, Calc.GetMaxHp(Role.player));
    Role.player:SetAttribute(EAttribute.MaxSp, Calc.GetMaxSp(Role.player));
  end

  local npcs = Role.GetFollowNpcs(Role.playerId);
  for k,v in pairs(npcs)do   
    v:SetAttribute(EAttribute.MaxHp, Calc.GetMaxHp(v));
    v:SetAttribute(EAttribute.MaxSp, Calc.GetMaxSp(v));
  end
end

function CollectStyle.SendStoreStyle(id, index) --時裝id, 部位
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  sendBuffer:Clear();
  sendBuffer:WriteUInt16(id);  
  sendBuffer:WriteByte(index);  
  Network.Send(95, 2, sendBuffer);
  storeStyleId = collectStyleDatas[id].itemId[index];

  UI.Close(UIItemInfo);
end

function CollectStyle.ReciveStoreStyle()
  if storeStyleId ~= 0 and itemDatas[storeStyleId] ~= nil then
    ShowCenterMessage(string.format(string.Get(21411), itemDatas[storeStyleId]:GetName(true)));
    Item.SetBagItem(EThings.OutfitStyleInCollection, Item.GetBagCount(EThings.OutfitStyleInCollection) + 1,  itemDatas[storeStyleId], true);
  end
  storeStyleId = 0;
end

function CollectStyle.SendTakeStyleOut(id, index) --時裝id, 部位
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  sendBuffer:Clear();
  sendBuffer:WriteUInt16(id);  
  sendBuffer:WriteByte(index);  
  Network.Send(95, 3, sendBuffer);
  storeStyleId = collectStyleDatas[id].itemId[index];

  UI.Close(UIItemInfo);
end

function CollectStyle.ReciveTakeStyleOut()
  if storeStyleId ~= 0 and itemDatas[storeStyleId] ~= nil then
    ShowCenterMessage(string.format(string.Get(21416), itemDatas[storeStyleId]:GetName(true)));
    Item.DelBagItem(EThings.OutfitStyleInCollection, Item.GetItemIndex(storeStyleId,EThings.OutfitStyleInCollection))
  end
  storeStyleId = 0;
end

function CollectStyle.SaveOutfitCollecttionIntoBag()
  local styleCollection = {};
    for k, v in pairs(equipedData)do
      for i = 1, 5 do
          if  v.equiped[i] and collectStyleDatas[k].itemId[i] ~= 0 then
            table.insert(styleCollection,collectStyleDatas[k].itemId[i])
          end
      end
    end
  local totalCount = table.Count(styleCollection);

  for index = 1,totalCount do
    Item.SetBagItem(EThings.OutfitStyleInCollection, index, itemDatas[styleCollection[index]], true);
  end
end

function CollectStyle.GetTotalCollectionDataCount()
  if dataCount == nil then
    return 0
  end
  
  return dataCount;
end

function CollectStyle.GetItemId(collectId,index)
  local itemId = collectStyleDatas[collectId].itemId[index];
  if itemId == nil then return end
  return itemId;
end

function CollectStyle.SendUnEquipOutfitStyle(index)
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end
  
  --C:023-125 <脫顯示時裝外觀>+ 部位索引(1)
  sendBuffer:Clear();
  sendBuffer:WriteByte(index);
  Network.Send(23, 125, sendBuffer);
end

function CollectStyle.SendEquipOutfitStyle(id, index) --收集冊ID, 部位
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end
  
  --C:023-124 <穿顯示時裝外觀>+ 收集冊ID(2) + 部位索引(1)
  sendBuffer:Clear();
  sendBuffer:WriteUInt16(id);
  sendBuffer:WriteByte(index);
  Network.Send(23, 124, sendBuffer);
end