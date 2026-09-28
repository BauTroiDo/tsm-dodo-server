--物品存放位置種類
EThings = {
  None = 0,
  Bag = 1,
  Bank = 4,
  Warship = 6,
  Equip = 8,
  Vender = 11,
  Mounts = 17,
  Chat = 18,
  Category = 19,
  Filter = 20,
  BankCategory = 21,
  ChatNpc = 22,
  Compound = 23,
  NpcStore = 24,
  Furnace = 25,
  SlotMachine = 26,
  LockBlissBag = 27,
  Poke = 28,
  WeaponRecipe = 29,
  Storage = 30,
  StorageCategory = 31,
  OutfitStyleInCollection = 32,
  UniqueBlood = 33,
  UniqueBloodEquip = 34,
  ArmyPoke = 253,
  EvolutionSelect = 254,
  LuckyBag = 255,
}

EThingsCategory = {
  None = 0,
  All = 1,       --全顯示
  Equip = 2,     --裝備
  Props = 3,     --道具
  Material = 4,  --材料
}

EThingsDurability = {
  Perfect = 0,
  Good = 100,
  Normal = 200,
  Damaged = 250,
}

ThingData = {}
ThingData.__index = ThingData;

function ThingData.New(data)
  local self = {};
  setmetatable(self, ThingData);

  self.Id = 0; --物品編號
  self.quant = 0; --物品數量
  self.damage = 0; --物品毀壞度
  self.element = 0; --屬性
  self.elementValue = 0; --屬性值
  self.proofKind = 0; --抵抗

  self.growLv = 0; --成長等級
  self.growExp = 0; --成長經驗值

  self.isVender = false; --擺攤中
  self.isDeliver = false; --快遞中
  self.specialKind = 0; --特殊作用 0:無 1:可交易1次

  self.stoneAttr = 0; --靈石屬性
  self.stoneLv = 0; --靈石強化等級 
  self.enhanceLv = 0; --專武強化等級
  self.delTime = 0; --物品刪除時間
  self.damagedItemId = 0; --損壞前物品ID
  self.isLock = false; --鎖定
  self.Reinforced = 0; --升階等級
  self.affix1 = 0; --洗鍊等級
  self.affix2 = 0; --洗鍊等級
  self.affix3 = 0; --洗鍊等級
  self.styleLv = 0; --時裝強化等級

  if data ~= nil then
    self.Id = data:ReadUInt16(); --物品編號(2)    
    self.quant = data:ReadInt32(); --物品數量(4)   
    self.damage = data:ReadByte(); --物品毀壞度(1)  
    self.element = data:ReadByte(); --屬性(1)
    self.elementValue = data:ReadByte();--屬性值(1)
    self.proofKind = data:ReadByte(); --抵抗(1)
    self.growLv = data:ReadByte(); --成長等級(1)
    self.growExp = data:ReadInt32(); --成長經驗值(4)
    self.specialKind = data:ReadByte(); --特殊作用 0:無 1:可交易1次(1)
    self.stoneAttr = data:ReadByte(); --靈石屬性(1)
    self.stoneLv = data:ReadByte(); --靈石強化等級(1)
    self.enhanceLv = data:ReadByte(); --專武強化等級(1)
    self.delTime = data:ReadDouble(); --物品刪除時間
    self.damagedItemId = data:ReadUInt16(); --損壞前物品ID
    self.isLock = data:ReadBoolean(); --鎖定
    self.Reinforced = data:ReadByte(); --升階等級
    self.affix1 = data:ReadByte(); --洗鍊等級
    self.affix2 = data:ReadByte(); --洗鍊等級
    self.affix3 = data:ReadByte(); --洗鍊等級
    self.styleLv = data:ReadByte(); --時裝強化等級
  end

  return self;
end

function ThingData.Copy(data)
  local self = {};
  setmetatable(self, ThingData);

  self.Id = data.Id; --物品編號
  self.quant = data.quant; --物品數量
  self.damage = data.damage; --物品毀壞度
  self.element = data.element; --屬性
  self.elementValue = data.elementValue; --屬性值
  self.proofKind = data.proofKind; --抵抗

  self.growLv = data.growLv; --成長等級
  self.growExp = data.growExp; --成長經驗值

  self.isVender = data.isVender; --擺攤中
  self.isDeliver = data.isDeliver; --快遞中
  self.specialKind = data.specialKind; --特殊作用 0:無 1:可交易1次

  self.stoneAttr = data.stoneAttr; --靈石屬性
  self.stoneLv = data.stoneLv; --靈石強化等級
  self.enhanceLv = data.enhanceLv; --專武強化等級
  self.delTime = data.delTime; --物品刪除時間
  self.damagedItemId = data.damagedItemId; --損壞前物品ID
  self.isLock = data.isLock; --鎖定
  self.Reinforced = data.Reinforced; --升階等級
  self.affix1 = data.affix1; --洗鍊等級
  self.affix2 = data.affix2; --洗鍊等級
  self.affix3 = data.affix3; --洗鍊等級
  self.styleLv = data.styleLv; --時裝強化等級
  return self;
end

function ThingData:WriteBuffer(buffer)
  buffer:WriteUInt16(self.Id); --物品編號(2)    
  buffer:WriteInt32(self.quant); --物品數量(4)   
  buffer:WriteByte(self.damage); --物品毀壞度(1)  
  buffer:WriteByte(self.element); --屬性(1)
  buffer:WriteByte(self.elementValue);--屬性值(1)
  buffer:WriteByte(self.proofKind); --抵抗(1)
  buffer:WriteByte(self.growLv); --成長等級(1)
  buffer:WriteInt32(self.growExp); --成長經驗值(4)
  buffer:WriteByte(self.specialKind); --特殊作用 0:無 1:可交易1次(1)
  buffer:WriteByte(self.stoneAttr); --靈石屬性(1)
  buffer:WriteByte(self.stoneLv); --靈石強化等級(1)
  buffer:WriteByte(self.enhanceLv); --專武強化等級(1)
  buffer:WriteDouble(self.delTime); --物品刪除時間
  buffer:WriteUInt16(self.damagedItemId); --損壞前物品ID
  buffer:WriteBoolean(self.isLock); --鎖定
  buffer:WriteByte(self.Reinforced); --升階等級
  buffer:WriteByte(self.affix1); --洗鍊等級
  buffer:WriteByte(self.affix2); --洗鍊等級
  buffer:WriteByte(self.affix3); --洗鍊等級
  buffer:WriteByte(self.styleLv); --時裝強化等級
end

Item = {}
local this = Item

this.StoneAttrValue = {
  { 1,	2, 3 ,	4, 5 ,	7, 9, 11, 13, 15, 18,	21, 24, 27, 30},
  { 1,	2, 3 ,	4, 5 ,	7, 9, 11, 13, 15, 18,	21, 24, 27, 30},
  { 1,	2, 3 ,	4, 5 ,	7, 9, 11, 13,	15, 18,	21, 24, 27, 30},
  { 1,	2, 3 ,	4, 5 ,	7, 9, 11, 13,	15, 18,	21, 24, 27, 30},
  { 1,	2, 3 ,	4, 5 ,	7, 9 ,	11, 13,	15, 18,	21, 24, 27, 30},
  { 1,	2, 3 ,	4, 5 ,	7, 9 ,	11, 13,	15, 18,	21, 24, 27, 30},
  { 5,	10, 15,	20, 25,	35, 45,	55, 65,	75, 90,	105, 120, 135, 150}
};

--合成規則
--1.靈石數量, 2.祝福靈石, 3.強化機率道具數量, 4.指定強化階層
this.MixStoneTable = {
  {1, 0, 0, 0},   --1.裝備+靈石
  {0, 1, 0, 0},   --2.裝備+祝福靈石
  {1, 0, 1, 0},   --3.裝備+靈石     +強化機率
  {0, 1, 1, 0},   --4.裝備+祝福靈石 +強化機率
  {0, 0, 0, 1}    --5.裝備{已有靈石}+指定階層
};

this.StoneAttrKind = {212, 210, 211, 218, 219, 214, 226};

--const
this.MaxBThingGrid = 250;     --玩家錢莊物品欄幾個
this.MaxStorageGrid = 70;     --玩家寶庫物品欄幾個
this.BaseBThingGrid = 25;     --玩家基本錢莊物品欄數
this.AddBThingGrid = 5        --玩家單次增加錢莊物品欄數
this.MaxThingGrid = 250;      --玩家物品物品欄總數
this.MaxPageItem = 25;        --玩家物品物品欄每頁總數
this.InitBagGrid = 50;        --玩家初始背包格數
this.AddThingGrid = 5;        --玩家單次增加物品欄數
this.MaxStoneLv = 15;         --最大強化等級
this.MaxStoneKind = 4;        --最大靈石種類
this.MaxMixStoneRules = 5;    --最大靈石合成規則數量
this.MaxStoneRateUp = 50;     --最大強化機率提升
this.MaxStoneAttrKind = 7;    --最大靈石屬性種類
this.MaxStoneMixNum = 3;      --最大強化物品數量
this.MaxBlessStoneDamage = 20;--最大祝福靈石強化失敗損壞值
this.MaxStoneFailDamage = 200;--最大靈石強化失敗損壞值
this.MaxStyleMixNum = 3;      --最大強化物品數量
this.MaxSuitAttr = 3;         --最多套裝效果
this.MaxThingGridQuant = 9999;--每格物品欄最放幾個
this.MaxEquip = 6;            --裝備最大數量
this.MaxStyle = 5             --時裝最大數量
this.MaxOutfitStyle = 5;      --外顯時裝最大數量
this.MaxGrowLv = 20;          --武器最大成長值
this.MaxSparExtKind = 2;      --最多可擴充晶石數量
this.durable = 250;           --物品耐久值
this.MaxGod = 255;            --福神上限
this.MaxBossCount = 5;        --世界BOSS挑戰次數
this.MaxFirework = 7;         --歡樂煙火(46075)最大施放量
this.maxTianGuanLv = 0;       --天官裝備強化上限(讀表)
this.maxTurn3ExpPill = 50;    --三轉每日限定使用次數
this.baseUniqueBloodBagGrid = 50; --奇魂初始背包值
this.maxUniqueBloodBagGrid = 200; --奇魂背包上限
this.maxUniqueBloodEquipBagGrid = 200; --經脈
this.UnEquipingIndex = 0;      --正在卸下裝備索引

--Bag
local bagThings = {}; --背包

local bagCategory = {};
local bagFilter = {};
local bankCategory = {};
local storageCategory = {};

local bankThings = {}; --錢莊
local storageThings = {}; --寶庫
local warshipThings = {}; --戰船錢莊
local equipThings = {}; --裝備
equipThings[0] = {}; --玩家裝備
equipThings[1] = {}; --武將1裝備
equipThings[2] = {}; --武將2裝備
equipThings[3] = {}; --武將3裝備
equipThings[4] = {}; --武將4裝備
local venderThings = {}; --擺攤
local mountsThings = {}; --座騎裝備
local chatThings = {}; --聊天裝備
local chatNpcThings = {}; --聊天裝備
local npcStoreThings = {}; --商店販賣
local CompoundThings = {}; --合成
local outfitStyleThings = {}; --外顯時裝
local uniqueBloodThings = {}; --經脈
local uniqueBloodEquipThings = {}; --經脈

local itemRedDot = {};
itemRedDot[EThings.Bag] = {};
itemRedDot[EThings.Bank] = {};
itemRedDot[EThings.Storage] = {};

local itemInUse = {};

this.currentThingGrid = 0;      --玩家目前物品欄數
this.currentBThingGrid = 0;     --玩家目前錢莊物品欄數
this.currentStorageGrid = 0;    --玩家目前寶庫物品欄數
this.currentUniqueBloodGrid = 0;

local callback;

function Item.Clear()
  this.ClearBag(EThings.Bag);
  this.ClearBag(EThings.Bank);
  this.ClearBag(EThings.Storage);
  this.ClearBag(EThings.Warship);
  for i = 0, Role.maxFollowNpc do
    this.ClearBag(EThings.Equip, i);
  end
  this.ClearBag(EThings.Vender);
  this.ClearBag(EThings.Mounts);
  this.ClearBag(EThings.Chat);
  this.ClearBag(EThings.Compound);
  this.ClearBag(EThings.NpcStore);
  this.ClearBag(EThings.OutfitStyleInCollection);
  this.ClearBag(EThings.UniqueBlood);
  this.ClearBag(EThings.UniqueBloodEquip);
end

function Item.GetBagItem(bagKind, bagIndex, followIndex)
  local bag = this.GetBag(bagKind, followIndex);

  if bag == nil then return nil end

  return bag[bagIndex];
end

function Item.GetBagByFilter(filter, kind, bagkind)
  table.Clear(bagFilter);

  local category, items;

  if bagkind ~= nil then
    items = this.GetBag(bagkind);
  else
    items = this.GetBag(EThings.Bag);
  end

  for k, v in pairs(items) do
    if filter(v) then
      if kind == EThingsCategory.Equip then
        category = this.ConditionEquip(v.Id);
      elseif kind == EThingsCategory.Props then
        category = this.ConditionProps(v.Id);
      elseif kind == EThingsCategory.Material then
        category = this.ConditionMaterial(v.Id);
      else
        category = v.Id > 0;
      end

      if category then
        v.bagIndex = k;
        v.redDot = itemRedDot[EThings.Bag][k];
        v.inUse = itemInUse[k];
        v.sort = itemDatas[v.Id].sort;
        table.insert(bagFilter, v);
      end
    end
  end

  table.sort(bagFilter, this.Sort);

  return bagFilter;
end

function Item.GetBagByCategory(kind, bagkind)
  local bag;

  if bagkind ~= nil then
    bag = this.GetBag(bagkind);
  else
    bag = this.GetBag(EThings.Bag);
  end

  if bag == nil then return nil end

  table.Clear(bagCategory);

  local category;

  for k, v in pairs(bag) do
    if v.Id > 0 then
      if kind == EThingsCategory.Equip then
        category = this.ConditionEquip(v.Id);
      elseif kind == EThingsCategory.Props then
        category = this.ConditionProps(v.Id);
      elseif kind == EThingsCategory.Material then
        category = this.ConditionMaterial(v.Id);
      else
        category = v.Id > 0;
      end

      if category then
        v.bagIndex = k;
        v.redDot = itemRedDot[EThings.Bag][k];
        v.inUse = itemInUse[k];
        v.sort = itemDatas[v.Id].sort;
        table.insert(bagCategory, v);
      end
    end
  end

  table.sort(bagCategory, this.Sort);

  return bagCategory;
end

function Item.Sort(a, b)
  if a.sort < b.sort then return true end
  if a.sort == b.sort then
      return a.Id < b.Id;
  end

  return false;
end

function Item.GetBankByCategory(bagKind, kind)
  if not Contains(bagKind, EThings.Bank, EThings.Storage, EThings.UniqueBlood) then return nil end

  local bag = this.GetBag(bagKind);

  if bag == nil then return nil end

  local categoryBag;

  if bagKind == EThings.Bank then
    table.Clear(bankCategory);
    categoryBag = bankCategory;

  elseif bagKind == EThings.Storage then
    table.Clear(storageCategory);
    categoryBag = storageCategory;
  end

  local classification;

  for k, v in pairs(bag) do
    if v.Id > 0 then
      if kind == EThingsCategory.Equip then
        classification = this.ConditionEquip(v.Id);
      elseif kind == EThingsCategory.Props then
        classification = this.ConditionProps(v.Id);
      elseif kind == EThingsCategory.Material then
        classification = this.ConditionMaterial(v.Id);
      else
        classification = v.Id > 0;
      end

      if classification then
        v.bagIndex = k;
        v.redDot = itemRedDot[bagKind][k];
        v.sort = itemDatas[v.Id].sort;
        table.insert(categoryBag, v);
      end
    end
  end

  table.sort(categoryBag, this.Sort);

  return categoryBag;
end

