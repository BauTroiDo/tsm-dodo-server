Soul = {}
local this = Soul;

local mainSubSkillScale = 50/65;

local equipMessage = {};
equipMessage.switch = false;
equipMessage.equipInt = 0;
equipMessage.equipAtk = 0;
equipMessage.equipDef = 0;
equipMessage.equipAgi = 0;
equipMessage.equipHpx = 0;
equipMessage.equipSpx = 0;

function Soul.GetEmptyIndex(bagkind)
  local bag = Item.GetBag(bagkind)
  local count = Item.GetBagCount(bagkind);

  for i = 1, count do
    if bag[i] == nil then
      return i;
    end
  end

  return count + 1;
end

function Soul.HasBreakInt(data)
  local hasSkill = false;

  for _, v in pairs(data.soulData.skill) do
    if v.lv > 1 then
      hasSkill = true;
      break;
    end
  end

  return data.soulData.lv > 1 or data.soulData.starLv > 0 or hasSkill;
end

function Soul.SetAffSkills(id, mainSub, usingInfo)
  local skillData = skillDatas[id];  --靈魂技能紀錄
  if skillData ~= nil then
    if mainSub.icon == nil then
      mainSub.icon = UISkillController.New(mainSub.rawImage_Icon);
    end

    local skillController = mainSub.icon;
    skillController:SetSkill(skillData.id, usingInfo:GetSkillLv(id));
    skillController:SetShowCost(false);
    skillController:SetSize(mainSubSkillScale);
    skillController:SetEvent(UISoul.OnClick_Skill);
    skillController.transform.localPosition = Vector3.New(0, 0, 0);
  end
end

function Soul.GetEquippedSoul(npcId)
  local bag = Item.GetBag(EThings.EquippedSoul);
  for _, v in pairs(bag) do
    if v.equipId == npcId then
      return v;
    end
  end

  return nil;
end

function Soul.HasSoulSkill(npcId, skillId)
  local temp = this.GetEquippedSoul(npcId);
  if temp ~= nil then
    for index = 1, Role.maxSoulSkill do
      if skillId == temp.skill[index].id then
        return true;
      else
        return false;
      end
    end
  else
    return false;
  end
end

function Soul.GetNpcList()
  local npcList = {};

  npcList.role = {};
  npcList.oriFollowIndex = {};
  local role = nil;

  for i = 1, Role.maxFollowNpc do
    if Role.GetRole(EHuman.FollowNpc, Role.playerId, i) then

      role = Role.GetRole(EHuman.FollowNpc, Role.playerId, i);
      if role ~= nil and role.data.canGrow ~= 0 and role.data.canGrow ~= 2 then
        table.insert(npcList.role, role);
        table.insert(npcList.oriFollowIndex, i);
      end
    end
  end

  return npcList;
end

function Soul.GetFollowIndex(npcList, currentRole)
  for i = 1, table.Count(npcList.role) do
    if npcList.role[i] == currentRole then
      return npcList.oriFollowIndex[i];
    end
  end

  return 0;
end

function Soul.SaveAttribute(currentRole, switch)
  equipMessage.switch = switch;

  if not switch then
    if currentRole == nil then return end

    equipMessage.equipInt = currentRole:GetAttribute(EAttribute.EquipInt);
    equipMessage.equipAtk = currentRole:GetAttribute(EAttribute.EquipAtk);
    equipMessage.equipDef = currentRole:GetAttribute(EAttribute.EquipDef);
    equipMessage.equipAgi = currentRole:GetAttribute(EAttribute.EquipAgi);
    equipMessage.equipHpx = currentRole:GetAttribute(EAttribute.EquipHpx);
    equipMessage.equipSpx = currentRole:GetAttribute(EAttribute.EquipSpx);
  end
end

function Soul.CheckItemCon(item, checkFight, checkLock)
  if item == nil then return false end

  if checkFight then
    if FightField.isInBattle then
      ShowCenterMessage(string.Get(80099));
      return false;
    end
  end

  if checkLock then
    if item.isLock then
      ShowCenterMessage(string.Get(23712));
      return false;
    end
  end

  return true;
