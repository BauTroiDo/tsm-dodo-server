local ETag =
{
  Inn = 1,
  WarriorRepository = 2
}

UINpcInn = {}
local this = UINpcInn;
this.name = "UINpcInn";

local uiController;
local transform_FrameRect;
local text_NpcSpace;
local text_WarriorRepositorySpace;
local scrollContent_Npc;
local scrollItems_Npc = {};
local scrollContent_WarriorRepository;
local scrollItems_WarriorRepository = {};

local followNpcs = {};
local innNpcs = {};
local warriorRepository = {};

local currentTag = ETag.Inn;
local tagDatas = {};
local tagGameObject = {};

local allFull = false;
local isWarriorRepositoryFull = false;
local exchangeInnIndex = 0;
local exchangeFollowIndex = 0;

-- UnityFunctions

function UINpcInn.Initialize(go)
  this.InitUIController(go);
  this.InitInnNpcScroll();
  this.InitWarriorRepositoryScroll();
  this.InitFollowNpc();
  this.InitEvent();
  this.InitContent();
end

function UINpcInn.OnOpen()
  currentTag = ETag.Inn;
  --C:122-001<查詢武將倉庫> +空包(0)
  sendBuffer:Clear();
  Network.Send(122, 1);
  return true;
end

function UINpcInn.OnVisible(visible)
  if visible then
    UINpcInn.SetTagDatas();
    UIFrame.Active(transform_FrameRect, string.Get(10074), this.OnClick_Close, this.OnClick_Help, this.OnClick_Tag, tagDatas, 1, 1);
    this.UpdateUI(true);
  end
end

function UINpcInn.OnClose()
  EventManager.SetConduct(true);
  --C:031-010 <關閉隨身客棧>
  Network.Send(31, 10);
end

-- Initializers

function UINpcInn.InitUIController(go)
  this.uiController = go:GetComponent("UIController");
  uiController = this.uiController;
  uiController.onOpen = this.OnOpen;
  uiController.onVisible = this.OnVisible;
  uiController.onClose = this.OnClose;
end

function UINpcInn.InitFollowNpc()
  local tempObject = uiController:FindGameObject("Image_FollowBG");
  for i = 1, Role.maxFollowNpc do
    local child = tempObject.transform:Find(string.Concat("Image_FollowNpc (", i, ")"));
    followNpcs[i] = {};
    followNpcs[i].text_Name = child:Find("Text_Name"):GetComponent("Text");
    followNpcs[i].text_Hp = child:Find("Text_Hp"):GetComponent("Text");
    followNpcs[i].text_Lv = child:Find("Text_Lv"):GetComponent("Text");
    followNpcs[i].uiCareer = UICareerController.New(child:Find("GameObject_Career"));
    followNpcs[i].image_State = followNpcs[i].text_Lv.transform:Find("Image_State"):GetComponent("Image");
    followNpcs[i].image_Empty = child:Find("Image_Empty"):GetComponent("Image");
    followNpcs[i].image_Save = child:Find("Image_Save"):GetComponent("Image");
    followNpcs[i].text_Save = followNpcs[i].image_Save.transform:Find("Text_"):GetComponent("Text");
    local tempEvent = child:Find("Image_Save"):GetComponent("UIEvent");
    tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Save);
    tempEvent.parameter = i;
  end
end

function UINpcInn.InitContent()
  transform_FrameRect = uiController:FindGameObject("FrameRect").transform;
  text_NpcSpace = uiController:FindText("Text_NpcSpace");
  text_WarriorRepositorySpace = uiController:FindText("Text_WarriorRepositorySpace");
  tagGameObject[ETag.Inn] = uiController:FindGameObject("Inn");
  tagGameObject[ETag.WarriorRepository] = uiController:FindGameObject("WarriorRepository");
end

function UINpcInn.InitEvent()
  local tempEvent = uiController:FindEvent("Image_AddNpcSpace");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_AddNpcSpace);
  tempEvent = uiController:FindEvent("Image_AddWarriorRepositorySpace");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_AddWarriorRepositorySpace);
end

--NpcScroll

function UINpcInn.InitInnNpcScroll()
  scrollContent_Npc = uiController:FindScrollContent("ScrollContent_Npc");
  scrollContent_Npc.onInitialize = this.OnInitialize_ScrollContent_Npc;
  scrollContent_Npc.onItemChange = this.OnItemChange_ScrollContent_Npc
  scrollContent_Npc:Initialize(1);
end