function Item.GetBag(bagKind, followIndex)
  followIndex = followIndex or 0;

  if bagKind == EThings.Bag then
    return bagThings, this.MaxThingGrid, this.MaxThingGridQuant;

  elseif bagKind == EThings.Bank then
    return bankThings, this.MaxBThingGrid, this.MaxThingGridQuant;

  elseif bagKind == EThings.Storage then
    return storageThings, this.MaxStorageGrid, this.MaxThingGridQuant;

  elseif bagKind == EThings.Warship then
    return warshipThings, this.MaxThingGrid, this.MaxThingGridQuant;

  elseif bagKind == EThings.Equip then
    return equipThings[followIndex], this.MaxEquip + this.MaxStyle +this.MaxOutfitStyle, 1;

  elseif bagKind == EThings.Vender then
    return venderThings, this.MaxThingGrid, this.MaxThingGridQuant;

  elseif bagKind == EThings.Mounts then
    return mountsThings, this.MaxEquip, 1;

  elseif bagKind == EThings.Chat then
    return chatThings, Chat.maxItem, 1;

  elseif bagKind == EThings.Category then
    return bagCategory, this.MaxBThingGrid, this.MaxThingGridQuant;

  elseif bagKind == EThings.Filter then
    return bagFilter, this.MaxBThingGrid, this.MaxThingGridQuant;

  elseif bagKind == EThings.BankCategory then
    return bankCategory, this.MaxBThingGrid, this.MaxThingGridQuant;

  elseif bagKind == EThings.StorageCategory then
    return storageCategory, this.MaxThingGrid,1;

  elseif bagKind == EThings.ChatNpc then
    if chatNpcThings[followIndex] == nil then
      chatNpcThings[followIndex] = {};
    end

    return chatNpcThings[followIndex], this.MaxEquip, 1;

  elseif bagKind == EThings.Compound then
    return CompoundThings, this.MaxThingGrid, this.MaxThingGridQuant;

  elseif bagKind == EThings.NpcStore then
    return CompoundThings, this.MaxThingGrid, this.MaxThingGridQuant;
  
  elseif bagKind == EThings.OutfitStyleInCollection then
    return outfitStyleThings,CollectStyle.GetTotalCollectionDataCount()*5 ,1;
  
  elseif bagKind == EThings.UniqueBlood then
    return uniqueBloodThings, this.maxUniqueBloodBagGrid, 1;

  elseif bagKind == EThings.UniqueBloodEquip then
    return uniqueBloodEquipThings, this.maxUniqueBloodEquipBagGrid, 1;

  else
    return nil, 0, 0;
  end
end

function Item.GetBagCount(bagKind, followIndex)
  local bag = this.GetBag(bagKind, followIndex);

  if bag == nil then return 0 end

  return table.Count(bag);
end

function Item.GetBagLeftCount(bagKind)
  if bagKind == EThings.Bag then
    return this.currentThingGrid - this.GetBagCount(EThings.Bag, 0);
  elseif bagKind == EThings.Bank then
    return this.currentBThingGrid - this.GetBagCount(EThings.Bank, 0);
  elseif bagKind == EThings.Storage then
    return this.currentStorageGrid - this.GetBagCount(EThings.Storage, 0);
  elseif bagKind == EThings.UniqueBlood then
    return this.currentUniqueBloodGrid - this.GetBagCount(EThings.UniqueBlood, 0);
  end

  return 0;
end

function Item.SetItemRedDot(bagkind, index)
  if not (bagkind == EThings.Bag or bagkind == EThings.Bank or bagkind == EThings.Storage) then return end

  itemRedDot[bagkind][index] = true;
end

function Item.SetItemInUse(index)
  itemInUse[index] = true;

  if UI.IsVisible(UIBag) then
    UIBag.UpdateUI();
  end
end

function Item.ClearAllInUse()
  for k, v in pairs(itemInUse) do
    if itemInUse[k] then
      itemInUse[k] = false;
    end
  end

  if UI.IsVisible(UIBag) then
    UIBag.UpdateUI();
  end
end

function Item.ClearRedDot()
  for k, v in pairs(itemRedDot) do
    for k1, v1 in pairs(itemRedDot[k]) do
      if itemRedDot[k] then
        itemRedDot[k][k1] = false;
      end
    end
  end
end

function Item.UpdateUI(bagKind, followIndex, isAdd)
  --更新介面
  if bagKind == EThings.Bag then
    if UI.IsVisible(UIBag) then
      UIBag.UpdateUI();
    end

    if UI.IsVisible(UIMiniMap) then
      UIMiniMap.UpdateMission();
    end

    if UI.IsVisible(UIMission) then
      UIMission.UpdateMission();
    end

    if UI.IsVisible(UIHotkey) then
      UIHotkey.UpdateUI();
    end

  elseif bagKind == EThings.Bank or bagKind == EThings.Storage then
    if UI.IsVisible(UIBank) then
      UIBank.UpdateUI();
    end
  elseif bagKind == EThings.Warship then
  elseif bagKind == EThings.Equip then
    if UI.IsVisible(UIStatus) then
      UIStatus.UpdateUI();

      this.ShowEquipMessage(isAdd, followIndex);
    end

    if UI.IsVisible(UIHotkey) then
      UIHotkey.UpdateUI();
    end

  elseif bagKind == EThings.Mounts then
    if UI.IsVisible(UIMounts) then
      UIMounts.UpdateUI();

      this.ShowEquipMessage(isAdd);
    end
  elseif bagKind == EThings.OutfitStyleInCollection then
    if UI.IsVisible(UIStatus) then
      UIStatus.UpdateUI();
    end
  elseif bagKind == EThings.Chat then
  end
end

--設定某背包物品
function Item.SetBagItem(bagKind, bagIndex, itemSave, updateOther, followIndex, showMessage)
  followIndex = followIndex or 0;

  local bag = this.GetBag(bagKind, followIndex);

  if bag == nil then return end

  local itemId = 0;
  if itemSave ~= nil then
    itemId = itemSave.Id;
  end

  local oriItemId = 0;
  local oriItemQuant = 0;
  if bag[bagIndex] ~= nil then
    oriItemId = bag[bagIndex].Id;
    oriItemQuant = bag[bagIndex].quant;
  end

  local newItemId = 0;
  local newItemQuant = 0;
  if itemSave ~= nil and itemSave.quant ~= nil then
    newItemId = itemSave.Id;
    newItemQuant = itemSave.quant;
  end

  local itemData = itemDatas[itemId];
  if itemData ~= nil then
    bag[bagIndex] = itemSave;
  else
    bag[bagIndex] = nil;
  end

  --裝備額外處理
  if bagKind == EThings.Equip and followIndex == 0 then
    Role.player:SetEquip(bagIndex, itemId);
  end

  if updateOther == false then return end

  local newItemData = itemDatas[newItemId];
  local oriItemData = itemDatas[oriItemId];
  --秀訊息
  if bagKind == EThings.Bag and showMessage then
    if newItemId == oriItemId then
      --同物品
      if newItemData ~= nil then
        if newItemQuant > oriItemQuant then
          ShowCenterMessage(string.Concat(string.Get(40049), newItemQuant - oriItemQuant, string.Get(40050), newItemData:GetName(true, true)));
          Chat.AddMessage(EChannel.System, string.format(string.Get(21179), newItemData:GetName(true), newItemQuant - oriItemQuant));
        elseif newItemQuant < oriItemQuant then
          Chat.AddMessage(EChannel.System, string.format(string.Get(21160), newItemData:GetName(true), oriItemQuant - newItemQuant));
        end
      end
    else
      --不同物品
      if oriItemData ~= nil and oriItemQuant > 0 then
        Chat.AddMessage(EChannel.System, string.format(string.Get(21160), oriItemData:GetName(true), oriItemQuant));
      end

      if newItemData ~= nil and newItemQuant > 0 then
        ShowCenterMessage(string.Concat(string.Get(40049), newItemQuant - oriItemQuant, string.Get(40050), newItemData:GetName(true, true)));
        Chat.AddMessage(EChannel.System, string.format(string.Get(21179), newItemData:GetName(true), newItemQuant));
      end
    end
  end

  if bagKind == EThings.Bag then
    --更新背包內物品紅點 機關盒統計
    if newItemId == oriItemId then
      --同物品
      if newItemQuant > oriItemQuant then
        RedDot.Add(ERedDot.Bag);
        this.SetItemRedDot(bagKind, bagIndex);
        if newItemData ~= nil then
          MachineBox.Statistics(EMachineBoxStats.GetItem, newItemId, newItemQuant - oriItemQuant, newItemData:GetName(true));
        end
      end
    else
      --不同物品
      if newItemQuant > 0 then
        RedDot.Add(ERedDot.Bag);
        this.SetItemRedDot(bagKind, bagIndex);
        if newItemData ~= nil then
          MachineBox.Statistics(EMachineBoxStats.GetItem, newItemId, newItemQuant, newItemData:GetName(true));
        end
      end
    end

    --更新兌換紅點
    if UILoginAward.CheckConvertItem(itemId) then
      UILoginAward.UpdateChangeRedDot();
    end

    --更新九宮格紅點
    if UIJiugongge.CheckConvertItem(itemId, EAward.Festival) then
      UIFestival.UpdateRedDot();
    end

    --更新坐騎紅點
    Mounts.OnGetItem(itemId);

    --更新收集冊紅點
    if itemData ~= nil and itemData.kind == EItemKind.Item_68 then 
      UICollectBook.CheckStyleRedDot();
    end
  end

  --更新介面
  if bagKind == EThings.Equip and itemSave ~= nil and itemSave.damage ~= nil and itemSave.damage >= EThingsDurability.Damaged and itemSave.Id == 23024 then
    this.UpdateUI(bagKind, followIndex, nil);  --損壞的裝備不刷新裝備數質
  else
    this.UpdateUI(bagKind, followIndex, true);
  end
end

--減少某背包物品
function Item.DelBagItem(bagKind, bagIndex, quant, followIndex, showMessage)
  followIndex = followIndex or 0;

  local bag, bagSize, quantSize = this.GetBag(bagKind, followIndex);

  if bag == nil then return end
  if bag[bagIndex] == nil then return end

  local itemId = bag[bagIndex].Id;
  local itemData = itemDatas[itemId];

  if quant ~= nil then
    bag[bagIndex].quant = bag[bagIndex].quant - quant;
  end

  --已扣完
  if quant == nil or bag[bagIndex].quant <= 0 then
    if bagKind == EThings.OutfitStyleInCollection then
      table.remove(bag,bagIndex);
    else
      bag[bagIndex] = nil;
    end
    if UI.IsVisible(UIItemInfo) then
      UI.Close(UIItemInfo);
    end

    --裝備額外處理
    if bagKind == EThings.Equip and followIndex == 0 then
      Role.player:SetEquip(bagIndex, 0);
    end
  end

  if showMessage ~= false and itemData ~= nil and quant ~= nil then
    Chat.AddMessage(EChannel.System, string.format(string.Get(21160), itemData:GetName(true), quant));
  end

  if bagKind == EThings.Bag then
    --更新兌換紅點
    if UILoginAward.CheckConvertItem(itemId) then
      UILoginAward.UpdateChangeRedDot();
    end

    --更新九宮格紅點
    if UIJiugongge.CheckConvertItem(itemId, EAward.Festival) then
      UIFestival.UpdateRedDot();
    end

    --更新坐騎紅點
    Mounts.OnLostItem(itemId);

    --更新收集冊紅點
    UICollectBook.CheckStyleRedDot();
  end

  --更新介面
  this.UpdateUI(bagKind, followIndex, false);
end

--清空某背包
function Item.ClearBag(bagKind, followIndex)
  local bag = this.GetBag(bagKind, followIndex);

  if bag == nil then return 0 end

  for k, v in pairs(bag) do
    bag[k] = nil;
  end

  --更新介面
  this.UpdateUI(bagKind, followIndex, false);
end

function Item.GetSimpleValue(value, useCeil)
  if value > 1000000 then
    if useCeil then 
      value = string.Concat(math.modf(value / 1000000), "m");
    else
      value = string.Concat(value / 1000000, "m");
    end
  elseif value > 1000 then
    if useCeil then 
      value = string.Concat(math.modf(value / 1000), "k");
    else
      value = string.Concat(value / 1000, "k");
    end
  end
  
  return value
end

function Item.GetItemCount(itemId)
  local count = 0;

  if itemId == 65301 then
    count = Role.GetPoint();
  elseif itemId == 65351 then
    count = Role.GetPlayerGold();
  else
    local bag = this.GetBag(EThings.Bag);
    if bag ~= nil then
      for k, v in pairs(bag) do
        if v ~= nil and v.Id == itemId and v.quant > 0 then
          count = count + v.quant;
        end
      end
    end
  end

  return count;
end

function Item.GetItemIndex(itemId,bagkind)
  local bag;
  if bagkind == nil then
    bag = this.GetBag(EThings.Bag);
  else
    bag = this.GetBag(bagkind);
  end
  if bag ~= nil then
    for k, v in pairs(bag) do
      if v ~= nil and v.Id == itemId then
        if bagkind ~= EThings.OutfitStyleInCollection then
          if v.quant > 0 then
            return k;
          end
        else
          return k;
        end
      end
    end
  end

  return 0;
end

function Item.GetAllItemIndex(itemId, count)
  local bag = this.GetBag(EThings.Bag);
  local bagCount = 0;
  local needCount = 0;
  local result = {};

  for i = 1, this.currentThingGrid do
    if bag[i] ~= nil and bag[i].Id == itemId and bag[i].quant > 0 then
      bagCount = bagCount + bag[i].quant;
      if bagCount <= count then
        result[i] = bag[i].quant;
        needCount = needCount + bag[i].quant;
      else
        result[i] = count - needCount;
        break;
      end
    end
  end

  return result;
end

function Item.GetLockBlissBagKeys(keyKind)
  local result = {};
  local itemSave;
  local itemData;
  local index = 0;

  local items = this.GetBag(EThings.Bag);
  for k, v in pairs(items) do
    itemData = itemDatas[v.Id];
    if itemData ~= nil and itemData.specialAbility == EItemUseKind.BlissBagKey and itemData.skillLink == keyKind then
      index = index + 1;
      result[index] = {};
      result[index].count = v.quant;
      result[index].itemId = v.Id;
      result[index].bagIndex = v.bagIndex;
    end
  end

  return result;
end

function Item.MoveBagItem(fromIndex, quant, toIndex)
  local fromItemSave = this.GetBagItem(EThings.Bag, fromIndex);
  local toItemSave = this.GetBagItem(EThings.Bag, toIndex);

  this.DelBagItem(EThings.Bag, fromIndex, quant);

  if toItemSave == nil then
    toItemSave = ThingData.Copy(fromItemSave);
    toItemSave.quant = quant;
  else
    toItemSave.quant = toItemSave.quant + quant;
  end

  this.SetBagItem(EThings.Bag, toIndex, toItemSave);

  if UI.IsVisible(UIBag) then
    UIBag.UpdateUI(true);
    UIBag.OnClick_Refresh();
  end
end

function Item.SetItemLock(bagKind, bagIndex, followIndex, isLock)
  local itemSave = this.GetBagItem(bagKind, bagIndex, followIndex);

  if itemSave == nil then return end

  itemSave.isLock = isLock;

  if UI.IsVisible(UIItemInfo) then
    UIItemInfo.UpdateUI();
  end
end

function Item.SendUseEquip(bagIndex, followIndex)
  if followIndex == nil or followIndex == 0 then
    --C:023-011<玩家穿上裝備> +背包索引(1)
    sendBuffer:Clear()
    sendBuffer:WriteByte(bagIndex);
    Network.Send(23, 11, sendBuffer);
  else
    --C:023-011<武將穿上裝備> +背包索引(1)
    if Role.player.war ~= EWar.None then
      local itemSave = this.GetBagItem(EThings.Bag, bagIndex);
      if itemSave == nil then return end

      local itemData = itemDatas[itemSave.Id];
      if itemData == nil then return end

      if itemData.kind == EItemKind.ExclusiveWeapon then
        ShowCenterMessage(string.Get(23472));
        return;
      end
    end

    sendBuffer:Clear()
    sendBuffer:WriteByte(followIndex);
    sendBuffer:WriteByte(bagIndex);
    Network.Send(23, 17, sendBuffer);
  end
end

function Item.SendUseItem(bagIndex, quant, followIndex, useType)
  --C:023-015 <使用物品> +背包索引(1) +數量(4) +武將索引(1) +使用種類(1)
  sendBuffer:Clear()  
  sendBuffer:WriteByte(bagIndex);
  sendBuffer:WriteInt32(quant);
  sendBuffer:WriteByte(followIndex);
  sendBuffer:WriteByte(useType);
  Network.Send(23, 15, sendBuffer);
end

