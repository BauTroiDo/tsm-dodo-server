EArmyBattleFunction = {
  Team = 1,
  Order = 2,
  History = 3,
  Result = 4
}

EArmyResultMainTag = {
  Award = 1,
  Board = 2,
  Prize = 3
}

UIArmyBattleFunction = {}
local this = UIArmyBattleFunction;

this.name = "UIArmyBattleFunction";
this.uiController = nil;

local transform_FrameRect;

--team
local teamUIs = {};
local gameObject_NPC;
local scrollContent_Npc;
local scrollItems_Npc = {};

--order
local scrollContent_Order;
local scrollItems_Order = {};
local text_OrderCount;
local text_OrderCountTime;
local text_GoldOrderCount;
local text_GoldOrderCountTime;
local text_DrumCountTime;
local gameObject_Plus;

--history
local scrollContent_History;
local scrollItems_History = {};

--result
local scrollContent_Leaderboard;
local scrollItems_Leaderboard = {};
local scrollContent_Prize;
local scrollItems_Prize = {};
local gameObject_Award;
local gameObject_Board;
local gameObject_Prize;
local gameObject_Result;
local gameObject_Default;
local gameObject_Info;
local text_Empty;
local text_Page;
local text_Score;
local text_Status;
local text_Rank;
local text_Result;

--var
local functions = {};
local tagData = {};
local drums = {};
local award = {};
local history = {};
local board = {};
local pageOfBoard = {};
local resultImg = {};
local orders = {};
local drumTime = 0;
local orderTime = 0;
local goldOrderTime = 0;

local title;
local currentMainTag = EArmyResultMainTag.Award;
local currentFunction;
local currentTeamIndex;
local currentPage = 1;
local totalPage = 1;
local changeOrder = 0;

function UIArmyBattleFunction.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onClose = this.OnClose;
  this.uiController.onVisible = this.OnVisible;

  for i = 1, table.Count(EArmyBattleFunction) do
    functions[i] = {};
  end

  functions[EArmyBattleFunction.Team].gameObject = uiController:FindGameObject("Team");
  functions[EArmyBattleFunction.Team].frameRects = uiController:FindGameObject("FrameRect_Team").transform;
  functions[EArmyBattleFunction.Order].gameObject = uiController:FindGameObject("Order");
  functions[EArmyBattleFunction.Order].frameRects = uiController:FindGameObject("FrameRect_Order").transform;
  functions[EArmyBattleFunction.History].gameObject = uiController:FindGameObject("History");
  functions[EArmyBattleFunction.History].frameRects = uiController:FindGameObject("FrameRect_History").transform;
  functions[EArmyBattleFunction.Result].gameObject = uiController:FindGameObject("Result");
  functions[EArmyBattleFunction.Result].frameRects = uiController:FindGameObject("FrameRect_Result").transform;

  local tempEvent = nil;

  --region team
  gameObject_NPC = uiController:FindGameObject("Npc");
  local teamRoot = uiController:FindGameObject("Image_TeamBG").transform;

  for i = 0, DataFight.maxTeamNpc do
    local tempTransform = teamRoot:Find(string.Concat("Team (", i, ")"));
    teamUIs[i] = {};
    teamUIs[i].npcId = 0;
    teamUIs[i].uiCareer = UICareerController.New(tempTransform:Find("Career"));
    teamUIs[i].text_Name = tempTransform:Find("Text_Name"):GetComponent("Text");
    teamUIs[i].text_Level = tempTransform:Find("Text_Lv"):GetComponent("Text");
    teamUIs[i].text_Hp = tempTransform:Find("Text_Hp"):GetComponent("Text");
    teamUIs[i].gameObject_Empty = tempTransform:Find("Image_Empty").gameObject;

    if i == 0 then
      tempEvent = tempTransform:Find("Image_SaveTeam"):GetComponent("UIEvent");
      tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SaveTeam);
    else
      tempEvent = tempTransform:Find("Image_ChooseNpc"):GetComponent("UIEvent");
      tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ChooseNpc);
      tempEvent.parameter = i;

      teamUIs[i].text_ChooseNpc = tempEvent.transform:Find("Text_"):GetComponent("Text");
    end

    teamUIs[i].skills = {};
    for j = 1, DataFight.maxTeamSkill do
      local skillTransform = tempTransform:Find(string.Concat("Image_Skill (", j, ")"));
      teamUIs[i].skills[j] = {};
      teamUIs[i].skills[j].skillId = 0;
      teamUIs[i].skills[j].rawImage_Icon = skillTransform:Find("RawImage_Icon"):GetComponent("RawImage");
      teamUIs[i].skills[j].event_Remove = skillTransform:Find("Image_Close"):GetComponent("UIEvent");
      teamUIs[i].skills[j].event_Remove:SetListener(EventTriggerType.PointerClick, this.OnClick_RemoveSkill);
      teamUIs[i].skills[j].event_Remove.parameter = i * 100 + j;
      tempEvent = skillTransform:GetComponent("UIEvent");
      tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ChooseSkill);
      tempEvent.parameter = i * 100 + j;
    end
  end

  scrollContent_Npc = uiController:FindScrollContent("ScrollContent_Npc");
  scrollContent_Npc.onInitialize = this.OnInitialize_ScrollContent_Npc;
  scrollContent_Npc.onItemChange = this.OnItemChange_ScrollContent_Npc;
  scrollContent_Npc:Initialize(1);

  gameObject_NPC:SetActive(false);

  tempEvent = gameObject_NPC:GetComponent("UIEvent");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_CloseNpc);
  --endregion

  --region order
  scrollContent_Order = uiController:FindScrollContent("ScrollContent_Order");
  scrollContent_Order.onInitialize = this.OnInitialize_ScrollContent_Order;
  scrollContent_Order.onItemChange = this.OnItemChange_ScrollContent_Order;
  scrollContent_Order:Initialize(1);

  text_OrderCount = uiController:FindText("Text_OrderCount");
  text_OrderCountTime = uiController:FindText("Text_OrderCountTime");
  text_GoldOrderCount = uiController:FindText("Text_GoldOrderCount");
  text_GoldOrderCountTime = uiController:FindText("Text_GoldOrderCountTime");
  text_DrumCountTime = uiController:FindText("Text_DrumCountTime");

  gameObject_Plus = uiController:FindGameObject("Image_Plus");

  tempEvent = gameObject_Plus:GetComponent("UIEvent");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_PlusDrum);

  local tempRoot = uiController:FindGameObject("DrumRoot").transform;

  for i = 1, ArmyBattle.MAX_DRUM_COUNT do
    drums[i] = tempRoot:Find(string.Concat("Image_Drum (", i ,")")):Find("Image_Drum"):GetComponent("Image");
  end
  --endregion

  --region history
  scrollContent_History = uiController:FindScrollContent("ScrollContent_History");
  scrollContent_History.onInitialize = this.OnInitialize_ScrollContent_History;
  scrollContent_History.onItemChange = this.OnItemChange_ScrollContent_History;
  scrollContent_History:Initialize(1);
  --endregion

  --region result
  scrollContent_Leaderboard = uiController:FindScrollContent("ScrollContent_Leaderboard");
  scrollContent_Leaderboard.onInitialize = this.OnInitialize_ScrollContent_Leaderboard;
  scrollContent_Leaderboard.onItemChange = this.OnItemChange_ScrollContent_Leaderboard;
  scrollContent_Leaderboard:Initialize(1);

  scrollContent_Prize = uiController:FindScrollContent("ScrollContent_Prize");
  scrollContent_Prize.onInitialize = this.OnInitialize_ScrollContent_Prize;
  scrollContent_Prize.onItemChange = this.OnItemChange_ScrollContent_Prize;
  scrollContent_Prize:Initialize(1);

  gameObject_Award = uiController:FindGameObject("Award");
  gameObject_Board = uiController:FindGameObject("Board");
  gameObject_Prize = uiController:FindGameObject("Image_PrizeBG");
  gameObject_Result = uiController:FindGameObject("AwardResult");
  gameObject_Default = uiController:FindGameObject("Default");
  gameObject_Info = uiController:FindGameObject("Info");

  tempRoot = uiController:FindGameObject("Image_Result").transform;

  for i = 1, 4 do
    resultImg[i] = tempRoot:Find(string.Concat("RawImage_(", i, ")")):GetComponent("RawImage");
  end

  text_Empty = uiController:FindText("Text_Empty");
  text_Page = uiController:FindText("Text_Page");
  text_Score = uiController:FindText("Text_Score");
  text_Status = uiController:FindText("Text_Status");
  text_Rank = uiController:FindText("Text_Rank");
  text_Result = uiController:FindText("Text_ Result");

  tempRoot = uiController:FindGameObject("ItemRoot").transform;

  for i = 1, 5 do
    award[i] = {};
    award[i].item = UIItemController.New(tempRoot:Find(string.Concat("Image_ItemBG (", i, ")")));
    award[i].BG = tempRoot:Find(string.Concat("Image_ItemBG (", i, ")")):GetComponent("Image");
  end

  tempEvent = uiController:FindEvent("Image_Prev");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchPage);
  tempEvent.parameter = -1;
  tempEvent = uiController:FindEvent("Image_Next");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchPage);
  tempEvent.parameter = 1;
  --endregion

  for i = 1, table.Count(EArmyBattleFunction) do
  functions[i].gameObject:SetActive(false);
  end
