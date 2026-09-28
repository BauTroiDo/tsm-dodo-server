local json = require 'cjson'
EHotkeyType =
{
  PlayerMain = 1,
  PlayerSub = 2,
  NpcSkill = 3,
  SceneBattleMain = 4,
  SceneBattleSub = 5,
  Equip = 6,
  CurrentEquip = 7
}

-- 顯示模式
EHotkeyDisplayMode = {
  None = 0,
  SingleSkill = 1,
  SingleItem = 2,
  Both = 3
}

EHotkeyEditMode =
{
  None = 0,
  Setting = 1,
  Clear = -1
}

EHotkeyMode =
{
  None = 0,
  FightField = 1,
  SkillTree = 2,
  ItemInfo = 3,
  SceneBattle = 4,
  Equip = 5
}

EHotKeyIdType = {
  Mixed = 0,
  Skill = 1,
  Item = 2,
  Equip = 3
}

EHotkeyUI =
{
  Main = 1,
  Sub = 2
}

HotkeyData = {};
HotkeyData.__index = HotkeyData;

function HotkeyData.New(pageCount, btnCount, keyType)
  local self = {};
  setmetatable(self, HotkeyData);

  self.maxPages = pageCount;
  self.maxKeys = btnCount;
  self.count = pageCount * btnCount;
  self.keys = {};
  for p = 1, self.maxPages do
    for k = 1 , self.maxKeys do
      local key = {};
      key.useId = -1;
      key.pageIndex = p;
      key.keyIndex = k;
      table.insert(self.keys, key);
    end
  end
  self.keyType = keyType;
  return self;
end

Hotkey = {}
Hotkey.__index = Hotkey;

local this = Hotkey;
this.currentDisplayMode = EHotkeyDisplayMode.None;

local currentRole = nil;
local hotkeyData = {};
local currentHotkey = {};
local currentDisplay = {};
local currentSelectId = {};
local currentPage = {};
local currentHotkeyMode;
local currentEditMode = EHotkeyEditMode.None;
local nextHotkeyMode;
local followIndex = 0;
local directUse = true;

--region control
function Hotkey.Show(mode)
  if mode == EHotkeyMode.FightField then
    this.SetCurrentHotkey(EHotkeyUI.Sub, EHotkeyType.PlayerSub);
    this.SetCurrentDisplayUI(EHotkeyUI.Main, true);
    this.SetCurrentDisplayUI(EHotkeyUI.Sub, true);
    this.SwitchEditMode(EHotkeyEditMode.None);
    this.currentDisplayMode = EHotkeyDisplayMode.Both;
    UIHotkey.SetSortOrder(UIFight.GetSortOrder());

  elseif mode == EHotkeyMode.SkillTree then
    this.SetCurrentHotkey(EHotkeyUI.Main, EHotkeyType.PlayerMain);
    this.SetCurrentDisplayUI(EHotkeyUI.Main, true);
    this.SetCurrentDisplayUI(EHotkeyUI.Sub, false);
    this.SwitchEditMode(EHotkeyEditMode.Setting);
    this.currentDisplayMode = EHotkeyDisplayMode.SingleSkill;
    UIHotkey.SetSortOrder(UISkillTree.GetSortOrder());
    Hotkey.UpdateRoleSkill();

  elseif mode == EHotkeyMode.ItemInfo then
    this.SetCurrentHotkey(EHotkeyUI.Sub, EHotkeyType.PlayerSub);
    this.SetCurrentDisplayUI(EHotkeyUI.Main, false);
    this.SetCurrentDisplayUI(EHotkeyUI.Sub, true);
    this.SwitchEditMode(EHotkeyEditMode.Setting);
    this.currentDisplayMode = EHotkeyDisplayMode.SingleItem;
    UIHotkey.SetSortOrder(UIItemInfo.GetSortOrder());

  elseif mode == EHotkeyMode.SceneBattle then
    this.SetCurrentHotkey(EHotkeyUI.Sub, EHotkeyType.SceneBattleSub);
    this.SetCurrentDisplayUI(EHotkeyUI.Main, true);
    this.SetCurrentDisplayUI(EHotkeyUI.Sub, true);
    this.SwitchEditMode(EHotkeyEditMode.None);
    this.currentDisplayMode = EHotkeyDisplayMode.Both;

  elseif mode == EHotkeyMode.Equip then
    this.SetCurrentHotkey(EHotkeyUI.Main, EHotkeyType.Equip);
    this.SetCurrentHotkey(EHotkeyUI.Sub, EHotkeyType.CurrentEquip);
    this.SetCurrentDisplayUI(EHotkeyUI.Main, true);
    this.SetCurrentDisplayUI(EHotkeyUI.Sub, true);
    this.SwitchEditMode(EHotkeyEditMode.None);
    this.currentDisplayMode = EHotkeyDisplayMode.Both;
  else
    return
  end

  currentHotkeyMode = mode;
  if UI.IsVisible(UIHotkey) then
    UIHotkey.UpdateUI(true);
  else
    if not MachineBox.client.general[EMachineBoxSwitch.SampleMode] then
      UI.Open(UIHotkey);
    end
  end
