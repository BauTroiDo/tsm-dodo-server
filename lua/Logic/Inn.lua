ENpcInnStatus =
{
  Common = 0,
  Dispatch = 1,
};

Inn = {}
local this = Inn;

--const
this.minSpace = 6;
this.maxSpace = 30;
this.warriorRepositoryMin = 5;
this.warriorRepositoryMax = 150;

--var
this.Lv = 0; --客棧等級
this.npcs = {};
this.warriorRepository = {};
this.currentSpace = 0;
this.currentWarriorRepositorySpace = 0;
this.warshipIndex = 0;
this.warships = {};

function Inn.SaveNpc(index, followIndex, data)
  this.npcs[index] = NpcSaveData.New();

  if data ~= nil then
    this.npcs[index].npcId = data:ReadUInt16();
    this.npcs[index].level = data:ReadByte();
    this.npcs[index].exp = data:ReadUInt32();
    this.npcs[index].hp = data:ReadUInt32();
    local name = data:ReadString(data:ReadByte());
    this.npcs[index].name = string.CheckNpcName(this.npcs[index].npcId, name);
    this.npcs[index].status = data:ReadByte();
  end

  if followIndex ~= nil then
    local followNpc = Role.GetRole(EHuman.FollowNpc, Role.playerId, followIndex);
    if followNpc ~= nil then
      this.npcs[index].npcId = followNpc.npcId;
      this.npcs[index].name = followNpc.name
      this.npcs[index].hp = followNpc:GetAttribute(EAttribute.Hp);
      this.npcs[index].sp = followNpc:GetAttribute(EAttribute.Sp);
      this.npcs[index].int = followNpc:GetAttribute(EAttribute.Int);
      this.npcs[index].atk = followNpc:GetAttribute(EAttribute.Atk);
      this.npcs[index].def = followNpc:GetAttribute(EAttribute.Def);
      this.npcs[index].agi = followNpc:GetAttribute(EAttribute.Agi);
      this.npcs[index].hpx = followNpc:GetAttribute(EAttribute.Hpx);
      this.npcs[index].spx = followNpc:GetAttribute(EAttribute.Spx);
      this.npcs[index].level = followNpc:GetAttribute(EAttribute.Lv);
      this.npcs[index].exp = followNpc:GetAttribute(EAttribute.Exp);
      this.npcs[index].skillPoint = followNpc:GetAttribute(EAttribute.SkillPoint);
      this.npcs[index].dieCount = followNpc.data.dieCount;
      this.npcs[index].faith = followNpc:GetAttribute(EAttribute.Faith);
      for i = 1, Role.maxNpcSkill do
        this.npcs[index].skillLv[i] = followNpc.data.skillLv[i];
      end
      for i = 1, Role.maxEquip do
        this.npcs[index].equips[i] = Item.GetBagItem(EThings.Equip, i, followIndex);
      end
      this.npcs[index].canGrow = followNpc.data.canGrow;
    end
  end

  if Define.IsInternal() then
    if not UIDebug.RecordMode then
      this.npcs[index].name = string.Concat(this.npcs[index].name, this.npcs[index].npcId)
    end
  end

  if UI.IsVisible(UINpcInn) then
    UINpcInn.UpdateUI();
  end
end

function Inn.RemoveNpc(index)
  this.npcs[index] = nil;

  if UI.IsVisible(UINpcInn) then
    UINpcInn.UpdateUI();
  end
end

function Inn.InitWarship(data)
  this.warshipIndex = data:ReadByte();

  table.Clear(this.warships);
  while data.length > 0 do
    local index = data:ReadByte();
    local warship = {};

    warship.Id = data:ReadUInt16();
    warship.equips = {};
    for i = 1, 4 do
      warship.equips[i] = data:ReadUInt16();
    end
    warship.used = data:ReadBoolean();

    this.warships = {};
  end
end

function Inn.AddWarship(index, npcId)
  this.warships[index] = npcId

  if npcDatas[npcId] == nil then return end

  ShowCenterMessage(string.Concat(string.Get(90123), npcDatas[npcId].name));
end

function Inn.DelWarship(index)
  if this.warshipIndex == index then
    --Player.UnLoadShip;//則清除玩家戰船資料
    this.warshipIndex = 0;
  end

  this.warships[index] = 0;
end

function Inn.RefreshRoleCount(data)
  this.currentSpace = data.value + this.minSpace;
  if UI.IsVisible(UINpcInn) then
    UINpcInn.UpdateUI();
  end
end

function Inn.RefreshWarriorRepositoryCount(data)
  this.currentWarriorRepositorySpace = data.value + this.warriorRepositoryMin;
  if UI.IsVisible(UINpcInn) then
    UINpcInn.UpdateUI();
  end
