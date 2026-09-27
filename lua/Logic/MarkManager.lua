local json = require 'cjson'

MarkManager = {};
local this = MarkManager;

--const
this.maxTimeMission = 5;

--var
this.missions = {};
this.flags = {};
this.showMissions = {};
this.timeMissions = {};

--navigation
this.navigationMissionId = 0;
this.navigationTarget = nil;

local navigationSceneId = 0;
local navigationAreaId = 0;
local navigationPosition = Vector2.zero;
local navigationEventKind = ENavigationEvent.None;
local navigationEventId = 0;

--Hint
local shineMissionId = 0;
local pairMessageBitIndex = 0;

function MarkManager.Initialize()
  CGTimer.AddListener(this.CheckIdleShine, 1, false);

  MarkManager.LoadHideMission();
end

function MarkManager.Clear()
  shineMissionId = 0;
  pairMessageBitIndex = 0;

  table.Clear(this.missions);
  table.Clear(this.flags);
  table.Clear(this.showMissions);
  table.Clear(this.timeMissions);

  this.StopNavigation();
end

function MarkManager.UpdateShowMission()
  table.Clear(this.showMissions);

  for bitIndex, missionId in pairs(bitMarkDatas) do
    if CheckFlag(this.flags, bitIndex) then
      if markDatas[missionId] ~= nil and markDatas[missionId].kind ~= 8 and markDatas[missionId].steps ~= nil then
        --logError(string.Concat("Add Bit Mission ", missionId));
        table.insert(this.showMissions, missionId);
      end
    end
  end

  for k, v in pairs(this.missions) do
    local missionId = v.id;

    if markDatas[missionId] ~= nil and markDatas[missionId].kind ~= 8 and markDatas[missionId].steps ~= nil then
      if markDatas[missionId].bitId == 0 then
        missionId = missionId + 1;
      end
      
      if markDatas[missionId] ~= nil then
        --logError(string.Concat("Add Step Mission ", missionId));
        table.insert(this.showMissions, missionId);
      end
    end
  end

  this.SortShowMission();
end

function MarkManager.SortShowMission()
  table.sort(this.showMissions,
    function(a, b)
      local aMissionInfo = this.GetMission(a);
      local bMissionInfo = this.GetMission(b);
      local aIsTimeMission = this.GetTimeMission(aMissionInfo.id) ~= nil;
      local bIsTimeMission = this.GetTimeMission(bMissionInfo.id) ~= nil;

      if aIsTimeMission and not bIsTimeMission then
        return true;
      elseif not aIsTimeMission and bIsTimeMission then
        return false
      elseif markDatas[a].kind == markDatas[b].kind then
        return a < b;
      else
        return markDatas[a].kind < markDatas[b].kind;
      end
    end
  );

  this.UpdateOther();
end

function MarkManager.GetOwnMission(searchText)
  local ownMissions = {};

  if string.IsNullOrEmpty(searchText) then
    searchText = nil;
  end

  --未完成的任務
  for i = 1, table.Count(this.showMissions) do
    local missionId = this.showMissions[i];
    if markDatas[missionId] ~= nil then
      if searchText == nil or string.find(markDatas[missionId].name, searchText) ~= nil then
        table.insert(ownMissions, missionId);
      end
    end
  end

  --已完成的任務
  local compeletedMissions = {};
  for bitIndex, missionId in pairs(bitMarkDatas) do
    if CheckFlag(this.flags, bitIndex) then
      if markDatas[missionId] ~= nil and markDatas[missionId].kind ~= 8 then
        if searchText == nil or string.find(markDatas[missionId].name, searchText) ~= nil then
          table.insert(compeletedMissions, missionId);
        end
      end
    end
  end
  table.sort(compeletedMissions, function(a, b) return a < b end);

  for i = 1, table.Count(compeletedMissions) do
    table.insert(ownMissions, compeletedMissions[i]);
  end

  return ownMissions;
end

function MarkManager.UpdateOther()
  SceneEffect.SetShowEffectSW(SceneManager.sceneId, SceneManager.sceneState.type);

  if UI.IsVisible(UIMission) then
    UIMission.UpdatePage();
  end

  if UI.IsVisible(UIMiniMap) then
    UIMiniMap.UpdateMission();
  end
end

--step
function MarkManager.InitMissionStep(data)
  table.Clear(this.missions);

  local count = data:ReadInt32();
  for i = 1, count do
    data:ReadByte();

    this.AddMission(data:ReadUInt16(), data:ReadByte(), false);
  end

  this.UpdateShowMission();
end

