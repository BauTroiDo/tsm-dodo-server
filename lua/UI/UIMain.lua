
UIMain = {}
local this = UIMain;

this.name = "UIMain";
this.uiController = nil;

--ui
local uiCareer;
local image_HeadIcon;
local image_PlayerInfo;
local text_Lv;
local text_Name;
local text_Hp;
local text_Sp;
local text_Exp;
local gameObject_Golds;
local text_Gold;
local text_LockPoint;
local text_Point;
local text_OrgGold;
local slider_Hp;
local slider_Sp;
local slider_Exp;
local gameObject_Buff;
local scrollContent_Buff;
local scrollItems_Buff = {};

--dungeon元件
local gameObject_Dungeon;
local gameObject_Leave;
local text_DungeonTimeM;
local text_DungeonTimeS;
local text_DungeonInfo;
local dungeonVisible = false;
local dropdown_AnotherWorldLv;

--region BattleRoyale UI
local gameObject_BattleRoyale;
local gameObject_Skill;
local currentSkills = {};
local gameObject_PKCountDown;
local text_BattleId;
local text_PKCountDown;
local text_Participant;
local text_Win;
local text_Seal;
local event_CloseHotkey;
local event_OpenHotkey;
local event_DirectUse;
local event_OpenInfo;
local event_Item;
local event_Equip;

local directUse = false;
local currentHotkeyMode = EHotkeyMode.SceneBattle;
local hotkeyState;
local followNpc = {};
--endregion

--var
local buffDatas = {};
local IslimitTimeDungeon = false;
local dungeonEndTime;
local pkEndTime;

local scrollContent_FieldEffect;
local scrollItems_FieldEffect = {};

function UIMain.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;

  Function.PrefabInitialize(2, uiController:FindGameObject("Function_Left").transform);

  uiCareer = UICareerController.New(uiController:FindGameObject("GameObject_Career"));
  image_PlayerInfo = uiController:FindImage("Image_PlayerInfo"):GetComponent("RectTransform");
  image_HeadIcon = HeadIconController.New(image_PlayerInfo:Find("Icon"));
  image_OneClick = uiController:FindImage("Image_OneClick");
  
  text_Lv = uiController:FindText("Text_Lv");
  text_Name = uiController:FindText("Text_Name");
  text_Hp = uiController:FindText("Text_Hp");
  text_Sp = uiController:FindText("Text_Sp");
  text_Exp = uiController:FindText("Text_Exp");

  gameObject_Golds = uiController:FindGameObject("Golds");
  text_Gold = uiController:FindText("Text_Gold (1)");
  text_LockPoint = uiController:FindText("Text_Gold (2)");
  text_Point = uiController:FindText("Text_Gold (3)");
  text_OrgGold = uiController:FindText("Text_Gold (4)");

  slider_Hp = uiController:FindSlider("Slider_Hp");
  slider_Sp = uiController:FindSlider("Slider_Sp");
  slider_Exp = uiController:FindSlider("Slider_Exp");

  gameObject_Buff = uiController:FindGameObject("ScrollRect_Buff");

  scrollContent_Buff = uiController:FindScrollContent("ScrollContent_Buff");
  scrollContent_Buff.onInitialize = this.OnInitialize_ScrollContent_Buff;
  scrollContent_Buff.onItemChange = this.OnItemChange_ScrollContent_Buff;
  scrollContent_Buff:Initialize(1);

  dropdown_AnotherWorldLv = uiController:FindDropdown("Dropdown_Lv");
  dropdown_AnotherWorldLv:AddListener(this.OnDropChange_AnotherWorldLv);

  local tempEvent = uiController:FindEvent("Image_PlayerInfo");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_PlayerInfo);

  gameObject_Dungeon = uiController:FindGameObject("GameObject_Dungeon");
  gameObject_Leave = uiController:FindGameObject("Leave");

  text_DungeonTimeM = uiController:FindText("Text_DungeonTimeM");
  text_DungeonTimeS = uiController:FindText("Text_DungeonTimeS");
  text_DungeonInfo = uiController:FindText("Text_DungeonInfo");
  text_DungeonInfo.text = "";

  tempEvent = uiController:FindEvent("Image_OnReel");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_DungeonHide);
  
  tempEvent = uiController:FindEvent("Image_DungeonExit");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_DungeonExit);
  gameObject_Dungeon:SetActive(false);

  gameObject_BattleRoyale = uiController:FindGameObject("BattleRoyale");
  text_PKCountDown = uiController:FindText("Text_PKCountDown");

  gameObject_Skill = uiController:FindGameObject("Skill");
  this.InitializeSkill(uiController:FindGameObject("BattleRoyaleSkill1").transform, 1);
  this.InitializeSkill(uiController:FindGameObject("BattleRoyaleSkill2").transform, 2);
  tempEvent = uiController:FindEvent("Image_SkillInfo");
  tempEvent:SetListener(EventTriggerType.PointerClick, UIBattleRoyale.CheckVocation);

  gameObject_PKCountDown = uiController:FindGameObject("PKCountDown");

  event_CloseHotkey = uiController:FindEvent("Image_CloseHotkey");
  event_CloseHotkey:SetListener(EventTriggerType.PointerClick, this.OnClick_ChangeHotkeyState);
  event_CloseHotkey.parameter = false;

  event_OpenHotkey = uiController:FindEvent("Image_OpenHotkey");
  event_OpenHotkey:SetListener(EventTriggerType.PointerClick, this.OnClick_ChangeHotkeyState);
  event_OpenHotkey.parameter = true;

  event_DirectUse = uiController:FindEvent("Image_DirectUse");
  event_DirectUse:SetListener(EventTriggerType.PointerClick, this.OnClick_ChangeDirectUseHotkey);
  event_DirectUse.parameter = true;

  event_OpenInfo = uiController:FindEvent("Image_OpenInfo");
  event_OpenInfo:SetListener(EventTriggerType.PointerClick, this.OnClick_ChangeDirectUseHotkey);
  event_OpenInfo.parameter = false;

  event_Item = uiController:FindEvent("Image_Item");
  event_Item:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchHotkeyMode);
  event_Item.parameter = EHotkeyMode.SceneBattle;

  event_Equip = uiController:FindEvent("Image_Equip");
  event_Equip:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchHotkeyMode);
  event_Equip.parameter = EHotkeyMode.Equip;

  scrollContent_FieldEffect = uiController:FindScrollContent("ScrollContent_FieldEffect");
  scrollContent_FieldEffect.onInitialize = this.OnInitialize_ScrollContentFieldEffect;
  scrollContent_FieldEffect.onItemChange = this.OnItemChange_ScrollContentFieldEffect;
  scrollContent_FieldEffect:Initialize(1);

  text_BattleId = uiController:FindText("Text_BattleId");
  text_Participant = uiController:FindText("Text_Participant");
  text_Win = uiController:FindText("Text_Win");
  text_Seal = uiController:FindText("Text_Seal");

  local followNpcRoot = uiController:FindGameObject("FollowNpc").transform;
  for i = 1, 4 do
    followNpc[i] = {};
    followNpc[i].image_BG = followNpcRoot:Find(string.Concat("Image_BG (", i, ")"));
    followNpc[i].event_BG = followNpc[i].image_BG:GetComponent("UIEvent");
    followNpc[i].event_BG:SetListener(EventTriggerType.PointerClick, this.OnClick_SelectNpc);
    followNpc[i].event_BG.parameter = i;
    local tempTransform = followNpc[i].image_BG.transform;
    followNpc[i].slider_Hp = tempTransform:Find("Slider_Hp"):GetComponent("Slider");
    followNpc[i].text_Hp = followNpc[i].slider_Hp.transform:Find("Text_Hp"):GetComponent("Text");
    followNpc[i].slider_Sp = tempTransform:Find("Slider_Sp"):GetComponent("Slider");
    followNpc[i].text_Sp = followNpc[i].slider_Sp.transform:Find("Text_Sp"):GetComponent("Text");
    followNpc[i].text_Name = tempTransform:Find("Text_Name"):GetComponent("Text");
    followNpc[i].selectImage = tempTransform:Find("Image_Select"):GetComponent("Image");
  end

  gameObject_BattleRoyale:SetActive(false);