end

function UIArmyBattleFunction.OnOpen(clickFunction, var)
  if clickFunction == nil then
    logError("Open false");
    return false;
  end

  currentFunction = clickFunction;
  transform_FrameRect = functions[clickFunction].frameRects;
  functions[clickFunction].gameObject:SetActive(true);

  if currentFunction == EArmyBattleFunction.Team then
    title = string.Get(24256);
  elseif currentFunction == EArmyBattleFunction.Order then
    title = string.Get(24244);
    orders = var.orders;
    drumTime = var.drumTime;
    orderTime = var.orderTime;
    goldOrderTime = var.goldOrderTime;
  elseif currentFunction == EArmyBattleFunction.History then
    title = string.Get(24245);
    history = var;
  elseif currentFunction == EArmyBattleFunction.Result then
    title = string.Get(24246);

    tagData[1] = {string.Get(24292)};
    tagData[2] = {string.Get(24392)};
    tagData[3] = {string.Get(24293)};

    if var ~= nil then
      board = var;
    end

    if table.Count(board) == 0 then
      totalPage = 1;
    else
      totalPage = math.floor(table.Count(board) / 5);

      if table.Count(board) % 5 ~= 0 then
        totalPage = totalPage + 1;
      end
    end

    currentPage = 1;
  end

  this.UpdateUI(true);

  return true;
end

function UIArmyBattleFunction.OnVisible(visible)
  if visible then

    UIFrame.Active(transform_FrameRect, title, this.OnClick_Close, this.OnClick_Help, this.OnClick_Tag, tagData, currentMainTag);

    this.UpdateUI();
  end
end

function UIArmyBattleFunction.OnClose()
  functions[currentFunction].gameObject:SetActive(false);
  table.Clear(tagData);
  currentMainTag = EArmyResultMainTag.Award;
  currentPage = 1;

  for i = 0, table.Count(scrollItems_Order) - 1 do
    if CGTimer.ContainsListener(scrollItems_Order[i].timerEvent) then
      CGTimer.RemoveListener(scrollItems_Order[i].timerEvent);
    end
  end

  if CGTimer.ContainsListener(this.UpdateDrumTime) then
    CGTimer.RemoveListener(this.UpdateDrumTime);
  end

  if CGTimer.ContainsListener(this.UpdateOrderTime) then
    CGTimer.RemoveListener(this.UpdateOrderTime);
  end

  if CGTimer.ContainsListener(this.UpdateGoldOrderTime) then
    CGTimer.RemoveListener(this.UpdateGoldOrderTime);
  end

  return true;
end

  --region onClick
function UIArmyBattleFunction.OnClick_Tag(mainTag)
  if table.Count(tagData) == 0 then return false end

  currentMainTag = mainTag;

  this.UpdateUI(true);

  return true;
end