function MarkManager.AddMission(missionId, step, shine)
  --logError(string.Concat("Add Mission ", missionId, " Step ", step));
  this.missions[missionId] = { id = missionId, step = step };

  if shine ~= false and markDatas[missionId] ~= nil and Contains(markDatas[missionId].kind, 1, 2) then
    this.SetShineMission(missionId);
  end

  this.UpdateShowMission();
end

function MarkManager.RemoveMission(missionId)
  if this.missions[missionId] == nil then return end

  this.missions[missionId] = nil;

  if shineMissionId == missionId then
    this.SetShineMission(0);
  end

  this.UpdateShowMission();
end

function MarkManager.AddMissionStep(missionId, step)
  local mission = this.GetMission(missionId);
  
  if mission ~= nil then
    if 255 - mission.step >= step then
      mission.step = mission.step + step;
    end
    
    if mission == this.GetMission(this.navigationMissionId) then
      this.Navigation(missionId);
    end
  else
    this.AddMission(missionId, step);
  end

  this.UpdateOther();

  NotifyManager.Notify("CheckCondition", ECondition.MissionStep);
end

function MarkManager.DecMissionStep(missionId, step)
  local mission = this.GetMission(missionId);

  if mission == nil then return end

  if step ~= nil then
    mission.step = mission.step - step;
  else
    mission.step = 0;
  end

  mission.step = math.max(0, mission.step);

  if mission == this.GetMission(this.navigationMissionId) then
    if mission.step ~= 0 then
      this.Navigation(missionId);
    end
  end

  if mission.step == 0 then
    this.RemoveMission(missionId);
  end

  this.UpdateOther();

  NotifyManager.Notify("CheckCondition", ECondition.MissionStep);
end

function MarkManager.GetMission(missionId)
  if missionId ~= nil and missionId ~= 0 then
    for k, v in pairs(this.missions) do
      if v.id == missionId then
        return v;
      elseif markDatas[v.id] ~= nil and markDatas[v.id].bitId == 0 and v.id + 1 == missionId then
        return v;
      end
    end
  end

  return nil;
end

function MarkManager.IsMissionItem(itemId)
  for k, v in pairs(this.missions) do
    if markDatas[v.id] ~= nil and markDatas[v.id].steps ~= nil and markDatas[v.id].steps[v.step] ~= nil then
      for i = 1, 5 do
        if markDatas[v.id].steps[v.step].conditions[i].kind == 3 and markDatas[v.id].steps[v.step].conditions[i].id == itemId then
          return true;
        end
      end
    end
  end

  return false;
end

--flag
function MarkManager.InitMissionFlag(data)
  table.Clear(this.flags);

  local count = data:ReadUInt16();
  for i = 1, count do
    local index = data:ReadUInt16();

    this.flags[index] = data:ReadByte();
  end

  this.UpdateShowMission();

  NotifyManager.Notify("CheckCondition", ECondition.MissionFlag);
end

function MarkManager.SetMissionFlag(bitIndex, bitValue, endData)
  local lastValue = CheckFlag(this.flags, bitIndex);

  this.flags = SetFlag(this.flags, bitIndex, bitValue);

  this.UpdateShowMission();
  
  --活動撮合訊息
  for k, v in pairs(markDatas) do
    if v.bitId == bitIndex then
      if Contains(k, 10007, 10674, 10578, 10753, 10753, 10755, 10757, 11698, 12031, 12591) or (Define.FieldBattle and k == 10511) then
        --由false -> true
        if not lastValue and bitValue then
          pairMessageBitIndex = bitIndex;
          --C:110-002 <開始大逃殺排隊>
          if k == 10757 then
            Network.Send(110, 2);
          else
            CGTimer.AddListener(this.ShowPairMessage, 2, true);
          end
        --由true -> false
        elseif lastValue and not bitValue then
          if k == 10757 then
            BattleRoyale.LeaveQueue();
          else
            pairMessageBitIndex = 0;
            CGTimer.RemoveListener(this.ShowPairMessage);
            ShowCenterMessage(string.Get(30071));
          end
        end
      end
      break;
    end
  end

  if endData then
    NotifyManager.Notify("CheckCondition", ECondition.MissionFlag);
  end

  Function.UpdateVisible();

  if bitValue then
    for k, v in pairs(guideDatas) do
      if v[1] ~= nil and v[1].markFlagId == bitIndex then
        UITeach.Guide(k);
      end
    end
  end
end

function MarkManager.ShowPairMessage()
  if CheckFlag(this.flags, pairMessageBitIndex) then
    ShowCenterMessage(string.Get(30070));
  else
    CGTimer.RemoveListener(this.ShowPairMessage);
  end
end

function MarkManager.GetMissionFlag(missionId)
  if markDatas[missionId] == nil then return false end
  
  return CheckFlag(this.flags, markDatas[missionId].bitId);
end

