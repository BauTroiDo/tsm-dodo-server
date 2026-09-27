Mounts = {}
local this = Mounts;

local levelUpItemId = 0;
local atkUpItemId = 0;
local intUpItemId = 0;
local defUpItemId = 0;
local hpUpItemId = 0;
local spUpItemId = 0;

this.level = 1;

local attributePoints = {};
local attributeLvs = {};
for i = 1, 6 do
  attributePoints[i] = 0;
  attributeLvs[i] = 0;
end

local attributes = {};

this.currentNpcId = 0;

this.collectionCount = 0;
this.collectionBonus = 1;

function Mounts.Clear()
  this.level = 1;

  for i = 1, 6 do
    attributePoints[i] = 0;
    attributeLvs[i] = 0;
  end

  table.Clear(attributes);

  this.currentNpcId = 0;

  this.collectionCount = 0;
  this.collectionBonus = 1;
end

--Set
function Mounts.SetData(data)
  --S:079-001 <座騎資料> +等級(1) +點數(2)*6 +數量(1) <<+物品資料(16)>> +NPCID(2)
  this.level = data:ReadByte();

  for i = 1, 6 do
    this.SetAttributePoint(i, data:ReadUInt16(), false);
  end

  local count = data:ReadByte();  
  for i = 1, count do
    local itemSave = ThingData.New(data);

    Item.SetBagItem(EThings.Mounts, itemDatas[itemSave.Id].fitType, itemSave, false);
  end

  this.currentNpcId = data:ReadUInt16();

  this.OnGetItem();
end

function Mounts.SetLevel(level)
  this.level = level;

  ShowCenterMessage(string.Get(20187));

  if UI.IsVisible(UIMounts) then
    UIMounts.UpdateUI();
  end

  if Role.player ~= nil then
    Role.player:UpdateSpeed();
  end
end

function Mounts.SetAttributePoint(kind, value, updateOther)
  local addValue = value - attributePoints[kind];
  local oriLevel = attributeLvs[kind];
  local nowLevel = this.GetAttributeProgress(kind, value);

  attributePoints[kind] = value;
  attributeLvs[kind] = nowLevel;

  local attributeValue = 0;
  if mountsGrowDatas[nowLevel] ~= nil then
    attributeValue = mountsGrowDatas[nowLevel].attributes[kind].addValue;
  end

  if kind == 1 then
    this.SetAttribute(EAttribute.Atk, attributeValue);
  elseif kind == 2 then
    this.SetAttribute(EAttribute.Int, attributeValue);
  elseif kind == 3 then
    this.SetAttribute(EAttribute.Def, attributeValue);
  elseif kind == 4 then
    this.SetAttribute(EAttribute.ExtraHp, attributeValue);
  elseif kind == 5 then
    this.SetAttribute(EAttribute.ExtraSp, attributeValue);
  end

  if updateOther == false then return end

  if UI.IsVisible(UIMounts) then
    UIMounts.OnAttributeAdd(kind, addValue, attributeLvs[kind] - oriLevel);
  end
end

function Mounts.GetAttributePoint(kind)
  if attributePoints[kind] ~= nil then
    return attributePoints[kind];
  else
    return 0;
  end
end

function Mounts.SetCurrent(npcId)
  if this.currentNpcId == npcId then return end

  if this.currentNpcId ~= 0 then
    ShowCenterMessage(string.Get(20189));
  end

  this.currentNpcId = npcId;
  
  if UI.IsVisible(UIMounts) then
    UIMounts.UpdateUI();
  end

  Function.UpdateVisible();
end

function Mounts.UpdateCollection()
  this.collectionCount = 0;
  this.collectionBonus = 1;

  for npcId in pairs(mountsDatas) do
    if this.HaveCollection(npcId) then
      this.collectionCount = this.collectionCount + 1;
    end
  end

  this.collectionBonus = 1 + (0.01 * this.collectionCount);

  if Role.player ~= nil then
    Role.player:SetAttribute(EAttribute.MaxHp, Calc.GetMaxHp(Role.player));
    Role.player:SetAttribute(EAttribute.MaxSp, Calc.GetMaxSp(Role.player));
  end

  if UI.IsVisible(UIMounts) then
    UIMounts.UpdateUI();
  end
end