function Item.SendAskVender(uiEvent)
  --[[
  --C:023-032 <訪問擺攤> +玩家ID(8)
  sendBuffer:Clear();
  sendBuffer:WriteInt64(uiEvent.parameter);
  Network.Send(23, 32, sendBuffer);
  --]]
end

function Item.GetGrowLv(itemId, growLv, growExp)
  local result = growLv;
  repeat
    result = result + 1;
  until (result > 20) or (this.GetGrowNeedCount(itemId, result) > growExp)
  return result - 1;
end

function Item.GetGrowNeedCount(itemId, growLv)
  local itemData = itemDatas[itemId];
  if itemData == nil then
    return 0;
  end

  local result = 0;
  local itemLv = itemData.level;
  for i = 1, growLv do
    result = result + this.GetGrowUpCount(itemLv, i);
  end
  
  return result;
end

function Item.GetGrowUpCount(itemLv, growLv)
  return math.pow((3 * itemLv + 13 * growLv), 2);
end

--找出非裝備類,可堆疊物品的位置
function Item.ThingFindId(itemId, checkSpace)
  local bag = this.GetBag(EThings.Bag);
  if bag ~= nil then
    for k, v in pairs(bag) do
      if v ~= nil and v.Id == itemId and v.quant > 0 then
        if not checkSpace or v.quant < this.MaxThingGridQuant then
          return k;
        end
      end
    end
  end
  
  return 0;
end

--找出空的位置
function Item.ThingFindEmpty()
  if this.GetBagCount(EThings.Bag, 0) >= this.currentThingGrid then return 0 end

  local bag = this.GetBag(EThings.Bag);

  for i = 1, this.currentThingGrid do
    if bag[i] == nil then
      return i;
    end
  end
  
  return 0;
end

function Item.CheckBagIsFull(showMsg)
  if this.ThingFindEmpty() <= 0 then
    if showMsg == nil then showMsg = true end

    if showMsg then
      ShowCenterMessage(string.Get(80359));
    end

    return true;
  else
    return false;
  end
end

--檢查是否為裝備類
function Item.IsTypeOfEquips(fitType)
  return Contains(fitType, EItemFitType.Equip_Head, EItemFitType.Equip_Body, EItemFitType.Equip_Hand, EItemFitType.Equip_Wrist, EItemFitType.Equip_Boots, EItemFitType.Equip_Spec, EItemFitType.Style_Head, EItemFitType.Style_Body, EItemFitType.Style_Hand, EItemFitType.Style_Wrist, EItemFitType.Style_Boots, EItemFitType.Equip_Cape);
end

--檢查是否為時裝類
function Item.IsStyle(fitType)
  if Contains(fitType, EItemFitType.Style_Head, EItemFitType.Style_Body, EItemFitType.Style_Hand, EItemFitType.Style_Wrist, EItemFitType.Style_Boots) then
    return true;
  else
    return false;
  end
end

--檢查是否為一般裝備
function Item.IsEquip(fitType)
  if Contains(fitType, EItemFitType.Equip_Head, EItemFitType.Equip_Body, EItemFitType.Equip_Hand, EItemFitType.Equip_Wrist, EItemFitType.Equip_Boots, EItemFitType.Equip_Spec) then
    return true;
  else
    return false;
  end
end

--檢查是否為外顯時裝
function Item.IsOutfitStyle(fitType)
  if Contains(fitType, EItemFitType.OutfitStyle_Head, EItemFitType.OutfitStyle_Body, EItemFitType.OutfitStyle_Hand, EItemFitType.OutfitStyle_Wrist, EItemFitType.OutfitStyle_Boots) then
    return true;
  else
    return false;
  end
end

function Item.IsNpcEquip(fitType)
  return Contains(fitType, EItemFitType.Equip_Head, EItemFitType.Equip_Body, EItemFitType.Equip_Hand, EItemFitType.Equip_Wrist, EItemFitType.Equip_Boots);
end

function Item.IsUniqueBlood(kind)
  return kind == EItemKind.UniqueBlood;
end

function Item.CheckUsedID(itemId)
  if Contains(itemId, 23023, 23085, 23179, 23179, 23180, 23198, 23206) then --福神紋章 大福神紋章 福神紋章 大福神紋章 神佑紋章 五倍福神紋章
    return true;
  else
    return false;
  end
end

function Item.CheckConjureID(itemId)
  if Contains(itemId, 23086, 23087, 23088, 23089, 23135, 23136, 23137, 23138) then  --岩怪紋章 水神紋章 鳳凰紋章 青龍紋章
    return true;
  else
    return false;
  end
end

function Item.CheckHotKeyItemID(itemId)
  local itemData = itemDatas[itemId];
  if BattleRoyale.GetBattleRoyaleState() then
    return false
  end

  if itemData ~= nil then
    if Contains(itemData.kind, EItemKind.Item_16, EItemKind.Item_17, EItemKind.Item_18 ,EItemKind.Item_19, EItemKind.Item_50) or 
      Contains(itemId, 50001 , 50002, 50003, 50004, 50005, 50006, 50007, 50008, 50009, 50010, 50011, 50012) then
        return true;
    elseif itemData.specialAbility >= EItemUseKind.ItemUse_66 and itemData.specialAbility <= EItemUseKind.ItemUse_71 then --大戰役相關效果
      return true;
    elseif Contains(itemData.specialAbility, EItemUseKind.ItemUse_25, EItemUseKind.ItemUse_54, EItemUseKind.ItemUse_165) then -- 全補物品
      return true;
    else
      return false;
    end
  end

  return false;
end

function Item.CheckPreviewItemID(itemId)
  local itemData = itemDatas[itemId];

  if itemData ~= nil then
    if itemData.specialAbility == EItemUseKind.NpcPicture or itemData.specialAbility == EItemUseKind.Mounts or this.IsStyle(itemData.fitType) then
      return true;
    end
  end

  return false;
end

function Item.IsLock(itemSave)
  if itemSave == nil then return false end;

  if itemSave.isLock then
    ShowCenterMessage(string.Get(22048));
    return true;
  else
    return false;
  end
end

function Item.GetElementName(index)
  if index == 0 then
    return string.GetElement(index), Color.SkillKind[0];--無
  elseif index == 1 then
    return string.GetElement(index), Color.SkillKind[1]; -- 地
  elseif index == 2 then
    return string.GetElement(index), Color.SkillKind[2]; -- 水
  elseif index == 3 then
    return string.GetElement(index), Color.SkillKind[3]; --火
  elseif index == 4 then
    return string.GetElement(index), Color.SkillKind[4]; --風
  elseif index == 5 then
    return string.GetElement(index), Color.SkillKind[5]; --心
  elseif index == 7 then
    return string.GetElement(index), Color.SkillKind[6]; --光
  elseif index == 8 then
    return string.GetElement(index), Color.SkillKind[7]; --暗
  else
    return "?", Color.Purple;
  end
end

function Item.GetStoneAttrStr(index, isContent)
  if index == 1 then
    return string.Get(80311);
  elseif index == 2 then
    if isContent then
      return string.Get(80335);
    else
      return string.Get(80312);
    end
  elseif index == 3 then
    if isContent then
      return string.Get(80334);
    else
      return string.Get(80313);
    end
  elseif index == 4 then
    return string.Get(80314);
  elseif index == 5 then
    return string.Get(80315);
  elseif index == 6 then
    if isContent then
      return string.Get(80336);
    else
      return string.Get(80316);
    end
  elseif index == 7 then
    if isContent then
      return string.Get(24122);
    else
      return string.Get(80317);
    end
  end
end

function Item.GetAttrHintStr(stoneAttr, stoneLv)
  if Between(stoneLv, 1, this.MaxStoneLv) and Between(stoneAttr, 1, this.MaxStoneAttrKind) then
    local val = this.StoneAttrValue[stoneAttr][stoneLv];
    if stoneAttr == EStoneAttr.Stone_DblAtk then
      return val / 10;
    else
      return val;
    end
  end
end

function Item.GetSuitAttrStr(sum, attr, val)
  local val = val - 100;
  if attr == EAttribute.EquipMaxHp then
    return string.format(string.Get(90316), sum, val);
  elseif attr == EAttribute.EquipMaxSp then
    return string.format(string.Get(90317), sum, val);
  elseif attr == EAttribute.EquipAtk then
    return string.format(string.Get(90318), sum, val);
  elseif attr == EAttribute.EquipDef then
    return string.format(string.Get(90319), sum, val);
  elseif attr == EAttribute.EquipInt then
    return string.format(string.Get(90320), sum, val);
  elseif attr == EAttribute.EquipAgi then
    return string.format(string.Get(90321), sum, val);
  elseif attr == EAttribute.EquipHpx then
    return string.format(string.Get(90323), sum, val);
  elseif attr == EAttribute.EquipSpx then
    return string.format(string.Get(90324), sum, val);
  elseif attr == EAttribute.AtkPercent then
    return string.format(string.Get(23661), sum, val);
  elseif attr == EAttribute.DefPercent then
    return string.format(string.Get(23662), sum, val);
  elseif attr == EAttribute.Crit then
    return string.format(string.Get(80318), sum, val);
  elseif attr == EAttribute.CritDamage then
    return string.format(string.Get(23658), sum, val);
  elseif attr == EAttribute.Dodge then
    return string.format(string.Get(23665), sum, val);
  elseif attr == EAttribute.Hit then
    return string.format(string.Get(23666), sum, val);
  elseif attr == EAttribute.HpPercent then
    return string.format(string.Get(23663), sum, val);
  elseif attr == EAttribute.SpPercent then
    return string.format(string.Get(23664), sum, val);
  elseif attr == EAttribute.IntPercent then
    return string.format(string.Get(23660), sum, val);
  elseif attr == EAttribute.StealHp then
    return string.format(string.Get(23090), sum, val);
  elseif attr == EAttribute.StealSp then
    return string.format(string.Get(23091), sum, val);
  elseif attr == EAttribute.ResistancePercent then
    return string.format(string.Get(90328), sum, val);
  elseif attr == EAttribute.ReactiveDamage then
    return string.format(string.Get(23093), sum, val);
  else
    return "";
  end
end

function Item.GetEQIncrease(role, followIndex)
  local increase = {};
  local elemIncrease = {};
  local attrKind;
  local value;
  --屬性套裝成立條件: 5 件(屬性、附屬性)與自身屬性相同的裝備。PS:若裝備屬性為心屬性則必須為靈性裝備才列入計算
  --屬性套裝獎勵: 所有裝備(含紋章)自身屬性 +3
  local suitEq = {};
  local spirituality = 0;

  for i = 1, this.MaxEquip do
    local data = {};
    data.suitId = 0;
    data.sum = 0;
    suitEq[i] = data;
  end

  local itemSave, itemData;

  for i = 1, this.MaxEquip do
    local itemSave = this.GetBagItem(EThings.Equip, i, followIndex);

    if itemSave ~= nil then
      local itemIdx = itemSave.Id;
      itemData = itemDatas[itemIdx];
      if itemIdx ~= 0 then
        if itemData.element == role:GetAttribute(EAttribute.Element) or
                itemSave.element == role:GetAttribute(EAttribute.Element) or ((itemData.kind == EItemKind.SoulWeapon or
                itemData.kind == EItemKind.SoulEquip) and itemData.element == 5) then
          spirituality = spirituality + 1;
        end

        if itemData.suitId ~= 0 then
          for j = 1, this.MaxEquip do
            if suitEq[j].suitId == 0 then
              suitEq[j].suitId = itemData.suitId;
              suitEq[j].sum = suitEq[j].sum + 1;
            elseif suitEq[j].suitId == itemData.suitId then
              suitEq[j].sum = suitEq[j].sum + 1;
            end
          end
        end
      end
    end
  end

  for i = 1, this.MaxEquip do
    local itemSave = this.GetBagItem(EThings.Equip, i, followIndex);

    if itemSave ~= nil then
      local itemIdx = itemSave.Id;
      if itemIdx ~= 0 then
        for j = 1, 2 do
          itemData = itemDatas[itemIdx];
          if itemData.attribute[j].kind ~= 0 and itemData.attribute[j].value ~= 100 then
            attrKind = itemData.attribute[j].kind;
            value = itemData.attribute[j].value - 100;
            local elementValue = 0;
            if value > 0 then
              elementValue = elementValue + this.AddElemIncrease(role:GetAttribute(EAttribute.Element), itemData.element, itemData.elementValue - 100); --屬性加成
              elementValue = elementValue + this.AddElemIncrease(role:GetAttribute(EAttribute.Element), itemSave.element, itemSave.elementValue - 100); --附加屬性加成
              --靈性裝備加成
              if Contains(itemData.kind, EItemKind.SoulWeapon, EItemKind.SoulEquip) then
                elementValue = elementValue + this.AddElemIncrease(role:GetAttribute(EAttribute.Element), itemData.element, itemSave.lv);
              end
              --屬性套裝加成(屬性裝備達5件,所有裝備再加3點屬性)
              if spirituality >= 5 then
                elementValue = elementValue + 3;
              end

              if increase[attrKind] == nil then
                increase[attrKind] = 0;
              end
              increase[attrKind] = increase[attrKind] + value;

              if elemIncrease[attrKind] == nil then
                elemIncrease[attrKind] = 0;
              end
              elemIncrease[attrKind] = elemIncrease[attrKind] + elementValue;
            end
          end
        end

        --靈石加成
        if itemSave.stoneAttr ~= nil and Between(itemSave.stoneAttr, 1, 6) then
          attrKind = this.StoneAttrKind[itemSave.stoneAttr];
          value = this.StoneAttrValue[itemSave.stoneAttr][itemSave.stoneLv];
          if increase[attrKind] == nil then
            increase[attrKind] = 0;
          end
          increase[attrKind] = increase[attrKind] + value;
        end
      end
    end
  end

  --套裝系統加成
  for i = 1, this.MaxEquip do
    if suitEq[i].suitId ~= 0 then
      for j = 1, this.MaxSuitAttr do
        if suitEq[i].sum >= suitDatas[suitEq[i].suitId].attributes[j].suitSum then
          attrKind = suitDatas[suitEq[i].suitId].attributes[j].attribute;
          value = suitDatas[suitEq[i].suitId].attributes[j].value - 100;
          if increase[attrKind] == nil then
            increase[attrKind] = 0;
          end
          increase[attrKind] = increase[attrKind] + value;
        end
      end
    end
  end

  --todo 馬鞍系統加成

  return increase, elemIncrease;
end

function Item.GetStyleIncrease(increase)
  local attrKind;

  local suitEq = {};
  for i = 1, this.MaxEquip do
    suitEq[i] = {};
    suitEq[i].suitId = 0;
    suitEq[i].sum = 0;
  end

  local itemSave, itemData;
  for i = EItemFitType.Style_Head, EItemFitType.Style_Boots do
    itemSave = this.GetBagItem(EThings.Equip, i, 0);
    itemData = itemDatas[itemSave.Id];
    if itemSave ~= nil then
      if itemData ~= nil then
        for j = 1, 2 do
          attrKind = itemData.attribute[j].kind;

          if itemData.attribute[j].kind ~= 0 and itemData.attribute[j].value ~= 100 then
            increase[attrKind] = increase[attrKind] + (itemData.attribute[j].value - 100);
          end
          
          if itemSave.styleLv ~= nil and itemSave.styleLv ~= 0 then
            increase[attrKind] = increase[attrKind] + this.GetFashionAttrValue(itemSave.Id, attrKind, itemSave.styleLv);
          end
        end
      end
    end
  end

  for i = 1, this.MaxEquip do
    itemSave = this.GetBagItem(EThings.Equip, i, 0);
    itemData = itemDatas[itemSave.Id];
    if itemSave ~= nil then
      if itemData ~= nil and itemData.suitId ~= 0 then
        for j = 1, this.MaxEquip do
          if suitEq[j].suitId == 0 then
            suitEq[j].suitId = itemData.suitId;
            suitEq[j].sum = suitEq[j].sum + 1;
            break;
          elseif suitEq[j].suitId == itemData.suitId then
            suitEq[j].sum = suitEq[j].sum + 1;
            break;
          end
        end
      end
    end
  end

  local suitData;

  for i = 1, this.MaxEquip do
    if suitEq[i].suitId ~= 0 then
      for j = 1, this.MaxSuitAttr do
        suitData = suitDatas[suitEq[i].suitId];
        if suitEq[i].sum >= suitData.attributes[j].suitSum then
          attrKind = suitData.attributes[j].attribute;

          increase[attrKind] = increase[attrKind] + (suitData.attributes[j].value - 100);
        end
      end
    end
  end