--time mission
function MarkManager.AddTimeMission(missionId, time)
  for i = 1, this.maxTimeMission do
    if this.timeMissions[i] ~= nil then
      if this.timeMissions[i].missionId == missionId then
        this.timeMissions[i].time = time;

        --this.ShowTimeMission(missionId, time);
        this.SortShowMission();
        return;
      end
    end
  end

  for i = 1, this.maxTimeMission do
    if this.timeMissions[i] == nil then
      this.timeMissions[i] = {};
      this.timeMissions[i].missionId = missionId;
      this.timeMissions[i].time = time;

      --this.ShowTimeMission(missionId, time);
      this.SortShowMission();
      return;
    end
  end
end

function MarkManager.RemoveTimeMission(missionId)
  for i = 1, this.maxTimeMission do
    if this.timeMissions[i] ~= nil then
      if this.timeMissions[i].missionId == missionId then
        this.timeMissions[i] = nil;

        this.ShowTimeMission(missionId);
        this.SortShowMission();
        break;
      end
    end
  end
end

function MarkManager.SetTimeMission(index, time)
  if this.timeMissions[index] == nil then return end

  this.timeMissions[index].time = time;

  this.ShowTimeMission(this.timeMissions[index].missionId, time);
  this.SortShowMission();
end

function MarkManager.ShowTimeMission(missionId, time)
  if markDatas[missionId] == nil then return end

  if time == nil then
    ShowCenterMessage(string.Concat(string.Get(90095), string.Get(90088), markDatas[missionId].name, string.Get(90094)));
  elseif time > 0 then
    ShowCenterMessage(string.Concat(string.Get(90088), markDatas[missionId].name, " ", string.Get(90086), time, string.Get(90087)));
  else
    ShowCenterMessage(string.Concat(string.Get(90088), markDatas[missionId].name, " ", string.Get(90089)));
  end
end

function MarkManager.GetTimeMission(missionId)
  for i = 1, this.maxTimeMission do
    if this.timeMissions[i] ~= nil then
      if this.timeMissions[i].missionId == missionId then
        return this.timeMissions[i];
      end
    end
  end

  return nil;
end

--navigation
function MarkManager.Navigation(missionId)
  this.SetShineMission(0);

  if markDatas[missionId] == nil then return end

  local mission = this.GetMission(missionId);

  if mission == nil then return end
  if markDatas[mission.id] == nil then return end
  if markDatas[mission.id].steps == nil then return end

  local step = markDatas[mission.id].steps[mission.step];

  if step == nil then return end

  local sceneId = step.endSceneId;
  local areaId = step.endAreaId;
  local position = step.endPosition;
  local eventKind = step.endEventKind;
  local eventId = step.endEventId;
  
  local haveCondition = false;
  local doneCondition = true;

  for i = 1, 5 do
    local condition = step.conditions[i];

    if condition.kind ~= 0 then
      haveCondition = true;

      if condition.kind == 1 then
        --捕捉npc
        doneCondition = Role.GetFollowNpc(Role.playerId, condition.id) ~= nil;

      elseif condition.kind == 2 then
        --擊殺npc

      elseif condition.kind == 3 then
        --收集道具
        doneCondition = Item.GetItemCount(condition.id) >= condition.count;
      end

      if not doneCondition then
        sceneId = condition.sceneId;
        areaId = condition.areaId;
        position = condition.position;
        eventKind = condition.eventKind;
        eventId = condition.eventId;
        break;
      end
    end
  end

  if sceneId ~= 0 and (position.x ~= 0 or position.y ~= 0) then
    this.StartNavigation(missionId, sceneId, areaId, position, eventKind, eventId);
  else
    ShowCenterMessage(string.Get(21264));
    this.StopNavigation();
  end
end

function MarkManager.StartNavigation(missionId, sceneId, areaId, position, eventKind, eventId)
  logWarn(string.Concat("Mission Auto Go To ", sceneId, "-", areaId, " (", position.x, ", ", position.y, ")"));

  this.navigationMissionId = missionId;
  this.navigationTarget = nil;

  navigationSceneId = sceneId;
  navigationAreaId = areaId;
  navigationPosition.x = position.x;
  navigationPosition.y = position.y;
  navigationEventKind = eventKind;
  navigationEventId = eventId;

  this.ContinueNavigation();

  if UI.IsVisible(UIMiniMap) then
    UIMiniMap.UpdateMission();
  end
end

function MarkManager.CheckNavigationNpc(role)
  if navigationEventKind ~= ENavigationEvent.Npc then return end
  if SceneManager.sceneId ~= navigationSceneId then return end
  if SceneManager.areaId ~= navigationAreaId then return end
  if Role.mapNpcs[navigationEventId] ~= role then return end

  logWarn(string.Concat("Mission Auto Talk To ", role.name));

  TargetManager.ChangeTarget(role);
  role:OnInteractive(EInteractive.Trig);

  this.StopNavigation();
