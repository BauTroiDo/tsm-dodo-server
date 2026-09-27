UniqueBlood = {}
local this = UniqueBlood;

EUniqueBloodKind = {
  All = 0,
  Sky = 1,
  Land = 2,
  Human = 3,
  Sun = 4,
  Moon = 5,
  Star = 6,
}

this.qualityCount = 5;
this.maxLevel = 10;

this.item = {};
this.item[EUniqueBloodKind.Sky] = {};
this.item[EUniqueBloodKind.Land] = {};
this.item[EUniqueBloodKind.Human] = {};
this.item[EUniqueBloodKind.Sun] = {};
this.item[EUniqueBloodKind.Moon] = {};
this.item[EUniqueBloodKind.Star] = {};

this.item[EUniqueBloodKind.Sky].check = {};
this.item[EUniqueBloodKind.Land].check = {};
this.item[EUniqueBloodKind.Human].check = {};
this.item[EUniqueBloodKind.Sun].check = {};
this.item[EUniqueBloodKind.Moon].check = {};
this.item[EUniqueBloodKind.Star].check = {};

for i = 1, this.qualityCount do
  local check = false;
  if i == 1 then
    check = true;
  end

  this.item[EUniqueBloodKind.Sky].check[i] = check;
  this.item[EUniqueBloodKind.Land].check[i] = check;
  this.item[EUniqueBloodKind.Human].check[i] = check;
  this.item[EUniqueBloodKind.Sun].check[i] = check;
  this.item[EUniqueBloodKind.Moon].check[i] = check;
  this.item[EUniqueBloodKind.Star].check[i] = check;
end

this.meridianDatas = {};
this.nowEquipItems = {};
this.suitCount = {};

local mainLevelUpData = {};
local sameSuit = {};
local equipMessage = {};
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

function UniqueBlood.InitData()
  local data = uniqueBloodLevelUpDatas;
  for i = 1, table.Count(data) do
    if mainLevelUpData[data[i].group] == nil then
      mainLevelUpData[data[i].group] = {};
    end

    if mainLevelUpData[data[i].group].lv == nil then
      mainLevelUpData[data[i].group].lv = {};
    end

    if mainLevelUpData[data[i].group].lv[data[i].lv] == nil then
      mainLevelUpData[data[i].group].lv[data[i].lv] = {};
    end

    mainLevelUpData[data[i].group].lv[data[i].lv].element = data[i].element;
    mainLevelUpData[data[i].group].lv[data[i].lv].value = data[i].value;
    mainLevelUpData[data[i].group].lv[data[i].lv].piece = data[i].piece;
  end
end

function UniqueBlood.GetInfo(itemId)
  if itemDatas[itemId] == nil then return nil end
  if not Item.IsUniqueBlood(itemDatas[itemId].kind) then return nil end

  return uniqueBloodInfoDatas[itemDatas[itemId].spare3];
end

function UniqueBlood.GetSub(itemSave)
  return uniqueBloodMinorDatas[itemSave.subInitIdx];
end

function UniqueBlood.GetSuitData(styleId)
  return uniqueBloodSuitDataDatas[styleId];
end

function UniqueBlood.GetEquipItem(npcId)
  table.Clear(this.nowEquipItems);
  local bag = Item.GetBag(EThings.UniqueBloodEquip);

  for k, v in pairs(bag) do
    if v.equipId == npcId then
    v.bagIndex = k;
    table.insert(this.nowEquipItems, v);
    end
  end

  this.SetSuitPieceCount();
end

function UniqueBlood.SetSuitPieceCount()
  table.Clear(sameSuit);

  for _, v in pairs(this.nowEquipItems) do
    if sameSuit[v:GetStyleID()] == nil then
      sameSuit[v:GetStyleID()] = 1;
    else
      sameSuit[v:GetStyleID()] = sameSuit[v:GetStyleID()] + 1;
    end
  end
end

