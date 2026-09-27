BattleRoyale = {}
EBattleRoyaleNpcKind =
{
  None = 0,
  Normal = 1,
  Killer = 2,
  Pillar = 3
}

EBattleRoyaleEffectType =
{
  Fixed = 1,
  Percentage = 2
}

local this = BattleRoyale;
local battleRoyaleState = false;
local vocationOption = {};
local sealSkillOption = {};
local currentVocation;

local battleRoyaleId;
local maxParticipant;
local survivorCount;
local winCount;
local sealCount;
local rank;
local rankPoints;
local participateText;
local doors = {};
local lastTeleportTime;
local startTime;
local QueueStartTime;
local matchCountDownTime;
local minParticipant = 10;
local outfitId = 2;
local entranceScene = 54968;

local displayFieldEffects = {};
local fieldEffects = {};

local currentSkill;
local acquiredSealSkills = {};
local sealSkills = {};
local sealingIndex;

function BattleRoyale.Initialize()

  NotifyManager.Register(ENotifyManager.EnterScene, "BattleRoyale", this.OnEnterScene);
  NotifyManager.Register(ENotifyManager.ExitScene, "BattleRoyale", this.OnExitScene);
end

function BattleRoyale.Clear()
  this.SetBattleRoyaleState(false);
end

function BattleRoyale.Update()
  local playerPosition = Role.player.position;
  for k, v in pairs(doors) do
    if v.rect:Contains(playerPosition) and v.valid and this.CheckTeleportValid(lastTeleportTime) then

      --C:110-008 <進傳送門> +場景編號(2) +區號(2) +傳送門ID(1)
      sendBuffer:Clear();
      sendBuffer:WriteUInt16(SceneManager.sceneId);
      sendBuffer:WriteUInt16(SceneManager.instanceId);
      sendBuffer:WriteByte(k);
      lastTeleportTime = CGTimer.serverTime;
      Network.Send(110, 8, sendBuffer);
    end
  end
end

function BattleRoyale.TeleportFail()
  lastTeleportTime = CGTimer.serverTime;
end

function BattleRoyale.CheckTeleportValid(time)
  if time == nil then return true end
  local timeSpan = DateTime.Subtract(CGTimer.serverTime, time).TotalSeconds;
  return timeSpan >= 0.5
end

function BattleRoyale.OnEnterScene()
  if SceneManager.sceneId == entranceScene then
    Network.Send(1, 16);
    return
  end

  if battleRoyaleSceneDatas[SceneManager.sceneId] == nil then
    return
  end
  this.EnterScene(SceneManager.sceneId);
end

function BattleRoyale.OnExitScene()
  if battleRoyaleSceneDatas[SceneManager.sceneId] == nil then
    return
  end
  lastTeleportTime = nil;
  this.ExitScene();
end

function BattleRoyale.EnterScene(id)
  table.Clear(doors);
  local data = battleRoyaleSceneDatas[id];
  for k, v in pairs(data.doors) do
    if v.valid then
      v.view = Door.New(v);
    else
      v.view = nil;
    end
    table.insert(doors, k, v);
  end

  lastTeleportTime = nil;
  CGTimer.AddListener(this.Update, 0.25, false);
end

function BattleRoyale.ExitScene()
  for k, v in pairs(doors) do
    if v.valid and v.view ~= nil then
      v.view:Destroy();
      v.view = nil;
    end
  end
  CGTimer.RemoveListener(this.Update);
end

function BattleRoyale.SetBattleRoyaleState(state)
  battleRoyaleState = state;
  UI.Close(UIBattleRoyale);
  Function.UpdateVisible();
  UIMain.UpdateDungeon();
  UIMain.ShowBattleRoyaleUI(state);
  UIMain.UpdateSelectRole(0);
  UIMain.UpdateHotkeyDirectUse(not state);
  if state == false then
    table.Clear(displayFieldEffects);
    table.Clear(acquiredSealSkills);
    table.Clear(fieldEffects);
    table.Clear(sealSkills);
    currentVocation = nil;
    UIMain.UpdateFieldEffect(table.Count(displayFieldEffects));
    Hotkey.Close();
  else
    this.InitHotkey();
    this.OpenHotkey(EHotkeyMode.SceneBattle);
    UIMain.UpdateHotkey(true);
  end
  Role.UpdateAllView();
