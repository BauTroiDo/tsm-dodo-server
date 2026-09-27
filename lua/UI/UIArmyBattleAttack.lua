EArmyList = {
  Inn = 1,
  Cart = 2
}

UIArmyBattleAttack = {}
local this = UIArmyBattleAttack;

this.name = "UIArmyBattleAttack";
this.uiController = nil;

local transform_FrameRect;

local text_Empty;
local text_Title;

local scrollContent_List;
local scrollItems_List = {};

--var
local tagData = {};
local currentMainTag;
local currentSubTag;
local followNpcs = {};
local innNpcs = {};
local allFull = false;
local exchangeFollowIndex = 0;
local exchangeInnIndex = 0;
local exchangeCartIndex = 0;
local diff = 0;
local hasFight = false;

function UIArmyBattleAttack.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onClose = this.OnClose;
  this.uiController.onVisible = this.OnVisible;

  transform_FrameRect = uiController:FindGameObject("FrameRect").transform;

  text_Empty = uiController:FindText("Text_Empty");
  text_Title = uiController:FindText("Text_ListTitle");

  local tempObject = uiController:FindGameObject("Image_FollowNpcBG");
  for i = 1, Role.maxFollowNpc do
    local child = tempObject.transform:Find(string.Concat("Image_FollowNpc (", i, ")"));

    followNpcs[i] = {};
    followNpcs[i].text_Name = child:Find("Text_Name"):GetComponent("Text");
    followNpcs[i].text_Hp = child:Find("Text_Hp"):GetComponent("Text");
    followNpcs[i].text_Lv = child:Find("Text_Lv"):GetComponent("Text");
    followNpcs[i].uiCareer = UICareerController.New(child:Find("Career"));
    followNpcs[i].empty = child:Find("Image_Empty"):GetComponent("Image");
    followNpcs[i].hasFight = child:Find("Text_HasUse"):GetComponent("Text");

    followNpcs[i].text_State = child:Find("Image_SwitchState"):Find("Text_State"):GetComponent("Text");
    followNpcs[i].image_State = child:Find("Image_SwitchState"):GetComponent("Image");
    followNpcs[i].event_State = child:Find("Image_SwitchState"):GetComponent("UIEvent");
    followNpcs[i].event_State:SetListener(EventTriggerType.PointerClick, this.OnClick_FollowNpcState);
  end

  scrollContent_List = uiController:FindScrollContent("ScrollContent_List");
  scrollContent_List.onInitialize = this.OnInitialize_ScrollContent_List;
  scrollContent_List.onItemChange = this.OnItemChange_ScrollContent_List;
  scrollContent_List:Initialize(1);

  local tempEvent = uiController:FindEvent("Image_Fight");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Fight);
end

function UIArmyBattleAttack.OnOpen(difficult)
  currentSubTag = EArmyList.Inn;
  table.Clear(tagData);

  if Role.player:GetAttribute(EAttribute.Turn) > 0 then
    tagData[1] = {string.Get(20115), {string.Get(10074), string.Get(22491)}};
  else
    tagData[1] = {string.Get(20115), {string.Get(10074)}};
  end

  diff = difficult;
  hasFight = false;

  return true;
end

function UIArmyBattleAttack.OnVisible(visible)
  if visible then
    UIFrame.Active(transform_FrameRect, string.Get(20282), this.OnClick_Close, this.OnClick_Help, this.OnClick_Tag, tagData, currentMainTag, currentSubTag);
    this.UpdateUI(true);
  end
end