end

function Hotkey.Close()
  if nextHotkeyMode == EHotkeyMode.FightField then
    nextHotkeyMode = nil;
    FightField.OpenHotkey();
  else
    nextHotkeyMode = nil;
    currentEditMode = EHotkeyEditMode.None;
    UI.Close(UIHotkey);
  end
end

function Hotkey.SetNextDisplayMode(nextMode)
  nextHotkeyMode = nextMode;
end

function Hotkey.GetCurrentHotkeyMode()
  return currentHotkeyMode
end

function Hotkey.OnSettingFinish()
  if UI.IsVisible(UIHotkey) then
    UIHotkey.UpdateUI();
  end

  if UI.IsVisible(UIItemInfo) and currentEditMode == EHotkeyEditMode.Setting then
    UI.Close(UIItemInfo);
  end
end

function Hotkey.GetEditMode()
  return currentEditMode
end

function Hotkey.SwitchEditMode(mode)
  if mode == nil then
    currentEditMode = currentEditMode * -1;
  else
    currentEditMode = mode;
  end
end

function Hotkey.UpdateRoleSkill()
  if currentRole == nil then return end
  if currentRole.kind == EHuman.Player or this.CheckIsDivide(currentRole.kind) then
    Hotkey.SetCurrentHotkey(EHotkeyUI.Main, EHotkeyType.PlayerMain);
  else

    local skills = currentRole:GetElementSkill(0);
    if BattleRoyale.GetBattleRoyaleState() then
      this.CreateHotkeyData(EHotkeyType.NpcSkill, 1, table.Count(skills) + hotkeyData[EHotkeyType.PlayerMain].count);
    else
      this.CreateHotkeyData(EHotkeyType.NpcSkill, 1, 5);
    end

    for k, v in pairs(skills) do
      local skilldata = skillDatas[v.Id];
      if skilldata ~= nil and skilldata:CanUse() then
        this.SetHotkeyUseId(EHotkeyType.NpcSkill, 1, k, v.Id);
        this.SetHotkeyUseType(EHotkeyType.NpcSkill, 1, k, EHotKeyIdType.Skill);
      end
    end

    if BattleRoyale.GetBattleRoyaleState() then
      for k, v in pairs(battleRoyaleHotkeyDatas[EHotkeyType.PlayerMain]) do
        this.SetHotkeyUseId(EHotkeyType.NpcSkill, 1, table.Count(skills) + k, v);
        this.SetHotkeyUseType(EHotkeyType.NpcSkill, 1, table.Count(skills) + k, EHotKeyIdType.Item);
      end
      Hotkey.SetHotkeyIdType(EHotkeyType.NpcSkill, EHotKeyIdType.Mixed);
    else
      Hotkey.SetHotkeyIdType(EHotkeyType.NpcSkill, EHotKeyIdType.Skill);
    end

    Hotkey.SetCurrentHotkey(EHotkeyUI.Main, EHotkeyType.NpcSkill);
  end
  if UI.IsVisible(UIHotkey) then
    UIHotkey.UpdateUI(true);
  end
end