end

function BattleRoyale.GetBattleRoyaleState()
  return battleRoyaleState
end

function BattleRoyale.InBattleRoyaleScene(id)
  local sceneId;
  if id == nil then
    sceneId = SceneManager.sceneId;
  else
    sceneId = id;
  end
  if sceneId == entranceScene then
    return true
  end
  return battleRoyaleSceneDatas[sceneId] ~= nil
end

--C:110-007 <大逃殺互動> 索引(8)
function BattleRoyale.Interact(index)
  sendBuffer:Clear();
  sendBuffer:WriteInt64(index);
  Network.Send(110, 7, sendBuffer);
end

function BattleRoyale.CheckNpcKind(role)
  if role.npcId == nil then
    return EBattleRoyaleNpcKind.None;
  end
  local data = battleRoyaleSceneDatas[SceneManager.sceneId];
  if role.npcId == battleRoyaleSceneDatas[SceneManager.sceneId].pillar.id then
    return EBattleRoyaleNpcKind.Pillar;
  elseif table.Contains(data.normalMob.ID, role.npcId) then
    return EBattleRoyaleNpcKind.Normal;
  elseif table.Contains(data.killerMob.ID, role.npcId) then
    return EBattleRoyaleNpcKind.Killer;
  else
    return EBattleRoyaleNpcKind.None;
  end
end

function BattleRoyale.EnterFight()
  UIMain.ShowBattleRoyaleUI(false);
end

function BattleRoyale.ExitFight()
  if not battleRoyaleState then return end
  UIMain.ShowBattleRoyaleUI(true);
  this.OpenHotkey();
  UIMain.UpdateHotkey();
  UIMain.SetPKCountDown();
end

function BattleRoyale.InitHotkey()
  for dataIndex, data in pairs(battleRoyaleHotkeyDatas) do
    Hotkey.CreateHotkeyData(dataIndex, 1, table.Count(data));
    Hotkey.SetHotkeyIdType(dataIndex, EHotKeyIdType.Item);
    for k, v in pairs(battleRoyaleHotkeyDatas[dataIndex]) do
      Hotkey.SetHotkeyUseId(dataIndex, 1, k, v);
    end
  end
  Hotkey.CreateHotkeyData(EHotkeyType.CurrentEquip, 1, 5);
  Hotkey.SetHotkeyIdType(EHotkeyType.CurrentEquip, EHotKeyIdType.Equip);
end

function BattleRoyale.OpenHotkey(mode)
  if not battleRoyaleState then return end
  UIHotkey.SetSortOrder(UIMain.uiController.canvas.sortingOrder);
  UIMain.UpdateHotkeyMode(mode);
end

function BattleRoyale.GetOutfit()
  return collectStyleDatas[outfitId].itemId
end

function BattleRoyale.SetOutfit(id)
  outfitId = id;
end

--region Queue
function BattleRoyale.CheckInQueue()
  return CheckFlag(MarkManager.flags, 403);
end

function BattleRoyale.StartQueue()
  QueueStartTime = CGTimer.serverTime;
  maxParticipant = 20;
  UI.Open(UIBattleRoyale, EBattleRoyalFunction.Match);
  UIBattleRoyale.UpdateMatchingTime();
  CGTimer.AddListener(this.UpdateQueueTime, 1, true);
end

function BattleRoyale.LeaveQueue()
  CGTimer.RemoveListener(this.UpdateQueueTime);
  UI.Close(UIBattleRoyale);
end

function BattleRoyale.SetMatchCountDown(time)
  matchCountDownTime = time;
end

function BattleRoyale.UpdateQueueTime()
  if battleRoyaleState then
    this.LeaveQueue();
    return
  end
  
  UIBattleRoyale.UpdateMatchingTime(CGTimer.serverTime:Subtract(QueueStartTime));
end
--endregion

