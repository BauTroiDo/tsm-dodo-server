EArmyBattleFrameType = {
  Defence = 1,
  Attack = 2
}

UIArmyBattleDefence = {}
local this = UIArmyBattleDefence;

this.name = "UIArmyBattleDefence";
this.uiController = nil;

local transform_FrameRect;
local image_BG_Rect;
local gameObject_Difficultly;
local gameObject_Defence;
local image_Flag;
local image_Defence;
local text_Id;
local text_Title;
local text_Score;
local text_Hint;
local text_Name;
local text_DrumHint;
local scrollContent_DefenceList;
local scrollItems_DefenceList = {};

local npcs = {};
local diff = {};

local dataTemp;
local frameType = nil;
local lastRequestTime;

function UIArmyBattleDefence.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onClose = this.OnClose;
  this.uiController.onVisible = this.OnVisible;

  transform_FrameRect = uiController:FindGameObject("FrameRect").transform;
  image_BG_Rect = uiController:FindImage("Image_BG"):GetComponent("RectTransform");

  gameObject_Difficultly = uiController:FindGameObject("Difficultly");
  gameObject_Defence = uiController:FindGameObject("Defence");
  image_Flag = uiController:FindImage("Image_Flag");
  image_Defence = uiController:FindImage("Image_Defence");
  text_Id = uiController:FindText("Text_Life");
  text_Title = uiController:FindText("Text_CityName");
  text_Score = uiController:FindText("Text_Score");
  text_Hint = uiController:FindText("Text_Hint");
  text_Name = uiController:FindText("Text_Name");
  text_DrumHint = uiController:FindText("Text_DrumHint");

  local tempRoot = uiController:FindGameObject("NPCRoot").transform;

  for i = 1, 4 do
    npcs[i] = {};
    npcs[i].infoRoot = tempRoot:Find(string.Concat("NPCInfo (", i, ")")).gameObject;
    npcs[i].uiCareer = UICareerController.New(npcs[i].infoRoot.transform:Find("Career"));
    npcs[i].text_Name = npcs[i].infoRoot.transform:Find("Text_Name"):GetComponent("Text");
    npcs[i].text_Lv = npcs[i].infoRoot.transform:Find("Text_T9_"):Find("Text_LvValue"):GetComponent("Text");
  end

  tempRoot = uiController:FindGameObject("DiffRoot").transform;

  for i = 1, 3 do
    diff[i] = {};
    local temp = tempRoot:Find(string.Concat("Image_Diff (", i, ")"));
    diff[i].image_BG = temp:GetComponent("Image");
    diff[i].image_Mask = temp:Find("Image_Mask"):GetComponent("Image");
    diff[i].text_Score = temp:Find("Text_ScorePlus"):GetComponent("Text");
    diff[i].text_Status = temp:Find("Text_Fight"):GetComponent("Text");
    diff[i].button = temp:Find("Image_Fight").gameObject;

    diff[i].event = diff[i].button:GetComponent("UIEvent");
    diff[i].event:SetListener(EventTriggerType.PointerClick, this.OnClick_Button);
    diff[i].event.parameter = i;
  end

  scrollContent_DefenceList = uiController:FindScrollContent("ScrollContent_DefenceList");
  scrollContent_DefenceList.onInitialize = this.OnInitialize_ScrollContent_DefenceList;
  scrollContent_DefenceList.onItemChange = this.OnItemChange_ScrollContent_DefenceList;
  scrollContent_DefenceList:Initialize(1);

  local tempEvent = uiController:FindEvent("Image_Defence");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Defence);

end

function UIArmyBattleDefence.OnOpen(type)
  dataTemp = ArmyBattle.cityInfo[ArmyBattle.nowChoosePoint];
  gameObject_Difficultly:SetActive(type == EArmyBattleFrameType.Attack);
  gameObject_Defence:SetActive(type == EArmyBattleFrameType.Defence);
  image_Defence.enabled = type == EArmyBattleFrameType.Defence;

  frameType = type;

  text_Title.text = string.Get(dataTemp.nameString);
  text_Id.text = dataTemp.id;
  text_Score.text = string.format(string.Get(24254), dataTemp.score);
  text_DrumHint.enabled = type == EArmyBattleFrameType.Attack and ArmyBattle.IsDrumPoint(dataTemp.moral);

  return true;