--Get
function Mounts.GetSpeed(level)
  if mountsGrowDatas[level] ~= nil then
    return mountsGrowDatas[level].speed;
  else
    return 0;
  end
end

function Mounts.GetAttributeProgress(kind, point)
  local needPoint = 0;
  local level = 0;

  for k, v in pairs(mountsGrowDatas) do
    if v.attributes[kind] == nil then
      break;
    end

    needPoint = v.attributes[kind].upItemCount;
    
    if point < needPoint then
      break;
    end
    
    level = k;
    point = point - needPoint;
  end
  
  return level, point, needPoint;
end

function Mounts.SetAttribute(kind, value, arg)
  attributes[kind] = value;

  if Role.player ~= nil then
    if Contains(kind, EAttribute.Hpx, EAttribute.ExtraHp, EAttribute.EquipMaxHp, EAttribute.EquipHpx) then
      Role.player:SetAttribute(EAttribute.MaxHp, Calc.GetMaxHp(Role.player));
    elseif Contains(kind, EAttribute.Spx, EAttribute.ExtraSp, EAttribute.EquipMaxSp, EAttribute.EquipSpx) then
      Role.player:SetAttribute(EAttribute.MaxSp, Calc.GetMaxSp(Role.player));
    end
  end
end

function Mounts.GetAttribute(kind)
  if attributes[kind] ~= nil then
    return attributes[kind];
  else
    return 0;
  end
end

function Mounts.HaveCollection(npcId)
  if mountsDatas[npcId] ~= nil then
    return BitFlag.Get(mountsDatas[npcId].flagId);
  else
    return false;
  end
end

function Mounts.OnMoneyChange()
  if mountsGrowDatas[this.level] ~= nil then
    if mountsGrowDatas[this.level].upItemId > 0 and mountsGrowDatas[this.level].upItemCount > 0 then
      if Item.GetItemCount(mountsGrowDatas[this.level].upItemId) >= mountsGrowDatas[this.level].upItemCount and Role.GetPlayerGold() >= mountsGrowDatas[this.level].upMoney then
        RedDot.Add(ERedDot.MountLevel);
      else
        RedDot.Remove(ERedDot.MountLevel);
      end
    end
  end

  if UI.IsVisible(UIMounts) then
    UIMounts.UpdateUI();
  end
end

function Mounts.OnGetItem(itemId)
  if mountsGrowDatas[this.level] ~= nil then
    if mountsGrowDatas[this.level].upItemId > 0 and mountsGrowDatas[this.level].upItemCount > 0 then
      if itemId == nil or itemId == mountsGrowDatas[this.level].upItemId then
        if Item.GetItemCount(mountsGrowDatas[this.level].upItemId) >= mountsGrowDatas[this.level].upItemCount and Role.GetPlayerGold() >= mountsGrowDatas[this.level].upMoney then
          RedDot.Add(ERedDot.MountLevel);
        end
      end
    end
  end

  for i = 1, 5 do
    local attributeLv, value, maxValue = this.GetAttributeProgress(i, attributePoints[i]);

    if attributeLv < this.level then
      if mountsGrowDatas[attributeLv + 1] ~= nil then
        if (itemId ~= nil and itemId == mountsGrowDatas[attributeLv + 1].attributes[i].upItemId) or
          (itemId == nil and Item.GetItemCount(mountsGrowDatas[attributeLv + 1].attributes[i].upItemId) > 0) then
          RedDot.Add(ERedDot.MountAttribute);
          break;
        end
      end
    end
  end

  if UI.IsVisible(UIMounts) then
    UIMounts.UpdateUI();
  end
end