function UINpcInn.OnInitialize_ScrollContent_Npc(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Npc[itemIndex] = {};
    scrollItems_Npc[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_Npc[itemIndex].text_Hp = scrollItems[itemIndex]:Find("Text_Hp"):GetComponent("Text");
    scrollItems_Npc[itemIndex].text_Lv = scrollItems[itemIndex]:Find("Text_Lv"):GetComponent("Text");
    scrollItems_Npc[itemIndex].uiCareer = UICareerController.New(scrollItems[itemIndex]:Find("GameObject_Career"));
    scrollItems_Npc[itemIndex].image_Lock = scrollItems[itemIndex]:Find("Image_Lock"):GetComponent("Image");
    scrollItems_Npc[itemIndex].image_Dispatch = scrollItems[itemIndex]:Find("Image_Dispatch"):GetComponent("Image");
    scrollItems_Npc[itemIndex].image_Empty = scrollItems[itemIndex]:Find("Image_Empty"):GetComponent("Image");
    scrollItems_Npc[itemIndex].image_Take = scrollItems[itemIndex]:Find("Image_Take"):GetComponent("Image");
    scrollItems_Npc[itemIndex].text_Take = scrollItems_Npc[itemIndex].image_Take.transform:Find("Text_"):GetComponent("Text");
    scrollItems_Npc[itemIndex].event_Take = scrollItems[itemIndex]:Find("Image_Take"):GetComponent("UIEvent");
    scrollItems_Npc[itemIndex].event_Take:SetListener(EventTriggerType.PointerClick, this.OnClick_Take);
    local tempEvent = scrollItems[itemIndex]:Find("Image_Lock"):GetComponent("UIEvent");
    tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_AddNpcSpace);
  end
end

function UINpcInn.OnItemChange_ScrollContent_Npc(dataIndex, itemIndex)
  local innIndex = innNpcs[dataIndex + 1];

  if innIndex ~= nil and Inn.npcs[innIndex] ~= nil then
    scrollItems_Npc[itemIndex].text_Name.text = Inn.npcs[innIndex].name;
    scrollItems_Npc[itemIndex].text_Hp.text = Inn.npcs[innIndex].hp;
    scrollItems_Npc[itemIndex].text_Lv.text = Inn.npcs[innIndex].level;

    local npcData = npcDatas[Inn.npcs[innIndex].npcId];
    if npcData ~= nil then
      scrollItems_Npc[itemIndex].uiCareer:SetCareer(npcData.turn, npcData.element, ECareer.None, true);
      scrollItems_Npc[itemIndex].image_Empty.enabled = false;
    else
      scrollItems_Npc[itemIndex].uiCareer:SetCareer();
      scrollItems_Npc[itemIndex].image_Empty.enabled = true;
    end

    scrollItems_Npc[itemIndex].image_Dispatch.gameObject:SetActive(Inn.npcs[innIndex].status == ENpcInnStatus.Dispatch);

    if allFull then
      if innIndex == exchangeInnIndex then
        scrollItems_Npc[itemIndex].image_Take.sprite = UI.GetSprite("but167D");
      else
        scrollItems_Npc[itemIndex].image_Take.sprite = UI.GetSprite("but064D");
      end

      scrollItems_Npc[itemIndex].text_Take.text = string.Get(21669);
    else
      scrollItems_Npc[itemIndex].image_Take.sprite = UI.GetSprite("but064D");
      scrollItems_Npc[itemIndex].text_Take.text = string.Get(30022);
    end

    scrollItems_Npc[itemIndex].event_Take.gameObject:SetActive(Inn.npcs[innIndex].status ~= ENpcInnStatus.Dispatch);
    scrollItems_Npc[itemIndex].event_Take.parameter = innIndex;
  else
    scrollItems_Npc[itemIndex].text_Name.text = "";
    scrollItems_Npc[itemIndex].text_Hp.text = "";
    scrollItems_Npc[itemIndex].text_Lv.text = "";
    scrollItems_Npc[itemIndex].uiCareer:SetCareer();
    scrollItems_Npc[itemIndex].image_Empty.enabled = true;

    scrollItems_Npc[itemIndex].image_Dispatch.gameObject:SetActive(false);

    scrollItems_Npc[itemIndex].event_Take.gameObject:SetActive(false);
    scrollItems_Npc[itemIndex].event_Take.parameter = nil;
  end

  scrollItems_Npc[itemIndex].image_Lock.gameObject:SetActive(dataIndex + 1 > Inn.currentSpace);

  return true;
end

--WarriorRepositoryScroll

function UINpcInn.InitWarriorRepositoryScroll()
  scrollContent_WarriorRepository = uiController:FindScrollContent("ScrollContent_WarriorRepository");
  scrollContent_WarriorRepository.onInitialize = this.OnInitialize_ScrollContent_WarriorRepository;
  scrollContent_WarriorRepository.onItemChange = this.OnItemChange_ScrollContent_WarriorRepository;
  scrollContent_WarriorRepository:Initialize(1);
end

function UINpcInn.OnInitialize_ScrollContent_WarriorRepository(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_WarriorRepository[itemIndex] = {};
    scrollItems_WarriorRepository[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_WarriorRepository[itemIndex].text_Hp = scrollItems[itemIndex]:Find("Text_Hp"):GetComponent("Text");
    scrollItems_WarriorRepository[itemIndex].text_Lv = scrollItems[itemIndex]:Find("Text_Lv"):GetComponent("Text");
    scrollItems_WarriorRepository[itemIndex].uiCareer = UICareerController.New(scrollItems[itemIndex]:Find("GameObject_Career"));
    scrollItems_WarriorRepository[itemIndex].image_Lock = scrollItems[itemIndex]:Find("Image_Lock"):GetComponent("Image");
    scrollItems_WarriorRepository[itemIndex].image_Dispatch = scrollItems[itemIndex]:Find("Image_Dispatch"):GetComponent("Image");
    scrollItems_WarriorRepository[itemIndex].image_Empty = scrollItems[itemIndex]:Find("Image_Empty"):GetComponent("Image");
    scrollItems_WarriorRepository[itemIndex].image_Take = scrollItems[itemIndex]:Find("Image_Take"):GetComponent("Image");
    scrollItems_WarriorRepository[itemIndex].text_Take = scrollItems_WarriorRepository[itemIndex].image_Take.transform:Find("Text_"):GetComponent("Text");
    scrollItems_WarriorRepository[itemIndex].event_Take = scrollItems[itemIndex]:Find("Image_Take"):GetComponent("UIEvent");
    scrollItems_WarriorRepository[itemIndex].event_Take:SetListener(EventTriggerType.PointerClick, this.OnClick_Take);
    local tempEvent = scrollItems[itemIndex]:Find("Image_Lock"):GetComponent("UIEvent");
    tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_AddWarriorRepositorySpace);
  end
end

function UINpcInn.OnItemChange_ScrollContent_WarriorRepository(dataIndex, itemIndex)
  local warriorRepositoryId = warriorRepository[dataIndex + 1];
  if warriorRepositoryId ~= nil and Inn.warriorRepository[warriorRepositoryId] ~= nil then
    local npcData = npcDatas[Inn.warriorRepository[warriorRepositoryId].npcId];
    if npcData ~= nil then
      scrollItems_WarriorRepository[itemIndex].uiCareer:SetCareer(npcData.turn, npcData.element, ECareer.None, true);
      scrollItems_WarriorRepository[itemIndex].image_Empty.enabled = false;
      scrollItems_WarriorRepository[itemIndex].text_Name.text = npcData.name;
    else
      scrollItems_WarriorRepository[itemIndex].uiCareer:SetCareer();
      scrollItems_WarriorRepository[itemIndex].image_Empty.enabled = true;
      scrollItems_WarriorRepository[itemIndex].text_Name.text = "";
    end
    scrollItems_WarriorRepository[itemIndex].text_Hp.text = Inn.warriorRepository[warriorRepositoryId].hp;
    scrollItems_WarriorRepository[itemIndex].text_Lv.text = Inn.warriorRepository[warriorRepositoryId].level;
    scrollItems_WarriorRepository[itemIndex].image_Dispatch.gameObject:SetActive(Inn.warriorRepository[warriorRepositoryId].status == ENpcInnStatus.Dispatch);
    scrollItems_WarriorRepository[itemIndex].image_Take.sprite = UI.GetSprite("but064D");
    scrollItems_WarriorRepository[itemIndex].text_Take.text = string.Get(30022);
    scrollItems_WarriorRepository[itemIndex].event_Take.gameObject:SetActive(Inn.warriorRepository[warriorRepositoryId].status ~= ENpcInnStatus.Dispatch);
    scrollItems_WarriorRepository[itemIndex].event_Take.parameter = warriorRepositoryId;
  else
    scrollItems_WarriorRepository[itemIndex].text_Name.text = "";
    scrollItems_WarriorRepository[itemIndex].text_Hp.text = "";
    scrollItems_WarriorRepository[itemIndex].text_Lv.text = "";
    scrollItems_WarriorRepository[itemIndex].uiCareer:SetCareer();
    scrollItems_WarriorRepository[itemIndex].image_Empty.enabled = true;
    scrollItems_WarriorRepository[itemIndex].image_Dispatch.gameObject:SetActive(false);
    scrollItems_WarriorRepository[itemIndex].event_Take.gameObject:SetActive(false);
    scrollItems_WarriorRepository[itemIndex].event_Take.parameter = nil;
  end

  scrollItems_WarriorRepository[itemIndex].image_Lock.gameObject:SetActive(dataIndex + 1 > Inn.currentWarriorRepositorySpace);

  return true;
end

-- Helper Functions

function UINpcInn.UpdateUI(reset)
  this.UpdateInnNpc();
  this.UpdateFollowNpc();
  this.UpdateInnNpcScroll(reset);
  this.UpdateWarriorRepositoryScroll(reset);
  this.UpdateWarriorRepository();
  this.UpdateContent();
  this.UpdateTag();
end

function UINpcInn.UpdateContent()
  text_NpcSpace.text = string.Concat(table.Count(innNpcs), "/", tostring(Inn.currentSpace));
  text_WarriorRepositorySpace.text = string.Concat(table.Count(warriorRepository), "/", tostring(Inn.currentWarriorRepositorySpace));
end

function UINpcInn.UpdateInnNpc()
  table.Clear(innNpcs);
  for i = 1, Inn.maxSpace do
    if Inn.npcs[i] ~= nil then
      table.insert(innNpcs, i);
    end
  end
  local isInnNpcFull = table.Count(innNpcs) >= Inn.currentSpace;
  local isFollowNpcFull = table.Count(Role.GetFollowNpcs(Role.playerId)) >= Role.maxFollowNpc;
  allFull = isInnNpcFull and isFollowNpcFull;
  exchangeInnIndex = 0;
  exchangeFollowIndex = 0;
end

function UINpcInn.UpdateWarriorRepository()
  table.Clear(warriorRepository);
  for i = 1, Inn.warriorRepositoryMax do
    if Inn.warriorRepository[i] ~= nil then
      table.insert(warriorRepository, i);
    end
  end
  isWarriorRepositoryFull = table.Count(warriorRepository) >= Inn.currentWarriorRepositorySpace;
end

function UINpcInn.UpdateFollowNpc()
  for i = 1, Role.maxFollowNpc do
    local followNpc = Role.GetRole(EHuman.FollowNpc, Role.playerId, i);

    if followNpc ~= nil then
      if followNpc.data.canGrow == 0 then
        followNpcs[i].text_Name.text = string.GetColorText(followNpc.name, Color.Orange);
        followNpcs[i].image_Save.gameObject:SetActive(false);
      elseif followNpc.data.canGrow == 2 then
        followNpcs[i].text_Name.text = string.GetColorText(followNpc.name, Color.MissionNPC);
        followNpcs[i].image_Save.gameObject:SetActive(false);
      else
        followNpcs[i].text_Name.text = followNpc.name;
        followNpcs[i].image_Save.gameObject:SetActive(true);

        if allFull and currentTag == ETag.Inn then
          if i == exchangeFollowIndex then
            followNpcs[i].image_Save.sprite = UI.GetSprite("but167D");
          else
            followNpcs[i].image_Save.sprite = UI.GetSprite("but064D");
          end

          followNpcs[i].text_Save.text = string.Get(21669);
        else
          followNpcs[i].image_Save.sprite = UI.GetSprite("but064D");
          followNpcs[i].text_Save.text = string.Get(30023);
        end
      end

      followNpcs[i].text_Hp.text = followNpc:GetAttribute(EAttribute.Hp);
      followNpcs[i].text_Lv.text = followNpc:GetAttribute(EAttribute.Lv);
      followNpcs[i].uiCareer:SetCareer(followNpc:GetAttribute(EAttribute.Turn), followNpc:GetAttribute(EAttribute.Element), followNpc:GetAttribute(EAttribute.Career), true);
      followNpcs[i].image_State.gameObject:SetActive(followNpc == Role.fightNpc);
      followNpcs[i].image_Empty.enabled = false;
    else
      followNpcs[i].text_Name.text = "";
      followNpcs[i].text_Hp.text = "";
      followNpcs[i].text_Lv.text = "";
      followNpcs[i].uiCareer:SetCareer();
      followNpcs[i].image_State.gameObject:SetActive(false);
      followNpcs[i].image_Save.gameObject:SetActive(false);
      followNpcs[i].image_Empty.enabled = true;
    end
  end
end

function UINpcInn.UpdateInnNpcScroll(reset)
  if reset == true then
    scrollContent_Npc:Reset(Inn.maxSpace);
  else
    scrollContent_Npc:Refresh(Inn.maxSpace);
  end
end

function UINpcInn.UpdateWarriorRepositoryScroll(reset)
  if reset == true then
    scrollContent_WarriorRepository:Reset(Inn.warriorRepositoryMax);
  else
    scrollContent_WarriorRepository:Refresh(Inn.warriorRepositoryMax);
  end
end

function UINpcInn.UpdateTag()
  for _, gameObjects in pairs(tagGameObject) do
    gameObjects:SetActive(false);
  end
  tagGameObject[currentTag]:SetActive(true);
end

function UINpcInn.SetTagDatas()
  tagDatas[ETag.Inn] = { string.Get(10074), nil };
  tagDatas[ETag.WarriorRepository] = { string.Get(24904), nil };
end

-- UI Event Handlers

function UINpcInn.OnClick_Close()
  UI.Close(UINpcInn);
  return true;
end

function UINpcInn.OnClick_AddNpcSpace()
  UISell.Launch(2);
end

function UINpcInn.OnClick_AddWarriorRepositorySpace()
  UISell.Launch(69);
end

function UINpcInn.OnClick_Take(uiEvent)
  if currentTag == ETag.Inn then
    this.OnClick_Take_Inn(uiEvent);
  elseif currentTag == ETag.WarriorRepository then
    this.OnClick_Take_WarriorRepository(uiEvent);
  end
end

function UINpcInn.OnClick_Take_Inn(uiEvent)
  local innIndex = uiEvent.parameter;
  if Inn.npcs[innIndex].status == ENpcInnStatus.Dispatch then return end

  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  if allFull then
    if exchangeFollowIndex ~= 0 then
      --C:031-004 <客棧交換武將> +客棧索引(1) +身上索引(1)
      sendBuffer:Clear();
      sendBuffer:WriteByte(innIndex);
      sendBuffer:WriteByte(exchangeFollowIndex);
      Network.Send(31, 4, sendBuffer);
    else
      if exchangeInnIndex == innIndex then
        exchangeInnIndex = 0;
      else
        exchangeInnIndex = innIndex;
      end

      scrollContent_Npc:Refresh(Inn.maxSpace);
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
    sendBuffer:WriteByte(innIndex);
    Network.Send(31, 2, sendBuffer);
  end
end

function UINpcInn.OnClick_Take_WarriorRepository(uiEvent)
  local warriorRepositoryIndex = uiEvent.parameter;
  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  local isFull = Inn.CheckIsFollowNpcFull();
  if isFull then
    ShowCenterMessage(string.Get(71184));
    return;
  end

  local emptyIndex = Inn.GetFirstEmptyFollowNpcSpace();
  --C:122-003<武將倉庫取出> +跟隨武將編號(1)+NPCID(2)
  sendBuffer:Clear();
  sendBuffer:WriteByte(emptyIndex);
  sendBuffer:WriteUInt16(Inn.warriorRepository[warriorRepositoryIndex].npcId);
  Network.Send(122, 3, sendBuffer);
end

function UINpcInn.OnClick_Save(uiEvent)
  if currentTag == ETag.Inn then
    this.OnClick_Save_Inn(uiEvent);
  elseif currentTag == ETag.WarriorRepository then
    this.OnClick_Save_WarriorRepository(uiEvent);
  end
end

function UINpcInn.OnClick_Save_Inn(uiEvent)
  local followIndex = uiEvent.parameter;
  local followNpc = Role.GetRole(EHuman.FollowNpc, Role.playerId, followIndex);
  if followNpc == nil then return end

  if Contains(followNpc.data.canGrow, 0, 2) then
    ShowCenterMessage(string.Get(21760));
    return;
  end

  if followNpc.data.isRetire then
    ShowCenterMessage(string.Get(71314));
    return;
  end

  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  if followNpc.npcId == Role.rebornNpcId then
    ShowCenterMessage(string.Get(71291));
    return;
  end

  this.CheckEquipUniqueBlood(followIndex, followNpc.npcId, true);
end

function UINpcInn.OnClick_Save_WarriorRepository(uiEvent)
  local followIndex = uiEvent.parameter;
  local followNpc = Role.GetRole(EHuman.FollowNpc, Role.playerId, followIndex);
  if followNpc == nil then return end

  if Contains(followNpc.data.canGrow, 0, 2) then
    ShowCenterMessage(string.Get(21760));
    return;
  end

  if followNpc.data.isRetire then
    ShowCenterMessage(string.Get(71314));
    return;
  end

  if Role.player.war ~= EWar.None then
    ShowCenterMessage(string.Get(80099));
    return;
  end

  if followNpc.npcId == Role.rebornNpcId then
    ShowCenterMessage(string.Get(71291));
    return;
  end

  if isWarriorRepositoryFull then
    ShowCenterMessage(string.Get(24893));
    return;
  end

  local haveGear = table.Count(Item.GetBag(EThings.Equip, followIndex)) ~= 0;
  if haveGear then
    ShowCheckMessage(
      function(result)
        if result then
          Item.UnEquipAll(followIndex);
          this.CheckEquipUniqueBlood(followIndex, followNpc.npcId, false);
        end
      end,
      string.Get(25094),
      Role.player
    );
  else
    this.CheckEquipUniqueBlood(followIndex, followNpc.npcId, false);
  end
end

function UINpcInn.CheckEquipUniqueBlood(followIndex, npcId, bSaveInn)
  table.Clear(UniqueBlood.nowEquipItems);
  local bag = Item.GetBag(EThings.UniqueBloodEquip);
  for k, v in pairs(bag) do
    if v.equipId == npcId then
      v.bagIndex = k;
      table.insert(UniqueBlood.nowEquipItems, v);
    end
  end

  local nowEquipCount = table.Count(UniqueBlood.nowEquipItems);
  if Item.GetBagCount(EThings.UniqueBlood) + nowEquipCount > Item.currentUniqueBloodGrid then
    ShowCenterMessage(string.Get(24049));
    return;
  end

  if nowEquipCount > 0 then
    ShowCheckMessage(
      function(result)
        if result then
          for _, v in pairs(UniqueBlood.nowEquipItems) do
            sendBuffer:Clear();
            sendBuffer:WriteByte(2);
            sendBuffer:WriteUInt16(npcId);
            sendBuffer:WriteByte(followIndex);
            sendBuffer:WriteInt64(v.sn);
            Network.Send(121, 1, sendBuffer);
          end

          if bSaveInn then
            this.SendSaveInn(followIndex);
          else
            this.SendSaveWarrior(followIndex, npcId);
          end
        end
      end,
      string.Get(25095),
      Role.player
    );
  else
    if bSaveInn then
      this.SendSaveInn(followIndex);
    else
      this.SendSaveWarrior(followIndex, npcId);
    end
  end
end

--C:122-002<存入武將倉庫> +跟隨武將編號(1) +NPCID(2)
function UINpcInn.SendSaveWarrior(followIndex, npcId)
  sendBuffer:Clear();
  sendBuffer:WriteByte(followIndex);
  sendBuffer:WriteUInt16(npcId);
  Network.Send(122, 2, sendBuffer);
end

function UINpcInn.SendSaveInn(followIndex)
  if allFull then
    if exchangeInnIndex ~= 0 then
      --C:031-004 <客棧交換武將> +客棧索引(1) +身上索引(1)
      sendBuffer:Clear();
      sendBuffer:WriteByte(exchangeInnIndex);
      sendBuffer:WriteByte(followIndex);
      Network.Send(31, 4, sendBuffer);
    else
      if exchangeFollowIndex == followIndex then
        exchangeFollowIndex = 0;
      else
        exchangeFollowIndex = followIndex;
      end

      this.UpdateFollowNpc();
    end
  else
    if table.Count(innNpcs) >= Inn.currentSpace then
      ShowCenterMessage(string.Get(71276));
      return;
    end

    --C:031-003 <客棧存武將> +身上索引(1)
    sendBuffer:Clear();
    sendBuffer:WriteByte(followIndex);
    Network.Send(31, 3, sendBuffer);
  end
end

function UINpcInn.OnClick_Tag(mainTag)
  currentTag = mainTag;
  this.UpdateUI(true);
  return true;
end

function UINpcInn.OnClick_Help(checkShow)
  if not checkShow then
    UI.Open(UITutorialHint, 83, this);
  end
  return true;
end