end

function UIArmyBattleDefence.OnVisible(visible)
  if visible then
    UIFrame.Active(transform_FrameRect, ArmyBattle.GetDefAtkString(dataTemp.moral), this.OnClick_Close);

    this.UpdateUI(true);
  end
end

function UIArmyBattleDefence.OnClose()
  ArmyBattle.nowChooseTeam = 1;
  return true;
end

function UIArmyBattleDefence.UpdateUI(reset)
  image_Flag.sprite = dataTemp:GetFlagSprite();

  if reset then
    scrollContent_DefenceList:Reset(dataTemp.count);
  else
    scrollContent_DefenceList:Refresh(dataTemp.count);
  end

  this.UpdateNPC();
  this.UpdateAttack();
end

function UIArmyBattleDefence.UpdateNPC()
  local name = "";

  if not dataTemp.isNpc[ArmyBattle.nowChooseTeam] then
    name = dataTemp.playerInfo[ArmyBattle.nowChooseTeam].name;
  else
    name = npcDatas[dataTemp.npcList[ArmyBattle.nowChooseTeam][1]].name;
  end

  for i = 1, 4 do
    if dataTemp.npcList[ArmyBattle.nowChooseTeam][i + 1] == nil or dataTemp.npcList[ArmyBattle.nowChooseTeam][i + 1] == 0 then
      npcs[i].infoRoot:SetActive(false);
    else
      local npc = {};

      if dataTemp.isNpc[ArmyBattle.nowChooseTeam] then
        npc = npcDatas[dataTemp.npcList[ArmyBattle.nowChooseTeam][i + 1]];
      else
        npc = dataTemp.npcList[ArmyBattle.nowChooseTeam][i + 1];
      end

      if npc ~= nil then
        npcs[i].uiCareer:SetCareer(npc.turn, npc.element, npc.career, true);
        npcs[i].text_Name.text = npc.name;
        npcs[i].text_Lv.text = npc.level;
      end

      npcs[i].infoRoot:SetActive(npc ~= nil);
    end

    if ArmyBattle.HasOrder(dataTemp.moral, 3) and ArmyBattle.IsEnemy(dataTemp.moral) then
      npcs[i].uiCareer:SetCareer(0, 0, 0, true);
      npcs[i].text_Name.text = "???";
      npcs[i].text_Lv.text = "?";
      name = "???";
    end
  end

  gameObject_Defence:SetActive(npcDatas[dataTemp.npcList[ArmyBattle.nowChooseTeam][1]] ~= nil and frameType ~= EArmyBattleFrameType.Attack and dataTemp.nowLife[ArmyBattle.nowChooseTeam] ~= 0);
  text_Hint.enabled = npcDatas[dataTemp.npcList[ArmyBattle.nowChooseTeam][1]] ~= nil and frameType ~= EArmyBattleFrameType.Attack;
  text_Name.text = string.format(string.Get(24461), name);
end

function UIArmyBattleDefence.UpdateAttack()
  if frameType ~= EArmyBattleFrameType.Attack then return end

  local npc = npcDatas[dataTemp.npcList[ArmyBattle.nowChooseTeam][1]];
  for i = 1, 3 do
    diff[i].image_Mask.enabled = dataTemp.nowLife[ArmyBattle.nowChooseTeam] < i;

    if dataTemp.nowLife[ArmyBattle.nowChooseTeam] < i then
      diff[i].button:SetActive(false);
    else
      diff[i].button:SetActive(not dataTemp.isFight[ArmyBattle.nowChooseTeam]);
    end

    if dataTemp.isFight[ArmyBattle.nowChooseTeam] then
      diff[i].text_Status.text = string.Get(24271);
    else
      diff[i].text_Status.text = string.Get(24458);
    end

    local scoreText = "";

    if npc ~= nil then
      scoreText = string.format(string.Get(24266), ArmyBattle.GetScore(i, npc.turn, npc.level));
    else
      scoreText = string.format(string.Get(24266), ArmyBattle.GetScore(i, dataTemp.playerInfo[ArmyBattle.nowChooseTeam].turn, dataTemp.playerInfo[ArmyBattle.nowChooseTeam].level));
    end

    if ArmyBattle.HasOrder(dataTemp.moral, 3) and ArmyBattle.IsEnemy(dataTemp.moral) then
      scoreText = "???";
    end

    diff[i].text_Score.text = scoreText;
  end
