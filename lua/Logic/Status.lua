Status = {};
local this = Status;
local currentAttSet = 1;
local maxAttSet = 8;
local maxAttSetNameLen = 6;
local maxPoint = 999;
local allAttSets = {};
local applyingAttSet = false;
local applyingRole;

function Status.Init()
  this.LoadAttSet();
  applyingAttSet = false;
  applyingRole = nil;
end

function Status.GetInt(role)
  local baseValue = role:GetAttribute(EAttribute.Int);
  local equipAdd = role:GetAttribute(EAttribute.EquipInt);
  local otherAdd = 0;
  if role == Role.player then
    otherAdd = otherAdd + DrugBuff.GetAttribute(EDrugAttribute.Int);
    otherAdd = otherAdd + DrugBuff.timeBuffs[ETimeBuff.IntUp].value;
    otherAdd = otherAdd + Astrolabe.GetAttribute(EAstrolabeAttribute.Int);
    otherAdd = otherAdd + role:GetAttribute(EAttribute.Buff, EAttribute.Int);
    otherAdd = otherAdd + role:GetAttribute(EAttribute.Turn3Int);
    otherAdd = otherAdd + math.floor((Mounts.GetAttribute(EAttribute.Int) + Mounts.GetAttribute(EAttribute.EquipInt)) * Mounts.collectionBonus);
  elseif role == Role.fightNpc then
    otherAdd = otherAdd + DrugBuff.GetAttribute(EDrugAttribute.Int);
  end
  otherAdd = otherAdd + CollectStyle.GetAttribute(EAttribute.Int);
  otherAdd = otherAdd + CollectCard.GetAttribute(EAttribute.Int);

  if BattleRoyale.GetBattleRoyaleState() and role == Role.player then
    otherAdd = otherAdd + BattleRoyale.GetFixedFieldEffectValue(EAttribute.Int);
    otherAdd = otherAdd + BattleRoyale.GetPercentageFieldEffectValue(EAttribute.Int, role:GetAttribute(EAttribute.Int));
  end

  return baseValue, equipAdd, otherAdd;
end

function Status.GetAtk(role)
  local baseValue = role:GetAttribute(EAttribute.Atk);
  local equipAdd = role:GetAttribute(EAttribute.EquipAtk);
  local otherAdd = 0;
  if role == Role.player then
    otherAdd = otherAdd + DrugBuff.GetAttribute(EDrugAttribute.Atk);
    otherAdd = otherAdd + DrugBuff.timeBuffs[ETimeBuff.AtkUp].value;
    otherAdd = otherAdd + Astrolabe.GetAttribute(EAstrolabeAttribute.Atk);
    otherAdd = otherAdd + role:GetAttribute(EAttribute.Buff, EAttribute.Atk);
    otherAdd = otherAdd + role:GetAttribute(EAttribute.Turn3Atk);
    otherAdd = otherAdd + math.floor((Mounts.GetAttribute(EAttribute.Atk) + Mounts.GetAttribute(EAttribute.EquipAtk)) * Mounts.collectionBonus);
  elseif role == Role.fightNpc then
    otherAdd = otherAdd + DrugBuff.GetAttribute(EDrugAttribute.Atk);
  end
  otherAdd = otherAdd + CollectStyle.GetAttribute(EAttribute.Atk);
  otherAdd = otherAdd + CollectCard.GetAttribute(EAttribute.Atk);

  if BattleRoyale.GetBattleRoyaleState() and role == Role.player then
    otherAdd = otherAdd + BattleRoyale.GetFixedFieldEffectValue(EAttribute.Atk);
    otherAdd = otherAdd + BattleRoyale.GetPercentageFieldEffectValue(EAttribute.Atk, role:GetAttribute(EAttribute.Atk));
  end

  return baseValue, equipAdd, otherAdd;
end