--region Schedule
function BattleRoyale.SetTimer(time)
  startTime = time;
  this.UpdateSchedule();
  local passTime = math.floor(CGTimer.serverTime:Subtract(startTime).TotalSeconds);
  if passTime > battleRoyaleScheduleDatas[1].endTime then return end

  CGTimer.AddListener(this.ShowPrepareMessage, 1, true);
end

function BattleRoyale.ShowPrepareMessage()
  local passTime = math.floor(CGTimer.serverTime:Subtract(startTime).TotalSeconds);
  if not battleRoyaleState then
    CGTimer.RemoveListener(this.ShowPrepareMessage);
  end
  if passTime < battleRoyaleScheduleDatas[1].endTime then
    ShowCenterMessage(string.Get(24145));
  else
    CGTimer.RemoveListener(this.ShowPrepareMessage);
    this.ShowStartMessage();
  end
end

function BattleRoyale.ShowStartMessage()
  for i = 0, 2 do
    CGTimer.DoFunctionDelay(i,
      function()
        if battleRoyaleState then
          ShowCenterMessage(string.Get(24144))
        end
    end);
  end
end

function BattleRoyale.UpdateSchedule()
  local passTime = math.floor(CGTimer.serverTime:Subtract(startTime).TotalSeconds);
  if passTime < 0 then
    CGTimer.DoFunctionDelay( -passTime, this.UpdateSchedule);
    return
  end
  for k, v in ipairs(battleRoyaleScheduleDatas) do
    if passTime >= v.startTime and passTime < v.endTime then
      UIMain.UpdateDungeonInfo(v.content);
      UIMain.UpdateDungeon();
      UIMain.SetCountDownTime(startTime:AddSeconds(v.endTime));
      CGTimer.DoFunctionDelay(v.endTime - passTime, this.UpdateSchedule);
      return
    end
  end
  UIMain.UpdateDungeon();
end
--endregion

--region Vocation
function BattleRoyale.ReceiveVocationOption(options)
  vocationOption = {};
  currentVocation = nil;
  for index = 1, table.Count(options) do
    local vocation = battleRoyaleVocationDatas[options[index]];
    vocation.show = true;
    table.insert(vocationOption, vocation);
  end
  if UI.IsVisible(UIBattleRoyale) then
    UIBattleRoyale.UpdateUI(EBattleRoyalFunction.Vocation);
  else
    UI.Open(UIBattleRoyale, EBattleRoyalFunction.Vocation);
  end
end

--C:110-004 <選擇大逃殺職業> 職業ID(2)
function BattleRoyale.SendSelectVocation(index)
  sendBuffer:Clear();
  sendBuffer:WriteUInt16(index);
  Network.Send(110, 4, sendBuffer);
end

function BattleRoyale.SetVocation(index, unlockSkills, reset)
  currentVocation = battleRoyaleVocationDatas[index];
  for i = 1, table.Count(vocationOption) do
    vocationOption[i].show = vocationOption[i].id == index;
  end
  local vocationSkills = currentVocation:GetSkill();
  local skills = {};
  if unlockSkills ~= nil then
    for k, v in ipairs(unlockSkills) do
      if vocationSkills[v] ~= nil then
        skills[v] = vocationSkills[v];
      end
    end
  else
    skills = vocationSkills;
  end
  UIMain.SetVocationSkill(skills, reset);
  UIBattleRoyale.UpdateVocation();
  UI.Close(UIBattleRoyale);
end

function BattleRoyale.GetVocation()
  return currentVocation
end

function BattleRoyale.GetVocationOption()
  return vocationOption;
end

--C:110-006 <使用技能> 技能ID(2) +目標ID(8)
function BattleRoyale.SendUseSkill(target)
  sendBuffer:Clear();
  sendBuffer:WriteUInt16(currentSkill);
  currentSkill = nil;
  if target == nil then
    target = Role.playerId;
  end
  sendBuffer:WriteInt64(target);
  Network.Send(110, 6, sendBuffer);
  UIMain.UpdateSkillState(currentSkill);
end

function BattleRoyale.CancelSkill()
  currentSkill = nil;
  UIMain.UpdateSkillState(currentSkill);