function UniqueBlood.SetAllSuitCountText()
  table.Clear(this.suitCount);

  local index = 1;

  for k, v in pairs(sameSuit) do
    local data = this.GetSuitData(k);

    if data ~= nil then
      for i = 1, 3 do
        if v >= data.pieces[i] and data.pieces[i] ~= 0 then
          this.suitCount[index] = {};
          this.suitCount[index].styleID = data.styleID;
          this.suitCount[index].stringId = data.stringId;
          this.suitCount[index].text = string.Concat(string.format(string.Get(24019), data.pieces[i]), " ", this.GetAttrText(data.att[i], true), data.value[i]);

          index = index + 1;
        end
      end
    end
  end
end

function UniqueBlood.SetLock(kind, sn)
  local lock = false;
  local hasFind = false;
  local bag = Item.GetBag(EThings.UniqueBlood);

  if kind == 1 then
    lock = true;
  elseif kind == 0 then
    lock = false;
  end

  for _, v in pairs(bag) do
    if v.sn == sn then
      v.isLock = lock;
      hasFind = true;
      break;
    end
  end

  if not hasFind then
    bag = Item.GetBag(EThings.UniqueBloodEquip);

    for _, v in pairs(bag) do
      if v.sn == sn then
        v.isLock = lock;
        hasFind = true;
        break;
      end
    end
  end

  UIItemInfo.UpdateUI();

  if UI.IsVisible(UIUniqueBlood) then
    UIUniqueBlood.UpdateUI();
  end
end

function UniqueBlood.GetSuitPieceCount(styleID)
  if sameSuit[styleID] == nil then
    return 0;
  end

  return sameSuit[styleID];
end

function UniqueBlood.GetMainAttValue(itemSave, lv)
  local value = 0;
  local level = itemSave.level;

  if lv > level then
    level = lv;
  end

  value = itemSave:GetMainAttLv1Value() + itemSave:GetMainAttLvAdd() * (level - 1);

  return value;
end

function UniqueBlood.GetSubAttValue(itemSave, lv)
  local value = itemSave:GetSubAttValue();
  local level = itemSave.level;
  local count = 0;

  if itemSave:GetSubElementLvUp() == 3 then
    if lv == 2 and lv > level then
      value = 0;
    else
      count = lv - 1;
    end
  else
    for i = 2, lv do
      if itemSave:GetSubElementLvUp() == 1 then
        if lv == 3 and lv > level then
          value = 0;
          break;
        end

        if i % 2 == 1 then
          count = count + 1;
        end
      elseif itemSave:GetSubElementLvUp() == 2 then
        if lv == 2 and lv > level then
          value = 0;
          break;
        end

        if i % 2 == 0 then
          count = count + 1;
        end
      end
    end
  end

  value = value + itemSave.subAdd * count;

  return value;
end

function UniqueBlood.IsSelectAll()
  for i = 1, table.Count(EUniqueBloodKind) - 1 do
    for j = 1, this.qualityCount do
      if not this.item[i].check[j] then
        return false;
      end
    end
  end

  return true;
end

function UniqueBlood.NoSelect()
  for i = 1, table.Count(EUniqueBloodKind) - 1 do
    for j = 1, this.qualityCount do
      if this.item[i].check[j] then
        return false;
      end
    end
  end

  return true;
end

function UniqueBlood.HasEquip(npcId)
  local bag = Item.GetBag(EThings.UniqueBloodEquip);

  for _, v in pairs(bag) do
    if v.equipId == npcId then
      return true;
    end
  end

  return false;
end

function UniqueBlood.GetDePiece(itemId)
  local data = this.GetInfo(itemId);

  return data.decomposePiece;
end

function UniqueBlood.GetIconId(itemId)
  local data = this.GetInfo(itemId);
  if this.GetSuitData(data.styleID) == nil then
    return "icon040";
  end

  return this.GetSuitData(data.styleID).icon;
end

function UniqueBlood.GetLevelUpPiece(group, lv)
  return mainLevelUpData[group].lv[lv].piece;
end

function UniqueBlood.GetStarSprite(quality)
  if quality == 0 then
    return UI.GetSprite("icon168");
  elseif quality == 1 then
    return UI.GetSprite("icon169");
  elseif quality == 2 then
    return UI.GetSprite("icon170");
  elseif quality == 3 then
    return UI.GetSprite("icon171");
  elseif quality == 4 then
    return UI.GetSprite("icon172");
  end

  return nil;
end