end

function Item.GetFashionAttrValue(index, attr, lv)
  if fashionStressDatas[index] == nil then return 0 end

  if lv > fashionStressDatas[index].maxLv then
    lv = fashionStressDatas[index].maxLv;
  end

  for i = 1, 2 do
    if fashionStressDatas[index].attributes[i].attr == attr then
      return (math.floor(lv / 5) * fashionStressDatas[index].attributes[i].bonus) + (fashionStressDatas[index].attributes[i].value * lv);
    end
  end

  return 0;
end

function Item.AddElemIncrease(humElement, itemElement, increaseValue)
  if itemElement == 0 then
    return 0;
  end

  if humElement ~= itemElement and itemElement ~= 5 then
    return 0;
  end

  return increaseValue;
end

local equipMessage = {};
equipMessage.itemName = nil;
equipMessage.equipInt = 0;
equipMessage.equipAtk = 0;
equipMessage.equipDef = 0;
equipMessage.equipAgi = 0;
equipMessage.equipHpx = 0;
equipMessage.euqipSpx = 0;
equipMessage.equipMHp = 0;
equipMessage.equipMSp = 0;
equipMessage.equipCrit = 0;
equipMessage.equipCritDamage = 0;
equipMessage.equipDodge = 0;
equipMessage.equipHit = 0;
equipMessage.equipAtkPercent = 0;
equipMessage.equipDefPercent = 0;
equipMessage.equipHpPercent = 0;
equipMessage.equipSpPercent = 0;
equipMessage.equipIntPercent = 0;
equipMessage.equipStealHp = 0;
equipMessage.equipStealSp = 0;
equipMessage.equipResistancePercent = 0;
equipMessage.equipReactiveDamage = 0;

function Item.SaveEquipMessage(itemId, followIndex)
  if itemDatas[itemId] == nil then return end
  
  equipMessage.itemName = itemDatas[itemId]:GetName(true, true);

  --玩家及武將
  if followIndex ~= nil then
    local role = Role.GetRole(EHuman.FollowNpc, Role.playerId, followIndex);
    if role == nil then return end

    equipMessage.equipInt = role:GetAttribute(EAttribute.EquipInt);
    equipMessage.equipAtk = role:GetAttribute(EAttribute.EquipAtk);
    equipMessage.equipDef = role:GetAttribute(EAttribute.EquipDef);
    equipMessage.equipAgi = role:GetAttribute(EAttribute.EquipAgi);
    equipMessage.equipHpx = role:GetAttribute(EAttribute.EquipHpx);
    equipMessage.euqipSpx = role:GetAttribute(EAttribute.EquipSpx);
    equipMessage.equipMHp = role:GetAttribute(EAttribute.EquipMaxHp);
    equipMessage.equipMSp = role:GetAttribute(EAttribute.EquipMaxSp);
    equipMessage.equipCrit = role:GetAttribute(EAttribute.Crit);
    equipMessage.equipCritDamage = role:GetAttribute(EAttribute.CritDamage);
    equipMessage.equipDodge = role:GetAttribute(EAttribute.Dodge);
    equipMessage.equipHit = role:GetAttribute(EAttribute.Hit);
    equipMessage.equipAtkPercent = role:GetAttribute(EAttribute.AtkPercent);
    equipMessage.equipDefPercent = role:GetAttribute(EAttribute.DefPercent);
    equipMessage.equipHpPercent = role:GetAttribute(EAttribute.HpPercent);
    equipMessage.equipSpPercent = role:GetAttribute(EAttribute.SpPercent);
    equipMessage.equipIntPercent = role:GetAttribute(EAttribute.IntPercent);
    equipMessage.equipStealHp = role:GetAttribute(EAttribute.StealHp);
    equipMessage.equipStealSp = role:GetAttribute(EAttribute.StealSp);
    equipMessage.equipResistancePercent = role:GetAttribute(EAttribute.ResistancePercent);
    equipMessage.equipReactiveDamage = role:GetAttribute(EAttribute.ReactiveDamage);

  --坐騎
  else
    equipMessage.equipInt = math.floor((Mounts.GetAttribute(EAttribute.Int) + Mounts.GetAttribute(EAttribute.EquipInt)) * Mounts.collectionBonus);
    equipMessage.equipAtk = math.floor((Mounts.GetAttribute(EAttribute.Atk) + Mounts.GetAttribute(EAttribute.EquipAtk)) * Mounts.collectionBonus);
    equipMessage.equipDef = math.floor((Mounts.GetAttribute(EAttribute.Def) + Mounts.GetAttribute(EAttribute.EquipDef)) * Mounts.collectionBonus);
    equipMessage.equipAgi = math.floor((Mounts.GetAttribute(EAttribute.Agi) + Mounts.GetAttribute(EAttribute.EquipAgi)) * Mounts.collectionBonus);
    equipMessage.equipHpx = math.floor((Mounts.GetAttribute(EAttribute.Hpx) + Mounts.GetAttribute(EAttribute.EquipHpx)) * Mounts.collectionBonus);
    equipMessage.euqipSpx = math.floor((Mounts.GetAttribute(EAttribute.Spx) + Mounts.GetAttribute(EAttribute.EquipSpx)) * Mounts.collectionBonus);
    equipMessage.equipMHp = math.floor((Mounts.GetAttribute(EAttribute.ExtraHp) + Mounts.GetAttribute(EAttribute.EquipMaxHp)) * Mounts.collectionBonus);
    equipMessage.equipMSp = math.floor((Mounts.GetAttribute(EAttribute.ExtraSp) + Mounts.GetAttribute(EAttribute.EquipMaxSp)) * Mounts.collectionBonus);
    equipMessage.equipCrit = math.floor((Mounts.GetAttribute(EAttribute.Crit) + Mounts.GetAttribute(EAttribute.Crit)) * Mounts.collectionBonus);
    equipMessage.equipCritDamage = math.floor((Mounts.GetAttribute(EAttribute.CritDamage) + Mounts.GetAttribute(EAttribute.CritDamage)) * Mounts.collectionBonus);
    equipMessage.equipDodge = math.floor((Mounts.GetAttribute(EAttribute.Dodge) + Mounts.GetAttribute(EAttribute.Dodge)) * Mounts.collectionBonus);
    equipMessage.equipHit = math.floor((Mounts.GetAttribute(EAttribute.Hit) + Mounts.GetAttribute(EAttribute.Hit)) * Mounts.collectionBonus);
    equipMessage.equipAtkPercent = math.floor((Mounts.GetAttribute(EAttribute.AtkPercent) + Mounts.GetAttribute(EAttribute.AtkPercent)) * Mounts.collectionBonus);
    equipMessage.equipDefPercent = math.floor((Mounts.GetAttribute(EAttribute.DefPercent) + Mounts.GetAttribute(EAttribute.DefPercent)) * Mounts.collectionBonus);
    equipMessage.equipHpPercent = math.floor((Mounts.GetAttribute(EAttribute.HpPercent) + Mounts.GetAttribute(EAttribute.HpPercent)) * Mounts.collectionBonus);
    equipMessage.equipSpPercent = math.floor((Mounts.GetAttribute(EAttribute.SpPercent) + Mounts.GetAttribute(EAttribute.SpPercent)) * Mounts.collectionBonus);
    equipMessage.equipIntPercent = math.floor((Mounts.GetAttribute(EAttribute.IntPercent) + Mounts.GetAttribute(EAttribute.IntPercent)) * Mounts.collectionBonus);
    equipMessage.equipStealHp = math.floor((Mounts.GetAttribute(EAttribute.StealHp) + Mounts.GetAttribute(EAttribute.StealHp)) * Mounts.collectionBonus);
    equipMessage.equipStealSp = math.floor((Mounts.GetAttribute(EAttribute.StealSp) + Mounts.GetAttribute(EAttribute.StealSp)) * Mounts.collectionBonus);
    equipMessage.equipResistancePercent = math.floor((Mounts.GetAttribute(EAttribute.ResistancePercent) + Mounts.GetAttribute(EAttribute.ResistancePercent)) * Mounts.collectionBonus);
    equipMessage.equipReactiveDamage = math.floor((Mounts.GetAttribute(EAttribute.ReactiveDamage) + Mounts.GetAttribute(EAttribute.ReactiveDamage)) * Mounts.collectionBonus);
  end
end

function Item.ShowEquipMessage(isAdd, followIndex)
  if isAdd == nil then return end
  if string.IsNullOrEmpty(equipMessage.itemName) then return end

  local equipInt = 0;
  local equipAtk = 0;
  local equipDef = 0;
  local equipAgi = 0;
  local equipHpx = 0;
  local euqipSpx = 0;
  local equipMHp = 0;
  local equipMSp = 0;
  local equipCrit = 0;
  local equipCritDamage = 0;
  local equipDodge = 0;
  local equipHit = 0;
  local equipAtkPercent = 0;
  local equipDefPercent = 0;
  local equipHpPercent = 0;
  local equipSpPercent = 0;
  local equipIntPercent = 0;
  local equipStealHp = 0;
  local equipStealSp = 0;
  local equipResistancePercent = 0;
  local equipReactiveDamage = 0;

  --玩家及武將
  if followIndex ~= nil then
    local role = Role.GetRole(EHuman.FollowNpc, Role.playerId, followIndex);
    if role == nil then return end

    equipInt = role:GetAttribute(EAttribute.EquipInt);
    equipAtk = role:GetAttribute(EAttribute.EquipAtk);
    equipDef = role:GetAttribute(EAttribute.EquipDef);
    equipAgi = role:GetAttribute(EAttribute.EquipAgi);
    equipHpx = role:GetAttribute(EAttribute.EquipHpx);
    euqipSpx = role:GetAttribute(EAttribute.EquipSpx);
    equipMHp = role:GetAttribute(EAttribute.EquipMaxHp);
    equipMSp = role:GetAttribute(EAttribute.EquipMaxSp);
    equipCrit = role:GetAttribute(EAttribute.Crit);
    equipCritDamage = role:GetAttribute(EAttribute.CritDamage);
    equipDodge = role:GetAttribute(EAttribute.Dodge);
    equipHit = role:GetAttribute(EAttribute.Hit);
    equipAtkPercent = role:GetAttribute(EAttribute.AtkPercent);
    equipDefPercent = role:GetAttribute(EAttribute.DefPercent);
    equipHpPercent = role:GetAttribute(EAttribute.HpPercent);
    equipSpPercent = role:GetAttribute(EAttribute.SpPercent);
    equipIntPercent = role:GetAttribute(EAttribute.IntPercent);
    equipStealHp = role:GetAttribute(EAttribute.StealHp);
    equipStealSp = role:GetAttribute(EAttribute.StealSp);
    equipResistancePercent = role:GetAttribute(EAttribute.ResistancePercent);
    equipReactiveDamage = role:GetAttribute(EAttribute.ReactiveDamage);

  --坐騎
  else
    equipInt = math.floor((Mounts.GetAttribute(EAttribute.Int) + Mounts.GetAttribute(EAttribute.EquipInt)) * Mounts.collectionBonus);
    equipAtk = math.floor((Mounts.GetAttribute(EAttribute.Atk) + Mounts.GetAttribute(EAttribute.EquipAtk)) * Mounts.collectionBonus);
    equipDef = math.floor((Mounts.GetAttribute(EAttribute.Def) + Mounts.GetAttribute(EAttribute.EquipDef)) * Mounts.collectionBonus);
    equipAgi = math.floor((Mounts.GetAttribute(EAttribute.Agi) + Mounts.GetAttribute(EAttribute.EquipAgi)) * Mounts.collectionBonus);
    equipHpx = math.floor((Mounts.GetAttribute(EAttribute.Hpx) + Mounts.GetAttribute(EAttribute.EquipHpx)) * Mounts.collectionBonus);
    euqipSpx = math.floor((Mounts.GetAttribute(EAttribute.Spx) + Mounts.GetAttribute(EAttribute.EquipSpx)) * Mounts.collectionBonus);
    equipMHp = math.floor((Mounts.GetAttribute(EAttribute.ExtraHp) + Mounts.GetAttribute(EAttribute.EquipMaxHp)) * Mounts.collectionBonus);
    equipMSp = math.floor((Mounts.GetAttribute(EAttribute.ExtraSp) + Mounts.GetAttribute(EAttribute.EquipMaxSp)) * Mounts.collectionBonus);
    equipCrit = math.floor((Mounts.GetAttribute(EAttribute.Crit) + Mounts.GetAttribute(EAttribute.Crit)) * Mounts.collectionBonus);
    equipCritDamage = math.floor((Mounts.GetAttribute(EAttribute.CritDamage) + Mounts.GetAttribute(EAttribute.CritDamage)) * Mounts.collectionBonus);
    equipDodge = math.floor((Mounts.GetAttribute(EAttribute.Dodge) + Mounts.GetAttribute(EAttribute.Dodge)) * Mounts.collectionBonus);
    equipHit = math.floor((Mounts.GetAttribute(EAttribute.Hit) + Mounts.GetAttribute(EAttribute.Hit)) * Mounts.collectionBonus);
    equipAtkPercent = math.floor((Mounts.GetAttribute(EAttribute.AtkPercent) + Mounts.GetAttribute(EAttribute.AtkPercent)) * Mounts.collectionBonus);
    equipDefPercent = math.floor((Mounts.GetAttribute(EAttribute.DefPercent) + Mounts.GetAttribute(EAttribute.DefPercent)) * Mounts.collectionBonus);
    equipHpPercent = math.floor((Mounts.GetAttribute(EAttribute.HpPercent) + Mounts.GetAttribute(EAttribute.HpPercent)) * Mounts.collectionBonus);
    equipSpPercent = math.floor((Mounts.GetAttribute(EAttribute.SpPercent) + Mounts.GetAttribute(EAttribute.SpPercent)) * Mounts.collectionBonus);
    equipIntPercent = math.floor((Mounts.GetAttribute(EAttribute.IntPercent) + Mounts.GetAttribute(EAttribute.IntPercent)) * Mounts.collectionBonus);
    equipStealHp = math.floor((Mounts.GetAttribute(EAttribute.StealHp) + Mounts.GetAttribute(EAttribute.StealHp)) * Mounts.collectionBonus);
    equipStealSp = math.floor((Mounts.GetAttribute(EAttribute.StealSp) + Mounts.GetAttribute(EAttribute.StealSp)) * Mounts.collectionBonus);
    equipResistancePercent = math.floor((Mounts.GetAttribute(EAttribute.ResistancePercent) + Mounts.GetAttribute(EAttribute.ResistancePercent)) * Mounts.collectionBonus);
    equipReactiveDamage = math.floor((Mounts.GetAttribute(EAttribute.ReactiveDamage) + Mounts.GetAttribute(EAttribute.ReactiveDamage)) * Mounts.collectionBonus);
  end

  if isAdd then
    ShowCenterMessage(string.Concat(equipMessage.itemName, string.Get(50023)));
  else
    ShowCenterMessage(string.Concat(equipMessage.itemName, string.Get(50024)));
  end

  local att = equipInt - equipMessage.equipInt;
  if att > 0 then
    ShowCenterMessage(string.Concat(string.Get(50031), " ", math.abs(att)));
  elseif att < 0 then
    ShowCenterMessage(string.Concat(string.Get(50032), " ", math.abs(att)));
  end

  att = equipAtk - equipMessage.equipAtk;
  if att > 0 then
    ShowCenterMessage(string.Concat(string.Get(50025), " ", math.abs(att)));
  elseif att < 0 then
    ShowCenterMessage(string.Concat(string.Get(50026), " ", math.abs(att)));
  end

  att = equipDef - equipMessage.equipDef;
  if att > 0 then
    ShowCenterMessage(string.Concat(string.Get(50027), " ", math.abs(att)));
  elseif att < 0 then
    ShowCenterMessage(string.Concat(string.Get(50028), " ", math.abs(att)));
  end

  att = equipAgi - equipMessage.equipAgi;
  if att > 0 then
    ShowCenterMessage(string.Concat(string.Get(50029), " ", math.abs(att)));
  elseif att < 0 then
    ShowCenterMessage(string.Concat(string.Get(50030), " ", math.abs(att)));
  end

  att = equipHpx - equipMessage.equipHpx;
  if att > 0 then
    ShowCenterMessage(string.Concat(string.Get(21035), " ", math.abs(att)));
  elseif att < 0 then
    ShowCenterMessage(string.Concat(string.Get(21036), " ", math.abs(att)));
  end

  att = euqipSpx - equipMessage.euqipSpx;
  if att > 0 then
    ShowCenterMessage(string.Concat(string.Get(21037), " ", math.abs(att)));
  elseif att < 0 then
    ShowCenterMessage(string.Concat(string.Get(21038), " ", math.abs(att)));
  end

  att = equipMHp - equipMessage.equipMHp;
  if att > 0 then
    ShowCenterMessage(string.Concat(string.Get(50033), " ", math.abs(att)));
  elseif att < 0 then
    ShowCenterMessage(string.Concat(string.Get(50034), " ", math.abs(att)));
  end

  att = equipMSp - equipMessage.equipMSp;
  if att > 0 then
    ShowCenterMessage(string.Concat(string.Get(50035), " ", math.abs(att)));
  elseif att < 0 then
    ShowCenterMessage(string.Concat(string.Get(50036), " ", math.abs(att)));
  end

  equipMessage.itemName = nil;
  
  if callback ~= nil then
    callback();
    callback = nil;
  end