function Mounts.OnLostItem(itemId)
  local haveItem = false;
  local growData = mountsGrowDatas[this.level];
  local count = 0;
  local bag = Item.GetBag(EThings.Bag);

  if growData ~= nil then
    if growData.upItemId > 0 and growData.upItemCount > 0 then
      if itemId == nil or itemId == growData.upItemId then
        if Role.GetPlayerGold() >= growData.upMoney then
          if bag ~= nil then
            for k, v in pairs(bag) do
              if v ~= nil and v.Id == growData.upItemId and v.quant > 0 then
                count = count + v.quant;
              end
            end
          end

          if count >= growData.upItemCount then
            haveItem = true;
          end
        end
      end
    end
  end

  if not haveItem then
    RedDot.Remove(ERedDot.MountLevel);
  end
  
  haveItem = false;
  for i = 1, 5 do
    if growData ~= nil then
      count = 0;
      local attributeLv, value, maxValue = this.GetAttributeProgress(i, attributePoints[i]);
      growData = mountsGrowDatas[attributeLv + 1];
      
      if growData ~= nil then
        if bag ~= nil then
          for k, v in pairs(bag) do
            if v ~= nil and v.Id == growData.attributes[i].upItemId and v.quant > 0 then
              count = count + v.quant;
            end
          end
        end
        
        if count >= growData.attributes[i].upItemCount then
          haveItem = true;
          break;
        end
      end
    end
  end

  if not haveItem then
    RedDot.Remove(ERedDot.MountAttribute);
  end

  if UI.IsVisible(UIMounts) then
    UIMounts.UpdateUI();
  end
end

function Mounts.OnGetCollection(flagId)
  this.UpdateCollection();

  RedDot.Add(ERedDot.MountCollect);

  if UI.IsVisible(UIMounts) then
    UIMounts.UpdateUI();
  end

  ShowCenterMessage(string.format(string.Get(10398), this.GetMountsName(flagId)));  
end

function Mounts.GetMountsName(flagId)
  for k, v in pairs(mountsDatas) do
    if v.flagId == flagId then
      if npcDatas[v.npcId] ~= nil then
        return npcDatas[v.npcId].name;
      end
    end
  end

  return "";
end

--Function
function Mounts.LevelUp()
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  local mountsLv = Mounts.level;

  if mountsGrowDatas[mountsLv] == nil then return end

  if mountsLv >= UIVIP.GetVIPLevel(EVIPPermission.Mount) + 10 then
    ShowCenterMessage(string.Get(25062));
    return;
  end

  if mountsGrowDatas[mountsLv + 1] == nil then
    ShowCenterMessage(string.Get(20582));
    return;
  end

  if itemDatas[mountsGrowDatas[mountsLv].upItemId] ~= nil then
    if Item.GetItemCount(mountsGrowDatas[mountsLv].upItemId) < mountsGrowDatas[mountsLv].upItemCount then
      ShowCenterMessage(string.Concat(itemDatas[mountsGrowDatas[mountsLv].upItemId]:GetName(true), string.Get(20075)));
      return;
    end
  end

  if Role.GetPlayerGold() < mountsGrowDatas[mountsLv].upMoney then
    ShowCenterMessage(string.Get(40077));
    return;
  end

  --C:079-003 <座騎升級> +背包索引(1)
  sendBuffer:Clear();
  sendBuffer:WriteByte(Item.GetItemIndex(mountsGrowDatas[mountsLv].upItemId));
  Network.Send(79, 3, sendBuffer);
end

function Mounts.AttributeUp(kind, showMessage, justCheck)
  justCheck = justCheck or false;

  local mountsLv = this.level;
  local attributeLv = attributeLvs[kind];
  
  if mountsGrowDatas[attributeLv + 1] == nil then
    if showMessage then
      ShowCenterMessage(string.Get(20582));
    end

    return false;
  end

  if attributeLv >= mountsLv then
    if showMessage then
      ShowCenterMessage(string.Get(20494));
    end

    return false;
  end

  local itemIndex = Item.GetItemIndex(mountsGrowDatas[attributeLv + 1].attributes[kind].upItemId);
  if itemIndex == 0 then
    if showMessage then
      ShowCenterMessage(string.Concat(itemDatas[mountsGrowDatas[attributeLv + 1].attributes[kind].upItemId]:GetName(true), string.Get(20075)));
    end

    return false;
  end
  
  if not justCheck then
    --C:079-004 <座騎投點> +種類(1) +背包索引(1)
    sendBuffer:Clear();
    sendBuffer:WriteByte(kind);
    sendBuffer:WriteByte(itemIndex);
    Network.Send(79, 4, sendBuffer);
  end

  return true;
end

function Mounts.Mount()
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  --[[
  if Role.player.npcId ~= 0 then
    ShowCenterMessage(string.Get(50079));
    return;
  end
  --]]
  
  --C:079-001 <上座騎>
  Network.Send(79, 1);
end

function Mounts.Dismount()
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end
  
  --C:079-002 <下座騎>
  Network.Send(79, 2);
end