function UniqueBlood.GetColor(itemId)
  local data = this.GetInfo(itemId);
  local suit = this.GetSuitData(data.styleID);

  if suit == nil or suit.red == nil or suit.green == nil or suit.blue == nil then
    return  LuaHelper.GetColor(255, 255, 255, 255);
  end

  return  LuaHelper.GetColor(suit.red, suit.green, suit.blue, 255);
end

function UniqueBlood.DeleteMeridian(sn, kind)
  local bag = Item.GetBag(EThings.UniqueBlood);

  for k, v in pairs(bag) do
    if v.sn == sn then
      local message = string.format(string.Get(21160), itemDatas[v.Id]:GetName(true), 1);
      Chat.AddMessage(EChannel.System, message);

      Item.DelBagItem(EThings.UniqueBlood, k);
      break;
    end
  end

  if kind == 3 then
    bag = Item.GetBag(EThings.UniqueBloodEquip);
    for k, v in pairs(bag) do
      if v.sn == sn then
        local message = string.format(string.Get(21160), itemDatas[v.Id]:GetName(true), 1);
        Chat.AddMessage(EChannel.System, message);

        Item.DelBagItem(EThings.UniqueBloodEquip, k);
        break;
      end
    end
  end
end

function UniqueBlood.SaveAttribute(followIndex)
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
  end
end

function UniqueBlood.ShowEquipMessage(itemId, followIndex, isAdd)
  if itemDatas[itemId] == nil then return end

  local equipText = "";

  if isAdd then
    equipText = string.Get(50023);
  else
    equipText = string.Get(50024);
  end

  ShowCenterMessage(string.Concat(itemDatas[itemId]:GetName(true, true), equipText));

    --玩家及武將
  if followIndex ~= nil then
    local role = Role.GetRole(EHuman.FollowNpc, Role.playerId, followIndex);
    if role == nil then return end

    this.ShowEquipNsg(role:GetAttribute(EAttribute.EquipInt), equipMessage.equipInt, EAttribute.EquipInt);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.EquipAtk), equipMessage.equipAtk, EAttribute.EquipAtk);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.EquipDef), equipMessage.equipDef, EAttribute.EquipDef);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.EquipAgi), equipMessage.equipAgi, EAttribute.EquipAgi);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.EquipHpx), equipMessage.equipHpx, EAttribute.EquipHpx);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.EquipSpx), equipMessage.euqipSpx, EAttribute.EquipSpx);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.EquipMaxHp), equipMessage.equipMHp, EAttribute.EquipMaxHp);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.EquipMaxSp), equipMessage.equipMSp, EAttribute.EquipMaxSp);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.Crit), equipMessage.equipCrit, EAttribute.Crit);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.CritDamage), equipMessage.equipCritDamage, EAttribute.CritDamage);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.Dodge), equipMessage.equipDodge, EAttribute.Dodge);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.Hit), equipMessage.equipHit, EAttribute.Hit);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.AtkPercent), equipMessage.equipAtkPercent, EAttribute.AtkPercent);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.DefPercent), equipMessage.equipDefPercent, EAttribute.DefPercent);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.HpPercent), equipMessage.equipHpPercent, EAttribute.HpPercent);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.SpPercent), equipMessage.equipSpPercent, EAttribute.SpPercent);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.IntPercent), equipMessage.equipIntPercent, EAttribute.IntPercent);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.StealHp), equipMessage.equipStealHp, EAttribute.StealHp);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.StealSp), equipMessage.equipStealSp, EAttribute.StealSp);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.ResistancePercent), equipMessage.equipResistancePercent, EAttribute.ResistancePercent);
    this.ShowEquipNsg(role:GetAttribute(EAttribute.ReactiveDamage), equipMessage.equipReactiveDamage, EAttribute.ReactiveDamage);
  else
    equipMessage.switch = false;
  end

end

function UniqueBlood.ShowEquipNsg(before, after, attr)
  local att = before - after;

  if att > 0 then
    ShowCenterMessage(string.format(string.Get(80456), this.GetAttrText(attr), math.abs(att)));
  elseif att < 0 then
    ShowCenterMessage(string.format(string.Get(23983), this.GetAttrText(attr), math.abs(att)));
  end