end

function Item.CheckEquip(itemId, followIndex)
  local role;
  if followIndex <= 0 then
    role = Role.player;
  else
    role = Role.GetRole(EHuman.FollowNpc, Role.playerId, followIndex);
  end

  local itemData = itemDatas[itemId];
  local itemSave = this.GetBagItem(EThings.Equip, itemData.fitType, followIndex);

  if role:GetAttribute(EAttribute.Lv) < itemData.needLv and role:GetAttribute(EAttribute.Turn) < 3 then
    ShowCenterMessage(string.Get(40003));  --等級不夠,無法裝備
    return true;
  end

  if role:GetAttribute(EAttribute.Turn) < itemData.turn then
    ShowCenterMessage(string.Get(71103));  --轉生條件不符
    return true;
  end

  if followIndex <= 0 and bit.band(itemData.restrict, 128) == 128 then
    ShowCenterMessage(string.Get(20591));  --限武將裝備
    return true;
  end

  if followIndex > 0 and bit.band(itemData.restrict, 64) == 64 then
    ShowCenterMessage(string.Get(71095)); --玩家專用
    return true;
  end

  for i = 1, 2 do
    local attrKind = itemData.attribute[i].kind;
    local aValue = itemData.attribute[i].value - 100;
    local aValue2 = 0;
    local attrKind2;

    for j = 1, 2 do
      if itemSave == nil or itemSave.Id <= 0 or itemDatas[itemSave.Id] ~= nil then
        attrKind2 = 0;
      else
        attrKind2 = itemDatas[itemSave.Id].attribute[j].kind;
      end

      if attrKind2 == attrKind then
        if itemSave == nil or itemSave.Id <= 0 or itemDatas[itemSave.Id] ~= nil then
          aValue2 = 0;
        else
          aValue2 = itemDatas[itemSave.Id].attribute[j].value - 100;
        end
      end
    end

    if attrKind == EAttribute.EquipMaxHp and aValue + role:GetAttribute(EAttribute.MaxHp) - aValue2 <= 0 then
      ShowCenterMessage(string.Get(71319));  --限武將裝備
      return true;
    end

    if attrKind == EAttribute.EquipMaxSp and aValue + role:GetAttribute(EAttribute.MaxSp) - aValue2 <= 0 then
      ShowCenterMessage(string.Get(71320));  --限武將裝備
      return true;
    end
  end

  if role.data.sex ~= nil and ((itemData.gender == 1) or (itemData.gender == 2)) and role.data.sex ~= itemData.gender then
    if role.data.sex == 1 then
      ShowCenterMessage(string.Get(40307));
    elseif role.data.sex == 2 then
      ShowCenterMessage(string.Get(40306));
    end

    return true;
  end

  if itemData.kind == EItemKind.Item_7 and role:GetSkillLv(14036) <= 0 then
    ShowCenterMessage(string.Get(40422));  --要學會百步穿揚才可裝備弓
    return true;
  end

  if itemData.fitType == 6 then
    if Contains(itemId, 23086, 23135) and role:GetAttribute(EAttribute.Element) ~= 1 then
      ShowCenterMessage(string.Get(40342));  --屬性不合無法裝備
      return true;
    end

    if Contains(itemId, 23087, 23136) and role:GetAttribute(EAttribute.Element) ~= 2 then
      ShowCenterMessage(string.Get(40342));  --屬性不合無法裝備
      return true;
    end

    if Contains(itemId, 23088, 23137) and role:GetAttribute(EAttribute.Element) ~= 3 then
      ShowCenterMessage(string.Get(40342));  --屬性不合無法裝備
      return true;
    end

    if Contains(itemId, 23089, 23138) and role:GetAttribute(EAttribute.Element) ~= 4 then
      ShowCenterMessage(string.Get(40342));  --屬性不合無法裝備
      return true;
    end

    if followIndex <= 0 then 
      if Contains(itemId, 23361, 23367) and role:GetAttribute(EAttribute.Turn3Element) ~= 7 then
        ShowCenterMessage(string.Get(40342));  --屬性不合無法裝備
        return true;
      end

      if Contains(itemId, 23362, 23368) and role:GetAttribute(EAttribute.Turn3Element) ~= 8 then
        ShowCenterMessage(string.Get(40342));  --屬性不合無法裝備
        return true;
      end
    else
      if Contains(itemId, 23361, 23367) and role:GetAttribute(EAttribute.Element) ~= 7 then
        ShowCenterMessage(string.Get(40342));  --屬性不合無法裝備
        return true;
      end

      if Contains(itemId, 23362, 23368) and role:GetAttribute(EAttribute.Element) ~= 8 then
        ShowCenterMessage(string.Get(40342));  --屬性不合無法裝備
        return true;
      end
      
    end

    local summonSkill;
    if followIndex <= 0 then
      summonSkill = 14026;
    else
      summonSkill = Skill.GetSummonSkill(role:GetAttribute(EAttribute.Element));
    end

    --岩怪紋章
    if Contains(itemId, 23086, 23135) and this.HaveSummonSkill(role, summonSkill, followIndex) then
      ShowCenterMessage(string.Get(40341));  --尚未學會召喚技能無法裝備
      return true;
    end

    --水神紋章
    if Contains(itemId, 23087, 23136) and this.HaveSummonSkill(role, summonSkill, followIndex) then
      ShowCenterMessage(string.Get(40341));  --尚未學會召喚技能無法裝備
      return true;
    end

    --鳳凰紋章
    if Contains(itemId, 23088, 23137) and this.HaveSummonSkill(role, summonSkill, followIndex) then
      ShowCenterMessage(string.Get(40341));  --尚未學會召喚技能無法裝備
      return true;
    end

    --青龍紋章
    if Contains(itemId, 23089, 23138) and this.HaveSummonSkill(role, summonSkill, followIndex) then
      ShowCenterMessage(string.Get(40341));  --尚未學會召喚技能無法裝備
      return true;
    end

    if followIndex <= 0 then
      summonSkill = 14077;
    end

    --白虎紋章
    if Contains(itemId, 23361, 23367) and this.HaveSummonSkill(role, summonSkill, followIndex) then
      ShowCenterMessage(string.Get(40341));  --尚未學會召喚技能無法裝備
      return true;
    end

    --玄武紋章
    if Contains(itemId, 23362, 23368) and this.HaveSummonSkill(role, summonSkill, followIndex) then
      ShowCenterMessage(string.Get(40341));  --尚未學會召喚技能無法裝備
      return true;
    end

    if not Contains(Role.player.war, EWar.None, EWar.Guest) then
      --匕首
      if itemId == 23199 then
        ShowCenterMessage(string.Get(71179));  --戰鬥中無法裝備該裝備
        return true;
      end
      
      local currentEquip = this.GetBagItem(EThings.Equip, itemData.fitType, followIndex);
      if currentEquip ~= nil and currentEquip.Id == 23199 then
        ShowCenterMessage(string.Get(71177));  --戰鬥中無法卸下該裝備
        return true;
      end
      
      if Contains(itemData.specialAbility, EItemUseKind.ExpEquip150Pa, EItemUseKind.ExpEquip200Pa, EItemUseKind.ExpEquipArgPa) then
        ShowCenterMessage(string.Get(40487));  --戰鬥中無法裝備該物品
        return true;
      end
    end
  end

  return false;
end