end

function Inn.HaveNpc(npcId)
  for k, v in pairs(this.npcs) do
    if v.npcId == npcId then
      return true;
    end
  end

  for k, v in pairs(this.warriorRepository) do
    if v.npcId == npcId then
      return true;
    end
  end

  return false;
end

function Inn.Clear()
  table.Clear(this.npcs);
end

function Inn.AddWarriorRepositoryWithInfo(data)
  local warriorData = {};
  warriorData.npcId = data:ReadUInt16();
  warriorData.hp = data:ReadUInt32();
  warriorData.sp = data:ReadUInt16();
  warriorData.int = data:ReadUInt16();
  warriorData.atk = data:ReadUInt16();
  warriorData.def = data:ReadUInt16();
  warriorData.agi = data:ReadUInt16();
  warriorData.hpx = data:ReadUInt16();
  warriorData.spx = data:ReadUInt16();
  warriorData.level = data:ReadByte();
  warriorData.exp = data:ReadUInt32();
  warriorData.skillPoint = data:ReadUInt16();
  warriorData.dieCount = data:ReadByte();
  warriorData.skillLv = {};
  for i = 1, 4 do
    warriorData.skillLv[i] = data:ReadByte();
  end
  warriorData.conjureGrow = data:ReadUInt16();
  warriorData.sublimeCount = data:ReadByte();
  warriorData.isSpecSkill = data:ReadBoolean();
  warriorData.soulId = data:ReadUInt16();
  warriorData.graspPillCount = data:ReadByte();
  warriorData.SPPillCount = data:ReadByte();
  warriorData.HPPillCount = data:ReadByte();
  warriorData.upGradeLv = data:ReadByte();
  warriorData.turnKind = data:ReadByte();
  warriorData.surplus = data:ReadByte();
  warriorData.lvUpTime = data:ReadDouble();
  table.insert(this.warriorRepository, warriorData);
end

function Inn.AddWarriorRepositoryWithIndex(followIndex)
  local followNpc = Role.GetRole(EHuman.FollowNpc, Role.playerId, followIndex);
  if followNpc == nil then return end
  this.warriorRepository[followIndex].npcId = followNpc.npcId;
  this.warriorRepository[followIndex].name = followNpc.name
  this.warriorRepository[followIndex].hp = followNpc:GetAttribute(EAttribute.Hp);
  this.warriorRepository[followIndex].sp = followNpc:GetAttribute(EAttribute.Sp);
  this.warriorRepository[followIndex].int = followNpc:GetAttribute(EAttribute.Int);
  this.warriorRepository[followIndex].atk = followNpc:GetAttribute(EAttribute.Atk);
  this.warriorRepository[followIndex].def = followNpc:GetAttribute(EAttribute.Def);
  this.warriorRepository[followIndex].agi = followNpc:GetAttribute(EAttribute.Agi);
  this.warriorRepository[followIndex].hpx = followNpc:GetAttribute(EAttribute.Hpx);
  this.warriorRepository[followIndex].spx = followNpc:GetAttribute(EAttribute.Spx);
  this.warriorRepository[followIndex].level = followNpc:GetAttribute(EAttribute.Lv);
  this.warriorRepository[followIndex].exp = followNpc:GetAttribute(EAttribute.Exp);
  this.warriorRepository[followIndex].skillPoint = followNpc:GetAttribute(EAttribute.SkillPoint);
  this.warriorRepository[followIndex].dieCount = followNpc.data.dieCount;
  this.warriorRepository[followIndex].faith = followNpc:GetAttribute(EAttribute.Faith);
  this.warriorRepository[followIndex].canGrow = followNpc.data.canGrow;
  for i = 1, Role.maxNpcSkill do
    this.warriorRepository[followIndex].skillLv[i] = followNpc.data.skillLv[i];
  end
  for i = 1, Role.maxEquip do
    this.warriorRepository[followIndex].equips[i] = Item.GetBagItem(EThings.Equip, i, followIndex);
  end
end

function Inn.ClearWarriorRepository()
  table.Clear(this.warriorRepository);
end

function Inn.CheckIsFollowNpcFull()
  for i = 1, Role.maxFollowNpc do
    if Role.GetRole(EHuman.FollowNpc, Role.playerId, i) == nil then
      return false;
    end
  end
  return true;
end

function Inn.GetFirstEmptyFollowNpcSpace()
  for i = 1, Role.maxFollowNpc do
    if Role.GetRole(EHuman.FollowNpc, Role.playerId, i) == nil then
      return i;
    end
  end
  return nil;
end