function UIArmyBattleFunction.OnClick_Help(checkShow)
  if currentFunction ~= EArmyBattleFunction.Order then return false end

  if checkShow ~= true then
    UI.Open(UITutorialHint, 0, this, title, string.Get(24528));
  end

  return true;
end

function UIArmyBattleFunction.OnClick_Close()
  UI.Close(UIArmyBattleFunction);

  return true;
end

function UIArmyBattleFunction.OnClick_RemoveSkill(uiEvent)
  local teamIndex = math.floor(uiEvent.parameter / 100);
  local skillIndex = uiEvent.parameter % 10;

  teamUIs[teamIndex].skills[skillIndex].skillId = 0;

  this.UpdateTeam();
end

function UIArmyBattleFunction.OnClick_ChooseNpc(uiEvent)
  currentTeamIndex = uiEvent.parameter;

  --C:171-002 <查詢可用武將>
  Network.Send(171, 2);
end

function UIArmyBattleFunction.OnClick_NpcSelect(uiEvent)
  local oriTeamIndex = this.GetTeamIndex(uiEvent.parameter.npcId);

  --武將本來就在隊伍中
  if oriTeamIndex ~= nil then
    local tempTeamInfo = nil;

    --目標位置有其他武將
    if teamUIs[currentTeamIndex].npcId ~= 0 then
      if oriTeamIndex == currentTeamIndex then
        this.ClearTeamInfo(oriTeamIndex);
      else
        --將目標位置武將暫存
        tempTeamInfo = {};
        tempTeamInfo.npcId = teamUIs[currentTeamIndex].npcId;
        tempTeamInfo.level = teamUIs[currentTeamIndex].level;
        tempTeamInfo.hp = teamUIs[currentTeamIndex].hp;
        tempTeamInfo.extraSkills = teamUIs[currentTeamIndex].extraSkills;
        tempTeamInfo.skills = {};
        for j = 1, DataFight.maxTeamSkill do
          tempTeamInfo.skills[j] = teamUIs[currentTeamIndex].skills[j].skillId;
        end
      end
    end

    --將本來位置武將移至目標
    teamUIs[currentTeamIndex].npcId = teamUIs[oriTeamIndex].npcId;
    teamUIs[currentTeamIndex].level = teamUIs[oriTeamIndex].level;
    teamUIs[currentTeamIndex].hp = teamUIs[oriTeamIndex].hp;
    teamUIs[currentTeamIndex].extraSkills = teamUIs[oriTeamIndex].extraSkills;
    for j = 1, DataFight.maxTeamSkill do
      teamUIs[currentTeamIndex].skills[j].skillId = teamUIs[oriTeamIndex].skills[j].skillId;
    end

    if tempTeamInfo ~= nil then
      --將暫存武將移回本來位置
      teamUIs[oriTeamIndex].npcId = tempTeamInfo.npcId;
      teamUIs[oriTeamIndex].level = tempTeamInfo.level;
      teamUIs[oriTeamIndex].hp = tempTeamInfo.hp;
      teamUIs[oriTeamIndex].extraSkills = tempTeamInfo.extraSkills;
      for j = 1, DataFight.maxTeamSkill do
        teamUIs[oriTeamIndex].skills[j].skillId = tempTeamInfo.skills[j];
      end
    else
      --將本來位置武將資料清空
      this.ClearTeamInfo(oriTeamIndex);
    end

  else
    this.ClearTeamInfo(currentTeamIndex);

    teamUIs[currentTeamIndex].npcId = uiEvent.parameter.npcId;
    teamUIs[currentTeamIndex].level = uiEvent.parameter.level;
    teamUIs[currentTeamIndex].hp = uiEvent.parameter.hp;
    teamUIs[currentTeamIndex].extraSkills = uiEvent.parameter.extraSkills;
  end

  gameObject_NPC:SetActive(false);

  this.UpdateTeam();
end

function UIArmyBattleFunction.OnClick_CloseNpc()
  gameObject_NPC:SetActive(false);
end

function UIArmyBattleFunction.OnClick_ChooseSkill(uiEvent)
  local teamIndex = math.floor(uiEvent.parameter / 100);

  if teamIndex > 0 and teamUIs[teamIndex].npcId == 0 then return end

  local skillIndex = uiEvent.parameter % 10;

  UI.Open(UISkillPick,
    this.name,
    function(skillId)
      if skillId < 0 then return end
      if skillDatas[skillId] == nil then return end

      teamUIs[teamIndex].skills[skillIndex].skillId = skillId;

      if UI.IsVisible(UISkillPick) then
        UI.Close(UISkillPick);
      end

      this.UpdateTeam();
    end,
    teamUIs[teamIndex].npcId,
    teamUIs[teamIndex].extraSkills
  );
end

function UIArmyBattleFunction.OnClick_RemoveSkill(uiEvent)
  local teamIndex = math.floor(uiEvent.parameter / 100);
  local skillIndex = uiEvent.parameter % 10;

  teamUIs[teamIndex].skills[skillIndex].skillId = 0;

  this.UpdateTeam();
end

function UIArmyBattleFunction.OnClick_SaveTeam()
  --C:171-003 <設定防守隊伍> +數量(1) <<+位置索引(1) +NPCID(2) +技能(2)*5>>
  sendBuffer:Clear();
  sendBuffer:WriteByte(DataFight.maxTeamNpc + 1);
  for i = 0, DataFight.maxTeamNpc do
    sendBuffer:WriteByte(i);
    sendBuffer:WriteUInt16(teamUIs[i].npcId);
    for j = 1, DataFight.maxTeamSkill do
      sendBuffer:WriteUInt16(teamUIs[i].skills[j].skillId);
    end
  end

  Network.Send(171, 3, sendBuffer);
end

function UIArmyBattleFunction.OnClick_ChooseOrder(uiEvent)
  local data = uiEvent.parameter;

  if data.sellId ~= 0 then
    UISell.Launch(data.sellId, nil, data.id);
  else
    if data.drumCount > ArmyBattle.nowDrumCount then
      ShowCenterMessage(string.Get(24358));
      return;
    end

    ShowCheckMessage(
      function(result)
        if result then
          --C:171-019 <使用戰令> +戰令ID(1)
          sendBuffer:Clear();
          sendBuffer:WriteByte(data.id);
          Network.Send(171, 19, sendBuffer);
        end
      end,
      string.Get(24365)
    );
  end