end

function MarkManager.CheckNavigationDoor(door)
  if navigationEventKind ~= ENavigationEvent.Door then return end
  if SceneManager.sceneId ~= navigationSceneId then return end
  if SceneManager.areaId ~= navigationAreaId then return end
  if DoorManager.doors[navigationEventId] ~= door then return end
  
  this.StopNavigation();
end

function MarkManager.StopNavigation()
  if this.navigationMissionId == 0 and this.navigationTarget == nil then
    return;
  end
    
  this.navigationMissionId = 0;
  this.navigationTarget = nil;

  navigationSceneId = 0;
  navigationAreaId = 0;
  navigationPosition.x = 0;
  navigationPosition.y = 0;
  navigationEventKind = ENavigationEvent.None;
  navigationEventId = 0;

  if UI.IsVisible(UIMiniMap) then
    UIMiniMap.UpdateMission();
  end
end

function MarkManager.ContinueNavigation()
  if navigationSceneId == 0 then
    if MachineBox.client.moveMode == EAutoMoveMode.WithoutTrack then
      Scene.UpdateCamera(Role.player.position, true);
    end
    
    return false;
  end
  if not Role.CanControl() then return false end

  if SceneManager.sceneId == navigationSceneId and SceneManager.areaId == navigationAreaId and navigationEventKind == ENavigationEvent.Npc and Role.mapNpcs[navigationEventId] ~= nil then
    if TargetManager.Contains(Role.mapNpcs[navigationEventId]) then
      this.CheckNavigationNpc(Role.mapNpcs[navigationEventId]);
      return false;
    end
    
    this.navigationTarget = navigationPosition;
  else
    this.navigationTarget = SceneManager.GetPath(navigationSceneId, navigationAreaId, navigationPosition, Role.player.position);
  end

  if this.navigationTarget == nil then
    ShowCenterMessage(string.Get(21266));
    this.StopNavigation();
    return false;
  end

  Role.player:Move(this.navigationTarget,
    function(roleController, moveDone)
      if not moveDone then return end
      if SceneManager.sceneId ~= navigationSceneId then return end
      if SceneManager.areaId ~= navigationAreaId then return end

      this.StopNavigation();
    end
  );

  return true;
end

--Hint
function MarkManager.CheckIdleShine()
  if shineMissionId ~= 0 then return end
  if Role.latestMoveTime + 6 > CGTimer.time then return end
  if UIEvent.latestEventTime + 6 > CGTimer.time then return end
  if Role.player == nil or not Role.CheckPlayerLv(0, 30) then return end
  
  local missionId = 65535;
  for k, v in pairs(this.missions) do
    if markDatas[v.id] ~= nil and markDatas[v.id].kind == 1 then
      if v.id < missionId then
        missionId = v.id;
      end
    end
  end

  if missionId == 65535 then
    for k, v in pairs(this.missions) do
      if markDatas[v.id] ~= nil and markDatas[v.id].kind == 2 then
        if v.id < missionId then
          missionId = v.id;
        end
      end
    end
  end

  if missionId == 65535 then return end

  this.SetShineMission(missionId);
end

function MarkManager.SetShineMission(missionId)
  shineMissionId = missionId;

  if UI.IsVisible(UIMiniMap) then
    UIMiniMap.UpdateMission();
  end
end

function MarkManager.CheckShine(missionId)
  local mission = this.GetMission(missionId);

  if mission == nil then return false end

  return shineMissionId == mission.id;
end

--Hide Mission
local hideMissions = nil;

function MarkManager.LoadHideMission()
  local jsonText = CGResourceManager.ReadText("HideMissionList.txt");
  
  if not string.IsNullOrEmpty(jsonText) then
    hideMissions = json.decode(jsonText);
  end
end

function MarkManager.SaveHideMission()
  local jsonText = json.encode(hideMissions);
  
  CGResourceManager.WriteText("HideMissionList.txt", jsonText);
end

function MarkManager.SwitchHideMission(missionId)
  if hideMissions == nil then
    hideMissions = {};
  end

  if table.Contains(hideMissions, missionId) then
    table.RemoveByValue(hideMissions, missionId);
  else
    table.insert(hideMissions, missionId);
  end

  this.SaveHideMission();

  if UI.IsVisible(UIMiniMap) then
    UIMiniMap.UpdateMission();
  end

  if UI.IsVisible(UIMissionSwitch) then
    UIMissionSwitch.UpdateUI();
  end
end

function MarkManager.IsHideMission(missionId)
  if hideMissions == nil then return false end

  return table.Contains(hideMissions, missionId);
end