end

  --region text
function UniqueBlood.GetMainAttsText(itemSave, lv)
  local valueTemp = 0;
  local result = "";
  local compare = "";
  local level = itemSave.level;

  if lv > itemSave.maxLevel then
    lv = itemSave.maxLevel;
  end

  valueTemp = this.GetMainAttValue(itemSave, lv);

  if lv > level then
    valueTemp = valueTemp + itemSave:GetMainAttLvAdd();
  end

  result = string.Concat(this.GetAttrText(itemSave:GetMainAtt(), true), valueTemp, "\n");

  for _, v in pairs(this.nowEquipItems) do
    if v:GetKind() == itemSave:GetKind() and v.sn ~= itemSave.sn then
      compare = string.Concat(string.Get(24020), this.GetAttrText(v:GetMainAtt(), true), this.GetMainAttValue(v, v.level), "\n");
      break;
    end
  end

  result = string.Concat(result, compare);

  return result;
end

function UniqueBlood.GetLvAddAttText(itemSave, group)
  local result = "";
  local level = itemSave.level;
  local maxCount = table.Count(mainLevelUpData[group].lv);
  local count = 0;
  local lvCount = 0;
  local front = "";

  for i = 1, maxCount do
    local elementTemp = mainLevelUpData[group].lv[i].element;
    local valueTemp = mainLevelUpData[group].lv[i].value;

    if elementTemp ~= 0 then
      local temp = string.Concat(string.Get(71154), i, ":",this.GetAttrText(elementTemp, true), valueTemp, "\n");
      lvCount = lvCount + 1;

      if level >= i then
        result = string.Concat(result, string.GetColorText(temp, Color.Orange));
        count = count + 1;
      else
        result = string.Concat(result, string.GetColorText(temp, Color.Gray));
      end
    end
  end

  if lvCount > 0 then
    front = string.Concat(string.format(string.Get(24032), count, lvCount), "\n");
  end

  result = string.Concat(front, result);

  return result;
end

function UniqueBlood.GetSubAttText(itemSave, lv)
  local valueTemp = 0;
  local result = "";
  local compare = "";
  local level = itemSave.level;

  if lv > itemSave.maxLevel then
    lv = itemSave.maxLevel;
  end

  valueTemp = this.GetSubAttValue(itemSave, lv);

  if lv > level then
    valueTemp = valueTemp + itemSave:GetSubAttValue();
  end

  result = string.Concat(result, this.GetAttrText(itemSave:GetSubAtt(), true), valueTemp, "\n");

  for _, v in pairs(this.nowEquipItems) do
    if v:GetKind() == itemSave:GetKind() and v.sn ~= itemSave.sn then
      compare = string.Concat(string.Get(24020), this.GetAttrText(v:GetSubAtt(), true), this.GetSubAttValue(v, v.level), "\n");
      break;
    end
  end

  result = string.Concat(result, compare);

  return result;
end