end

function BattleRoyale.UseSkill(index)
  if currentSkill == index then
    currentSkill = nil;
  else
    currentSkill = index;
  end
  if sceneSkillDatas[index].target ~= 1 then
    BattleRoyale.SendUseSkill();
    return
  end
  if Role.target ~= nil then
    this.SendUseSkill(Role.target.index);
    Role.SetTarget(nil);
  end

  UIMain.UpdateSkillState(currentSkill);
end

function BattleRoyale.GetCurrentSkill()
  return currentSkill
end

function BattleRoyale.SetVocationSkillCoolDown(index, endTime)
  UIMain.SetSkillCountDownTime(index, CGTimer.serverTime, endTime);
end

function BattleRoyale.ShowSceneSkillEffect(caster, target, skillId)
  local index;
  local skill = sceneSkillDatas[skillId];
  if skill.effect.Id == 0 then return end
  if skill.target == 1 then
    index = target;
  elseif skill.target == 2 then
    index = caster;
  end

  if Role.players[index] == nil then return end
  if Role.players[index].npcId ~= 0 then return end
  Role.players[index]:PlaySkillEffect(skill.effect);
end
--endregion

--region SealSkill
--C:110-005 <選擇封印能力> 石柱SN(8) + 能力ID(2)
function BattleRoyale.SendSelectSealSkill(index)
  sendBuffer:Clear();
  sendBuffer:WriteInt64(sealingIndex);
  sendBuffer:WriteUInt16(index);
  Network.Send(110, 5, sendBuffer);
end

function BattleRoyale.ReceiveSealSkillOption(index, options)
  local skillOption = {};
  for i = 1, table.Count(options) do
    local sealSkill = battleRoyaleSealDatas[options[i]];
    table.insert(skillOption, sealSkill);
  end
  sealSkills[index] = skillOption;
  this.OpenSealSkillPage(index);
end

function BattleRoyale.OpenSealSkillPage(index)
  sealSkillOption = sealSkills[index];
  sealingIndex = index;
  if UI.IsVisible(UIBattleRoyale) then
    UIBattleRoyale.UpdateUI(EBattleRoyalFunction.Seal);
  else
    UI.Open(UIBattleRoyale, EBattleRoyalFunction.Seal);
  end
end

function BattleRoyale.GetSealSkillOption()
  return sealSkillOption;
end

function BattleRoyale.AddSealSkill(skill)
  table.insert(acquiredSealSkills, skill);
end

function BattleRoyale.ClearSealSkill(index)
  if sealSkills[index] == nil then
    return
  end
  sealSkills[index] = nil;
end

function BattleRoyale.TrySealPillar(index)
  if sealSkills[index] == nil then
    return false
  elseif this.CheckRepeatSkill(index) then
    return false
  else
    this.OpenSealSkillPage(index);
    return true
  end
end

function BattleRoyale.CheckRepeatSkill(index)
  local skills = sealSkills[index];
  if skills == nil then return false end
  for k, v in ipairs(skills) do
    if table.Contains(acquiredSealSkills, v.id) then
      return true
    end
  end
  return false
end
--endregion

--region FieldEffect
function BattleRoyale.ResetFieldEffect(roleId)
  fieldEffects[roleId] = {};
  fieldEffects[roleId].allEffects = {};
  fieldEffects[roleId].effectType = {};
  fieldEffects[roleId].effectType[EBattleRoyaleEffectType.Fixed] = {};
  fieldEffects[roleId].effectType[EBattleRoyaleEffectType.Percentage] = {};
  fieldEffects[roleId].updateFlags = {};
  fieldEffects[roleId].sceneEffect = {};
  fieldEffects[roleId].sceneEffect.Id = 0;
end