end

function UIMain.OnOpen()
  Function.UpdateVisible();

  this.UpdateInfo();
  this.UpdateBuff();
  this.UpdateMoney();
  this.UpdateIcon();
  this.UpdateDungeon();
  
  return true;
end

function UIMain.UpdateIcon()
  image_HeadIcon:ChangeIcon(Role.player.data.sex, Role.player.data.headStyle);
  image_HeadIcon:ChangeIconColor(EHeadIconPart.Hair, Role.player.data:GetColorTint(EColorPart.Hair));
  image_HeadIcon:ChangeIconColor(EHeadIconPart.Skin, Role.player.data:GetColorTint(EColorPart.Skin));
end

function UIMain.UpdateInfo()
  if Role.player == nil then return end

  text_Lv.text = Role.player:GetAttribute(EAttribute.Lv);
  text_Name.text = Role.player.name;

  local turn = Role.player:GetAttribute(EAttribute.Turn);
  
  uiCareer:SetCareer(turn, Role.player:GetAttribute(EAttribute.Element), Role.player:GetAttribute(EAttribute.Career), false, Role.player:GetAttribute(EAttribute.Turn3Element));

  slider_Hp.maxValue = Role.player:GetAttribute(EAttribute.MaxHp);
  slider_Hp.value = Role.player:GetAttribute(EAttribute.Hp);
  text_Hp.text = string.Concat(slider_Hp.value, "/", slider_Hp.maxValue);

  slider_Sp.maxValue = Role.player:GetAttribute(EAttribute.MaxSp);
  slider_Sp.value = Role.player:GetAttribute(EAttribute.Sp);
  text_Sp.text = string.Concat(slider_Sp.value, "/", slider_Sp.maxValue);

  local level = Role.player:GetAttribute(EAttribute.Lv);
  local exp = Role.player:GetAttribute(EAttribute.Exp);

  if turn >= 3 then
    level = level - Role.playerMaxLv;
    exp = Role.player:GetAttribute(EAttribute.Turn3Exp);
  end
  
  if (turn >= 3 and level >= Role.playerMaxLv + Role.playerTurn3MaxLv) or (turn < 3 and level == Role.playerMaxLv) then
    slider_Exp.maxValue = 1;
    slider_Exp.value = 1;
    text_Exp.text = "100%";
  else
    slider_Exp.maxValue = Calc.GetUpExp(level + 1, turn);
    slider_Exp.value = exp - Calc.GetNeedTExp(level, turn);
    text_Exp.text = string.Concat(math.floor((slider_Exp.value / slider_Exp.maxValue) * 100), "%");
  end