end

function Soul.CheckItemKind(itemSave, currentTag)
  local itemId = itemSave.Id;
  if itemDatas[itemId] ~= nil and itemDatas[itemId].kind == EItemKind.Soul then
    if currentTag ~= nil then
      if UISoul.CheckAff(itemSave) then return false end
    end

    return true;
  else
    return false;
  end
end

function Soul.ShowIntensifyAttMsg(after, before)
  local att;

  att = after[1] - before[1];
  if att > 0 then
    ShowCenterMessage(string.Concat(string.Get(50025), att));
  end

  att = after[2] - before[2]
  if att > 0 then
    ShowCenterMessage(string.Concat(string.Get(50027), att));
  end

  att = after[3] - before[3];
  if att > 0 then
    ShowCenterMessage(string.Concat(string.Get(50031), att));
  end

  att = after[4] - before[4];
  if att > 0 then
    ShowCenterMessage(string.Concat(string.Get(50029), att));
  end

  att = after[5] - before[5];
  if att > 0 then
    ShowCenterMessage(string.Concat(string.Get(21035), att));
  end

  att = after[6] - before[6];
  if att > 0 then
    ShowCenterMessage(string.Concat(string.Get(21037), att));
  end
end

function Soul.ShowEquipMessage(currentRole, soulId, isAdd)
  local equipInt = 0;
  local equipAtk = 0;
  local equipDef = 0;
  local equipAgi = 0;
  local equipHpx = 0;
  local euqipSpx = 0;

  if isAdd then
    ShowCenterMessage(string.Concat(itemDatas[soulId]:GetName(true, true), string.Get(50023)));
  else
    ShowCenterMessage(string.Concat(itemDatas[soulId]:GetName(true, true), string.Get(50024)));
  end

  if not equipMessage.switch then
    if currentRole == nil then return end

    equipInt = currentRole:GetAttribute(EAttribute.EquipInt);
    equipAtk = currentRole:GetAttribute(EAttribute.EquipAtk);
    equipDef = currentRole:GetAttribute(EAttribute.EquipDef);
    equipAgi = currentRole:GetAttribute(EAttribute.EquipAgi);
    equipHpx = currentRole:GetAttribute(EAttribute.EquipHpx);
    euqipSpx = currentRole:GetAttribute(EAttribute.EquipSpx);

    local att = equipInt - equipMessage.equipInt;
    if att > 0 then
      ShowCenterMessage(string.Concat(string.Get(50031), math.abs(att)));
    elseif att < 0 then
      ShowCenterMessage(string.Concat(string.Get(50032), math.abs(att)));
    end

    att = equipAtk - equipMessage.equipAtk;
    if att > 0 then
      ShowCenterMessage(string.Concat(string.Get(50025), math.abs(att)));
    elseif att < 0 then
      ShowCenterMessage(string.Concat(string.Get(50026), math.abs(att)));
    end

    att = equipDef - equipMessage.equipDef;
    if att > 0 then
      ShowCenterMessage(string.Concat(string.Get(50027), math.abs(att)));
    elseif att < 0 then
      ShowCenterMessage(string.Concat(string.Get(50028), math.abs(att)));
    end

    att = equipAgi - equipMessage.equipAgi;
    if att > 0 then
      ShowCenterMessage(string.Concat(string.Get(50029), math.abs(att)));
    elseif att < 0 then
      ShowCenterMessage(string.Concat(string.Get(50030), math.abs(att)));
    end

    att = equipHpx - equipMessage.equipHpx;
    if att > 0 then
      ShowCenterMessage(string.Concat(string.Get(21035), math.abs(att)));
    elseif att < 0 then
      ShowCenterMessage(string.Concat(string.Get(21036), math.abs(att)));
    end

    att = euqipSpx - equipMessage.equipSpx;
    if att > 0 then
      ShowCenterMessage(string.Concat(string.Get(21037), math.abs(att)));
    elseif att < 0 then
      ShowCenterMessage(string.Concat(string.Get(21038), math.abs(att)));
    end
  else
    equipMessage.switch = false;
  end
end


