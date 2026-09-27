DungeonCriteriaData = {}
DungeonCriteriaData.__index = DungeonCriteriaData;
local this = DungeonCriteriaData;

function DungeonCriteriaData.New(reader)
  local self = {};
  setmetatable(self, DungeonCriteriaData);
  
  self.id = reader:ReadUInt16();
  self.playerOrNPC = reader:ReadByte();
  self.kind = reader:ReadByte();
  self.compare = reader:ReadByte();
  self.value = reader:ReadUInt32();
  self.value2 = reader:ReadUInt32();

  return self.id, self;
end

function DungeonCriteriaData.GetRoleStr(id)
  local data = dungeonCriteriaDatas[id];

  if data == nil or data.kind == 6 or data.kind == 7 then
    return "";
  end

  if data.playerOrNPC == 1 then
    return string.Get(20502);
  elseif data.playerOrNPC == 2 then
    return string.Get(24524);
  elseif data.playerOrNPC == 3 then
    return string.Get(24525);
  elseif data.playerOrNPC == 4 then
    return string.Concat(string.Get(20502), "+", string.Get(24524));
  elseif data.playerOrNPC == 5 then
    return string.Concat(string.Get(20502), "+", string.Get(24525));
  end

  return "";
end

function DungeonCriteriaData.CheckForQualification(id)
  local data = dungeonCriteriaDatas[id];

  if data == nil then
    return false;
  end

  if data.playerOrNPC == 1 then
    --玩家
    if this.GetRoleResult(data, Role.player) then
      return true;
    end
  elseif data.playerOrNPC == 2 then
    --出戰武將
    if this.GetRoleResult(data, Role.fightNpc) then
      return true;
    end
  elseif data.playerOrNPC == 3 then
    --跟隨武將
    local check = {};

    if Role.followNpcs[Role.playerId] ~= nil or table.Count(Role.followNpcs[Role.playerId]) > 0 then
      for _, v in pairs(Role.followNpcs[Role.playerId]) do
        local role = Role.GetFollowNpc(Role.playerId, v.npcId);
        if role ~= nil then
          table.insert(check, this.GetRoleResult(data, role) or this.GetRoleResult(data, role, true));
        end
      end
    else
      return false;
    end

    return not table.Contains(check, false);
  elseif data.playerOrNPC == 4 then
    --玩家+出戰武將
    return this.GetRoleResult(data, Role.player) and this.GetRoleResult(data, Role.fightNpc);
  elseif data.playerOrNPC == 5 then
    --玩家+跟隨武將
    local check = {};

    if Role.followNpcs[Role.playerId] ~= nil or table.Count(Role.followNpcs[Role.playerId]) > 0 then
      for _, v in pairs(Role.followNpcs[Role.playerId]) do
        local role = Role.GetFollowNpc(Role.playerId, v.npcId);
        if role ~= nil then
          table.insert(check, this.GetRoleResult(data, role) or this.GetRoleResult(data, role, true));
        end
      end
    else
      return false;
    end

    return this.GetRoleResult(data, Role.player) and not table.Contains(check, false);
  end

  return false;
end

function DungeonCriteriaData.GetRoleResult(data, role, sec)
  local value = data.value;

  if sec and data.value2 ~= 0 then
    value = data.value2;
  end

  if data.kind == 1 then     --國家
    if role ~= Role.player and role:GetAttribute(EAttribute.Moral) ~= nil then
      return this.CompareResult(role:GetAttribute(EAttribute.Moral), data.compare, value);
    else
      local npcId = role.npcId;

      if npcDatas[npcId] ~= nil then
        return this.CompareResult(npcDatas[npcId].moral, data.compare, value);
      end
    end
  elseif data.kind == 2 then --屬性
    if role:GetAttribute(EAttribute.Element) ~= nil then
      return this.CompareResult(role:GetAttribute(EAttribute.Element), data.compare, value);
    end
  elseif data.kind == 3 then --轉生
    if role:GetAttribute(EAttribute.Turn) ~= nil then
      return this.CompareResult(role:GetAttribute(EAttribute.Turn), data.compare, value);
    end
  elseif data.kind == 4 then --擁有技能
    if data.compare == 1 then
      return role:GetSkillLv(value) > 0;
    elseif data.compare == 6 then
      return role:GetSkillLv(value) <= 0;
    end
  elseif data.kind == 5 then --擁有技能
  elseif data.kind == 6 then --回合
  elseif data.kind == 7 then --回合
  elseif data.kind == 8 then --裝備靈魂屬性
  elseif data.kind == 9 then --出戰靈魂等級
  elseif data.kind == 10 then --出戰靈魂等級
  end

  return false;
end

function DungeonCriteriaData.CompareResult(need, compare, value)
  if compare == 1 then
    return need == value;
  elseif compare == 2 then
    return need < value;
  elseif compare == 3 then
    return need <= value;
  elseif compare == 4 then
    return need > value;
  elseif compare == 5 then
    return need >= value;
  elseif compare == 6 then
    return need ~= value;
  end

  return false;
end

function DungeonCriteriaData.GetResultStr(id)
  local data = dungeonCriteriaDatas[id];

  if data == nil then
    return "";
  end

  local aStr = "";

  if data.kind == 1 then     --國家
    aStr = string.Get(20323);

    for i = 0, 4 do
      if data.value == i+1 then
        aStr = string.Concat(aStr, this.GetCompareStr(data.compare), string.Get(10086+i));
        break;
      end
    end
  elseif data.kind == 2 then --屬性
    aStr = string.Get(99684);

    for i = 0, 3 do
      if data.value == i+1 then
        aStr = string.Concat(aStr, this.GetCompareStr(data.compare), string.Get(20002+i));
        break;
      end
    end

    if data.value == 7 then
      aStr = string.Concat(aStr, this.GetCompareStr(data.compare), string.Get(20922));
    elseif data.value == 8 then
      aStr = string.Concat(aStr, this.GetCompareStr(data.compare), string.Get(20923));
    end
  elseif data.kind == 3 then --轉生
    local temp = "";

    if data.value == 0 then
      temp = string.Get(90107);
    elseif data.value == 1 then
      temp = string.Get(23215);
    elseif data.value == 2 then
      temp = string.Get(23216);
    elseif data.value == 3 then
      temp = string.Get(23217);
    end

    aStr = string.Concat(string.Get(10616), this.GetCompareStr(data.compare), temp);
  elseif data.kind == 4 then --擁有技能
    local temp = "";
    if data.compare == 1 then
      temp = string.Get(22421);
    elseif data.compare == 6 then
      temp = string.Get(22422);
    end

    local skillData = skillDatas[data.value];
    aStr = string.format(temp, skillData.name);
  elseif data.kind == 5 then --擁有技能
  elseif data.kind == 6 then --回合
    aStr = string.Concat(string.Get(90023), this.GetCompareStr(data.compare), data.value);
  elseif data.kind == 7 then --回合
  elseif data.kind == 8 then --裝備靈魂屬性
  elseif data.kind == 9 then --出戰靈魂等級
  elseif data.kind == 10 then --出戰靈魂等級
  end

  return aStr;
end

function DungeonCriteriaData.GetCompareStr(compare)
  if compare == 1 then
    return string.Get(90029);
  elseif compare == 2 then
    return string.Get(90028);
  elseif compare == 3 then
    return string.Get(90031);
  elseif compare == 4 then
    return string.Get(90027);
  elseif compare == 5 then
    return string.Get(90030);
  elseif compare == 6 then
    return string.Get(24523);
  end

  return "";
end