function UniqueBlood.GetInfoText(itemSave, index)
  local result = "";
  local tempText = "";

  if itemSave == nil then
    itemSave = Meridian.DefaultData(index);

    tempText = string.format(string.Get(24014), this.TransKindToText(itemSave:GetKind()));
    if not string.IsNullOrEmpty(tempText) then
      result = tempText;
    end

    local level = itemSave.level;

    tempText = this.GetMainAttsText(itemSave, level);
    if not string.IsNullOrEmpty(tempText) then
      result = string.Concat(result, "\n", string.Get(24017), tempText);
    end

    tempText = this.GetLvAddAttText(itemSave, itemSave:GetUpgradeGroup());
    if not string.IsNullOrEmpty(tempText) then
      result = string.Concat(result, tempText);
    end

    local suitData = this.GetSuitData(itemSave:GetStyleID());
    local count = this.GetSuitPieceCount(itemSave:GetStyleID());
    local total = 0;

    for i = 1, 3 do
      if suitData.pieces[i] ~= 0 then
        total = suitData.pieces[i];
      end
    end

    tempText = string.Concat(string.format(string.Get(90325), string.Get(suitData.stringId), count, total));
    if not string.IsNullOrEmpty(tempText) then
      result = string.Concat(result, tempText);
    end

    for i = 1, 3 do
      if suitData.pieces[i] ~= 0 then
        tempText = string.Concat(string.format(string.Get(24019), suitData.pieces[i]), " ", this.GetAttrText(suitData.att[i], true), suitData.value[i]);
        if not string.IsNullOrEmpty(tempText) then
          result = string.Concat(result, "\n", string.GetColorText(tempText, Color.Gray));
        end
      end
    end
  else
    tempText = string.format(string.Get(24014), this.TransKindToText(itemSave:GetKind()));
    if not string.IsNullOrEmpty(tempText) then
      result = tempText;
    end

    tempText = string.Concat(string.format(string.Get(24015), itemSave.level), string.format(string.Get(24052), itemSave.nowMaxLv));
    if not string.IsNullOrEmpty(tempText) then
      result = string.Concat(result, "\n", tempText);
    end

    local level = itemSave.level;

    tempText = this.GetMainAttsText(itemSave, level);
    if not string.IsNullOrEmpty(tempText) then
      result = string.Concat(result, "\n", string.Get(24017), tempText);
    end

    tempText = this.GetSubAttText(itemSave, level);
    if not string.IsNullOrEmpty(tempText) then
      result = string.Concat(result, string.Get(24018), tempText);
    end

    tempText = this.GetLvAddAttText(itemSave, itemSave:GetUpgradeGroup());
    if not string.IsNullOrEmpty(tempText) then
      result = string.Concat(result, tempText);
    end

    local suitData = this.GetSuitData(itemSave:GetStyleID());
    local count = this.GetSuitPieceCount(itemSave:GetStyleID());
    local total = 0;

    for i = 1, 3 do
      if suitData.pieces[i] ~= 0 then
        total = suitData.pieces[i];
      end
    end

    tempText = string.Concat(string.format(string.Get(90325), string.Get(suitData.stringId), count, total));
    if not string.IsNullOrEmpty(tempText) then
      result = string.Concat(result, tempText);
    end

    for i = 1, 3 do
      if suitData.pieces[i] ~= 0 then
        tempText = string.Concat(string.format(string.Get(24019), suitData.pieces[i]), " ", this.GetAttrText(suitData.att[i], true), suitData.value[i]);
        if not string.IsNullOrEmpty(tempText) then
          if sameSuit[itemSave:GetStyleID()] ~= nil and sameSuit[itemSave:GetStyleID()] >= suitData.pieces[i] then
            result = string.Concat(result, "\n", string.GetColorText(tempText, Color.Orange));
          else
            result = string.Concat(result, "\n", string.GetColorText(tempText, Color.Gray));
          end
        end
      end
    end

    result = string.Concat(result, "\n", string.format(string.Get(24021), itemSave.maxLevel));
  end

  return result;
end

function UniqueBlood.TransKindToText(kind)
  if kind == EUniqueBloodKind.Sky then
    return string.Get(24004);
  elseif kind == EUniqueBloodKind.Land then
    return string.Get(24005);
  elseif kind == EUniqueBloodKind.Human then
    return string.Get(24006);
  elseif kind == EUniqueBloodKind.Sun then
    return string.Get(24007);
  elseif kind == EUniqueBloodKind.Moon then
    return string.Get(24008);
  elseif kind == EUniqueBloodKind.Star then
    return string.Get(24009);
  else
    return "";
  end
end