function Status.GetDef(role)
  local baseValue = role:GetAttribute(EAttribute.Def);
  local equipAdd = role:GetAttribute(EAttribute.EquipDef);
  local otherAdd = 0;
  if role == Role.player then
    otherAdd = otherAdd + DrugBuff.GetAttribute(EDrugAttribute.Def);
    otherAdd = otherAdd + DrugBuff.timeBuffs[ETimeBuff.DefUp].value;
    otherAdd = otherAdd + Astrolabe.GetAttribute(EAstrolabeAttribute.Def);
    otherAdd = otherAdd + role:GetAttribute(EAttribute.Buff, EAttribute.Def);
    otherAdd = otherAdd + role:GetAttribute(EAttribute.Turn3Def);
    otherAdd = otherAdd + math.floor((Mounts.GetAttribute(EAttribute.Def) + Mounts.GetAttribute(EAttribute.EquipDef)) * Mounts.collectionBonus);
  elseif role == Role.fightNpc then
    otherAdd = otherAdd + DrugBuff.GetAttribute(EDrugAttribute.Def);
  end
  otherAdd = otherAdd + CollectStyle.GetAttribute(EAttribute.Def);
  otherAdd = otherAdd + CollectCard.GetAttribute(EAttribute.Def);

  if BattleRoyale.GetBattleRoyaleState() and role == Role.player then
    otherAdd = otherAdd + BattleRoyale.GetFixedFieldEffectValue(EAttribute.Def);
    otherAdd = otherAdd + BattleRoyale.GetPercentageFieldEffectValue(EAttribute.Def, role:GetAttribute(EAttribute.Def));
  end

  return baseValue, equipAdd, otherAdd;
end

function Status.GetHpx(role)
  local baseValue = role:GetAttribute(EAttribute.Hpx);
  local equipAdd = role:GetAttribute(EAttribute.EquipHpx);
  local otherAdd = 0;
  if role == Role.player then
    otherAdd = otherAdd + DrugBuff.GetAttribute(EDrugAttribute.Hpx);
    otherAdd = otherAdd + DrugBuff.timeBuffs[ETimeBuff.HpxUp].value;
    otherAdd = otherAdd + Astrolabe.GetAttribute(EAstrolabeAttribute.Hpx);
    otherAdd = otherAdd + role:GetAttribute(EAttribute.Buff, EAttribute.Hpx);
    otherAdd = otherAdd + role:GetAttribute(EAttribute.Turn3Hpx);
  elseif role == Role.fightNpc then
    otherAdd = otherAdd + DrugBuff.GetAttribute(EDrugAttribute.Hpx);
  end
  otherAdd = otherAdd + CollectStyle.GetAttribute(EAttribute.Hpx);
  otherAdd = otherAdd + CollectCard.GetAttribute(EAttribute.Hpx);

  if BattleRoyale.GetBattleRoyaleState() and role == Role.player then
    otherAdd = otherAdd + BattleRoyale.GetFixedFieldEffectValue(EAttribute.Hpx);
    otherAdd = otherAdd + BattleRoyale.GetPercentageFieldEffectValue(EAttribute.Hpx, role:GetAttribute(EAttribute.Hpx));
  end

  return baseValue, equipAdd, otherAdd;
end

function Status.GetSpx(role)
  local baseValue = role:GetAttribute(EAttribute.Spx);
  local equipAdd = role:GetAttribute(EAttribute.EquipSpx);
  local otherAdd = 0;
  if role == Role.player then
    otherAdd = otherAdd + DrugBuff.GetAttribute(EDrugAttribute.Spx);
    otherAdd = otherAdd + DrugBuff.timeBuffs[ETimeBuff.SpxUp].value;
    otherAdd = otherAdd + Astrolabe.GetAttribute(EAstrolabeAttribute.Spx);
    otherAdd = otherAdd + role:GetAttribute(EAttribute.Buff, EAttribute.Spx);
    otherAdd = otherAdd + role:GetAttribute(EAttribute.Turn3Spx);
  elseif role == Role.fightNpc then
    otherAdd = otherAdd + DrugBuff.GetAttribute(EDrugAttribute.Spx);
  end
  otherAdd = otherAdd + CollectStyle.GetAttribute(EAttribute.Spx);
  otherAdd = otherAdd + CollectCard.GetAttribute(EAttribute.Spx);

  if BattleRoyale.GetBattleRoyaleState() and role == Role.player then
    otherAdd = otherAdd + BattleRoyale.GetFixedFieldEffectValue(EAttribute.Spx);
    otherAdd = otherAdd + BattleRoyale.GetPercentageFieldEffectValue(EAttribute.Spx, role:GetAttribute(EAttribute.Spx));
  end

  return baseValue, equipAdd, otherAdd;