--command
function Item.UseItem(bagKind, bagIndex, followIndex)
  local itemSave = this.GetBagItem(EThings.Bag, bagIndex);
  if itemSave == nil then return end
  
  local itemData = itemDatas[itemSave.Id];
  if itemData == nil then return end

  local role;
  if followIndex <= 0 then
    role = Role.player;
  else
    role = Role.GetRole(EHuman.FollowNpc, Role.playerId, followIndex);
  end

  --不開放的物品不可使用
  if itemData.openUsed == 1 then
    ShowCenterMessage(string.Get(20344));
    return;
  end

  --販賣物不能做其他的用途
  if itemSave.isVender then
    ShowCenterMessage(string.Get(40053));
    return;
  end

  --該物品在快遞中,不可使用
  if itemSave.isDeliver then
    ShowCenterMessage(string.Get(40232));
    return;
  end

  if bit.band(itemData.restrict2, 128) == 128 then
    if this.GetBagCount(EThings.UniqueBlood) == this.currentUniqueBloodGrid then
      ShowCenterMessage(string.Get(24049));
      return;
    end
  end

  --戰鬥中
  if not Contains(Role.player.war, EWar.None, EWar.Guest) then
    --是否戰鬥角色
    local fightRole = FightField.fightHum[FightField.conIdx]
    local result = false;     
    local otherFollowNpc = false;
    
    if fightRole ~= nil and fightRole.kind == EHuman.Player and fightRole.kind == role.kind then --玩家不判斷npcId
      result = true; 
    elseif fightRole ~= nil and fightRole.kind == role.kind and fightRole.npcId == role.npcId then
      result = true;
    elseif fightRole ~= nil and fightRole.kind == EHuman.ChaosGod then
      -- 若使用角色為天空競技場NPC，則一律禁止用背包物品
      ShowCenterMessage(string.Get(22493));
      return;
    elseif Role.player.war ~= EWar.OneBringFour and Role.fightNpc ~= nil and role.npcId ~= Role.fightNpc.npcId then
      otherFollowNpc = true;
    end

    if otherFollowNpc == false then 
      if result == true then 
        --機關盒開啟中不可使用物品
        if MachineBox.autoFight then return end
        --是否是裝備
        if not this.IsTypeOfEquips(itemData.fitType) then
          --是否是戰鬥道具
          if Contains(itemData.kind, EItemKind.Item_16, EItemKind.Item_17, EItemKind.Item_18, EItemKind.Item_50) or
                  Contains(itemData.specialAbility, EItemUseKind.ItemUse_25, EItemUseKind.ItemUse_54, EItemUseKind.ItemUse_165) or
                  bit.band(itemData.restrict2, 2) == 2 then
            --走戰鬥
            FightField.SetConItem(itemSave.Id);
      
            UI.Close(UIStatus);
            UI.Close(UIItemInfo);
            return;
          else
            logError("Not Fight Item");
            ShowCenterMessage(string.Get(80099));
            return;
          end
        end
      else
        logError("Not Fight Role");
        ShowCenterMessage(string.Get(22595));
        return;
      end
    end
  end

  --限制使用次數
  if itemData.kind == EItemKind.SpecialAbility and bit.band(itemData.restrict2, 16) == 16 then
    if not Between(itemData.roleCountIndex, 0 , 999) then
      ShowCenterMessage(string.Get(23431));  --物品使用功能發生不明異常
      return;
    end
  
    if RoleCount.Get(itemData.roleCountIndex) >= RoleCount.Max(itemData.roleCountIndex) then
      ShowCenterMessage(string.Get(23432));  --以達使用此物品上限次數
      return;
    end
  end

  --兌換卡
  if itemData.specialAbility == EItemUseKind.Exchange and exchangeDatas[itemSave.Id] ~= nil then
    if this.GetBagLeftCount(EThings.Bag) < itemData.elementValue then
      ShowCenterMessage(string.Get(80359));
      return;
    end
    
    local selects = UIItemInfo.GetExchangeSelect();
    local selectCount = 0;
    for k, v in pairs(selects) do
      if v then
        selectCount = selectCount + 1;
      end
    end
  
    if selectCount < itemData.elementValue then
      ShowCenterMessage(string.Get(21104));
      return;
    end
    
    if selectCount > itemData.elementValue then
      ShowCenterMessage(string.Get(21103));
      return;
    end
    
    --C:090-001 <兌換> +兌換物品ID(2) +選取數量(1) <<+選取索引(1)>>
    sendBuffer:Clear();
    sendBuffer:WriteUInt16(itemSave.Id);
    sendBuffer:WriteByte(selectCount);
    for k, v in pairs(selects) do
      if v then
        sendBuffer:WriteByte(k);
      end
    end
    Network.Send(90, 1, sendBuffer);

    return;
  end

  --專武兌換
  if itemData.specialAbility == EItemUseKind.WeaponRecipe and exchangeDatas[itemSave.Id] ~= nil then
    if this.GetBagLeftCount(EThings.Bag) < itemData.elementValue then
      ShowCenterMessage(string.Get(80359));
      return;
    end
    
    local selects = UIItemInfo.GetExchangeSelect();
    local selectCount = 0;
    for k, v in pairs(selects) do
      if v then
        selectCount = selectCount + 1;
      end
    end
  
    if UIFurnace.GetDebris(itemData.furnaceKind) < itemData.furnaceCount then 
      ShowCenterMessage(string.Get(22701));
      return;
    end
    
    
    local func = function(result)
      if result ~= true then return end;
      --C:090-002 <專武兌換> +兌換物品ID(2) +選取數量(1) <<+選取索引(1)>>
      sendBuffer:Clear();
      sendBuffer:WriteUInt16(itemSave.Id);
      sendBuffer:WriteByte(selectCount);
      for k, v in pairs(selects) do
        if v then
          sendBuffer:WriteByte(k);
        end
      end
      Network.Send(90, 2, sendBuffer);
    end

    ShowCheckMessage(func, string.format(string.Get(22718), itemData.furnaceCount, UIFurnace.GetDebris(itemData.furnaceKind)));

    return;
  end

  --隨機專武券碎片檢查
  if itemData.specialAbility == EItemUseKind.RandomWeaponRecipe then 
    if UIFurnace.GetDebris(itemData.furnaceKind) < itemData.furnaceCount then 
      ShowCenterMessage(string.Get(21180));
      return;
    end
  end

  --開啟整型、屬性轉換介面
  if Contains(itemData.specialAbility, EItemUseKind.ItemUse_56, EItemUseKind.ItemUse_57, EItemUseKind.ItemUse_129, EItemUseKind.ItemUse_198, EItemUseKind.ItemUse_208) then
    if UI.IsVisible(UIItemInfo) then
      UI.Close(UIItemInfo);
    end

    local turnMode = nil;
    if itemData.specialAbility == EItemUseKind.ItemUse_56 then
      turnMode = ETrunMode.HairColor; --換髮色
    elseif itemData.specialAbility == EItemUseKind.ItemUse_57 then
      turnMode = ETrunMode.Skin; --換膚色
    elseif itemData.specialAbility == EItemUseKind.ItemUse_129 then
      turnMode = ETrunMode.HairStyle; --換髮型
    elseif itemData.specialAbility == EItemUseKind.ItemUse_198 then
      turnMode = ETrunMode.Element; --換屬性
    elseif itemData.specialAbility == EItemUseKind.ItemUse_208 then
      if Role.player == nil or Role.player:GetAttribute(EAttribute.Turn) < 2 then
        ShowCenterMessage(string.Get(80299));
        return;
      end

      if not MarkManager.GetMissionFlag(11495) then
        ShowCenterMessage(string.Get(80299));
        return;
      end

      turnMode = ETrunMode.Career; --換職業
    else
      return;
    end

    UI.Open(UITurn, turnMode, nil, nil, bagIndex);
    return;
  end

  if itemData.specialAbility == EItemUseKind.Mounts and mountsDatas[itemData.spare3] == nil then
    ShowCenterMessage(string.Get(20529));  --此座騎未開放
    return;
  end

  if itemData.kind == EItemKind.SpecialAbility then
    if role:GetAttribute(EAttribute.Turn) < itemData.turn then
      ShowCenterMessage(string.Get(23209));  --轉生使用限制
      return;
    end
  end

  if role:GetAttribute(EAttribute.Turn) < itemData.turn then
    if not this.CheckPlayerNpcLimit(itemData, followIndex) then return end;
    
    ShowCenterMessage(string.Get(71103));  --轉生條件不符
    return;
  end
  
  local roleLv = 0;
  if itemData.turn >= 3 then
    if role:GetAttribute(EAttribute.Turn) >= 3 then
      roleLv = role:GetAttribute(EAttribute.Lv) - Role.playerMaxLv;
    end
  else
    roleLv = role:GetAttribute(EAttribute.Lv);
  end
  
  if itemData.needLv > 0 and roleLv < itemData.needLv then
    if not this.CheckPlayerNpcLimit(itemData, followIndex) then return end;

    ShowCenterMessage(string.Get(40106));  --等級不足,無法使用
    return;
  end

  if itemData.specialAbility == EItemUseKind.ExpBottle and MachineBox.GetExpBottleCount() >= MachineBox.BaseExpBottle + MachineBox.expExtendNum then
    ShowCenterMessage(string.Get(22733));  --經驗瓶欄位已滿
    return;
  end

  local diffHp = role:GetAttribute(EAttribute.MaxHp) - role:GetAttribute(EAttribute.Hp);
  local diffSp = role:GetAttribute(EAttribute.MaxSp) - role:GetAttribute(EAttribute.Sp);

  if BattleRoyale.GetBattleRoyaleState() then
    diffHp = Role.player:GetAttribute(EAttribute.MaxHp) - Role.player:GetAttribute(EAttribute.Hp);
    diffSp = Role.player:GetAttribute(EAttribute.MaxSp) - Role.player:GetAttribute(EAttribute.Sp);
    local followNpcs = Role.GetFollowNpcs(Role.playerId);
    for k, v in pairs(followNpcs) do
      diffHp = diffHp + v:GetAttribute(EAttribute.MaxHp) - v:GetAttribute(EAttribute.Hp);
      diffSp = diffSp + v:GetAttribute(EAttribute.MaxSp) - v:GetAttribute(EAttribute.Sp);
    end
  end

  if itemData.specialAbility == EItemUseKind.ItemUse_25 and diffHp <= 0 then
    ShowCenterMessage(string.Get(20077));  --HP已補滿
    return;
  end

  if itemData.specialAbility == EItemUseKind.ItemUse_165 and diffSp <= 0 then
    ShowCenterMessage(string.Get(20078));  --SP已補滿
    return;
  end

  if itemData.specialAbility == EItemUseKind.ItemUse_54 and diffHp <= 0 and diffSp <= 0 then
    ShowCenterMessage(string.Get(20079));  --HP、SP已補滿
    return;
  end

  if this.CheckSaddle(itemData, role) then return end

  if Contains(itemData.specialAbility, EItemUseKind.ExpPill) and (followIndex > 0) then
    if (role.data.canGrow ~= nil) and (role.data.canGrow <= 0) then
      ShowCenterMessage(string.Get(40333));  --任務Npc無法使用該物品
      return;
    end
  end

  if Contains(itemData.kind, EItemKind.Item_17, EItemKind.Item_18, EItemKind.Item_19, EItemKind.SpecialAbility, EItemKind.Transform, EItemKind.NpcFaith, EItemKind.Item_52, EItemKind.Item_54, EItemKind.Item_65, EItemKind.Item_66) then --17食物 18補藥 19毒藥 52修練物品 38:外掛用物 48:變身寶物 49:攏絡物品 54.軍團回城捲軸 65.二代專用物 66.加標記用物
    if itemData.kind == EItemKind.Transform and itemData.specialAbility == EItemUseKind.TransformDoll then
      --[[ 變身娃娃不擋騎乘
      if Role.player.horseNpcId ~= 0 then
        ShowCenterMessage(string.Get(50081));  --騎乘中不可使用
        return;
      end
      --]]
    end

    if this.CheckUseCount(role, itemData.Id, followIndex) then
      ShowCenterMessage(string.Get(71094));  --使用次數已滿
      return;
    end

    if not this.CheckPlayerNpcLimit(itemData, followIndex) then return end;

    if itemData.attribute[1].kind == 25 then
      if itemData.attribute[2].kind == 26 then
        if diffHp <= 0 and diffSp <= 0 then
          ShowCenterMessage(string.Get(20079));  --HP、SP已補滿
          return;
        end
      else
        if diffHp <= 0 then
          ShowCenterMessage(string.Get(20077));  --HP已補滿
          return;
        end
      end
    elseif itemData.attribute[1].kind == 26 then
      if itemData.attribute[2].kind == 25 then
        if diffHp <= 0 and diffSp <= 0 then
          ShowCenterMessage(string.Get(20079));  --HP、SP已補滿
          return;
        end
      else
        if diffSp <= 0 then
          ShowCenterMessage(string.Get(20078));  --SP已補滿
          return;
        end
      end
    end

    if Contains(itemData.specialAbility, EItemUseKind.ItemUse_72, EItemUseKind.ItemUse_73) then  --餵養物品
      TlogError("Item.UseItem", " 餵養物品");
      return;
    end

    if itemData.specialAbility == EItemUseKind.ItemUse_191 then  --四系小寵物
      if Role.player:GetAttribute(EAttribute.Element) ~= itemData.element then
        ShowCenterMessage(string.Get(80101));  --屬性不符，無法使用
        return;
      end
    end

    if itemData.specialAbility == EItemUseKind.ItemUse_194 then  --商道錦囊
      if Role.player:GetSkillLv(14003) == 0 then
        ShowCenterMessage(string.Get(80123));  --您必須學會『買賣術』才能使用商道錦囊
        return;
      end
    end

    if itemData.specialAbility == EItemUseKind.NpcPicture then  --武將畫冊
      if Inn.HaveNpc(itemData.spare3) then
        ShowCenterMessage(string.Get(90014));  --「已有此武將」
        return;
      end

      if Role.GetFollowNpc(Role.playerId, itemData.spare3) ~= nil then
        ShowCenterMessage(string.Get(90016));  --「已有此武將」
        return;
      end

      local isFull = true;
      for i = 1, Role.maxFollowNpc do
        if Role.GetRole(EHuman.FollowNpc, Role.playerId, i) == nil then
          isFull = false;
          break;
        end
      end

      if isFull then
        ShowCenterMessage(string.Get(71184));  --武將欄位已滿
        return;
      end
    end

    if Contains(itemData.specialAbility, EItemUseKind.BattleAttributePill, EItemUseKind.ExpBottle)  then
      if Role.player.war ~= EWar.None then
        ShowCenterMessage(string.Get(80099));  --戰鬥中，無法使用
        return;
      end
    end

    if itemData.specialAbility == EItemUseKind.ItemUse_202 then
      if Role.GetInnLv() >= 5 then
        ShowCenterMessage(string.Get(71275));  --您的客棧已經無法再升級了
        return;
      end
    end

    if itemData.specialAbility == EItemUseKind.ItemUse_48 then
      if BlissBagData.IsBlissBag(itemData.Id) and (this.currentThingGrid - this.GetBagCount(EThings.Bag, 0)) < blissBagDatas[itemData.Id].kindCount then
        ShowCenterMessage(string.Get(80359));  --您身上的物品欄空間不足哦!
        return;
      end
    end

    if itemData.specialAbility == EItemUseKind.Doro then
      if Item.CheckBagIsFull(true) then
        return;
      end
    end

    if itemData.specialAbility == EItemUseKind.Mounts then  --坐騎畫冊
      if Mounts.HaveCollection(itemData.spare3) then
        ShowCenterMessage(string.Get(10399));  --「已有此座騎外型」
        return;
      end
    end

    if Contains(itemData.specialAbility, EItemUseKind.ExpPill, EItemUseKind.PlayerAttributePill, EItemUseKind.SkillPointPill, EItemUseKind.ResetSkill) then
      if role:GetAttribute(EAttribute.Turn) < itemData.turn then
        ShowCenterMessage(string.Get(71103));  --轉生條件不符
        return;
      end
    end

    if itemData.kind == EItemKind.SpecialAbility and itemData.specialAbility == EItemUseKind.ItemUse_10 and followIndex > 0 then
      if role.data.graspPill.count >= 1 then
        ShowCenterMessage(string.Get(71183));  --該武將目前已有領悟丹狀態
        return;
      end
    end

    --世界王
    if itemData.specialAbility == EItemUseKind.ItemUse_207 or itemData.specialAbility == EItemUseKind.ItemUse_203 then
      local startTime = activityTimes[5][1].serverStartTime;
      local endTime = activityTimes[5][1].serverCloseTime;

      if not (DateTime.Compare(endTime, CGTimer.serverTime) > 0 and DateTime.Compare(CGTimer.serverTime, startTime) > 0) then
        ShowCenterMessage(string.Get(80273));
        return;
      end

      local missionId = 12231;

      local mission = MarkManager.GetMission(missionId);

      if mission ~= nil then
        missionId = mission.id;
      end

      if itemData.specialAbility == EItemUseKind.ItemUse_207 and markDatas[missionId] ~= nil then
        if mission ~= nil then
          if mission.step == 1 then  --1的話表示是  隨機倍率
            ShowCenterMessage(string.Get(71301));
          else
            ShowCenterMessage(string.format(string.Get(71304), mission.step));
          end
          return;
        end
      end
    end

    if itemData.kind == EItemKind.Item_66 then
      if MarkManager.GetMissionFlag(itemData.skillLink) then
        ShowCenterMessage(string.Concat(string.Get(80224), markDatas[itemData.giftDot].name));
        return;
      end

      local mission = MarkManager.GetMission(itemData.giftDot);

      if mission ~= nil then
        ShowCenterMessage(string.Concat(string.Get(80221), markDatas[itemData.giftDot].name));
        return;
      end
    end

    if itemData.specialAbility == EItemUseKind.Mounts and not MarkManager.GetMissionFlag(10024) then
      ShowCenterMessage(string.Get(20076));
      return;
    end

    if itemData.specialAbility == EItemUseKind.ItemUse_203 then
      local mission = MarkManager.GetMission(12207);

      if mission ~= nil then
        if mission.step < this.MaxBossCount then
          ShowCenterMessage(string.Get(80274));
          return;
        end
      else
        ShowCenterMessage(string.Get(80274));
        return;
      end
    end

    if itemData.specialAbility == EItemUseKind.FairyBean then
      local sceneData = sceneDatas[SceneManager.sceneId];

      if (sceneData ~= nil and sceneData.kind == 2) or SceneManager.sceneId == Activity.orgWarSceneId or Dungeon.isInRoom then
        ShowCenterMessage(string.Get(90002));
        return;
      end
    end
    
    local maxQuant, fixFollowIndex = this.GetMaxUseCount(itemSave.Id, role, true);
    if maxQuant <= 0 then return end

    if fixFollowIndex ~= nil then
      followIndex = fixFollowIndex;
    end
    
    maxQuant = math.min(maxQuant, itemSave.quant);

    if itemData.specialAbility == EItemUseKind.RandomWeaponRecipe then 
      --專武福袋要確認數量
      if maxQuant > 1 then
        ShowCountInput(string.Get(40314), 1, maxQuant,
          function(text)
            if text == nil then return end
  
            local quant = tonumber(text);
            if quant == 0 then return end
            
            ShowCheckMessage(function(result) if result then  this.SendUseItem(bagIndex, quant, followIndex, 0); end end,
              string.format( string.Get(22719), itemData.furnaceCount * quant, quant, UIFurnace.GetDebris(itemData.furnaceKind)));                    
          end
        );
      else
        ShowCheckMessage(function(result) if result then  this.SendUseItem(bagIndex, 1, followIndex, 0); end end,
          string.format( string.Get(22719), itemData.furnaceCount, 1, UIFurnace.GetDebris(itemData.furnaceKind)));        
      end
      return;
    end

    if itemData.specialAbility == EItemUseKind.ArmyBlissBag then
      --檢查活動時間
      if not ArmyLuckyBag.IsActive() then
        ShowCenterMessage(string.Get(60065));
        return;
      end
    end

    if bit.band(itemData.restrict2, 128) == 128 then
      if maxQuant > this.currentUniqueBloodGrid - this.GetBagCount(EThings.UniqueBlood) then
        maxQuant = this.currentUniqueBloodGrid - this.GetBagCount(EThings.UniqueBlood);
      end
    end

    if maxQuant > 1 then
      ShowCountInput(string.Get(40314), 1, maxQuant,
        function(text)
          if text == nil then return end

          local quant = tonumber(text);
          if quant == 0 then return end

          this.SendUseItem(bagIndex, quant, followIndex, 0);

          this.CloseUIByFirework(itemData);
        end
      );
    else
      this.SendUseItem(bagIndex, 1, followIndex, 0);
      
      this.CloseUIByFirework(itemData);
    end

    return;
  end

  this.SaveEquipMessage(itemSave.Id, followIndex);

  if this.IsEquip(itemData.fitType) or this.IsStyle(itemData.fitType) then
    if this.CheckEquip(itemSave.Id, followIndex) then return end

    if followIndex > 0 then
      local followNpc = Role.GetRole(EHuman.FollowNpc, Role.playerId, followIndex);
      if followNpc ~= nil then
        if followNpc.data.canGrow <= 0 then
          ShowCenterMessage(string.Get(40333));  --任務Npc無法使用該物品
          return;
        end
      end
    end

    this.SendUseEquip(bagIndex, followIndex);
  else
    if this.ThingFindEmpty() == 0 and Contains(itemData.kind, EItemKind.SpecialAbility) and itemSave.Id ~= 46075 then
      ShowCenterMessage(string.Get(40311));  --請先清出空間,才可換取禮物
    end

    this.SendUseItem(bagIndex, 1, followIndex, 0);
  end
  
  if not Contains(Role.player.war, EWar.None, EWar.Guest) then
    FightField.NextControlMan();
    this.SetCloseUICallback(
      function()
        UI.Close(UIStatus);
        UI.Close(UIItemInfo);
      end
    );
  else
    this.SetCloseUICallback(nil);
  end
end

function Item.CloseUIByFirework(itemData)
  if itemData.specialAbility == EItemUseKind.Firework then
    if UI.IsVisible(UIItemInfo) then
      UI.Close(UIItemInfo);
    end

    if UI.IsVisible(UIStatus) then
      UI.Close(UIStatus);
    end
  end
end

function Item.IsDismantle(itemData)
  return itemData.furnaceCount > 0;
end

function Item.Dismantle(bagKind, bagIndex, followIndex)
  local itemSave = this.GetBagItem(EThings.Bag, bagIndex);
  if itemSave == nil then return end
  
  local itemData = itemDatas[itemSave.Id];
  if itemData == nil then return end

  local subTag = 0;
  local baseRate = 5;
  local activeRate = 1;
  local total = 0;
  local disString = "";
  local furnaceCount = itemData.furnaceCount;
  local inputNum = 1;
  local queryStr = "";

  if this.IsDismantle(itemData) and itemData.furnaceKind == 1 then
    subTag = EFurnace.Npc;
    disString = string.Get(21033);
  elseif this.IsDismantle(itemData) and itemData.furnaceKind == 2 then
    subTag = EFurnace.Equip;
    disString = string.Get(21034);
  elseif this.IsDismantle(itemData) and itemData.furnaceKind == 3 then
    subTag = EFurnace.ExclusiveWeapon;
    disString = string.Get(22698);
  end

  local func = function(result)
    ShowCheckMessage(
      function(result)
        if result then
          --C:089-003 <分解物品> +count(1) <<+背包索引(1) +數量(4)>>
          sendBuffer:Clear();
          sendBuffer:WriteByte(1);
          sendBuffer:WriteByte(bagIndex);
          sendBuffer:WriteInt32(inputNum);
          Network.Send(89, 3, sendBuffer);

          if UI.IsVisible(UIItemInfo) then
            UI.Close(UIItemInfo);
          end
        end
      end,
      queryStr,
      Role.player
    );
  end

  if itemSave.quant > 1 then
    ShowCountInput(string.Get(21026), 1, itemSave.quant,
      function(text)
        if text == nil then return end
        if tonumber(text) == 0 then return end

        total = tonumber(text) * math.floor(furnaceCount / baseRate) * activeRate;
        inputNum = tonumber(text);
        queryStr = string.Concat(string.Get(21026), itemData:GetName(true, true), string.Get(30034), string.format(disString, total));
        func();
      end
    );
  else
    total = math.floor(furnaceCount / baseRate) * activeRate;
    inputNum = itemSave.quant;
    queryStr = string.Concat(string.Get(21026), itemData:GetName(true, true), string.Get(30034), string.format(disString, total));
    func();
  end