function BattleRoyale.ReceiveFieldEffect(roleId, effectID, layer, reset)
  if fieldEffects[roleId] == nil then
    BattleRoyale.ResetFieldEffect(roleId);
  end

  local effects = fieldEffectDatas[effectID];
  if effects == nil then
    return
  end

  --同個id可能有多個效果
  for k, v in pairs(effects) do
    local effectKind = v:GetEffectKind();

    fieldEffects[roleId].allEffects[effectID] = layer;
    if effectKind ~= nil then

      fieldEffects[roleId].allEffects[effectID] = layer;
      if roleId == Role.playerId and layer >= 20 and not reset then
        logWarn("效果"..effectID.."已達最高層數");
      end
      local kind = v:GetAttribute();
      --跑速
      if v.kind == 14 then
        kind = v.kind;
      end

      if effectKind ~= nil and kind ~= nil then

        if fieldEffects[roleId].effectType[effectKind][kind] == nil then
          fieldEffects[roleId].effectType[effectKind][kind] = {};
        end
        --184削弱(%數) + 最大HP跟最大SP 拿去做hp跟sp了
        local value = layer * v:GetValue();
        if effectKind == EBattleRoyaleEffectType.Percentage and kind == EAttribute.MaxHp then
          value = math.max(value, 0);
        elseif effectKind == EBattleRoyaleEffectType.Percentage and kind == EAttribute.MaxSp then
          value = math.max(value, 0);
        end
        fieldEffects[roleId].effectType[effectKind][kind][v.id] = value;
        fieldEffects[roleId].updateFlags[kind] = true;
      end
    end

    if v.effect.Id ~= 0 then
      if v.effect.Circle then
        if layer ~= 0 then
          fieldEffects[roleId].sceneEffect = v.effect;
        elseif layer == 0 and fieldEffects[roleId].sceneEffect.Id == v.effect.Id then
          for effectID, layer in pairs(fieldEffects[roleId].allEffects) do
            local effects = fieldEffectDatas[effectID];
            if effects ~= nil then
              for orderID, effect in pairs(effects) do
                if effect.effect.Id ~= 0 and layer > 0 and effect.effect.Circle then
                  fieldEffects[roleId].sceneEffect = effect.effect;
                  return
                end
              end
            end
          end

          fieldEffects[roleId].sceneEffect = {};
          fieldEffects[roleId].sceneEffect.Id = 0;
        end
      else

        if Role.players[roleId] == nil then return end
        if Role.players[roleId].npcId ~= 0 then return end
        Role.players[roleId]:PlaySkillEffect(v.effect);
      end
    end
  end
end

function BattleRoyale.UpdateFieldEffect(roleId)
  if roleId == nil then return end
  if fieldEffects[roleId] == nil then return end

  for k, v in pairs(fieldEffects[roleId].updateFlags) do
    if v == true then
      if k == 14 then
        if Role.players[roleId] ~= nil then
          Role.players[roleId]:UpdateSpeed();
        end
      elseif roleId == Role.playerId and Contains(k, EAttribute.Int, EAttribute.Atk, EAttribute.Def, EAttribute.Hpx, EAttribute.Spx, EAttribute.Agi, EAttribute.MaxHp, EAttribute.MaxSp) then
        local value;
        if k == EAttribute.MaxHp then
          value = Calc.GetMaxHp(Role.player);
        elseif k == EAttribute.MaxSp then
          value = Calc.GetMaxSp(Role.player);
        else
          value = Role.player:GetAttribute(k);
        end
        Role.SetAttribute(EHuman.Player, roleId, k, value);
      end
    end
  end
  fieldEffects[roleId].updateFlags = {};
  if Role.players[roleId] == nil then return end
  if Role.players[roleId].npcId ~= 0 then return end
  Role.players[roleId]:UpdateFieldEffect();
end

function BattleRoyale.UpdateDisplayEffects()
  displayFieldEffects = {};
  for effectID, layer in pairs(fieldEffects[Role.playerId].allEffects) do
    local effects = fieldEffectDatas[effectID];
    if effects ~= nil then
      for orderID, effect in pairs(effects) do
        if effect.image ~= nil and layer > 0 then
          table.insert(displayFieldEffects, effect.image);
        end
      end
    end
  end
  UIMain.UpdateFieldEffect(table.Count(displayFieldEffects));
  if UI.IsVisible(UIStatus) then
    UIStatus.UpdateUI();
  end
end