function Hotkey.UpdateRoleEquip()
  if currentRole == nil then return end
  this.CreateHotkeyData(EHotkeyType.CurrentEquip, 1, 5);

  for i = 1, 5 do
    local equip = Item.GetBagItem(EThings.Equip, i, followIndex)
    if equip ~= nil and itemDatas[equip.Id] ~= nil then
      this.SetHotkeyUseId(EHotkeyType.CurrentEquip, 1, i, equip.Id);
    else
      this.SetHotkeyUseId(EHotkeyType.CurrentEquip, 1, i, 0);
    end
  end
  Hotkey.SetHotkeyIdType(EHotkeyType.CurrentEquip, EHotKeyIdType.Equip);
  Hotkey.SetCurrentHotkey(EHotkeyUI.Sub, EHotkeyType.CurrentEquip);
end

function Hotkey.SetCurrentHotkey(uiIndex, hotkeyDataIndex)
  if hotkeyDataIndex ~= nil then
    currentHotkey[uiIndex] = hotkeyDataIndex;
  end
end
--endregion

--region Data
function Hotkey.CreateHotkeyData(dataIndex, pageCount, keyCount)
  hotkeyData[dataIndex] = HotkeyData.New(pageCount, keyCount);
end

function Hotkey.ReceiveHotKeyData(index, data)
  local pageCount = data:ReadByte();
  local keyCount = data:ReadByte();
  local totalCount = pageCount * keyCount;

  if totalCount > 0 then
    this.CreateHotkeyData(index, pageCount, keyCount);
  end

  for p = 1, pageCount do
    for k = 1 , keyCount do
      this.SetHotkeyUseId(index, p, k, data:ReadUInt16());
    end
  end
end

function Hotkey.SetCurrentRole(role)
  if role == nil then return end;
  currentRole = role;
  this.UpdateRoleSkill();

  if UI.IsVisible(UIHotkey) then
    UIHotkey.UpdateUI(true);
  end
end

function Hotkey.GetFollowIndex()
  return followIndex
end

function Hotkey.SetFollowIndex(index)
  followIndex = index;
end

function Hotkey.SetHotkeyIdType(index, type)
  hotkeyData[index].keyType = type;
end

function Hotkey.GetHotkeyIdType(uiIndex)
  return this.GetCurrentHotkeyData(uiIndex).keyType;
end

function Hotkey.GetCurrentHotkeyData(uiIndex)
  return hotkeyData[currentHotkey[uiIndex]]
end

function Hotkey.GetHotkey(uiIndex, keyIndex)
  return this.GetCurrentHotkeyData(uiIndex).keys[keyIndex]
end

function Hotkey.GetHotkeyUseId(uiIndex, keyIndex)
  return this.GetCurrentHotkeyData(uiIndex).keys[keyIndex].useId
end

function Hotkey.GetHotkeyUseType(uiIndex, keyIndex)
  local type = this.GetCurrentHotkeyData(uiIndex).keyType;
  if type == EHotKeyIdType.Mixed or type == nil then
    return this.GetCurrentHotkeyData(uiIndex).keys[keyIndex].useType
  else
    return type
  end
end

function Hotkey.SetHotkeyUseId(dataIndex, pageIndex, keyIndex, useId)
  local index = this.GetHotkeyIndex(pageIndex, keyIndex, hotkeyData[dataIndex].maxKeys);
  hotkeyData[dataIndex].keys[index].useId = useId;
end

function Hotkey.SetHotkeyUseType(dataIndex, pageIndex, keyIndex, useType)
  local index = this.GetHotkeyIndex(pageIndex, keyIndex, hotkeyData[dataIndex].maxKeys);
  hotkeyData[dataIndex].keys[index].useType = useType;
end

function Hotkey.GetHotkeyIndex(pageIndex, keyIndex, keyCount)
  return keyIndex + (pageIndex - 1) * keyCount
end
--endregion

--region Edit
function Hotkey.SetCurrentSelectId(useId, uiIndex)
  currentSelectId[uiIndex] = useId;
  if UI.IsVisible(UIHotkey) then
    UIHotkey.UpdateHotkey(uiIndex);
  end
end

function Hotkey.IsHotkeyEmpty(uiIndex, keyIndex)
  if this.GetHotkeyUseType(uiIndex, keyIndex) == EHotKeyIdType.Equip then
    return false
  else
    local useId = this.GetHotkeyUseId(uiIndex, keyIndex);
    return useId == nil or useId <= 0
  end
end