function UniqueBlood.GetAttrText(attr, isAdd)
  local result = "";

  if attr == 101 then
    result = string.Get(23661);
  elseif attr == 102 then
    result = string.Get(23660);
  elseif attr == 103 then
    result = string.Get(80318);
  elseif attr == 104 then
    result = string.Get(23658);
  elseif attr == 105 then
    result = string.Get(23665);
  elseif attr == 106 then
    result = string.Get(23666);
  elseif attr == 107 then
    result = string.Get(23663);
  elseif attr == 108 then
    result = string.Get(23664);
  elseif attr == 207 then
    result = string.Get(30057);
  elseif attr == 208 then
    result = string.Get(30059);
  elseif attr == 210 then
    result = string.Get(30049);
  elseif attr == 211 then
    result = string.Get(30051);
  elseif attr == 212 then
    result = string.Get(30053);
  elseif attr == 214 then
    result = string.Get(30055);
  elseif attr == 217 then
    result = string.Get(90136);
  elseif attr == 218 then
    result = string.Get(20040); --體質
  elseif attr == 219 then
    result = string.Get(20041); --能量
  elseif attr == 88 then
    result = string.Get(23090); --攻擊時HP回復
  elseif attr == 89 then
    result = string.Get(23091); --攻擊時SP回復
  elseif attr == 90 then
    result = string.Get(23092); --異常狀態抵祝率
  elseif attr == 91 then
    result = string.Get(23093); --反傷率
  elseif attr > 255 then
    local data = skillDatas[attr];
    if data ~= nil then
      result = data.name;
    end
  end

  --24046 光
  --24047 暗

  if isAdd then
    result = string.Concat(result, "+");
  end

  return result;
end
  --endregion

Meridian = {}
Meridian.__index = Meridian;
--[[
  rMeridianSave = packed record
    SN : Int64;
    Index : Word;
    EquipID : Word;
    SubInitIdx : Word;
    SubAdd : Word;
    Star : Byte;
    Lv : Byte;
    Lock: Boolean;
]]--
function Meridian.New(data)
  local self = {};
  setmetatable(self, Meridian);

  self.sn = 0;
  self.index = 0;
  self.Id = 0;
  self.quant = 1; --數量(固定)
  self.equipId = 0; --裝備武將ID
  self.level = 0; --等級
  self.subInitIdx = 0;
  self.subAdd = 0;
  self.starLv = 0;  --突破次數
  self.isLock = false; --鎖定

  if data ~= nil then
    self.sn = data:ReadInt64();
    self.index = data:ReadUInt16();
    self.Id = UniqueBlood.meridianDatas[self.index].Id;
    self.equipId = data:ReadUInt16();
    self.subInitIdx = data:ReadUInt16();
    self.subAdd = data:ReadUInt16();
    self.starLv = data:ReadByte();
    self.level = data:ReadByte();
    self.isLock = data:ReadBoolean();

    if itemDatas[self.Id] then
      self.quality = itemDatas[self.Id].quality;
    end
  end

  self.info = this.GetInfo(self.Id);
  self.maxLevel = uniqueBloodUnlimitedDatas[self.info.breakUpTime].lv;
  self.nowMaxLv = uniqueBloodUnlimitedDatas[self.starLv].lv

  if table.Count(mainLevelUpData) == 0 then
    this.InitData();
  end

  return self;
end

function Meridian.DefaultData(index)
  local self = {};
  setmetatable(self, Meridian);

  self.index = index;
  self.Id = UniqueBlood.meridianDatas[self.index].Id;
  self.level = 1; --等級
  self.subInitIdx = 0;
  self.subAdd = 0;

  if itemDatas[self.Id] then
    self.quality = itemDatas[self.Id].quality;
  end

  self.info = this.GetInfo(self.Id);
  self.maxLevel = uniqueBloodUnlimitedDatas[self.info.breakUpTime].lv;

  if table.Count(mainLevelUpData) == 0 then
    this.InitData();
  end

  return self;
end

function Meridian:RefreshData()
  self.maxLevel = uniqueBloodUnlimitedDatas[self.info.breakUpTime].lv;
  self.nowMaxLv = uniqueBloodUnlimitedDatas[self.starLv].lv
end

function Meridian:GetMainAtt()
  return self.info.mainElement;
end

function Meridian:GetSubAtt()
  local data = this.GetSub(self);

  return data.element;
end

function Meridian:GetSubAttValue()
  local data = this.GetSub(self);

  return data.value;
end

function Meridian:GetMainAttLv1Value()
  return self.info.lv1Att;
end

function Meridian:GetMainAttLvAdd()
  return self.info.lvUpAddValue;
end

function Meridian:GetKind()
  return self.info.kind;
end

function Meridian:GetSubElementLvUp()
  return self.info.subElementLvUp;
end

function Meridian:GetStyleID()
  return self.info.styleID;
end

function Meridian:GetUpgradeGroup()
  return self.info.upgradeGroup;
end