end

function UIMain.UpdateNpcInfo(reset)
  if not BattleRoyale.GetBattleRoyaleState() then
    return
  end

  local followNpcs = Role.GetFollowNpcs(Role.playerId);

  for k, v in pairs(followNpc) do
    if followNpcs == nil or followNpcs[k] == nil then
      v.image_BG.gameObject:SetActive(false);
    else
      local npc = followNpcs[k];
      v.image_BG.gameObject:SetActive(true);
      v.text_Name.text = npc.name;
      v.text_Hp.text = string.Concat(npc:GetAttribute(EAttribute.Hp), " / ", npc:GetAttribute(EAttribute.MaxHp));
      v.slider_Hp.maxValue = npc:GetAttribute(EAttribute.MaxHp);
      v.slider_Hp.value = npc:GetAttribute(EAttribute.Hp);
      v.text_Sp.text = string.Concat(npc:GetAttribute(EAttribute.Sp), " / ", npc:GetAttribute(EAttribute.MaxSp));
      v.slider_Sp.maxValue = npc:GetAttribute(EAttribute.MaxSp);
      v.slider_Sp.value = npc:GetAttribute(EAttribute.Sp);
    end
  end
  if reset then
    this.UpdateSelectRole();
  end
end

function UIMain.UpdateMoney()
  text_Gold.text = Role.GetPlayerGold();
  text_LockPoint.text = Role.GetLockPoint();
  text_Point.text = Role.GetPoint();
  text_OrgGold.text = Role.GetPlayerOrgGold();
end

function UIMain.UpdateBuff()
  table.Clear(buffDatas);

  --時間型BUFF
  for k, v in pairs(ETimeBuff) do
    if DrugBuff.timeBuffs[v] ~= nil and DrugBuff.timeBuffs[v].time ~= nil then
      table.insert(buffDatas, DrugBuff.maxBuffCount + v);
    end
  end

  --全頻號角
  if Chat.hornCount ~= nil then
    table.insert(buffDatas, DrugBuff.maxBuffCount + table.Count(ETimeBuff) + EOtherBuff.WorldChannelHorn);
  end

  --世界王
  if WorldBoss.active then
    table.insert(buffDatas, DrugBuff.maxBuffCount + table.Count(ETimeBuff) + EOtherBuff.WorldBoss);
  end

  --軍團戰備戰
  if Organization.GetDutyFlag(EOrgDuty.OrgWar, Role.playerId) then
    table.insert(buffDatas, DrugBuff.maxBuffCount + table.Count(ETimeBuff) + EOtherBuff.OrgWarPrepare);
  end

  --半月卡
  if StoreValue.IsHaveHalfMonthCard() then
    table.insert(buffDatas, DrugBuff.maxBuffCount + table.Count(ETimeBuff) + EOtherBuff.HalfMonthCard);
  end

  --戰鬥BUFF
  for k, v in pairs(DrugBuff.fightBuffs) do
    table.insert(buffDatas, DrugBuff.maxBuffCount + table.Count(ETimeBuff) + table.Count(EOtherBuff) + k);
  end
  
  --藥品
  for k, v in pairs(DrugBuff.buffs) do
    if evoStatusDatas[v.id] ~= nil then
      table.insert(buffDatas, k);
    end
  end

  --[[
  --解鎖
  if DrugBuff.openCount + 5 < DrugBuff.maxBuffCount then
    table.insert(buffDatas, 0);
  end
  --]]

  scrollContent_Buff:Reset(table.maxn(buffDatas));
end