function Hotkey.IsHotkeyIdDataEmpty(uiIndex, keyIndex)
  if this.IsHotkeyEmpty(uiIndex, keyIndex) then
    return true
  end
  local keyUseType = this.GetHotkeyUseType(uiIndex, keyIndex);
  local useId = this.GetHotkeyUseId(uiIndex, keyIndex);
  if keyUseType == EHotKeyIdType.Skill then
    return skillDatas[useId] == nil
  elseif keyUseType == EHotKeyIdType.Item then
    return itemDatas[useId] == nil
  elseif keyUseType == EHotKeyIdType.Equip then
    local equip = Item.GetBagItem(EThings.Equip, keyIndex, followIndex);
    return equip == nil or itemDatas[equip.Id] == nil
  end
end

function Hotkey.CheckEditPermission(uiIndex, keyIndex)
  if currentEditMode == EHotkeyEditMode.None then
    return false
  elseif currentEditMode == EHotkeyEditMode.Clear then
    return true
  elseif currentEditMode == EHotkeyEditMode.Setting then
    local keyUseType = this.GetHotkeyUseType(uiIndex, keyIndex);
    local selectId = currentSelectId[uiIndex];
    if keyUseType == EHotKeyIdType.Skill and currentRole.kind == EHuman.Player then
      if this.IsSummonSkill(selectId) then
        return currentRole ~= nil and skillDatas[selectId] ~= nil and currentRole:GetSkillLv(selectId) > 0;
      else
        return currentRole ~= nil and skillDatas[selectId] ~= nil and currentRole:GetSkillLv(selectId) > 0 and skillDatas[selectId]:CanUse();
      end
    elseif keyUseType == EHotKeyIdType.Item then
      return Item.CheckHotKeyItemID(selectId);
    end
  end

  return false
end
--endregion

--region Use Hotkey
function Hotkey.UseHotkey(uiIndex, keyIndex)
  local useId = this.GetHotkeyUseId(uiIndex, keyIndex);
  local hotKeyType = this.GetHotkeyUseType(uiIndex, keyIndex);
  local key = this.GetHotkey(uiIndex, keyIndex);

  if currentEditMode == EHotkeyEditMode.None then
    if hotKeyType == EHotKeyIdType.Skill then
      this.UseSkill(useId);
    elseif hotKeyType == EHotKeyIdType.Item then
      this.UseItem(useId);
    elseif hotKeyType == EHotKeyIdType.Equip then
      if not Hotkey.IsHotkeyIdDataEmpty(uiIndex, keyIndex) then
        this.UnEquip(keyIndex);
      end
    end
  elseif currentEditMode == EHotkeyEditMode.Setting then
    if this.CheckEditPermission(uiIndex, keyIndex) then
      this.SendEditHotkey(currentHotkey[uiIndex], key, currentSelectId[uiIndex]);
    end
  elseif currentEditMode == EHotkeyEditMode.Clear then
    if not this.IsHotkeyEmpty(uiIndex, keyIndex) then
      this.SendEditHotkey(currentHotkey[uiIndex], key, 0);
    end
  end
end

function Hotkey.SetDirectUse(state)
  directUse = state;
end

function Hotkey.UseSkill(useId)
  if FightField.isInBattle then
    if this.GetSkillLevel(useId) > 0 then
      -- 如果是招換技需要轉換skillid
      useId = this.GetUseSummonSkill(useId);

      if not this.CheckSkillElement(useId) then
        return;
      end

      if this.CheckCanUseSkill(useId) then
        FightField.SetConSkill(useId);
      else
        ShowCenterMessage(string.Get(40032));
      end

    else
      ShowCenterMessage(string.Get(20888));
    end
  end
end

function Hotkey.UseItem(useId)
  if FightField.isInBattle then
    if Item.GetItemCount(useId) > 0 then
      FightField.SetConItem(useId);
    else
      ShowCenterMessage(string.Get(20363));
    end
  else
    if directUse then
      Item.UseItem(EThings.Bag, Item.GetItemIndex(useId), followIndex);
    else
      if Item.GetItemCount(useId) > 0 then
        local funName;
        if Item.IsTypeOfEquips(itemDatas[useId].fitType) then
          funName = string.Get(20165); --裝備
        else
          funName = string.Get(40314); --使用
        end

        UI.Open(UIItemInfo, EThings.Bag, Item.GetItemIndex(useId), followIndex, string.Get(98025), Item.DropItem,
                funName,
                function()
                  Item.UseItem(EThings.Bag, Item.GetItemIndex(useId), followIndex);
                  UI.Close(UIItemInfo);
                end);
      else
        UIItemInfo.Show(useId);
      end
    end
  end