end

function UIArmyBattleDefence.OnInitialize_ScrollContent_DefenceList(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_DefenceList[itemIndex] = {};
    scrollItems_DefenceList[itemIndex].uiCareer = UICareerController.New(scrollItems[itemIndex]:Find("Career"));
    scrollItems_DefenceList[itemIndex].text_Lv = scrollItems[itemIndex]:Find("Text_T9_"):Find("Text_LvValue"):GetComponent("Text");
    scrollItems_DefenceList[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_DefenceList[itemIndex].text_Status = scrollItems[itemIndex]:Find("Text_Status"):GetComponent("Text");
    scrollItems_DefenceList[itemIndex].text_CanChange = scrollItems[itemIndex]:Find("Text_CanChange"):GetComponent("Text");
    scrollItems_DefenceList[itemIndex].image_Choose = scrollItems[itemIndex]:Find("Image_BG"):GetComponent("Image");

    scrollItems_DefenceList[itemIndex].image_Mask = scrollItems[itemIndex]:Find("Image_Mask"):GetComponent("Image");
    scrollItems_DefenceList[itemIndex].event_Click = scrollItems[itemIndex]:GetComponent("UIEvent");
    scrollItems_DefenceList[itemIndex].event_Click:SetListener(EventTriggerType.PointerClick, this.OnClick_Player);
    scrollItems_DefenceList[itemIndex].stars = {};
    for i = 1, 3 do
      scrollItems_DefenceList[itemIndex].stars[i] = scrollItems[itemIndex]:Find("StarRoot"):Find(string.Concat("Image_Star (", i , ")")):GetComponent("Image");
    end

    scrollItems_DefenceList[itemIndex].drums = {};
    for i = 1, 3 do
      scrollItems_DefenceList[itemIndex].drums[i] = scrollItems[itemIndex]:Find("DrumRoot"):Find(string.Concat("Image_Drum (", i , ")")):GetComponent("Image");
    end

    scrollItems_DefenceList[itemIndex].image_Fight = scrollItems[itemIndex]:Find("RawImage_Fight"):GetComponent("UIRawImage");
    TextureManager.SetPng(ETextureUseType.None, "Attack", scrollItems_DefenceList[itemIndex].image_Fight);
    scrollItems_DefenceList[itemIndex].image_Fight:ShiftUV(1, 5, 0.1, -1);
  end
end

function UIArmyBattleDefence.OnItemChange_ScrollContent_DefenceList(dataIndex, itemIndex)
  scrollItems_DefenceList[itemIndex].event_Click.parameter = dataIndex + 1;

  local npc = npcDatas[dataTemp.npcList[dataIndex + 1][1]];

  local leader = {};

  if npc == nil then
    local info = dataTemp.playerInfo[dataIndex + 1];
    leader.turn = info.turn;
    leader.element = info.element;
    leader.career = info.career;
    leader.turn3Element = info.turn3Element;
    leader.name = info.name;
    leader.level = info.level;
  else
    leader.turn = npc.turn;
    leader.element = npc.element;
    leader.career = npc.career;
    leader.turn3Element = npc.turn3Element;
    leader.name = npc.name;
    leader.level = npc.level;
  end

  if ArmyBattle.HasOrder(dataTemp.moral, 3) and ArmyBattle.IsEnemy(dataTemp.moral) then
    leader.turn = 0;
    leader.element = 0;
    leader.career = 0;
    leader.turn3Element = 0;
    leader.name = "???";
    leader.level = "?";
  end

  scrollItems_DefenceList[itemIndex].uiCareer:SetCareer(leader.turn, leader.element, leader.career, npc ~= nil, leader.turn3Element);
  scrollItems_DefenceList[itemIndex].text_Name.text = leader.name;
  scrollItems_DefenceList[itemIndex].text_Lv.text = leader.level;

  for i = 1, 3 do
    scrollItems_DefenceList[itemIndex].drums[i].enabled =
    dataTemp.pointType == StrongholdCityType.DrumHouse and i == dataTemp.drumCount and dataTemp.nowLife[dataIndex + 1] ~= 0;
  end

  for i = 1, 3 do
    scrollItems_DefenceList[itemIndex].stars[i].enabled = i <= dataTemp.nowLife[dataIndex + 1];
  end

  scrollItems_DefenceList[itemIndex].image_Choose.enabled = ArmyBattle.nowChooseTeam == dataIndex + 1 and dataTemp.nowLife[ArmyBattle.nowChooseTeam] ~= 0;
  scrollItems_DefenceList[itemIndex].image_Fight.enabled = dataTemp.isFight[dataIndex + 1];
  scrollItems_DefenceList[itemIndex].image_Mask.enabled = dataTemp.nowLife[dataIndex + 1] == 0;
  scrollItems_DefenceList[itemIndex].text_Status.enabled = dataTemp.nowLife[dataIndex + 1] == 0;
  scrollItems_DefenceList[itemIndex].text_CanChange.enabled = npc ~= nil and not ArmyBattle.IsEnemy(dataTemp.moral) and not ArmyBattle.IsDrumPoint(dataTemp.moral);

  return true;
end

function UIArmyBattleDefence.OnClick_Help(checkShow)
  if checkShow ~= true then
    UI.Open(UITutorialHint, 0, this, string.Get(22943), string.Get(22954));
  end

  return true;
end

function UIArmyBattleDefence.OnClick_Close()
  UI.Close(UIArmyBattleDefence);

  return true;
end

function UIArmyBattleDefence.OnClick_Player(uiEvent)
  ArmyBattle.nowChooseTeam = uiEvent.parameter;

  this.UpdateUI();
end

function UIArmyBattleDefence.OnClick_Button(uiEvent)
  UI.Open(UIArmyBattleAttack, uiEvent.parameter);
end

function UIArmyBattleDefence.OnClick_Defence()
  --C:171-008 <選擇城池> +城池ID(1)
  sendBuffer:Clear();
  sendBuffer:WriteByte(dataTemp.id);
  Network.Send(171, 8, sendBuffer);

  if not Requiem.CheckTimer(lastRequestTime) then
    ShowCenterMessage(string.Get(22841));
    return;
  end

  if ArmyBattle.playerDefencePoint == dataTemp.id then
    ShowCenterMessage(string.Get(24251));
    return;
  elseif ArmyBattle.playerDefencePoint ~= 0 then
    ShowCenterMessage(string.Get(24250));
    return;
  end

  ShowCheckMessage(
    function(result)
      if result then
        --C:<選擇對手 or 加入防守> +對像種類(1) +城池ID(1) +替換ID(8) +對手位置or被替換位置(1) +根據對象種類的內容(?)        sendBuffer:Clear();
        sendBuffer:Clear();
        sendBuffer:WriteByte(2);
        sendBuffer:WriteByte(ArmyBattle.nowChoosePoint);
        sendBuffer:WriteInt64(dataTemp.idList[ArmyBattle.nowChooseTeam]);
        sendBuffer:WriteByte(ArmyBattle.nowChooseTeam);
        Network.Send(171, 6, sendBuffer);
        lastRequestTime = CGTimer.serverTime;
      end
    end,
    string.Get(24248)
  );
end