function UIArmyBattleAttack.OnInitialize_ScrollContent_List(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_List[itemIndex] = {};
    scrollItems_List[itemIndex].uiCareer = UICareerController.New(scrollItems[itemIndex]:Find("Career"));
    scrollItems_List[itemIndex].text_Lv = scrollItems[itemIndex]:Find("Text_Lv"):GetComponent("Text");
    scrollItems_List[itemIndex].text_Hp = scrollItems[itemIndex]:Find("Text_Hp"):GetComponent("Text");
    scrollItems_List[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_List[itemIndex].image_Empty = scrollItems[itemIndex]:Find("Image_Empty"):GetComponent("Image");
    scrollItems_List[itemIndex].image_Dispatch = scrollItems[itemIndex]:Find("Image_Dispatch"):GetComponent("Image");
    scrollItems_List[itemIndex].text_HasFight = scrollItems[itemIndex]:Find("Text_HasUse"):GetComponent("Text");
    scrollItems_List[itemIndex].text_State = scrollItems[itemIndex]:Find("Image_SwitchState"):Find("Text_State"):GetComponent("Text");
    scrollItems_List[itemIndex].image_Click = scrollItems[itemIndex]:Find("Image_SwitchState"):GetComponent("Image");
    scrollItems_List[itemIndex].event_Click = scrollItems[itemIndex]:Find("Image_SwitchState"):GetComponent("UIEvent");
    scrollItems_List[itemIndex].event_Click:SetListener(EventTriggerType.PointerClick, this.OnClick_ListNpc);
  end
end

function UIArmyBattleAttack.OnItemChange_ScrollContent_List(dataIndex, itemIndex)
  local npcData;
  local time = 0;

  if currentSubTag == EArmyList.Inn then
    npcData = Inn.npcs[innNpcs[dataIndex + 1]];
    time = 0;
    scrollItems_List[itemIndex].event_Click.parameter = innNpcs[dataIndex + 1];
    scrollItems_List[itemIndex].image_Dispatch.enabled = npcData ~= nil and npcData.status == ENpcInnStatus.Dispatch;
    scrollItems_List[itemIndex].event_Click.gameObject:SetActive(npcData ~= nil and npcData.status ~= ENpcInnStatus.Dispatch);
    scrollItems_List[itemIndex].text_HasFight.enabled = npcData ~= nil;

    if npcData ~= nil then
      for _, v in pairs(ArmyBattle.usedNpc) do
        if v.npcId == npcData.npcId then
          time = v.time;
          break;
        end
      end
    end

    scrollItems_List[itemIndex].text_HasFight.text = string.format(string.Get(24510), time, ArmyBattle.MAX_NPCFIGHT_COUNT);
  elseif currentSubTag == EArmyList.Cart then
    npcData = Cart.followNpcs[dataIndex + 1];
    time = 0;
    scrollItems_List[itemIndex].event_Click.parameter = dataIndex + 1;
    scrollItems_List[itemIndex].image_Dispatch.enabled = false;
    scrollItems_List[itemIndex].event_Click.gameObject:SetActive(npcData ~= nil);
    scrollItems_List[itemIndex].text_HasFight.enabled = npcData ~= nil;

    if npcData ~= nil then
      for _, v in pairs(ArmyBattle.usedNpc) do
        if v.npcId == npcData.npcId then
          time = v.time;
          break;
        end
      end
    end
  end

  scrollItems_List[itemIndex].text_HasFight.text = string.format(string.Get(24510), time, ArmyBattle.MAX_NPCFIGHT_COUNT);
  scrollItems_List[itemIndex].image_Empty.enabled = npcData == nil;
  scrollItems_List[itemIndex].text_Lv.gameObject:SetActive(npcData ~= nil);
  scrollItems_List[itemIndex].text_Hp.gameObject:SetActive(npcData ~= nil);

  if npcData ~= nil then
    local temp = npcDatas[npcData.npcId];
    scrollItems_List[itemIndex].uiCareer:SetCareer(temp.turn, temp.element, temp.career, true);
    scrollItems_List[itemIndex].text_Name.text = npcData.name;
    scrollItems_List[itemIndex].text_Hp.text = npcData.hp;
    scrollItems_List[itemIndex].text_State.text = "set";

    if currentSubTag == EArmyList.Inn then
      scrollItems_List[itemIndex].text_Lv.text = npcData.level;

      if allFull then
        if dataIndex + 1 == exchangeInnIndex then
          scrollItems_List[itemIndex].image_Click.sprite = UI.GetSprite("but167D");
        else
          scrollItems_List[itemIndex].image_Click.sprite = UI.GetSprite("but064D");
        end

        scrollItems_List[itemIndex].text_State.text = string.Get(21669);
      else
        scrollItems_List[itemIndex].image_Click.sprite = UI.GetSprite("but064D");
        scrollItems_List[itemIndex].text_State.text = string.Get(30022);
      end
    elseif currentSubTag == EArmyList.Cart then
      scrollItems_List[itemIndex].text_Lv.text = npcData.Lv;

      if dataIndex + 1 == exchangeCartIndex then
        scrollItems_List[itemIndex].image_Click.sprite = UI.GetSprite("but167D");
        scrollItems_List[itemIndex].text_State.text = string.Get(21669);
      else
        scrollItems_List[itemIndex].image_Click.sprite = UI.GetSprite("but064D");
        scrollItems_List[itemIndex].text_State.text = string.Get(30022);
      end
    end
  else
    scrollItems_List[itemIndex].uiCareer:SetCareer();
    scrollItems_List[itemIndex].text_Name.text = "";
  end

  return true;
end

function UIArmyBattleAttack.UpdateUI(reset)
  if reset then
    exchangeFollowIndex = 0;
    exchangeInnIndex = 0;
    exchangeCartIndex = 0;
  end

  table.Clear(innNpcs);

  for i = 1, Inn.maxSpace do
    if Inn.npcs[i] ~= nil then
      table.insert(innNpcs, i);
    end
  end

  this.UpdateList(reset);
  this.UpdateFollowNpc();
end

function UIArmyBattleAttack.UpdateList(reset)
  local count = 0;

  if currentSubTag == EArmyList.Inn then
    count = Inn.currentSpace;
    text_Title.text = string.Get(20119);
  elseif currentSubTag == EArmyList.Cart then
    count = Cart.GetCartSize();
    text_Title.text = string.Get(22491);
  end

  allFull = table.Count(innNpcs) >= Inn.currentSpace and table.Count(Role.GetFollowNpcs(Role.playerId)) >= Role.maxFollowNpc;

  text_Empty.enabled = count == 0;

  if reset then
    scrollContent_List:Reset(count);
  else
    scrollContent_List:Refresh(count);
  end
end

function UIArmyBattleAttack.UpdateFollowNpc()
  hasFight = false;
  for i = 1, Role.maxFollowNpc do
    local time = 0;
    local followNpc = Role.GetRole(EHuman.FollowNpc, Role.playerId, i);

    followNpcs[i].empty.enabled = followNpc == nil;
    followNpcs[i].event_State.gameObject:SetActive(followNpc ~= nil);
    followNpcs[i].text_Hp.gameObject:SetActive(followNpc ~= nil);
    followNpcs[i].text_Lv.gameObject:SetActive(followNpc ~= nil);

    if followNpc ~= nil then
      if followNpc.data.canGrow == 0 then
        followNpcs[i].text_Name.text = string.GetColorText(followNpc.name, Color.Orange);
      elseif followNpc.data.canGrow == 2 then
        followNpcs[i].text_Name.text = string.GetColorText(followNpc.name, Color.MissionNPC);
      else
        followNpcs[i].text_Name.text = followNpc.name;
      end

      for _, v in pairs(ArmyBattle.usedNpc) do
        if v.npcId == followNpc.npcId then
          time = v.time;
          hasFight = time == ArmyBattle.MAX_NPCFIGHT_COUNT;
          break;
        end
      end

      if currentSubTag == EArmyList.Inn then
        allFull = table.Count(innNpcs) >= Inn.currentSpace and table.Count(Role.GetFollowNpcs(Role.playerId)) >= Role.maxFollowNpc;

        if allFull then
          if i == exchangeFollowIndex then
            followNpcs[i].image_State.sprite = UI.GetSprite("but167D");
          else
            followNpcs[i].image_State.sprite = UI.GetSprite("but064D");
          end

          followNpcs[i].text_State.text = string.Get(21669);
        else
          followNpcs[i].image_State.sprite = UI.GetSprite("but064D");
          followNpcs[i].text_State.text = string.Get(30023);
        end
      elseif currentSubTag == EArmyList.Cart then
        if i == exchangeFollowIndex then
          followNpcs[i].image_State.sprite = UI.GetSprite("but167D");
          followNpcs[i].text_State.text = string.Get(21669);
        else
          followNpcs[i].image_State.sprite = UI.GetSprite("but064D");
          followNpcs[i].text_State.text = string.Get(30023);
        end
      end

      followNpcs[i].text_Hp.text = string.Concat(followNpc:GetAttribute(EAttribute.Hp), " / ", followNpc:GetAttribute(EAttribute.MaxHp));
      followNpcs[i].text_Lv.text = followNpc:GetAttribute(EAttribute.Lv);
      followNpcs[i].uiCareer:SetCareer(followNpc:GetAttribute(EAttribute.Turn), followNpc:GetAttribute(EAttribute.Element), followNpc:GetAttribute(EAttribute.Career), true);
      followNpcs[i].event_State.parameter = i;
    else
      followNpcs[i].text_Name.text = "";
      followNpcs[i].uiCareer:SetCareer();
      followNpcs[i].event_State.parameter = nil;
    end

    followNpcs[i].hasFight.enabled = followNpc ~= nil;
    followNpcs[i].hasFight.text = string.format(string.Get(24510), time, ArmyBattle.MAX_NPCFIGHT_COUNT);
  end
end

function UIArmyBattleAttack.OnClose()
  hasFight = false;
  return true;
end

function UIArmyBattleAttack.OnClick_Help(checkShow)
  if checkShow ~= true then
    UI.Open(UITutorialHint, 0, this, string.Get(24232), string.Get(24916));
  end

  return true;
end

function UIArmyBattleAttack.OnClick_Tag(mainTag, subTag)
  currentSubTag = subTag;

  this.UpdateUI(true);

  return true;
end

function UIArmyBattleAttack.OnClick_Close()
  UI.Close(UIArmyBattleAttack);

  return true;
end

function UIArmyBattleAttack.OnClick_FollowNpcState(uiEvent)
  local index = uiEvent.parameter;

  if currentSubTag == EArmyList.Inn then
    if allFull then
      if exchangeInnIndex ~= 0 then
        --C:031-004 <客棧交換武將> +客棧索引(1) +身上索引(1)
        sendBuffer:Clear();
        sendBuffer:WriteByte(exchangeInnIndex);
        sendBuffer:WriteByte(index);
        Network.Send(31, 4, sendBuffer);
      else
        if exchangeFollowIndex == index then
          exchangeFollowIndex = 0;
        else
          exchangeFollowIndex = index;
        end

        this.UpdateUI();
      end
    else
      if table.Count(innNpcs) >= Inn.currentSpace then
        ShowCenterMessage(string.Get(71276));
        return;
      end

      --C:031-003 <客棧存武將> +身上索引(1)
      sendBuffer:Clear();
      sendBuffer:WriteByte(index);
      Network.Send(31, 3, sendBuffer);
    end
  elseif currentSubTag == EArmyList.Cart then
    if exchangeCartIndex ~= 0 then
      exchangeFollowIndex = index;

      --C:015-012 <交換武將> +跟隨索引(1) +馬車索引(1)
      sendBuffer:Clear();
      sendBuffer:WriteByte(exchangeFollowIndex);
      sendBuffer:WriteByte(exchangeCartIndex);
      Network.Send(15, 12, sendBuffer);
    else
      local isFull = true;
      for i = 1, Cart.GetCartSize() do
        if Cart.followNpcs[i] == nil or npcDatas[Cart.followNpcs[i].npcId] == nil then
          isFull = false;
          break;
        end
      end

      if isFull then
        exchangeFollowIndex = index;
        this.UpdateUI();
      else
        --C:015-010 <武將存入馬車> +索引(1)
        sendBuffer:Clear();
        sendBuffer:WriteByte(index);
        Network.Send(15, 10, sendBuffer);
      end
    end
  end
end

function UIArmyBattleAttack.OnClick_Fight()
  if ArmyBattle.battleStatus == ArmyBattleStatus.Rest then
    ShowCenterMessage(string.Get(24272));
    return;
  end

  if ArmyBattle.playerAttackCount == 0 then
    ShowCenterMessage(string.Get(24352));
    return;
  end

  if hasFight then
    ShowCenterMessage(string.Get(24353));
    return;
  end;

  --C:171-006 <選擇對手 or 加入防守> +對像種類(1) +城池ID(1) +替換ID(8) +對手位置or被替換位置(1) +根據對象種類的內容(?)
  sendBuffer:Clear();
  sendBuffer:WriteByte(1);
  sendBuffer:WriteByte(ArmyBattle.nowChoosePoint);
  sendBuffer:WriteInt64(ArmyBattle.cityInfo[ArmyBattle.nowChoosePoint].idList[ArmyBattle.nowChooseTeam]);
  sendBuffer:WriteByte(ArmyBattle.nowChooseTeam);
  sendBuffer:WriteByte(diff);
  Network.Send(171, 6, sendBuffer);
end

function UIArmyBattleAttack.OnClick_ListNpc(uiEvent)
  local index = uiEvent.parameter;

  if currentSubTag == EArmyList.Inn then
    if allFull then
      if exchangeFollowIndex ~= 0 then
        --C:031-004 <客棧交換武將> +客棧索引(1) +身上索引(1)
        sendBuffer:Clear();
        sendBuffer:WriteByte(index);
        sendBuffer:WriteByte(exchangeFollowIndex);
        Network.Send(31, 4, sendBuffer);
      else
        if exchangeInnIndex == index then
          exchangeInnIndex = 0;
        else
          exchangeInnIndex = index;
        end

        this.UpdateUI();
      end
    else
      local isFull = true;
      for i = 1, Role.maxFollowNpc do
        if Role.GetRole(EHuman.FollowNpc, Role.playerId, i) == nil then
          isFull = false;
          break;
        end
      end

      if isFull then
        ShowCenterMessage(string.Get(71184));
        return;
      end

      --C:031-002 <客棧領武將> +客棧索引(1)
      sendBuffer:Clear();
      sendBuffer:WriteByte(index);
      Network.Send(31, 2, sendBuffer);
    end
  elseif currentSubTag == EArmyList.Cart then
    if exchangeFollowIndex ~= 0 then
      exchangeCartIndex = index;

      --C:015-012 <交換武將> +跟隨索引(1) +馬車索引(1)
      sendBuffer:Clear();
      sendBuffer:WriteByte(exchangeFollowIndex);
      sendBuffer:WriteByte(exchangeCartIndex);
      Network.Send(15, 12, sendBuffer);
    else
      local full = true;
      for i = 1, Role.maxFollowNpc do
        if Role.GetRole(EHuman.FollowNpc, Role.playerId, i) == nil then
          full = false;
          break;
        end
      end

      if full then
        exchangeCartIndex = index;
        this.UpdateUI();
      else
        --C:015-011 <武將領出馬車> +索引(1)
        sendBuffer:Clear();
        sendBuffer:WriteByte(index);
        Network.Send(15, 11, sendBuffer);
      end
    end
  end
end