end

function Item.DropItem(bagKind, bagIndex, followIndex)
  local itemSave = this.GetBagItem(EThings.Bag, bagIndex);
  if itemSave == nil then return end

  local itemData = itemDatas[itemSave.Id];
  if itemData == nil then return end

  if itemSave.isLock then
    ShowCenterMessage(string.Get(22042));
    return;
  end

  if GdThingManager.FindThingSlot() == nil then
    ShowCenterMessage(string.Get(40043));
    return;
  end

  local quant, dropText;
  if itemData.kind == EItemKind.CityWeapon then
    quant = 1;
    dropText = string.Get(22120);
  else
    quant = itemSave.quant;
    dropText = string.Get(98025);
  end

  if quant > 1 then
    ShowCountInput(string.Get(98025), 1, quant,
      function(text)
        if text == nil then return end
        if tonumber(text) == 0 then return end

        ShowCheckMessage(
          function(result)
            if result then
              --C:023-003 <丟棄背包物品> +背包索引(1) +數量(1)
              sendBuffer:Clear()
              sendBuffer:WriteByte(bagIndex);  --物品欄第幾格
              sendBuffer:WriteInt32(tonumber(text));
              Network.Send(23, 3, sendBuffer);
      
              if UI.IsVisible(UIItemInfo) then
                UI.Close(UIItemInfo);
              end
            end
          end,
          string.Concat(string.Get(20168), dropText, itemData:GetName(true, true)),
          Role.player
        );
      end
    );
  else
    ShowCheckMessage(
      function(result)
        if result then
          --C:023-003 <丟棄背包物品> +背包索引(1) +數量(1)
          sendBuffer:Clear()
          sendBuffer:WriteByte(bagIndex);  --物品欄第幾格
          sendBuffer:WriteInt32(1);
          Network.Send(23, 3, sendBuffer);
  
          if UI.IsVisible(UIItemInfo) then
            UI.Close(UIItemInfo);
          end
        end
      end,
      string.Concat(string.Get(20168), dropText, itemData:GetName(true, true)),
      Role.player
    );
  end
end

function Item.UnEquip(bagKind, bagIndex, followIndex)
  local itemSave = this.GetBagItem(EThings.Equip, bagIndex, followIndex);
  if itemSave == nil then return end

  local emptyIndex = this.ThingFindEmpty();
  if emptyIndex <= 0 then
    ShowCenterMessage(string.Get(80359));
    return;
  end

  if not Contains(Role.player.war, EWar.None, EWar.Guest) then --檢查是否出戰&是否當前操作
    if followIndex > 0 then 
      if FightField.CheckFollowNpcIsInBattle(followIndex) or Role.player.war == EWar.OneBringFour then  --一帶四不必判斷出戰武將
        if not FightField.CheckFollowNpcCurrentControl(followIndex) then 
          ShowCenterMessage(string.Get(22595));        
          return;
        end
      end
    else
      if FightField.conIdx ~= FightField.GetPlayerIdx() then 
        ShowCenterMessage(string.Get(22595));        
        return;
      end
    end
  end

  this.SaveEquipMessage(itemSave.Id, followIndex);

  if followIndex > 0 then
    --C:023-018 <卸下武將裝備> +武將索引(1) +裝備位置(1) +背包索引(1)
    sendBuffer:Clear()
    sendBuffer:WriteByte(followIndex);
    sendBuffer:WriteByte(bagIndex);
    sendBuffer:WriteByte(emptyIndex);
    Network.Send(23, 18, sendBuffer);
  else
    --C:023-012 <卸下玩家裝備> +裝備位置(1) +背包索引(1)
    sendBuffer:Clear()
    sendBuffer:WriteByte(bagIndex);
    sendBuffer:WriteByte(emptyIndex);
    Network.Send(23, 12, sendBuffer);
  end

  if not Contains(Role.player.war, EWar.None, EWar.Guest) then
    FightField.NextControlMan();
    this.SetCloseUICallback(
      function()
        UI.Close(UIStatus);
        UI.Close(UIItemInfo);
      end
    );
  else
    this.SetCloseUICallback(nil);
  end
end

function Item.UnEquipAll(followIndex)
  local equips = {};
  for i = 1, this.MaxEquip do
    local itemSave = this.GetBagItem(EThings.Equip, i, followIndex);
    if itemSave ~= nil then
      table.insert(equips, i);
    end
  end

  local emptys = this.GetBagEmptys();
  local emptysCopunt = table.Count(emptys);
  if emptysCopunt <= 0 or emptysCopunt < table.Count(equips) then
    ShowCenterMessage(string.Get(80359));
    return;
  end

  if not Contains(Role.player.war, EWar.None, EWar.Guest) then --檢查是否出戰&是否當前操作
    if followIndex > 0 then
      if FightField.CheckFollowNpcIsInBattle(followIndex) or Role.player.war == EWar.OneBringFour then  --一帶四不必判斷出戰武將
        if not FightField.CheckFollowNpcCurrentControl(followIndex) then
          ShowCenterMessage(string.Get(22595));
          return;
        end
      end
    else
      if FightField.conIdx ~= FightField.GetPlayerIdx() then
        ShowCenterMessage(string.Get(22595));
        return;
      end
    end
  end

  for i = 1, table.Count(equips) do
    if followIndex > 0 then
      --C:023-018 <卸下武將裝備> +武將索引(1) +裝備位置(1) +背包索引(2)
      sendBuffer:Clear()
      sendBuffer:WriteByte(followIndex);
      sendBuffer:WriteByte(equips[i]);
      sendBuffer:WriteUInt16(emptys[i]);
      Network.Send(23, 18, sendBuffer);
    end
  end
end

--取背包的空格空間
function Item.GetBagEmptys()
  local bag = this.GetBag(EThings.Bag);
  local emptys = {};
  if this.GetBagCount(EThings.Bag, 0) >= this.currentThingGrid then return emptys end

  for i = 1, this.currentThingGrid do
    if bag[i] == nil then
      table.insert(emptys, i);
    end
  end

  return emptys;
end

function Item.DropEquip(bagKind, bagIndex, followIndex)
  local itemSave = this.GetBagItem(EThings.Equip, bagIndex, followIndex);
  if itemSave == nil then return end

  if itemSave.isLock then
    ShowCenterMessage(string.Get(22042));
    return;
  end

  if not Contains(Role.player.war, EWar.None, EWar.Guest) then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  ShowCheckMessage(
    function(result)
      if result then
        this.SaveEquipMessage(itemSave.Id, followIndex);

        if followIndex > 0 then
          --C:023-019 <丟棄武將裝備> +武將索引(1) +裝備位置(1)
          sendBuffer:Clear()
          sendBuffer:WriteByte(followIndex);
          sendBuffer:WriteByte(bagIndex);
          Network.Send(23, 19, sendBuffer);
        else
          --C:023-013 <丟棄玩家裝備> +背包索引(1)
          sendBuffer:Clear()
          sendBuffer:WriteByte(bagIndex);
          Network.Send(23, 13, sendBuffer);
        end

        if UI.IsVisible(UIItemInfo) then
          UI.Close(UIItemInfo);
        end
      end
    end,
    string.Concat(string.Get(20168), string.Get(98025), itemDatas[itemSave.Id]:GetName(true, true)),
    Role.player
  );
end

function Item.GetMaxUseCount(itemId, role, showZeroMessage)
  local itemData = itemDatas[itemId];
  if itemData == nil then return 0 end
  if role == nil then return 0 end

  --大量使用
  local result = 1;
  local followIndex = nil;

  --太上金丹, 太上仙丹
  if Contains(itemId, 46130, 46101) then
    result = Role.maxAttrPill - role.data.attrPillCount;

  --健體玉液
  elseif itemId == 46240 then
    result = Role.maxHpPill - role.data.hpPillCount;

  --幻能玉液
  elseif itemId == 46238 then
    result = Role.maxSpPill - role.data.spPillCount;

  --技能丹
  elseif itemId == 46395 then
    result = Role.maxSkillPill - role.data.skillPillCount;

  --健體仙丹, 健體鐵丸
  elseif Contains(itemId, 46241, 46973) then
    result = Role.npcMaxHpPill - role.data.hpPillCount;

  --幻能仙丹, 幻能鐵丸
  elseif Contains(itemId, 46239, 46971) then
    result = Role.npcMaxSpPill - role.data.spPillCount;

  --昇華丹, 昇華膽
  elseif Contains(itemId, 46050, 46063) then
    result = Role.npcMaxSublimePill - role.data.sublimeCount;

  --攻擊藥水
  elseif Contains(itemId, 46501, 46622) then
    if DrugBuff.timeBuffs[ETimeBuff.AtkUp].time ~= nil then
      local diffTime = DateTime.Subtract(CGTimer.serverTime:AddDays(1), DrugBuff.timeBuffs[ETimeBuff.AtkUp].time).TotalSeconds;
      result = math.floor(diffTime / 60 / itemData.spare3);
    else
      result = math.floor(24 * 60 / itemData.spare3);
    end

    if showZeroMessage and result <= 0 then
      ShowCenterMessage(string.Get(40390));
      return 0;
    end

    followIndex = 0;

  --知力藥水
  elseif Contains(itemId, 46502, 46623) then
    if DrugBuff.timeBuffs[ETimeBuff.IntUp].time ~= nil then
      local diffTime = DateTime.Subtract(CGTimer.serverTime:AddDays(1), DrugBuff.timeBuffs[ETimeBuff.IntUp].time).TotalSeconds;
      result = math.floor(diffTime / 60 / itemData.spare3);
    else
      result = math.floor(24 * 60 / itemData.spare3);
    end
    
    if showZeroMessage and result <= 0 then
      ShowCenterMessage(string.Get(40390));
      return 0;
    end

    followIndex = 0;

  --攏絡物品
  elseif itemData.kind == EItemKind.NpcFaith then
    if showZeroMessage and role:GetAttribute(EAttribute.Faith) >= 100 then
      ShowCenterMessage(string.Get(20754));
      return 0;
    end

    for i = 1, 2 do
      if itemData.attribute[i].value > 100 then
        if itemData.attribute[i].kind == 64 then
          result = math.ceil((100 - role:GetAttribute(EAttribute.Faith)) / (itemData.attribute[i].value - 100));
          break;
        end
      end
    end
    
  --補品
  elseif Contains(itemData.kind, EItemKind.Item_17, EItemKind.Item_18, EItemKind.Item_52) then
    local hpCount = 0;
    local spCount = 0;
    local roles = {};

    if BattleRoyale.GetBattleRoyaleState() then
      local followNpcs = Role.GetFollowNpcs(Role.playerId);
      roles[1] = Role.player;
      for k, v in pairs(followNpcs) do
        table.insert(roles, v);
      end
    else
      table.insert(roles, role);
    end

    for i = 1, 2 do
      if itemData.attribute[i].value > 100 then
        if itemData.attribute[i].kind == 25 then
          for k, v in pairs(roles) do
            hpCount = math.max(hpCount, math.ceil((v:GetAttribute(EAttribute.MaxHp) - v:GetAttribute(EAttribute.Hp)) / (itemData.attribute[i].value - 100)));
          end
        elseif itemData.attribute[i].kind == 26 then
          for k, v in pairs(roles) do
            spCount = math.max(spCount, math.ceil((v:GetAttribute(EAttribute.MaxSp) - v:GetAttribute(EAttribute.Sp)) / (itemData.attribute[i].value - 100)));
          end
        end
      end
    end

    result = math.max(hpCount, spCount);

  --歡樂煙火
  elseif itemData.kind == EItemKind.SpecialAbility and itemData.specialAbility == EItemUseKind.Firework then 
    if itemId == 46075 then
      result = this.MaxFirework;
    else
      result = 1;
    end

  --福神
  elseif itemData.kind == EItemKind.SpecialAbility and itemData.specialAbility == EItemUseKind.ExpGod then
    result = math.floor((this.MaxGod - Role.player.data.godMission) / itemData.skillLink);
    if showZeroMessage and result <= 0 then
      ShowCenterMessage(string.Get(80124));
      return 0;
    end

    followIndex = 0;

  --機關盒晶石
  elseif itemData.kind == EItemKind.SpecialAbility and itemData.specialAbility == EItemUseKind.MachineBoxStone then
    if showZeroMessage and MachineBox.GetSparCount() >= MachineBox.MaxSparBase + MachineBox.sparExtendNum and not MachineBox.CheckHaveSpar(itemId) then
      ShowCenterMessage(string.Get(20739));
      return 0;
    end
    
    local currentCount = 0;
    for i = 1, MachineBox.MaxSpar do
      if MachineBox.spar[i] ~= nil and MachineBox.spar[i].Id == itemId then
        currentCount = MachineBox.spar[i].count;
        break;
      end
    end
    
    result = math.floor((MachineBox.MaxSparCount - currentCount) / itemData.threshold);
    if showZeroMessage and result <= 0 then
      ShowCenterMessage(string.Get(40390));
      return 0;
    end

    followIndex = 0;

  elseif itemData.specialAbility == EItemUseKind.Astrolabe then
    result = math.floor((Astrolabe.maxNatalPoint - Astrolabe.natalPoint) / itemData.elementValue);
    if showZeroMessage and result <= 0 then
      ShowCenterMessage(string.Get(21684));
      return 0;
    end

    followIndex = 0;

  elseif itemData.specialAbility == EItemUseKind.RandomWeaponRecipe then 
    result = math.floor(UIFurnace.GetDebris(itemData.furnaceKind) / itemData.furnaceCount);

    if showZeroMessage and result <= 0 then
      ShowCenterMessage(string.Get(21180));
      return 0;
    end

    followIndex = 0;

  --玩家三轉時限制以下經驗丹每日只能使用50次
  elseif itemData.specialAbility == EItemUseKind.ExpPill and role:GetAttribute(EAttribute.Turn) >= 3 then
    if itemId == 46211 then
      result = this.maxTurn3ExpPill - RoleCount.Get(ERoleCount.ExpPill);
    elseif itemId == 46212 then
      result = this.maxTurn3ExpPill - RoleCount.Get(ERoleCount.ExpPill_Green);
    elseif itemId == 46213 then
      result = this.maxTurn3ExpPill - RoleCount.Get(ERoleCount.ExpPill_Yellow);
    elseif itemId == 46214 then
      result = this.maxTurn3ExpPill - RoleCount.Get(ERoleCount.ExpPill_Pink);
    elseif itemId == 46215 then
      result = this.maxTurn3ExpPill - RoleCount.Get(ERoleCount.ExpPill_Red);
    elseif itemId == 46216 then
      result = this.maxTurn3ExpPill - RoleCount.Get(ERoleCount.ExpPill_SkyBlue);
    elseif itemId == 46217 then
      result = this.maxTurn3ExpPill - RoleCount.Get(ERoleCount.ExpPill_WaterBlue);
    elseif itemId == 46218 then
      result = this.maxTurn3ExpPill - RoleCount.Get(ERoleCount.ExpPill_Orange);
    elseif itemId == 46219 then
      result = this.maxTurn3ExpPill - RoleCount.Get(ERoleCount.ExpPill_Purple);
    end

    if showZeroMessage and result <= 0 then
      ShowCenterMessage(string.format(string.Get(20898 ), itemData:GetName(true)));
      return 0;
    end
    
  elseif itemData.kind == EItemKind.SpecialAbility and bit.band(itemData.restrict2, 16) == 16 then
    result = RoleCount.Max(itemData.roleCountIndex) - (RoleCount.Get(itemData.roleCountIndex) * itemData.roleCountValue);
    
    if showZeroMessage and result <= 0 then
      ShowCenterMessage(string.Get(23434));
      return 0;
    end

    --其他可大量使用類型
  elseif itemData.kind == EItemKind.SpecialAbility and Contains(itemData.specialAbility, 4, 26, 48, 65, 92, 94, 95, 108, 141, 196, 210) then
    result = this.MaxThingGridQuant;
  end

  return result, followIndex;