function UIMain.OnClick_Buff(uiEvent)
  local buffIndex = uiEvent.parameter;
  if buffIndex == 0 then
    --解鎖
    if DrugBuff.openCount + 5 >= DrugBuff.maxBuffCount then return end
    
    ShowCheckMessage(
      function(result)
        if result then
          --C:066-034 <開啟增益狀態欄位>
          Network.Send(66, 34);
        end
      end,
      string.format(string.Get(80458), DrugBuff.openPoints[DrugBuff.openCount + 1]),
      Role.player
    );

  elseif Between(buffIndex, DrugBuff.maxBuffCount + 1, DrugBuff.maxBuffCount + table.Count(ETimeBuff)) then
    --時間型BUFF
    local buffInfo = DrugBuff.timeBuffs[buffIndex - DrugBuff.maxBuffCount];

    if buffInfo.value ~= nil then
      ShowCenterMessage(string.Concat(string.Get(buffInfo.text), string.Get(71156), buffInfo.value, " ", string.Get(71268), " ", buffInfo.time:ToString("yyyy/MM/dd HH:mm:ss")));
    else
      ShowCenterMessage(string.Concat(string.Get(buffInfo.text), " ", string.Get(71268), " ", buffInfo.time:ToString("yyyy/MM/dd HH:mm:ss")));
    end

  elseif buffIndex == DrugBuff.maxBuffCount + table.Count(ETimeBuff) + EOtherBuff.WorldChannelHorn then
    --全頻號角
    ShowCenterMessage(string.Concat(string.Get(80467), Chat.hornCount));

  elseif buffIndex == DrugBuff.maxBuffCount + table.Count(ETimeBuff) + EOtherBuff.WorldBoss then
    --世界王
    local mission = MarkManager.GetMission(12207);
    if mission ~= nil then
      ShowCenterMessage(string.format(string.Get(71286), mission.step));
    else
      ShowCenterMessage(string.format(string.Get(71286), 0));
    end

    mission = MarkManager.GetMission(12231);
    if mission ~= nil then
      if mission.step == 1 then
        ShowCenterMessage(string.Get(71306));
      else
        ShowCenterMessage(string.format(string.Get(71305), mission.step));
      end
    end

  elseif buffIndex == DrugBuff.maxBuffCount + table.Count(ETimeBuff) + EOtherBuff.OrgWarPrepare then
    --軍團戰備戰
    ShowCenterMessage(string.Get(21805));

  elseif buffIndex == DrugBuff.maxBuffCount + table.Count(ETimeBuff) + EOtherBuff.HalfMonthCard then
    --半月卡
    ShowCenterMessage(string.format(string.Get(21854), StoreValue.GetHalfMonthCardEndDate():ToString("yyyy/MM/dd")));

  elseif Between(buffIndex, DrugBuff.maxBuffCount + table.Count(ETimeBuff) + table.Count(EOtherBuff) + 1, DrugBuff.maxBuffCount + table.Count(ETimeBuff) + table.Count(EOtherBuff) + table.Count(DrugBuff.fightBuffs)) then
    --戰鬥BUFF
    local buffInfo = DrugBuff.fightBuffs[buffIndex - (DrugBuff.maxBuffCount + table.Count(ETimeBuff) + table.Count(EOtherBuff))];

    if buffInfo.kind == EFightBuff.Def then
      --防禦力
      if buffInfo.value >= 1000 then
        ShowCenterMessage(string.Concat(buffInfo.role.name, " ", string.Get(90308), "+", tostring(buffInfo.value - 1000)));
      else
        ShowCenterMessage(string.Concat(buffInfo.role.name, " ", string.Get(90308), tostring(buffInfo.value - 1000)));
      end
    elseif buffInfo.kind == EFightBuff.Atk then
      --攻擊力
      if buffInfo.value >= 1000 then
        ShowCenterMessage(string.Concat(buffInfo.role.name, " ", string.Get(90309), "+", tostring(buffInfo.value - 1000)));
      else
        ShowCenterMessage(string.Concat(buffInfo.role.name, " ", string.Get(90309), tostring(buffInfo.value - 1000)));
      end
    elseif buffInfo.kind == EFightBuff.Int then
      --知力
      if buffInfo.value >= 1000 then
        ShowCenterMessage(string.Concat(buffInfo.role.name, " ", string.Get(90310), "+", tostring(buffInfo.value - 1000)));
      else
        ShowCenterMessage(string.Concat(buffInfo.role.name, " ", string.Get(90310), tostring(buffInfo.value - 1000)));
      end
    elseif buffInfo.kind == EFightBuff.Eff then
      --傷害力
      ShowCenterMessage(string.Concat(buffInfo.role.name, " ", string.format(string.Get(90311), buffInfo.value)));
    elseif buffInfo.kind == EFightBuff.Imm then
      --抵抗封印技
      ShowCenterMessage(string.Concat(buffInfo.role.name, " ", string.format(string.Get(90312), buffInfo.value)));
    elseif buffInfo.kind == EFightBuff.Cur then
      --每回合SP減少
      ShowCenterMessage(string.Concat(buffInfo.role.name, " ", string.format(string.Get(90313), buffInfo.value)));
    end
  else
    --藥品
    local buffInfo = DrugBuff.buffs[buffIndex];
    
    if buffInfo == nil then return end
    if evoStatusDatas[buffInfo.id] == nil then return end

    ShowCenterMessage(evoStatusDatas[buffInfo.id].description);
    ShowCenterMessage(string.Concat(string.Get(90086), buffInfo.count, string.Get(90023), 3));
  end
end

function UIMain.OnClick_PlayerInfo()
  if Role.player ~= nil and Role.player.data.isWatching then return end
  if BattleRoyale.GetBattleRoyaleState() then
    Hotkey.SetFollowIndex(0);
    this.UpdateSelectRole(0);
    return
  end
  Function.Use(12);