function BattleRoyale.GetDisplayEffects()
  return displayFieldEffects;
end

function BattleRoyale.GetEffectLight(roleId)
  if fieldEffects[roleId] ~= nil then
    return fieldEffects[roleId].sceneEffect
  end
end

function BattleRoyale.GetFixedFieldEffectValue(kind, roleId)
  local sum = 0;
  if roleId == nil then
    roleId = Role.playerId;
  end

  if fieldEffects[roleId] == nil then
    return sum
  end

  if fieldEffects[roleId].effectType[EBattleRoyaleEffectType.Fixed][kind] ~= nil then
    for k, v in pairs(fieldEffects[roleId].effectType[EBattleRoyaleEffectType.Fixed][kind]) do
      sum = sum + v;
    end
  end

  return sum
end

function BattleRoyale.GetPercentageFieldEffectValue(kind, currentValue, roleId)
  local sum = 0;
  if roleId == nil then
    roleId = Role.playerId;
  end

  if fieldEffects[roleId] == nil then
    return sum
  end

  if fieldEffects[roleId].effectType[EBattleRoyaleEffectType.Percentage][kind] ~= nil then
    for k, v in pairs(fieldEffects[roleId].effectType[EBattleRoyaleEffectType.Percentage][kind]) do
      sum = sum + v;
    end
  end
  local result = math.floor(currentValue * (sum / 100));

  return result
end
--endregion

--region Info
function BattleRoyale.SetBattleId(id)
  battleRoyaleId = id;
  UIMain.SetBattleRoyaleId(string.format(string.Get(24368), battleRoyaleId));
end

function BattleRoyale.SetMaxParticipant(count)
  maxParticipant = count or maxParticipant;
end

function BattleRoyale.SetParticipantCount(count)
  if count ~= survivorCount then
    survivorCount = count;
  end
  participateText = string.format(string.Get(23634), survivorCount, maxParticipant);
  if battleRoyaleState then
    UIMain.SetBattleRoyaleParticipantText(participateText);
  else
    UIBattleRoyale.SetQueueCount(participateText);
  end
end

function BattleRoyale.SetWinsInfo(count)
  if count ~= winCount then
    winCount = count;
  end
  if count == 1 then
    local unlock = {};
    table.insert(unlock, 2);
    this.SetVocation(currentVocation.id, unlock, false);
  end
  if battleRoyaleState then
    UIMain.SetBattleRoyaleWinText(count);
  end
end

function BattleRoyale.SetSealInfo(count)
  if count ~= sealCount then
    sealCount = count;
  end
  if battleRoyaleState then
    UIMain.SetBattleRoyaleSealText(count);
  end
end

function BattleRoyale.SetRank(rankData, points)
  rank = rankData;
  rankPoints = points;
  local playerRank, stats = this.GetRecord();
  UI.Open(UIResult, Role.player, string.Get(23503), playerRank, stats);
end

function BattleRoyale.SetPKCountDownTime(endTime)
  UIMain.SetPKCountDown(endTime);
end

function BattleRoyale.GetMatchInfo()
  return participateText
end

function BattleRoyale.GetRecord()
  local finalRank = string.format(string.Get(23702), rank);
  local stats = "";

  if winCount ~= nil then
    local win = string.Concat(string.format(string.Get(23635), winCount), "\n");
    stats = string.Concat(stats, win);
  end

  if sealCount ~= nil then
    local seal = string.Concat(string.format(string.Get(23636), sealCount), "\n");
    stats = string.Concat(stats, seal);
  end

  if rankPoints ~= nil then
    local rp = string.Concat(string.format(string.Get(21412), rankPoints), "\n");
    stats = string.Concat(stats, rp);
  end

  if battleRoyaleId ~= nil then
    local battleId = string.Concat(string.format(string.Get(24368), battleRoyaleId), "\n");
    stats = string.Concat(stats, battleId);
  end

  if startTime ~= nil then
    local time = string.Concat(string.format(string.Get(21293), startTime:ToString("yyyy/MM/dd HH:mm")), "\n");
    stats = string.Concat(stats, time);
  end

  return finalRank, stats;
end
--endregion