end

function Hotkey.UnEquip(keyIndex)
  if directUse then
    Item.UnEquip(EThings.Equip, keyIndex, followIndex);
  else
    UI.Open(UIItemInfo, EThings.Equip, keyIndex, followIndex, string.Get(98025), Item.DropEquip, string.Get(98003), Item.UnEquip);
  end
end

function Hotkey.SendEditHotkey(dataIndex, key, newUseId)
  sendBuffer:Clear();
  sendBuffer:WriteByte(dataIndex);
  sendBuffer:WriteByte(key.pageIndex);
  sendBuffer:WriteByte(key.keyIndex);
  sendBuffer:WriteUInt16(newUseId);
  Network.Send(40, 2, sendBuffer);
end
--endregion

--region skill
function Hotkey.IsSummonSkill(id)
  return Contains(id , 14026, 14077);
end

function Hotkey.GetUseSummonSkill(id)
  if this.IsSummonSkill (id) then
    if this.CheckIsDivide(currentRole.kind) or this.CheckKindIsPlayer(currentRole.kind) then
      -- 分身 or Player
      if not ItemData.IsSummonItem(Role.player.data.equips[EItemFitType.Equip_Spec]) then --沒裝紋章的話
        return id;
      else
        if id == 14026 then
          return Skill.GetSummonSkill(Role.player:GetAttribute(EAttribute.Element));
        else
          if ItemData.IsLightDarkSummonItem(Role.player.data.equips[EItemFitType.Equip_Spec]) then
            if Role.player:GetAttribute(EAttribute.Turn3Element) == 7 then
              if Contains(Role.player.data.equips[EItemFitType.Equip_Spec], 23361, 23367) then
                return Skill.GetSummonSkill(Role.player:GetAttribute(EAttribute.Turn3Element));
              end
            else
              if Contains(Role.player.data.equips[EItemFitType.Equip_Spec], 23362, 23368) then
                return Skill.GetSummonSkill(Role.player:GetAttribute(EAttribute.Turn3Element));
              end
            end
            return 14077;
          end
        end
      end
    else
      if not ItemData.IsSummonItem(currentRole.data.equips[EItemFitType.Equip_Spec]) then --沒裝紋章的話
        return id;
      else
        return Skill.GetSummonSkill(currentRole:GetAttribute(EAttribute.Element));
      end
    end
  else
    return id;
  end
end

function Hotkey.CheckSkillElement(skillId, inUI)
  local skill = skillDatas[skillId];
  if skill ~= nil then
    local role;
    if inUI then
      role = currentRole;
    else
      role = UIFight.GetRole();
      if role == nil then
        return false;
      end
    end

    --判斷光暗
    if Contains(role.kind, EHuman.Player, EHuman.Players) and Contains(skill.element, 7, 8) then
      if role:GetAttribute(EAttribute.Turn3Element) ~= skill.element then
        return false;
      end
    end
  end

  return true;
end

function Hotkey.GetSkillLevel(id)
  if currentRole ~= nil then
    -- 分身上沒有技能等級，要從玩家身上拿資料
    if this.currentDisplayMode == EHotkeyDisplayMode.Both then
      if this.CheckIsDivide(currentRole.kind) or this.CheckKindIsPlayer(currentRole.kind) then
        return Role.player:GetSkillLv(id);
      else
        return currentRole:GetSkillLv(id);
      end
    else
      return currentRole:GetSkillLv(id);
    end
  else
    return 0;
  end
end

function Hotkey.CheckCanUseSkill(skillId)
  local skill = skillDatas[skillId];
  if skill ~= nil then
    local role = UIFight.GetRole();
    if role == nil then
      return false;
    end

    if skill.requireSp <= role:GetAttribute(EAttribute.Sp) then
      return true;
    end
  end

  return false;
end

function Hotkey.CheckIsDivide(kind)
  return kind == EHuman.Divide or kind == EHuman.Duplicate;
end

function Hotkey.CheckKindIsPlayer(kind)
  return kind == EHuman.Player;
end