end

function Status.GetAgi(role)
  local baseValue = role:GetAttribute(EAttribute.Agi);
  local equipAdd = role:GetAttribute(EAttribute.EquipAgi);
  local otherAdd = 0;
  if role == Role.player then
    otherAdd = otherAdd + DrugBuff.GetAttribute(EDrugAttribute.Agi);
    otherAdd = otherAdd + DrugBuff.timeBuffs[ETimeBuff.AgiUp].value;
    otherAdd = otherAdd + Astrolabe.GetAttribute(EAstrolabeAttribute.Agi);
    otherAdd = otherAdd + role:GetAttribute(EAttribute.Buff, EAttribute.Agi);
    otherAdd = otherAdd + role:GetAttribute(EAttribute.Turn3Agi);
  elseif role == Role.fightNpc then
    otherAdd = otherAdd + DrugBuff.GetAttribute(EDrugAttribute.Agi);
  end
  otherAdd = otherAdd + CollectStyle.GetAttribute(EAttribute.Agi);
  otherAdd = otherAdd + CollectCard.GetAttribute(EAttribute.Agi);

  if BattleRoyale.GetBattleRoyaleState() and role == Role.player then
    otherAdd = otherAdd + BattleRoyale.GetFixedFieldEffectValue(EAttribute.Agi);
    otherAdd = otherAdd + BattleRoyale.GetPercentageFieldEffectValue(EAttribute.Agi, role:GetAttribute(EAttribute.Agi));
  end

  return baseValue, equipAdd, otherAdd;
end

function Status.GetStatusAttribute(role, index)
  if role == nil or index == nil then
    return
  end
  if index == 1 then
    return this.GetInt(role);
  elseif index == 2 then
    return this.GetAtk(role);
  elseif index == 3 then
    return this.GetDef(role);
  elseif index == 4 then
    return this.GetHpx(role);
  elseif index == 5 then
    return this.GetSpx(role);
  elseif index == 6 then
    return this.GetAgi(role);
  end
end

function Status.CreateAttSet(index)
  local attSet = {};
  attSet.name = string.Get(24159)..index;
  attSet.att = {};
  attSet.att[1] = 0;
  attSet.att[2] = 0;
  attSet.att[3] = 0;
  attSet.att[4] = 0;
  attSet.att[5] = 0;
  attSet.att[6] = 0;
  attSet.index = index;
  return attSet
end

function Status.VerifyAttSet(attSet, index)
  local verifiedAttSet = this.CreateAttSet(index);
  if attSet.name ~= nil then
    if type(attSet.name) == "string" then
      if string.Count(attSet.name) <= maxAttSetNameLen and not string.IsNullOrEmpty(attSet.name) then
        verifiedAttSet.name = attSet.name;
      end
    end
  end
  if type(attSet.att) == 'table' then
    for i = 1, 6 do
      if attSet.att[i] ~= nil then
        if (type(attSet.att[i]) == "number") and (math.floor(attSet.att[i]) == attSet.att[i]) then
          verifiedAttSet.att[i] = math.clamp(attSet.att[i], 0, maxPoint);
        end
      end
    end
  end

  return verifiedAttSet;
end

function Status.GetCurrentAttSetIndex()
  return currentAttSet;
end

function Status.GetCurrentAttSet()
  return allAttSets[currentAttSet]
end

function Status.SelectAttSet(index)
  if applyingAttSet then return end
  currentAttSet = index;
end

function Status.SaveCurrentAttSet()
  Setting.client.attSet = allAttSets;
  Setting.Save();
end

function Status.LoadAttSet()
  if Setting.client.attSet == nil then
    Setting.client.attSet = {};
  end

  for k, v in pairs(Setting.client.attSet) do
    if Between(k, 1, maxAttSet) then
      allAttSets[k] = this.VerifyAttSet(v, k);
    end
  end

  for i = 1, maxAttSet do
    if allAttSets[i] == nil then
      allAttSets[i] = this.CreateAttSet(i);
    end
  end
end

function Status.ChangeAttSetName(name)
  if string.IsNullOrEmpty(name) or string.Count(name) > maxAttSetNameLen then
    return
  end
  allAttSets[currentAttSet].name = name;
  this.SaveCurrentAttSet();
  UIStatus.UpdateAttSetName();