end

function UIArmyBattleFunction.OnClick_ChangeOrder(uiEvent)
  local order = uiEvent.parameter;
  changeOrder = order.id;

  if order.changeSellId ~= 0 then
    UISell.Launch(order.changeSellId, nil ,changeOrder);
  else
    ShowCheckMessage(
      function(result)
        if result then
          --C:171-029 <刷新戰令> +戰令ID(1)
          sendBuffer:Clear();
          sendBuffer:WriteByte(changeOrder);
          Network.Send(171, 29, sendBuffer);
        end
      end,
      string.Get(24282)
    );
  end
end

function UIArmyBattleFunction.OnClick_PlusDrum()
  UISell.Launch(64);
end

function UIArmyBattleFunction.OnClick_SwitchPage(uiEvent)
  currentPage = currentPage + uiEvent.parameter;

  if currentPage > totalPage then
    currentPage = totalPage;
  end

  if currentPage < 1 then
    currentPage = 1;
  end

  this.UpdateResult();
end
  --endregion

  --region scrollContent
function UIArmyBattleFunction.OnInitialize_ScrollContent_Npc(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Npc[itemIndex] = {};
    scrollItems_Npc[itemIndex].uiCareer = UICareerController.New(scrollItems[itemIndex]:Find("Career"));
    scrollItems_Npc[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_Npc[itemIndex].text_Level = scrollItems[itemIndex]:Find("Text_Lv"):GetComponent("Text");
    scrollItems_Npc[itemIndex].text_Hp = scrollItems[itemIndex]:Find("Text_Hp"):GetComponent("Text");
    scrollItems_Npc[itemIndex].event_Select = scrollItems[itemIndex]:Find("Image_Select"):GetComponent("UIEvent");
    scrollItems_Npc[itemIndex].event_Select:SetListener(EventTriggerType.PointerClick, this.OnClick_NpcSelect);
    scrollItems_Npc[itemIndex].image_Select = scrollItems[itemIndex]:Find("Image_Select"):GetComponent("Image");
    scrollItems_Npc[itemIndex].text_Select = scrollItems_Npc[itemIndex].image_Select.transform:Find("Text_"):GetComponent("Text");
  end
end

function UIArmyBattleFunction.OnItemChange_ScrollContent_Npc(dataIndex, itemIndex)
  local npcInfo = ArmyBattle.npcList[dataIndex + 1];
  if npcInfo == nil then return false end

  local npcData = npcDatas[npcInfo.npcId];
  if npcData == nil then return false end

  scrollItems_Npc[itemIndex].uiCareer:SetCareer(npcData.turn, npcData.element, 0, true);
  scrollItems_Npc[itemIndex].text_Name.text = npcData.name;
  scrollItems_Npc[itemIndex].text_Level.text = npcInfo.level;
  scrollItems_Npc[itemIndex].text_Hp.text = npcInfo.hp;
  scrollItems_Npc[itemIndex].event_Select.parameter = npcInfo;

  if this.GetTeamIndex(npcInfo.npcId) ~= nil then
    scrollItems_Npc[itemIndex].image_Select.sprite = UI.GetSprite("but167D");
    if this.GetTeamIndex(npcInfo.npcId) ~= currentTeamIndex then
      scrollItems_Npc[itemIndex].text_Select.text = string.Get(21669);
    else
      scrollItems_Npc[itemIndex].text_Select.text = string.Get(20110);
    end
  else
    scrollItems_Npc[itemIndex].image_Select.sprite = UI.GetSprite("but064D");
    scrollItems_Npc[itemIndex].text_Select.text = string.Get(50316);
  end

  return true;
end

function UIArmyBattleFunction.OnInitialize_ScrollContent_Order(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Order[itemIndex] = {};
    scrollItems_Order[itemIndex].orderIdx = scrollItems[itemIndex]:Find("Image_"):Find("Text_Index"):GetComponent("Text");
    scrollItems_Order[itemIndex].title = scrollItems[itemIndex]:Find("Text_Title"):GetComponent("Text");
    scrollItems_Order[itemIndex].description = scrollItems[itemIndex]:Find("Text_Description"):GetComponent("Text");
    scrollItems_Order[itemIndex].highLight = scrollItems[itemIndex]:Find("Image_HighLight"):GetComponent("Image");
    scrollItems_Order[itemIndex].drumsRoot = scrollItems[itemIndex]:Find("DrumRoot").gameObject;
    scrollItems_Order[itemIndex].drums = {};
    for i = 1, 5 do
      scrollItems_Order[itemIndex].drums[i] = scrollItems_Order[itemIndex].drumsRoot.transform:Find(string.Concat("Image_Drum (", i, ")")):GetComponent("Image");
    end

    scrollItems_Order[itemIndex].goldRoot = scrollItems[itemIndex]:Find("GoldRoot").gameObject;
    scrollItems_Order[itemIndex].goldCount = scrollItems_Order[itemIndex].goldRoot.transform:Find("Text_Count"):GetComponent("Text");

    local subRoot = scrollItems[itemIndex]:Find("UseRoot");
    scrollItems_Order[itemIndex].usage = subRoot:Find("Text_Usage"):GetComponent("Text");
    scrollItems_Order[itemIndex].time = subRoot:Find("Text_Time"):GetComponent("Text");
    scrollItems_Order[itemIndex].choose = subRoot:Find("Image_Choose").gameObject;
    scrollItems_Order[itemIndex].change = subRoot:Find("Image_Change").gameObject;

    scrollItems_Order[itemIndex].chooseEvent = scrollItems_Order[itemIndex].choose:GetComponent("UIEvent");
    scrollItems_Order[itemIndex].changeEvent = scrollItems_Order[itemIndex].change:GetComponent("UIEvent");
    scrollItems_Order[itemIndex].chooseEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ChooseOrder);
    scrollItems_Order[itemIndex].changeEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_ChangeOrder);
  end
end

function UIArmyBattleFunction.OnItemChange_ScrollContent_Order(dataIndex, itemIndex)
  if orders[dataIndex + 1] ~= nil then
    local temp = strongholdWarDatas[orders[dataIndex + 1].id];

    if temp ~= nil then
      local isGold = temp.sellId ~= 0;

      scrollItems_Order[itemIndex].orderIdx.text = temp.id;
      scrollItems_Order[itemIndex].title.text = string.Get(temp.name);
      scrollItems_Order[itemIndex].description.text = string.Get(temp.description);
      scrollItems_Order[itemIndex].time.enabled = orders[dataIndex + 1].use == 1;
      scrollItems_Order[itemIndex].highLight.enabled = isGold;

      scrollItems_Order[itemIndex].choose:SetActive(ArmyBattle.IsChiefOrSub() and orders[dataIndex + 1].use == 0);
      scrollItems_Order[itemIndex].change:SetActive(ArmyBattle.IsChiefOrSub() and orders[dataIndex + 1].use == 0);
      scrollItems_Order[itemIndex].chooseEvent.parameter = temp;
      scrollItems_Order[itemIndex].changeEvent.parameter = temp;
      scrollItems_Order[itemIndex].drumsRoot:SetActive(not isGold);
      scrollItems_Order[itemIndex].goldRoot:SetActive(isGold);

      scrollItems_Order[itemIndex].timerEvent = function()
        if orders[dataIndex + 1].time == 0 or orders[dataIndex + 1].time == nil then
          if CGTimer.ContainsListener(scrollItems_Order[itemIndex].timerEvent) then
            CGTimer.RemoveListener(scrollItems_Order[itemIndex].timerEvent);
          end

          return;
        end

        scrollItems_Order[itemIndex].time.text = string.GetSecondSymbol(math.ceil(DateTime.Subtract(orders[dataIndex + 1].time, CGTimer.serverTime).TotalSeconds));

        if not ArmyBattle.CheckTime(orders[dataIndex + 1].time) then
          orders[dataIndex + 1].use = 0;

          if CGTimer.ContainsListener(scrollItems_Order[itemIndex].timerEvent) then
            CGTimer.RemoveListener(scrollItems_Order[itemIndex].timerEvent);
          end
        end
      end;

      if orders[dataIndex + 1].use == 1 then
        scrollItems_Order[itemIndex].usage.enabled = true;
        scrollItems_Order[itemIndex].usage.text = string.Get(99661);

        if not CGTimer.ContainsListener(scrollItems_Order[itemIndex].timerEvent) then
          CGTimer.AddListener(scrollItems_Order[itemIndex].timerEvent, 1);
        end
      elseif orders[dataIndex + 1].use == 0 then
        scrollItems_Order[itemIndex].usage.enabled = not ArmyBattle.IsChiefOrSub();
        scrollItems_Order[itemIndex].usage.text = string.Get(24284);
      end

      if not isGold then
        for i = 1, 5 do
          scrollItems_Order[itemIndex].drums[i].enabled = temp.drumCount >= i;
        end
      else
        scrollItems_Order[itemIndex].goldCount.text = string.Concat("x", temp.drumCount);
      end

      return true;
    end
  else
    return false;
  end
end

function UIArmyBattleFunction.OnInitialize_ScrollContent_History(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_History[itemIndex] = {};
    scrollItems_History[itemIndex].hpSlider = scrollItems[itemIndex]:Find("Slider_Hp"):GetComponent("UISlider");
    scrollItems_History[itemIndex].hpColor = scrollItems_History[itemIndex].hpSlider.transform:Find("Fill Area"):Find("Fill"):GetComponent("Image");
    scrollItems_History[itemIndex].flag = scrollItems_History[itemIndex].hpSlider.transform:Find("Image_Flag"):GetComponent("Image");
    scrollItems_History[itemIndex].index = scrollItems_History[itemIndex].flag.transform:Find("Text_Life"):GetComponent("Text");
    scrollItems_History[itemIndex].cityResult = scrollItems[itemIndex]:Find("Text_CityResult"):GetComponent("Text");
    scrollItems_History[itemIndex].cityResultOutLine = scrollItems_History[itemIndex].cityResult:GetComponent("Outline8");
    scrollItems_History[itemIndex].time = scrollItems[itemIndex]:Find("Text_Time"):GetComponent("Text");
    scrollItems_History[itemIndex].order = scrollItems[itemIndex]:Find("Order").gameObject;
    scrollItems_History[itemIndex].orderIdx = scrollItems[itemIndex]:Find("Order"):Find("Image_"):Find("Text_Index"):GetComponent("Text");
    scrollItems_History[itemIndex].orderName = scrollItems[itemIndex]:Find("Order"):Find("Text_Title"):GetComponent("Text");

    scrollItems_History[itemIndex].result = scrollItems[itemIndex]:Find("Text_Result"):GetComponent("Text");
    scrollItems_History[itemIndex].resultOutLine = scrollItems_History[itemIndex].result:GetComponent("Outline8");
    scrollItems_History[itemIndex].player = {};

    for i = 1, 2 do
      scrollItems_History[itemIndex].player[i] = {};
      local root = scrollItems[itemIndex]:Find(string.Concat("Player (", i, ")"));
      scrollItems_History[itemIndex].player[i].root = root.gameObject;
      scrollItems_History[itemIndex].player[i].uiCareer = UICareerController.New(root:Find("Career"));
      scrollItems_History[itemIndex].player[i].lv = root:Find("Text_T9_"):Find("Text_LvValue"):GetComponent("Text");
      scrollItems_History[itemIndex].player[i].name = root:Find("Text_Name"):GetComponent("Text");
    end
  end
end

function UIArmyBattleFunction.OnItemChange_ScrollContent_History(dataIndex, itemIndex)
  local data = history[dataIndex + 1];

  scrollItems_History[itemIndex].hpSlider.gameObject:SetActive(data.kind == 1);
  scrollItems_History[itemIndex].order:SetActive(data.kind == 2);

  for i = 1, 2 do
    scrollItems_History[itemIndex].player[i].root:SetActive(data.kind == 1);
  end

  scrollItems_History[itemIndex].time.text = DateTime.FromOADate(data.time):ToString("MM/dd HH:mm");

  if data.kind == 1 then
    scrollItems_History[itemIndex].hpColor.sprite = ArmyBattle.GetHPSprite(data.pointMoral);
    scrollItems_History[itemIndex].hpSlider.value = data.life / ArmyBattle.GetTotalLife(data.id);
    scrollItems_History[itemIndex].flag.sprite = ArmyBattle.GetFlagSprite(data.pointMoral);
    scrollItems_History[itemIndex].index.text = data.id;
    scrollItems_History[itemIndex].result.text = string.Get(24287);
    scrollItems_History[itemIndex].resultOutLine.effectColor = ArmyBattle.GetOutlineColor(data.moral);
    scrollItems_History[itemIndex].cityResultOutLine.effectColor = ArmyBattle.GetOutlineColor(data.moral);
    scrollItems_History[itemIndex].cityResult.text = ArmyBattle.GetOrderText(data);

    for i = 1, 2 do
      scrollItems_History[itemIndex].player[i].uiCareer:SetCareer(data.playerInfo[i].turn, data.playerInfo[i].element, data.playerInfo[i].career, false, data.playerInfo[i].turn3Element);
      scrollItems_History[itemIndex].player[i].lv.text = data.playerInfo[i].level;
      scrollItems_History[itemIndex].player[i].name.text = data.playerInfo[i].name;
    end
  elseif data.kind == 2 then
    local tempOrder = strongholdWarDatas[data.order];
    scrollItems_History[itemIndex].orderIdx.text = data.order;
    scrollItems_History[itemIndex].orderName.text = string.Get(tempOrder.name);
    scrollItems_History[itemIndex].resultOutLine.effectColor = Color.Black;
    scrollItems_History[itemIndex].result.text = string.Get(tempOrder.description);
    scrollItems_History[itemIndex].cityResultOutLine.effectColor = ArmyBattle.GetOutlineColor(data.moral);
    scrollItems_History[itemIndex].cityResult.text = ArmyBattle.GetOrderText(data);
  end


  return true;
end

function UIArmyBattleFunction.OnInitialize_ScrollContent_Leaderboard(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Leaderboard[itemIndex] = {};
    scrollItems_Leaderboard[itemIndex].image_Rank = scrollItems[itemIndex]:Find("Image_Rank"):GetComponent("Image");
    scrollItems_Leaderboard[itemIndex].text_Rank = scrollItems[itemIndex]:Find("Text_Rank"):GetComponent("Text");
    scrollItems_Leaderboard[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Level"):Find("Text_Name"):GetComponent("Text");
    scrollItems_Leaderboard[itemIndex].text_ArmyName = scrollItems[itemIndex]:Find("Text_ArmyName"):GetComponent("Text");
    scrollItems_Leaderboard[itemIndex].uiCareer = UICareerController.New(scrollItems[itemIndex]:Find("Career"));
    scrollItems_Leaderboard[itemIndex].text_Level = scrollItems[itemIndex]:Find("Text_Level"):GetComponent("Text");
    scrollItems_Leaderboard[itemIndex].text_Score = scrollItems[itemIndex]:Find("Text_Score"):GetComponent("Text");
    scrollItems_Leaderboard[itemIndex].item = UIItemController.New(scrollItems[itemIndex]:Find("Image_ItemBG"));
    scrollItems_Leaderboard[itemIndex].itemBG = scrollItems[itemIndex]:Find("Image_ItemBG"):GetComponent("Image");
  end
end

function UIArmyBattleFunction.OnItemChange_ScrollContent_Leaderboard(dataIndex, itemIndex)
  local data = pageOfBoard[dataIndex + 1];
  local awardTemp = ArmyBattle.award[ArmyAwardCon.Board][data.rank];

  if data ~= nil then
    if data.rank <= 3 then
      scrollItems_Leaderboard[itemIndex].image_Rank.sprite = UI.GetRankSprite(data.rank);
      scrollItems_Leaderboard[itemIndex].text_Rank.text = "";
    else
      scrollItems_Leaderboard[itemIndex].text_Rank.text = data.rank;
    end

    scrollItems_Leaderboard[itemIndex].image_Rank.enabled = data.rank <= 3;
    scrollItems_Leaderboard[itemIndex].text_Name.text = data.playerInfo.name;
    scrollItems_Leaderboard[itemIndex].text_ArmyName.text = data.name;
    scrollItems_Leaderboard[itemIndex].uiCareer:SetCareer(data.playerInfo.turn, data.playerInfo.element, data.playerInfo.career, false, data.playerInfo.turn3Element);
    scrollItems_Leaderboard[itemIndex].text_Level.text = data.playerInfo.level;
    scrollItems_Leaderboard[itemIndex].text_Score.text = string.format("【%d】", data.score);
    scrollItems_Leaderboard[itemIndex].item:SetItem(awardTemp.award, awardTemp.quant);
    scrollItems_Leaderboard[itemIndex].itemBG.enabled = awardTemp.award ~= 0;
  end


  return true;
end

function UIArmyBattleFunction.OnInitialize_ScrollContent_Prize(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Prize[itemIndex] = {};
    scrollItems_Prize[itemIndex].text_Description = scrollItems[itemIndex]:Find("Text_Description"):GetComponent("Text");
    scrollItems_Prize[itemIndex].item = UIItemController.New(scrollItems[itemIndex]:Find("Image_ItemBG"));
  end
end

function UIArmyBattleFunction.OnItemChange_ScrollContent_Prize(dataIndex, itemIndex)
  local temp = ArmyBattle.award[ArmyAwardCon.Points][dataIndex + 1];

  if temp ~= nil then
    scrollItems_Prize[itemIndex].text_Description.text = string.Concat(string.Get(24297), temp.value);
    if ArmyBattle.playerScore < temp.value then
      scrollItems_Prize[itemIndex].text_Description.color = Color.Black;
    else
      scrollItems_Prize[itemIndex].text_Description.color = Color.DarkGreen;
    end

    scrollItems_Prize[itemIndex].item:SetItem(temp.award, temp.quant);
  else
    scrollItems_Prize[itemIndex].item:SetItem(0);
  end
  return true;
end
--endregion

  --region update
function UIArmyBattleFunction.UpdateUI(reset)
  this.UpdateHistory(reset);
  this.UpdateTeam(reset);
  this.UpdateOrder(reset);
  this.UpdateResult();
end

function UIArmyBattleFunction.UpdateTeam(reset)
  if currentFunction ~= EArmyBattleFunction.Team then return end
  RedDot.Remove(ERedDot.ArmyBattleTeam);

  if reset then
    for i = 0, DataFight.maxTeamNpc do
      if i == 0 then
        teamUIs[i].npcId = ArmyBattle.defenceTeamInfos[i].npcId;
        teamUIs[i].level = ArmyBattle.defenceTeamInfos[i].level;
        teamUIs[i].hp = ArmyBattle.defenceTeamInfos[i].hp;
        teamUIs[i].extraSkills = ArmyBattle.defenceTeamInfos[i].extraSkills;
        for j = 1, DataFight.maxTeamSkill do
          teamUIs[i].skills[j].skillId = ArmyBattle.defenceTeamInfos[i].skills[j];
        end
      elseif ArmyBattle.defenceTeamInfos[i].npcId ~= 0 then
        teamUIs[i].npcId = ArmyBattle.defenceTeamInfos[i].npcId;
        teamUIs[i].level = ArmyBattle.defenceTeamInfos[i].level;
        teamUIs[i].hp = ArmyBattle.defenceTeamInfos[i].hp;
        teamUIs[i].extraSkills = ArmyBattle.defenceTeamInfos[i].extraSkills;
        for j = 1, DataFight.maxTeamSkill do
          teamUIs[i].skills[j].skillId = ArmyBattle.defenceTeamInfos[i].skills[j];
        end
      else
        this.ClearTeamInfo(i);
      end
    end
  end

  for i = 0, DataFight.maxTeamNpc do
    local npcData = npcDatas[teamUIs[i].npcId];

    teamUIs[i].text_Level.gameObject:SetActive(i == 0 or npcData ~= nil);
    teamUIs[i].text_Hp.gameObject:SetActive(i == 0 or npcData ~= nil);
    teamUIs[i].gameObject_Empty.gameObject:SetActive(npcData == nil and i ~= 0);

    if i == 0 then
      teamUIs[i].uiCareer:SetCareer(Role.player:GetAttribute(EAttribute.Turn), Role.player:GetAttribute(EAttribute.Element), Role.player:GetAttribute(EAttribute.Career), false, Role.player:GetAttribute(EAttribute.Turn3Element));
      teamUIs[i].text_Name.text = Role.player.name;
      teamUIs[i].text_Level.text = Role.player:GetAttribute(EAttribute.Lv);
      teamUIs[i].text_Hp.text = Role.player:GetAttribute(EAttribute.MaxHp);
      for j = 1, DataFight.maxTeamSkill do
        local skillData = skillDatas[teamUIs[i].skills[j].skillId];

        teamUIs[i].skills[j].rawImage_Icon.enabled = skillData ~= nil;
        teamUIs[i].skills[j].event_Remove.gameObject:SetActive(skillData ~= nil);

        if skillData ~= nil then
          skillData:SetIcon(teamUIs[i].skills[j].rawImage_Icon);
        end
      end
    elseif npcData ~= nil then
      teamUIs[i].uiCareer:SetCareer(npcData.turn, npcData.element, 0, true);
      teamUIs[i].text_Name.text = npcData.name;
      teamUIs[i].text_Level.text = teamUIs[i].level;
      teamUIs[i].text_Hp.text = teamUIs[i].hp;
      teamUIs[i].text_ChooseNpc.text = string.Get(22676);

      for j = 1, DataFight.maxTeamSkill do
        local skillData = skillDatas[teamUIs[i].skills[j].skillId];

        teamUIs[i].skills[j].rawImage_Icon.enabled = skillData ~= nil;
        teamUIs[i].skills[j].event_Remove.gameObject:SetActive(skillData ~= nil);

        if skillData ~= nil then
          skillData:SetIcon(teamUIs[i].skills[j].rawImage_Icon);
        end
      end
    else
      teamUIs[i].uiCareer:SetCareer();
      teamUIs[i].text_Name.text = "";
      teamUIs[i].text_ChooseNpc.text = string.Get(22745);

      for j = 1, DataFight.maxTeamSkill do
        teamUIs[i].skills[j].rawImage_Icon.enabled = false;
        teamUIs[i].skills[j].event_Remove.gameObject:SetActive(false);
      end
    end
  end
end

function UIArmyBattleFunction.UpdateNpc()
  if currentFunction ~= EArmyBattleFunction.Team then return end

  scrollContent_Npc:Reset(table.Count(ArmyBattle.npcList));
  gameObject_NPC:SetActive(true);
end

function UIArmyBattleFunction.UpdateHistory(reset)
  if currentFunction ~= EArmyBattleFunction.History then return end

  if reset then
    scrollContent_History:Reset(table.Count(history));
  else
    scrollContent_History:Refresh(table.Count(history));
  end
end

function UIArmyBattleFunction.UpdateOrder(reset)
  if currentFunction ~= EArmyBattleFunction.Order then return end

  for i = 1, ArmyBattle.MAX_DRUM_COUNT do
      drums[i].enabled = (i <= ArmyBattle.nowDrumCount);
  end

  text_OrderCount.text = string.Concat(ArmyBattle.playerOrderCount, "/", ArmyBattle.MAX_ORDER_COUNT);
  text_GoldOrderCount.text = string.Concat(ArmyBattle.GetGoldOrderCount(orders), "/", ArmyBattle.MAX_GOLD_ORDER_COUNT);

  gameObject_Plus:SetActive(ArmyBattle.IsChiefOrSub() and
    ArmyBattle.nowDrumCount < ArmyBattle.MAX_DRUM_COUNT and
    ArmyBattle.battleStatus == ArmyBattleStatus.Attack);

  if reset then
    scrollContent_Order:Reset(table.Count(orders));
  else
    scrollContent_Order:Refresh(table.Count(orders));
  end

  if not CGTimer.ContainsListener(this.UpdateDrumTime) then
    CGTimer.AddListener(this.UpdateDrumTime, 1, true)
  end

  if not CGTimer.ContainsListener(this.UpdateOrderTime) then
    CGTimer.AddListener(this.UpdateOrderTime, 1, true)
  end

  if not CGTimer.ContainsListener(this.UpdateGoldOrderTime) then
    CGTimer.AddListener(this.UpdateGoldOrderTime, 1, true)
  end
end

function UIArmyBattleFunction.UpdateResult()
  if currentFunction ~= EArmyBattleFunction.Result then return end

  gameObject_Award:SetActive(currentMainTag == EArmyResultMainTag.Award);
  gameObject_Board:SetActive(currentMainTag == EArmyResultMainTag.Board);
  gameObject_Prize:SetActive(currentMainTag == EArmyResultMainTag.Prize);
  gameObject_Info:SetActive(currentMainTag ~= EArmyResultMainTag.Award);

  if currentMainTag == EArmyResultMainTag.Award then
    text_Status.text = string.Get(24300);
    gameObject_Result:SetActive(ArmyBattle.battleStatus == ArmyBattleStatus.Result);
    gameObject_Default:SetActive(ArmyBattle.battleStatus ~= ArmyBattleStatus.Result);

    local awardTemp = {};

    if ArmyBattle.battleStatus == ArmyBattleStatus.Result then
      for i = 1, table.Count(resultImg) do
        if ArmyBattle.playerResult == ArmyBattleResult.Win then
          resultImg[i].color = Color.White;
          awardTemp = ArmyBattle.award[ArmyAwardCon.Win];
          text_Result.text = string.Get(24298);
        else
          resultImg[i].color = Color.Gray;
          awardTemp = ArmyBattle.award[ArmyAwardCon.Lose];
          text_Result.text = string.Get(24299);
        end
      end

      for i = 1, 5 do
        award[i].BG.enabled = table.Count(awardTemp) >= i;
        if awardTemp[i] ~= nil then
          award[i].item:SetItem(awardTemp[i].award, awardTemp[i].quant);
        else
          award[i].item:SetItem(0);
        end
      end
    end
  elseif currentMainTag == EArmyResultMainTag.Board then
    this.GetBoardPage();
    local score = "";
    local rank = "";

    if table.Count(board) == 0 or ArmyBattle.playerScore == 0 then
      rank = "---";
    else
      rank = ArmyBattle.playerRank;
    end

    if table.Count(board) == 0 then
      score =  string.format("【%s】", "---");
    else
      score = string.format("【%d】", ArmyBattle.playerScore);
    end

    text_Score.text = score;
    text_Rank.text = rank;
    text_Page.text = string.format("%d/%d", currentPage, totalPage);
    scrollContent_Leaderboard:Reset(table.Count(pageOfBoard));
    text_Empty.enabled = table.Count(board) == 0;

  elseif currentMainTag == EArmyResultMainTag.Prize then
    text_Score.text = string.format("【%d】", ArmyBattle.playerScore);
    scrollContent_Prize:Reset(table.Count(ArmyBattle.award[ArmyAwardCon.Points]));
  end
end
  --endregion

function UIArmyBattleFunction.GetTeamIndex(npcId)
  for i = 1, DataFight.maxTeamNpc do
    if teamUIs[i].npcId == npcId then
      return i;
    end
  end

  return nil;
end

function UIArmyBattleFunction.ClearTeamInfo(teamIndex)
  teamUIs[teamIndex].npcId = 0;
  teamUIs[teamIndex].level = 0;
  teamUIs[teamIndex].hp = 0;
  teamUIs[teamIndex].extraSkills = nil;
  for j = 1, DataFight.maxTeamSkill do
    teamUIs[teamIndex].skills[j].skillId = 0;
  end
end

function UIArmyBattleFunction.GetBoardPage()
  table.Clear(pageOfBoard);

  if table.Count(board) > 0 then
    for i = (currentPage - 1) * 5 + 1, currentPage * 5 do
      if board[i] == nil then break end

      table.insert(pageOfBoard, board[i]);
    end
  end
end

function UIArmyBattleFunction.ChangeOrdersAndTime(data)
  orders = nil;
  orders = data.orders;
  drumTime = data.drumTime;
  orderTime = data.orderTime;
  goldOrderTime = data.goldOrderTime;
end

function UIArmyBattleFunction.UpdateDrumTime()
  if ArmyBattle.CheckTime(drumTime) then
    text_DrumCountTime.enabled = true;
    text_DrumCountTime.text = string.GetSecondSymbol(math.ceil(DateTime.Subtract(drumTime, CGTimer.serverTime).TotalSeconds), true);
  else
    text_DrumCountTime.enabled = false;

    if CGTimer.ContainsListener(this.UpdateDrumTime) then
      CGTimer.RemoveListener(this.UpdateDrumTime);
    end
  end
end

function UIArmyBattleFunction.UpdateOrderTime()
  if ArmyBattle.CheckTime(orderTime) then
    text_OrderCountTime.enabled = true;
    text_OrderCountTime.text = string.GetSecondSymbol(math.ceil(DateTime.Subtract(orderTime, CGTimer.serverTime).TotalSeconds));
  else
    text_OrderCountTime.enabled = false;

    if CGTimer.ContainsListener(this.UpdateOrderTime) then
      CGTimer.RemoveListener(this.UpdateOrderTime);
    end
  end
end

function UIArmyBattleFunction.UpdateGoldOrderTime()
  if ArmyBattle.CheckTime(goldOrderTime) then
    text_GoldOrderCountTime.enabled = true;
    text_GoldOrderCountTime.text = string.GetSecondSymbol(math.ceil(DateTime.Subtract(goldOrderTime, CGTimer.serverTime).TotalSeconds));
  else
    text_GoldOrderCountTime.enabled = false;

    if CGTimer.ContainsListener(this.UpdateGoldOrderTime) then
      CGTimer.RemoveListener(this.UpdateGoldOrderTime);
    end
  end
end