function Hotkey.IsPlayer()
  if currentRole ~= nil and currentRole.kind == EHuman.Player then
    return true;
  end

  return false;
end
--endregion

--region UI
function Hotkey.GetDisplayFlag(uiIndex)
  return currentDisplay[uiIndex]
end

function Hotkey.GetCurrentKeyCount(uiIndex)
  return this.GetCurrentHotkeyData(uiIndex).count
end

function Hotkey.SetCurrentDisplayUI(uiIndex, displayFlag)
  if displayFlag ~= nil then
    currentDisplay[uiIndex] = displayFlag;
  end
end

function Hotkey.SetCurrentPage(uiIndex, page)
  currentPage[uiIndex] = page;
end

function Hotkey.GetCurrentPage(uiIndex)
  return currentPage[uiIndex]
end

function Hotkey.GetNumberText(uiIndex, keyIndex)
  local keyUseType = this.GetHotkeyUseType(uiIndex, keyIndex);
  local useId = this.GetHotkeyUseId(uiIndex, keyIndex);
  if keyUseType == EHotKeyIdType.Skill then
    return this.GetSkillLevel(useId);
  elseif keyUseType == EHotKeyIdType.Item then
    return Item.GetItemCount(useId);
  end
end

function Hotkey.SetIcon(uiIndex, keyIndex, rawImage)
  local keyUseType = this.GetHotkeyUseType(uiIndex, keyIndex);
  local useId = this.GetHotkeyUseId(uiIndex, keyIndex);
  if keyUseType == EHotKeyIdType.Skill then
    skillDatas[useId]:SetIcon(rawImage);
  elseif keyUseType == EHotKeyIdType.Item then
    itemDatas[useId]:SetIcon(rawImage);
  elseif keyUseType == EHotKeyIdType.Equip then
    local equip = Item.GetBagItem(EThings.Equip, keyIndex, followIndex)
    if equip ~= nil and itemDatas[equip.Id] ~= nil then
      itemDatas[equip.Id]:SetIcon(rawImage);
    end
  end
end

function Hotkey.SetEquipIcon(keyIndex, image)
  if keyIndex == EItemFitType.Equip_Head then
    image.sprite = UI.GetSprite("but263D");
  elseif keyIndex == EItemFitType.Equip_Body then
    image.sprite = UI.GetSprite("but060D");
  elseif keyIndex == EItemFitType.Equip_Hand then
    image.sprite = UI.GetSprite("but265D");
  elseif keyIndex == EItemFitType.Equip_Wrist then
    image.sprite = UI.GetSprite("but262D");
  elseif keyIndex == EItemFitType.Equip_Boots then
    image.sprite = UI.GetSprite("but264D");
  elseif keyIndex == EItemFitType.Equip_Spec then
    image.sprite = UI.GetSprite("but151D");
  end
end

function Hotkey.GetColor(uiIndex, keyIndex)
  local keyUseType = this.GetHotkeyUseType(uiIndex, keyIndex);
  local useId = this.GetHotkeyUseId(uiIndex, keyIndex);
  if keyUseType == EHotKeyIdType.Item then
    if Item.GetItemCount(useId) > 0 or Hotkey.currentDisplayMode == EHotkeyDisplayMode.SingleItem then
      return Color.White
    else
      return Color.Gray
    end
  elseif keyUseType == EHotKeyIdType.Skill then
    if Hotkey.GetSkillLevel(useId) > 0 then
      if Hotkey.currentDisplayMode == EHotkeyDisplayMode.SingleSkill then
        if not Hotkey.CheckSkillElement(useId, true) then
          return Color.Gray
        else
          return Color.White
        end
      elseif Hotkey.currentDisplayMode == EHotkeyDisplayMode.Both then
        if not Hotkey.CheckSkillElement(useId) then
          return Color.Gray
        elseif Hotkey.CheckCanUseSkill(useId) then
          return Color.White
        else
          return Color.Gray
        end
      else
        return Color.White
      end
    else
      return Color.Gray
    end
  elseif keyUseType == EHotKeyIdType.Equip then
    local equip = Item.GetBagItem(EThings.Equip, keyIndex, followIndex)
    if equip ~= nil and itemDatas[equip.Id] ~= nil then
      return Color.White
    else
      return Color.Gray
    end
  end
end
--endregion