end

function Item.GetItemEffectString(surfaceDataId)
  local effectString = "";
  local effectValue = 0;
  local itemData = itemDatas[surfaceDataId];

  if itemData.specialAbility ~= EItemUseKind.ItemUse_72 then
    for k, attribute in pairs(itemData.attribute) do
      --Item.dat中沒有屬性或數值的物品不處理
      if attribute.kind ~= 0 and attribute.value ~= 0 then
        effectValue = attribute.value - 100;

        if attribute.kind == EAttribute.Hp then
          if effectValue >= 0 then
            effectString = string.Concat(effectString, "HP+", tostring(effectValue))
          else
            effectString = string.Concat(effectString, "HP", tostring(effectValue))
          end
        elseif attribute.kind == EAttribute.Sp then
          if effectValue >= 0 then
            effectString = string.Concat(effectString, "SP+", tostring(effectValue))
          else
            effectString = string.Concat(effectString, "SP", tostring(effectValue))
          end
        elseif attribute.kind == EAttribute.EquipAtk then
          if effectValue >= 0 then
            effectString = string.Concat(effectString, string.Get(30048), tostring(effectValue))
          else
            effectString = string.Concat(effectString, string.Get(30049), tostring(effectValue))
          end
        elseif attribute.kind == EAttribute.EquipDef then
          if effectValue >= 0 then
            effectString = string.Concat(effectString, string.Get(30050), tostring(effectValue))
          else
            effectString = string.Concat(effectString, string.Get(30051), tostring(effectValue))
          end
        elseif attribute.kind == EAttribute.EquipInt then
          if effectValue >= 0 then
            effectString = string.Concat(effectString, string.Get(30052), tostring(effectValue))
          else
            effectString = string.Concat(effectString, string.Get(30053), tostring(effectValue))
          end
        elseif attribute.kind == EAttribute.EquipAgi then
          if effectValue >= 0 then
            effectString = string.Concat(effectString, string.Get(30054), tostring(effectValue))
          else
            effectString = string.Concat(effectString, string.Get(30055), tostring(effectValue))
          end
        elseif attribute.kind == EAttribute.Crit then
          if effectValue >= 0 then
            effectString = string.Concat(effectString, string.Get(80318), "+", tostring(effectValue))
          else
            effectString = string.Concat(effectString, string.Get(80318), tostring(effectValue))
          end
        elseif attribute.kind == EAttribute.CritDamage then
          if effectValue >= 0 then
            effectString = string.Concat(effectString, string.Get(23658), "+", tostring(effectValue))
          else
            effectString = string.Concat(effectString, string.Get(23658), tostring(effectValue))
          end
        elseif attribute.kind == EAttribute.Dodge then
          if effectValue >= 0 then
            effectString = string.Concat(effectString, string.Get(23665), "+", tostring(effectValue))
          else
            effectString = string.Concat(effectString, string.Get(23665), tostring(effectValue))
          end
        elseif attribute.kind == EAttribute.Hit then
          if effectValue >= 0 then
            effectString = string.Concat(effectString, string.Get(23666), "+", tostring(effectValue))
          else
            effectString = string.Concat(effectString, string.Get(23666), tostring(effectValue))
          end

        elseif attribute.kind == EAttribute.EquipMaxHp then
          if effectValue >= 0 then
            effectString = string.Concat(effectString, string.Get(30056), tostring(effectValue))
          else
            effectString = string.Concat(effectString, string.Get(30057), tostring(effectValue))
          end
        elseif attribute.kind == EAttribute.EquipMaxSp then
          if effectValue >= 0 then
            effectString = string.Concat(effectString, string.Get(30058), tostring(effectValue))
          else
            effectString = string.Concat(effectString, string.Get(30059), tostring(effectValue))
          end
        end

        effectString = string.Concat(effectString, " ");
      end
    end
  end

  --如果沒有上述說明
  if effectString == "" then
    if Contains(itemData.kind, EItemKind.Item_39) then
      effectString = string.Get(30060);
    elseif Between(itemData.kind, EItemKind.Item_23, EItemKind.Ice) then
      effectString = string.Get(30061);
    elseif Between(itemData.kind, EItemKind.Item_40, EItemKind.Item_44) then
      effectString = string.Get(30061);
    elseif Contains(itemData.kind, EItemKind.Item_16) then
      effectString = string.Get(30062);
    elseif Contains(itemData.kind, EItemKind.Item_14, EItemKind.SpecialAbility, EItemKind.Item_65) then
      effectString = itemData.description;
    else
      effectString = string.Get(30063);
    end

    effectString = string.Concat(effectString, " ");
  end

  --第1行字串
  --屬性   1地 2水 3火 4風 5心
  if Between(itemData.element, 1, 4) then
    effectString = string.Get(30064);
  end

  if Between(itemData.element, 1, 5) then
    effectString = string.Concat(effectString, string.GetElement(itemData.element), " ");
  end

  --限制等級
  if itemData.needLv > 0 then
    effectString = string.Concat(effectString, string.Get(30065), tostring(itemData.needLv));
  end

  return string.Concat(surfaceDataId, " ", effectString);
end

function Item.DropGold()
  ShowCountInput(string.Get(50303), 10, Role.GetPlayerGold(),
    function(text)
      if text == nil then return end
      if tonumber(text) == 0 then return end

      --C:023-016 <丟棄金錢到場景> + 數量(4)
      sendBuffer:Clear()
      sendBuffer:WriteInt32(tonumber(text));
      Network.Send(23, 16, sendBuffer);
    end
  );
end

function Item.ShowStoneLight(stoneLv, go)

  if go == nil then return end

  if stoneLv < 1 or stoneLv > this.MaxStoneLv then
    go.texture = nil;
    go.enabled = false;
    return;
  else
    go.enabled = true;
    go.gameObject:SetActive(true);
  end

  local picName = "";
  if Between(stoneLv, 1, 5) then
    picName = "Light_green01";
  elseif Between(stoneLv, 6, 10) then
    picName = "Light_bule01";
  elseif Between(stoneLv, 11, 15) then
    picName = "Light_purple01";
  end

  TextureManager.SetPngByCallBack(ETextureUseType.UI, picName, go,
    function(sucess)
      go.uvRect = Rect.New(0, 0, 1, 1 / 3);
    end
  );

  go:ShiftUV(1, 3, 0.5, -1);
end

function Item.RefreshRoleCount(data)
  if Contains(data.index, ERoleCount.Bag_1, ERoleCount.Bag_2, ERoleCount.Bag_3) then
    this.currentThingGrid = this.InitBagGrid +
                            RoleCount.Get(ERoleCount.Bag_1) * this.AddThingGrid +
                            RoleCount.Get(ERoleCount.Bag_2) * this.AddThingGrid +
                            RoleCount.Get(ERoleCount.Bag_3) * this.AddThingGrid;

    UIBag.UpdateUI();
  elseif Contains(data.index, ERoleCount.Bank_1, ERoleCount.Bank_2, ERoleCount.Bank_3) then
    this.currentBThingGrid = this.BaseBThingGrid +
                            RoleCount.Get(ERoleCount.Bank_1) * this.AddBThingGrid +
                            RoleCount.Get(ERoleCount.Bank_2) * this.AddBThingGrid +
                            RoleCount.Get(ERoleCount.Bank_3) * this.AddBThingGrid;
    if UI.IsVisible(UIBank) then
      UIBank.UpdateBankUI(false);
    end

  elseif Contains(data.index, ERoleCount.Storage_1, ERoleCount.Storage_2) then
    this.currentStorageGrid = RoleCount.Get(ERoleCount.Storage_1) * this.AddBThingGrid +
                             RoleCount.Get(ERoleCount.Storage_2) * this.AddBThingGrid
    if UI.IsVisible(UIBank) then
      UIBank.UpdatStorage(false);
    end
  elseif Contains(data.index, ERoleCount.UniqueBloodBag) then
    this.currentUniqueBloodGrid = this.baseUniqueBloodBagGrid +
      RoleCount.Get(ERoleCount.UniqueBloodBag) * this.AddBThingGrid;
    UIBag.UpdateUI();
  end
end

function Item.ConditionEquip(itemId)
  local itemData = itemDatas[itemId];

  return this.IsTypeOfEquips(itemData.fitType) or Contains(itemData.kind, EItemKind.Item_12, EItemKind.Item_13, EItemKind.Item_14, EItemKind.Item_39, EItemKind.Item_56, EItemKind.SoulWeapon, EItemKind.NpcEquip, EItemKind.SoulEquip);
end

function Item.ConditionProps(itemId)
  local itemData = itemDatas[itemId];

  if (this.IsTypeOfEquips(itemData.fitType)) then return false end
  if (Between(itemData.kind, EItemKind.Item_24, EItemKind.Item_35) or Between(itemData.kind, EItemKind.Item_40, EItemKind.Item_46)) then return false end

  return true;
end

function Item.ConditionMaterial(itemId)
  local itemData = itemDatas[itemId];

  return Between(itemData.kind, EItemKind.Item_24, EItemKind.Item_35) or Between(itemData.kind, EItemKind.Item_40, EItemKind.Item_46);
end

function Item.ConditionFurnace(itemSave, kind)
  local data = itemDatas[itemSave.Id];

  if data.specialAbility == EItemUseKind.WeaponRecipe or data.specialAbility == EItemUseKind.RandomWeaponRecipe then 
    return false;
  end
  
  if itemSave.styleLv > 0 then
    return false;
  end

  return data.furnaceKind == kind;
end

function Item.CheckItemUseState(itemId)
  local itemData = itemDatas[itemId];

  if Role.player.war == EWar.None then
    if itemData.btnState > 0 then
      return true;
    else
      return false;
    end
  else
    if itemData.btnState == 11 then
      return false;
    elseif itemData.btnState <= 0 then
      return false;
    else
      return true;
    end
  end
end

function Item.CheckSaddle(itemData, role)
  if itemData == nil or role == nil then return true end

  local npcData = npcDatas[role.npcId];

  if itemData.kind == EItemKind.Item_14 and itemData.specialAbility == EItemUseKind.ItemUse_81 and npcData ~= nil then
    if itemData.Id == 23330 and npcData.kind ~= 22 then
      ShowCenterMessage(string.Get(20904));  --鐵血鞍，只有『大象』才可裝備
      return true;
    end

    if itemData.Id == 23331 and npcData.saddleKind ~= 2 then
      ShowCenterMessage(string.Get(20905));  --精製的鐵血鞍，只有『扶南戰象』才可裝備
      return true;
    end

    if itemData.Id == 23333 and npcData.kind ~= 9 then
      ShowCenterMessage(string.Get(20906));  --狂暴鞍，只有『豹』和『獅子』才可裝備
      return true;
    end

    if itemData.Id == 23334 and npcData.saddleKind ~= 4 then
      ShowCenterMessage(string.Get(20907));  --精製的狂暴鞍，只有『豹王』、『獅王』、『白虎』才可裝備
      return true;
    end

    if itemData.Id == 23337 and npcData.saddleKind ~= 5 then
      ShowCenterMessage(string.Get(20908));  --精製的智賢鞍，只有『鷹』、『獨角獸』才可裝備
      return true;
    end

    if npcData.kind ~= 9 and npcData.kind ~= 22 and itemData.spare3 == 0 then
      ShowCenterMessage(string.Get(20926));  --馬鞍限馬或大象才可裝備
      return true;
    end

    return false;
  else
    return false;
  end
end

function Item.SetCloseUICallback(fun)
  callback = fun;
end

--檢查是否有招換技能(14026)要裝的紋章
function Item.CheckSummonEquip(skillId, role)
  local followIdx = 0;
  if role.kind ~= EHuman.Player then 
    followIdx = role.index;
  end
  local itemSave = this.GetBagItem(EThings.Equip, EItemFitType.Equip_Spec, followIdx);
  if itemSave == nil then return false end

  local itemId = itemSave.Id;

  if itemSave.damage >= EThingsDurability.Damaged then return false end

  if skillId == 10016 and role:GetAttribute(EAttribute.Element) == 1 then  --1地 2水 3火 4風
    if Contains(itemId, 23086, 23135) then
      return true;
    else
      return false;
    end
  elseif skillId == 11016 and role:GetAttribute(EAttribute.Element) == 2 then
    if Contains(itemId, 23087, 23136) then
      return true;
    else
      return false;
    end
  elseif skillId == 12016 and role:GetAttribute(EAttribute.Element) == 3 then
    if Contains(itemId, 23088, 23137) then
      return true;
    else
      return false;
    end
  elseif skillId == 13015 and role:GetAttribute(EAttribute.Element) == 4 then
    if Contains(itemId, 23089, 23138) then
      return true;
    else
      return false;
    end
  else
    local attributeKind = 0;
    if followIdx <= 0 then 
      attributeKind = EAttribute.Turn3Element;
    else
      attributeKind = EAttribute.Element;
    end

    if skillId == 22011 and role:GetAttribute(attributeKind) == 7 then  
      if Contains(itemId, 23361, 23367) then      
        return true;
      else      
        return false;
      end
    elseif skillId == 23011 and role:GetAttribute(attributeKind) == 8 then
      if Contains(itemId, 23362, 23368) then
        return true;
      else
        return false;
      end
    end
  end
  return false;
end

function Item.HaveSummonSkill(role, skillId, followIndex)
  if followIndex <= 0 then
    return role:GetSkillLv(skillId) < 1;  --玩家
  else
    local skills = role:GetElementSkill(role:GetAttribute(EAttribute.Element));
    for k, v in pairs(skills) do
      if v.Id == skillId then
        return false;
      end
    end

    return true;
  end
end

function Item.CheckUseCount(role, itemId, followIndex)
  if followIndex <= 0 then
    --太上金丹
    if Contains(itemId, 46130, 46101) and role.data.attrPillCount >= Role.maxAttrPill then
      return true;
    end

    --健體玉液
    if itemId == 46240 and role.data.hpPillCount >= Role.maxHpPill then
      return true;
    end

    --幻能玉液
    if itemId == 46238 and role.data.spPillCount >= Role.maxSpPill then
      return true;
    end

    --技能丹
    if itemId == 46395 and role.data.skillPillCount >= Role.maxSkillPill then
      return true;
    end

    return false;
  else
    --健體仙丹
    if Contains(itemId, 46241, 46973) and role.data.hpPillCount >= Role.npcMaxHpPill then
      return true;
    end

    --幻能仙丹
    if Contains(itemId, 46239, 46971) and role.data.spPillCount >= Role.npcMaxSpPill then
      return true;
    end

    --領悟丹
    if Contains(itemId, 46185, 46186, 46187, 46188, 46189, 46190) and role.data.graspPill.count >= Role.npcMaxGraspPill then
      return true;
    end

    --昇華丹
    if Contains(itemId, 46050, 46063) and role.data.sublimeCount >= Role.npcMaxSublimePill then
      return true;
    end

    return false;
  end
end

--檢查鋤頭
function Item.CheckHaveHoe(followIndex)
  local result = false;
  local itemData;
  local equips = this.GetBag(EThings.Equip, followIndex);

  for k, v in pairs(equips) do
    if v ~= nil then
      itemData = itemDatas[v.Id];

      if itemData.specialAbility == EItemUseKind.ItemUse_8 and v.damage < EThingsDurability.Damaged then
        result = true;
        break;
      end
    end
  end

  return result;
end

function Item.CheckPlayerNpcLimit(itemData, followIndex)
  if itemData.kind == EItemKind.SpecialAbility and Contains(itemData.specialAbility, EItemUseKind.ExpGod, EItemUseKind.MachineBoxStone) then
    return true;
  end

  if followIndex > 0 then
    --玩家專用
    if bit.band(itemData.restrict, 64) == 64 then
      ShowCenterMessage(string.Get(71095));
      return false;
    end
  else
    --武將專用
    if bit.band(itemData.restrict, 128) == 128 then
      ShowCenterMessage(string.Get(20591));
      return false;
    end
  end

  return true;
end

--檢查有沒有經驗瓶
function Item.CheckExpBottle()
  local result = false;
  local itemData;
  local items = this.GetBag(EThings.Bag, 0);

  for k, v in pairs(items) do
    if v ~= nil then
      itemData = itemDatas[v.Id];

      if itemData.specialAbility == EItemUseKind.ExpBottle then
        result = true;
        break;
      end
    end
  end

  return result;
end