end

function UIMain.OnInitialize_ScrollContent_Buff(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Buff[itemIndex] = {};
    scrollItems_Buff[itemIndex].item = UIItemController.New(scrollItems[itemIndex]:Find("Image_BG"));
  end
end

function UIMain.OnItemChange_ScrollContent_Buff(dataIndex, itemIndex)
  local buffIndex = buffDatas[dataIndex + 1];

  if buffIndex == nil then return false end

  if buffIndex == 0 then
    --解鎖
    scrollItems_Buff[itemIndex].item:SetItem(0);
    scrollItems_Buff[itemIndex].item:SetLock(true);

  elseif Between(buffIndex, DrugBuff.maxBuffCount + 1, DrugBuff.maxBuffCount + table.Count(ETimeBuff)) then
    --時間型BUFF
    local buffInfo = DrugBuff.timeBuffs[buffIndex - DrugBuff.maxBuffCount];

    scrollItems_Buff[itemIndex].item:SetItem(buffInfo.itemId, 1);
    scrollItems_Buff[itemIndex].item:SetLock(false);

  elseif buffIndex == DrugBuff.maxBuffCount + table.Count(ETimeBuff) + EOtherBuff.WorldChannelHorn then
    --全頻號角
    scrollItems_Buff[itemIndex].item:SetItem(Chat.hornItem, 1);
    scrollItems_Buff[itemIndex].item:SetLock(false);

  elseif buffIndex == DrugBuff.maxBuffCount + table.Count(ETimeBuff) + EOtherBuff.WorldBoss then
    --世界王
    scrollItems_Buff[itemIndex].item:SetItem(WorldBoss.ticketItemId, 1);
    scrollItems_Buff[itemIndex].item:SetLock(false);

  elseif buffIndex == DrugBuff.maxBuffCount + table.Count(ETimeBuff) + EOtherBuff.OrgWarPrepare then
    --軍團戰備戰
    scrollItems_Buff[itemIndex].item:SetItem(Activity.orgWarPrepareItemId, 1);
    scrollItems_Buff[itemIndex].item:SetLock(false);

  elseif buffIndex == DrugBuff.maxBuffCount + table.Count(ETimeBuff) + EOtherBuff.HalfMonthCard then
    --半月卡
    scrollItems_Buff[itemIndex].item:SetItem(StoreValue.halfMonthCardItemId, 1);
    scrollItems_Buff[itemIndex].item:SetLock(false);

  elseif Between(buffIndex, DrugBuff.maxBuffCount + table.Count(ETimeBuff) + table.Count(EOtherBuff) + 1, DrugBuff.maxBuffCount + table.Count(ETimeBuff) + table.Count(EOtherBuff) + table.Count(DrugBuff.fightBuffs)) then
    --戰鬥BUFF
    local buffInfo = DrugBuff.fightBuffs[buffIndex - (DrugBuff.maxBuffCount + table.Count(ETimeBuff) + table.Count(EOtherBuff))];

    scrollItems_Buff[itemIndex].item:SetItem(buffInfo.itemId, buffInfo.round - FightField.fightRound, nil, true);
    scrollItems_Buff[itemIndex].item:SetLock(false);

  else
    --藥品
    scrollItems_Buff[itemIndex].item:SetItem(evoStatusDatas[DrugBuff.buffs[buffIndex].id].itemId, DrugBuff.buffs[buffIndex].count);
    scrollItems_Buff[itemIndex].item:SetLock(false);
  end
  
  scrollItems_Buff[itemIndex].item:SetEvent(this.OnClick_Buff, buffIndex);

  return true;
end

--dungeon
function UIMain.UpdateDungeon()
  local sceneData = sceneDatas[SceneManager.sceneId];

  local active = sceneData ~= nil and sceneData.kind == 2;

  if SceneManager.sceneId == Activity.orgWarSceneId then
    active = true;
  elseif SceneManager.sceneId == Activity.orgBossSceneId then
    active = false;
  elseif HouseManager.IsInHouse() then
    active = false;
  elseif BattleRoyale.GetBattleRoyaleState() and not string.IsNullOrEmpty(text_DungeonInfo.text) and not FightField.isInBattle then
    active = true;
  end

  IslimitTimeDungeon = false;
  for k, v in pairs(limitTimeDungeonDatas) do
    if SceneManager.sceneId == v.sceneId then
      active = true;

      local time = RoleCount.Get(v.limitIndex);
      if time > 0 then
        Dungeon.StartLimitTimeDungeonTime(v.limitIndex, v.limitTime);
        IslimitTimeDungeon = true;
        this.UpdateAnotherWorldLv(v.missionId);
      end

      break;
    end
  end

  if not active then
    gameObject_Dungeon:SetActive(false);
    CGTimer.RemoveListener(this.UpdateTime);
    CGTimer.RemoveListener(Dungeon.UpdateLimitTimeDungeonTime);
    return;
  end

  if IslimitTimeDungeon then
    dropdown_AnotherWorldLv.gameObject:SetActive(true);
    gameObject_Leave:SetActive(false);
    this.UpdateDungeonInfo(string.Get(23236));
    CGTimer.RemoveListener(this.UpdateTime);
  elseif BattleRoyale.GetBattleRoyaleState() then
    gameObject_Leave:SetActive(false);
    dropdown_AnotherWorldLv.gameObject:SetActive(false);
    CGTimer.RemoveListener(Dungeon.UpdateLimitTimeDungeonTime);
  else
    dropdown_AnotherWorldLv.gameObject:SetActive(false);
    gameObject_Leave:SetActive(true);
    this.UpdateDungeonInfo();
    CGTimer.RemoveListener(Dungeon.UpdateLimitTimeDungeonTime);
  end
  
  if gameObject_Dungeon.activeInHierarchy == true then return end

  dungeonVisible = true;
  gameObject_Dungeon:SetActive(true);
  LuaHelper.DoTweenAnchoredPosition(gameObject_Dungeon.transform, Vector2.New(0, 0), 1);
  text_DungeonTimeM.text = "";
  text_DungeonTimeS.text = "";
end

function UIMain.UpdateDungeonTime(minute, second)
  text_DungeonTimeM.text = minute;  
  text_DungeonTimeS.text = string.format("%02d", second);
end

function UIMain.UpdateDungeonInfo(info)
  if not string.IsNullOrEmpty(info) then
    text_DungeonInfo.gameObject:SetActive(true);
    text_DungeonInfo.text = info;
  else
    text_DungeonInfo.gameObject:SetActive(false);
    text_DungeonInfo.text = "";
  end
end

function UIMain.OnClick_DungeonExit(event)
  if FightField.isInBattle then 
    ShowCenterMessage(string.Get(80099));
    return;
  end
  
  ShowCheckMessage(
    function(result)
      if result then
        Dungeon.LeaveSinglePlayDungeon();
      end
    end,
    string.Get(21630),
    Role.player
  );
end

function UIMain.OnClick_DungeonHide(event)
  dungeonVisible = not dungeonVisible;
  if dungeonVisible then
    LuaHelper.DoTweenAnchoredPosition(gameObject_Dungeon.transform, Vector2.New(0, 0), 1);
  else
    LuaHelper.DoTweenAnchoredPosition(gameObject_Dungeon.transform, Vector2.New(130, 0), 1);
  end
end

function UIMain.UpdateAnotherWorldLv(missionId)
  local turn = Role.player:GetAttribute(EAttribute.Turn);
  local level = Role.player:GetAttribute(EAttribute.Lv);
  local step = 0;
  local tableCount = table.Count(anotherWorldLvDatas) / 4;
  if MarkManager.missions[missionId] ~= nil then
    step = MarkManager.missions[missionId].step;
    step = math.max((step - turn * tableCount) - 1, 0);
  end
  dropdown_AnotherWorldLv:ClearOptions();
  for k, v in pairs(anotherWorldLvDatas) do
    if v.turn == turn then
      dropdown_AnotherWorldLv:AddOption(v.level);
      
      if step == 0 then
        if level >= v.level then
          step = math.max((v.id - turn * tableCount) - 1, 0);
        end
      end
    end
  end

  dropdown_AnotherWorldLv.value = step;
end

function UIMain.OnDropChange_AnotherWorldLv(step)
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end
  
  --C:097-002 修改限時副本任務階段 +階段(1)
  local turn = Role.player:GetAttribute(EAttribute.Turn);
  local tableCount = table.Count(anotherWorldLvDatas) / 4;
  
  step = step + turn * tableCount + 1;
  
  sendBuffer:Clear();
  sendBuffer:WriteByte(step);
  Network.Send(97, 2, sendBuffer);
end

function UIMain.SetDropdownActive(enable)
  if IslimitTimeDungeon then
    dropdown_AnotherWorldLv.gameObject:SetActive(enable);
    if not enable then
      local dropdownList = dropdown_AnotherWorldLv.gameObject.transform:Find("Dropdown List");
      if dropdownList ~= nil then
        local gameObject = dropdownList.gameObject;
        if gameObject ~= nil then
          if gameObject.activeInHierarchy == false then
            GameObject.Destroy(gameObject);
          end
        end
      end
    end
  end
end

--region BattleRoyale
function UIMain.InitializeSkill(transform, index)
  currentSkills[index] = {};
  currentSkills[index].go = transform.gameObject;
  currentSkills[index].skill = transform:Find("Image_Skill"):GetComponent("Image");
  currentSkills[index].coolDownImage = transform:Find("Image_CoolDown"):GetComponent("Image");
  currentSkills[index].setTargetImage = transform:Find("Image_Target"):GetComponent("Image").gameObject;
  currentSkills[index].event = transform:Find("Image_Skill"):GetComponent("UIEvent");
  currentSkills[index].event:SetListener(EventTriggerType.PointerClick, this.OnClickSkill);
  currentSkills[index].event.parameter = index;
end

function UIMain.OnInitialize_ScrollContentFieldEffect(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_FieldEffect[itemIndex] = {};
    local scrollItem = scrollItems_FieldEffect[itemIndex];
    scrollItem.image = scrollItems[itemIndex]:Find("Image_BG"):GetComponent("Image");
  end
end

function UIMain.OnItemChange_ScrollContentFieldEffect(dataIndex, itemIndex)
  local index = dataIndex + 1;
  local effectImage = BattleRoyale.GetDisplayEffects()[index];
  local scrollItem = scrollItems_FieldEffect[itemIndex];
  if effectImage == nil or scrollItem == nil then
    return false
  end
  scrollItem.image.sprite = UI.GetSprite(effectImage);
  return true
end

function UIMain.UpdateSkillCoolDown()
  local coolDownFlag = false;
  for k, v in ipairs(currentSkills) do
    if v.startTime == nil then
      v.coolDownImage.fillAmount = 0;
      v.event.clickScale = true;
    else
      local passTime = CGTimer.serverTime:Subtract(v.startTime).TotalSeconds;
      if(passTime <= v.coolDown) then
        v.coolDownImage.fillAmount = (v.coolDown - passTime) / v.coolDown;
        coolDownFlag = true;
      else
        v.coolDownImage.fillAmount = 0;
        v.event.clickScale = true;
      end
    end
  end

  if coolDownFlag == false and CGTimer.ContainsListener(this.UpdateSkillCoolDown) then
    CGTimer.RemoveListener(this.UpdateSkillCoolDown);
  end
end

function UIMain.UpdateSkillState()
  local selectSkill = BattleRoyale.GetCurrentSkill();
  for k, v in ipairs(currentSkills) do
    v.setTargetImage:SetActive(v.skillID == selectSkill);
  end
end

function UIMain.SetSkillCountDownTime(skillIndex, time, endTime)
  local index;
  for k, v in ipairs(currentSkills) do
    if v.skillID == skillIndex then
      index = k;
    end
  end

  currentSkills[index].startTime = time;
  if time ~= nil and endTime ~= nil then
    currentSkills[index].coolDown = endTime:Subtract(time).TotalSeconds;
    currentSkills[index].event.clickScale = false;
  end
  this.UpdateSkillCoolDown();
  if not CGTimer.ContainsListener(this.UpdateSkillCoolDown) then
    CGTimer.AddListener(this.UpdateSkillCoolDown, 1, true);
  end
end

function UIMain.SetVocationSkill(skills, reset)
  if skills == nil then
    gameObject_Skill:SetActive(false);
    return
  else
    gameObject_Skill:SetActive(true);
  end

  for k, v in ipairs(currentSkills) do
    local skill = skills[k];
    if reset then
      v.go:SetActive(skill~=nil);
    else
      v.go:SetActive(true);
    end

    if skill ~= nil then
      v.skill.sprite = UI.GetSprite(skill.icon);
      v.skillID = skills[k].id;
      this.SetSkillCountDownTime(v.skillID);
      v.setTargetImage:SetActive(false);
    end
  end
end

function UIMain.OnClickSkill(uiEvent)
  if currentSkills[uiEvent.parameter].coolDownImage.fillAmount > 0 then
    return
  end
  BattleRoyale.UseSkill(currentSkills[uiEvent.parameter].skillID);
end

function UIMain.UpdateFieldEffect(effectCount)
  scrollContent_FieldEffect:Reset(effectCount);
end

function UIMain.SetCountDownTime(endTime)
  dungeonEndTime = endTime;
  CGTimer.AddListener(this.UpdateTime, 1, true);
end

function UIMain.UpdateTime()
  local sec = math.floor((dungeonEndTime:Subtract(CGTimer.serverTime)).TotalSeconds);
  if sec > 0 then
    UIMain.UpdateDungeonTime(math.floor(sec / 60), sec % 60);
  else
    if BattleRoyale.GetBattleRoyaleState() then
      text_DungeonInfo.text = "";
    end
    CGTimer.RemoveListener(this.UpdateTime);
  end
end

function UIMain.SetBattleRoyaleId(info)
  text_BattleId.text = info;
end

function UIMain.SetBattleRoyaleParticipantText(info)
  text_Participant.text = info;
end

function UIMain.SetBattleRoyaleWinText(count)
  text_Win.text = string.format(string.Get(23635), count);
end

function UIMain.SetBattleRoyaleSealText(count)
  text_Seal.text = string.format(string.Get(23636), count);
end

function UIMain.SetPKCountDown(time)
  if time ~= nil then
    pkEndTime = time;
  end

  gameObject_PKCountDown:SetActive(true);
  if not CGTimer.ContainsListener(this.UpdatePKCountDown) then
    CGTimer.AddListener(this.UpdatePKCountDown, 1, true);
  end
end

function UIMain.StopPKCountDown()
  gameObject_PKCountDown:SetActive(false);
  CGTimer.RemoveListener(this.UpdatePKCountDown);
end

function UIMain.UpdateHotkey(state)
  if state ~= nil then
    hotkeyState = state
  end

  event_OpenHotkey.gameObject:SetActive(not hotkeyState);
  event_CloseHotkey.gameObject:SetActive(hotkeyState);

  if hotkeyState and BattleRoyale.GetBattleRoyaleState() then
    UI.Unhide(UIHotkey);
    this.UpdateHotkeyDirectUse();
    this.UpdateHotkeyMode();
  elseif not hotkeyState then
    UI.Hide(UIHotkey);
    event_OpenInfo.gameObject:SetActive(false);
    event_DirectUse.gameObject:SetActive(false);
    event_Item.gameObject:SetActive(false);
    event_Equip.gameObject:SetActive(false);
  end
end

function UIMain.SelectHotkeyType(type)
  currentHotkeyMode = type;
end

function UIMain.OpenHotkey()
  if not BattleRoyale.GetBattleRoyaleState() then return end
  UIHotkey.SetSortOrder(this.uiController.canvas.sortingOrder);
  if currentHotkeyMode == EHotkeyMode.SceneBattle then
    Hotkey.SetCurrentRole(Role.player);
    Hotkey.SetCurrentHotkey(EHotkeyUI.Main, EHotkeyType.SceneBattleMain);
  elseif currentHotkeyMode == EHotkeyMode.Equip then

  else
    return
  end
  Hotkey.Show(currentHotkeyMode);
end

function UIMain.UpdateHotkeyDirectUse(state)
  if state == nil then
    state = directUse;
  else
    directUse = state;
  end

  event_DirectUse.gameObject:SetActive(not state);
  event_OpenInfo.gameObject:SetActive(state);
  Hotkey.SetDirectUse(state);
end

function UIMain.UpdateHotkeyMode(mode)
  if mode == nil then
    mode = currentHotkeyMode;
  end

  if mode == EHotkeyMode.SceneBattle then
    event_Item.gameObject:SetActive(false);
    event_Equip.gameObject:SetActive(true);
    Hotkey.SetCurrentHotkey(EHotkeyUI.Main, EHotkeyType.SceneBattleMain);
    Hotkey.Show(EHotkeyMode.SceneBattle);
    currentHotkeyMode = mode;
  elseif mode == EHotkeyMode.Equip then
    event_Item.gameObject:SetActive(true);
    event_Equip.gameObject:SetActive(false);
    Hotkey.Show(EHotkeyMode.Equip);
    currentHotkeyMode = mode;
  end
end

function UIMain.OnClick_ChangeHotkeyState(uiEvent)
  this.UpdateHotkey(uiEvent.parameter);
end

function UIMain.OnClick_ChangeDirectUseHotkey(uiEvent)
  this.UpdateHotkeyDirectUse(uiEvent.parameter);
end

function UIMain.OnClick_SwitchHotkeyMode(uiEvent)
  this.UpdateHotkeyMode(uiEvent.parameter);
end

function UIMain.OnClick_SelectNpc(uiEvent)
  this.UpdateSelectRole(uiEvent.parameter);
end

function UIMain.UpdateSelectRole(index)
  local follows = Role.GetFollowNpcs(Role.playerId);
  if index == nil then
    index = Hotkey.GetFollowIndex();
    if follows == nil or follows[index] == nil then
      index = 0;
    end
  end
  Hotkey.SetFollowIndex(index);
  if UI.IsVisible(UIHotkey) then
    UIHotkey.UpdateUI(true);
  end
  for k, v in pairs(followNpc) do
    v.selectImage.enabled = k == index;
  end
end

function UIMain.UpdatePKCountDown()
  if pkEndTime == nil then
    CGTimer.RemoveListener(this.UpdatePKCountDown);
    return
  end

  local sec = math.floor((pkEndTime:Subtract(CGTimer.serverTime)).TotalSeconds);
  text_PKCountDown.text = sec;

  if sec <= 0 then
    CGTimer.RemoveListener(this.UpdatePKCountDown);
  end
end

function UIMain.ShowBattleRoyaleUI(state)
  text_PKCountDown.text = "";
  gameObject_PKCountDown:SetActive(false);
  gameObject_BattleRoyale:SetActive(state);
  gameObject_Skill:SetActive(state);
  gameObject_Golds:SetActive(not BattleRoyale.GetBattleRoyaleState());
  gameObject_Buff:SetActive(not state);
  this.UpdateNpcInfo(true);
  if state == false then
    CGTimer.RemoveListener(this.UpdatePKCountDown);
    CGTimer.RemoveListener(this.UpdateSkillCoolDown);
    UI.Unhide(UIHotkey);
  elseif state == true then
    CGTimer.AddListener(this.UpdateSkillCoolDown, 1, true);
    CGTimer.AddListener(this.UpdatePKCountDown, 1, true);
  end
end

function UIMain.Clear()
  pkEndTime = nil;
  dungeonEndTime = 0;
  CGTimer.RemoveListener(this.UpdateTime);
  CGTimer.RemoveListener(this.UpdatePKCountDown);
end