end

function Status.SetAttributeSet(att, value)
  allAttSets[currentAttSet].att[att] = value;
end

function Status.ChangeAtt(current, change, remain)
  if remain ~= nil then
    change = math.min(change, remain);
  end
  
  local result = math.clamp(current + change, 0, maxPoint);
  return result;
end

function Status.GetAttSetNames(index)
  return allAttSets[index].name;
end

function Status.GetAttSetCount()
  return maxAttSet;
end

function Status.GetAttSetMaxPoints()
  return maxPoint;
end

function Status.GetAttSetCostPoints(role, atts)
  local requirePoints = 0;
  for i = 1, 6 do
    local currentAtt = this.GetStatusAttribute(role, i);
    requirePoints = requirePoints + math.max(atts[i] - currentAtt, 0);
  end
  
  return requirePoints;
end

function Status.ApplyAttSet(role)
  if not this.IsAttSetApplyPointsEnough(role) then
    ShowCenterMessage(string.Get(24155));
    return
  end

  if this.IsAttSetNeedReset(role) then
    if Network.InUnbound then
      applyingAttSet = true;
      applyingRole = role;
      this.SendResetAttribute();
    else
      if not Contains(Role.player.war, EWar.None, EWar.Guest) then
        ShowCenterMessage(string.Get(80099));
        return;
      end
  
      UISell.Launch(15,function() this.SendApplyAttSet(role); end);
    end
  else
    this.SendApplyAttSet(role);
  end
end

function Status.SendResetAttribute()
  if Network.InUnbound then
    --C:008-016 <無界重置配點>
    Network.Send(8, 16);
  else
    UISell.Launch(15);
  end
end

function Status.IsAttSetNeedReset(role)
  for i = 1, 6 do
    if allAttSets[currentAttSet].att[i] < this.GetStatusAttribute(role, i) then
      return true;
    end
  end
  return false;
end

function Status.IsAttSetApplyPointsEnough(role)
  local points = role:GetAttribute(EAttribute.AttrPoint);
  for i = 1, 6 do
    points = points + Status.GetStatusAttribute(role, i) - allAttSets[currentAttSet].att[i];
  end
  
  return points >= 0;
end

function Status.SendApplyAttSet(role)
  if role == nil then return end

  local haveChangeAtt = false;
  for i = 1, 6 do
    
    local add = allAttSets[currentAttSet].att[i] - this.GetStatusAttribute(role, i);
    if add > 0 then
      haveChangeAtt = true;
    end
    
    this.SendAddAttribute(role, i, add);
  end
  
  applyingAttSet = false;
  applyingRole = nil;
  
  if not haveChangeAtt then
    ShowCenterMessage(string.Get(40348));
  end
end

function Status.ReceiveResetUnboundAttribute(result)
  if result == 1 then
    if applyingAttSet and applyingRole ~= nil then
      this.SendApplyAttSet(applyingRole);
      return
    end
  elseif result == 2 then
    ShowCenterMessage(string.Get(24505));
  elseif result == 3 then
    ShowCenterMessage(string.Get(24506));
  elseif result == 255 then
    ShowCenterMessage(string.Get(24507));
  end
  applyingAttSet = false;
  applyingRole = nil;
end

function Status.SendAddAttribute(role, attKind, add)
  if add > 0 then
    --C:008-001 <設定屬性> +武將索引(2) +種類(1) +數值(4) +參數(1)
    sendBuffer:Clear();
    if role == Role.player then
      sendBuffer:WriteUInt16(0);
    else
      sendBuffer:WriteUInt16(role.index);
    end

    local att;
    if attKind == 1 then
      att = EAttribute.Int;
    elseif attKind == 2 then
      att = EAttribute.Atk;
    elseif attKind == 3 then
      att = EAttribute.Def;
    elseif attKind == 4 then
      att = EAttribute.Hpx;
    elseif attKind == 5 then
      att = EAttribute.Spx;
    elseif attKind == 6 then
      att = EAttribute.Agi;
    end

    if att == nil then return end

    sendBuffer:WriteByte(att);
    sendBuffer:WriteInt32(add);
    sendBuffer:WriteByte(0);
    Network.Send(8, 1, sendBuffer);
  end
end