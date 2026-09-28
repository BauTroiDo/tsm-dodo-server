UIRelationship = {}
UIRelationship.name = "UIRelationship";
local this = UIRelationship;

local uiController;
local uiCareer;
local gameObject_RelationshipPanel;
local gameObject_CollectionPanel;
local gameObject_RelationshipBonusScrollRect;
local gameObject_RelationshipPlayerBonusScrollRect;
local gameObject_RelationshipBonusActiveBackground;
local gameObject_RelationshipPlayerBonusActiveBackground;
local gameObject_RelationshipBonusActiveText;
local gameObject_RelationshipPlayerBonusActiveText;
local text_Name;
local text_Percentage;
local text_DontHave;
local rawImage_Warrior;
local rawImage_FiftyPercentGift;
local rawImage_HundredPercentGift;
local event_FiftyPercentGift;
local event_HundredPercentGift;
local slider_CompletePercentage;
local scrollContent_Collection;
local scrollItems_Collection = {};
local scrollContent_RelatedWarrior;
local scrollItems_RelatedWarrior = {};
local scrollContent_RelationshipBonus;
local scrollItems_RelationshipBonus = {};
local scrollContent_RelationshipPlayerBonus;
local scrollItems_RelationshipPlayerBonus = {};

local currentRelationshipIndex;

-- UnityFunctions

function UIRelationship.Initialize(gameObject)
  this.InitUIController(gameObject);
  this.InitContents();
  this.InitCollectionScroll();
  this.InitRelatedWarriorScroll();
  this.InitRelationshipBonusScroll();
  this.InitRelationshipPlayerBonusScroll()
end

function UIRelationship.OnOpen()
  this.ResetPanelState();
  Relationship.SendAskOwnWarriorData();
  this.UpdateCollectionScroll();
  return true;
end

function UIRelationship.OnClose()
end

-- Initializers

function UIRelationship.InitUIController(gameObject)
  this.uiController = gameObject:GetComponent("UIController");
  uiController = this.uiController;
  uiController.onOpen = this.OnOpen;
  uiController.onClose = this.OnClose;
end

function UIRelationship.InitContents()
  gameObject_RelationshipPanel = uiController:FindGameObject("Relationship");
  gameObject_CollectionPanel = uiController:FindGameObject("Collection");
  gameObject_RelationshipBonusScrollRect = uiController:FindGameObject("ScrollRect_RelationshipBonus");
  gameObject_RelationshipPlayerBonusScrollRect = uiController:FindGameObject("ScrollRect_RelationshipPlayerBonus");
  gameObject_RelationshipBonusActiveBackground = uiController:FindGameObject("Image_RelationshipBonusActive");
  gameObject_RelationshipPlayerBonusActiveBackground = uiController:FindGameObject("Image_RelationshipPlayerBonusActive");
  gameObject_RelationshipBonusActiveText = uiController:FindGameObject("Text_RelationshipBonusActive");
  gameObject_RelationshipPlayerBonusActiveText = uiController:FindGameObject("Text_RelationshipPlayerBonusActive");
  rawImage_Warrior = uiController:FindRawImage("RawImage_Warrior").gameObject:AddComponent(typeof(JmpDrawer));
  rawImage_FiftyPercentGift = uiController:FindRawImage("RawImage_FiftyPercentGift"):GetComponent("RawImage");
  rawImage_HundredPercentGift = uiController:FindRawImage("RawImage_HundredPercentGift"):GetComponent("RawImage");
  text_Name = uiController:FindText("Text_Name"):GetComponent("Text");
  text_Percentage = uiController:FindText("Text_Percentage"):GetComponent("Text");
  text_DontHave = uiController:FindText("Text_DontHave"):GetComponent("Text");
  uiCareer = UICareerController.New(uiController:FindGameObject("GameObject_Career"));
  slider_CompletePercentage = uiController:FindSlider("Slider_CompletePercentage");
  event_FiftyPercentGift = uiController:FindEvent("RawImage_FiftyPercentGift");
  event_FiftyPercentGift:SetListener(EventTriggerType.PointerClick, this.OnClick_FiftyPercentGift);
  event_HundredPercentGift = uiController:FindEvent("RawImage_HundredPercentGift");
  event_HundredPercentGift:SetListener(EventTriggerType.PointerClick, this.OnClick_HundredPercentGift);

  local tempEvent = uiController:FindEvent("Image_Close");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Close);
  local tempEvent = uiController:FindEvent("Image_Help");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Help);
  local tempEvent = uiController:FindEvent("Image_Return");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Return);
  local tempEvent = uiController:FindEvent("Image_Next");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Next);
  local tempEvent = uiController:FindEvent("Image_Prev");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Prev);
  local tempEvent = uiController:FindEvent("Image_RelationshipBonus");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_RelationshipBonus);
  local tempEvent = uiController:FindEvent("Image_RelationshipPlayerBonus");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_RelationshipPlayerBonus);
end

function UIRelationship.InitCollectionScroll()
  scrollContent_Collection = uiController:FindScrollContent("ScrollContent_Collection");
  scrollContent_Collection.onInitialize = this.OnInitialize_ScrollContent_Collection;
  scrollContent_Collection.onItemChange = this.OnItemChange_ScrollContent_Collection;
  scrollContent_Collection:Initialize(1);
end

function UIRelationship.OnInitialize_ScrollContent_Collection(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Collection[itemIndex] = {};
    scrollItems_Collection[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_Collection[itemIndex].jmpDrawer_Warrior = scrollItems[itemIndex]:Find("RawImage_Warrior"):GetComponent("RawImage").gameObject:AddComponent(typeof(JmpDrawer));
    scrollItems_Collection[itemIndex].event_Check = scrollItems[itemIndex]:Find("Image_Check"):GetComponent("UIEvent");
    scrollItems_Collection[itemIndex].event_Check:SetListener(EventTriggerType.PointerClick, this.OnClick_Check);
    scrollItems_Collection[itemIndex].event_Favorite = scrollItems[itemIndex]:Find("Image_Favorite"):GetComponent("UIEvent");
    scrollItems_Collection[itemIndex].event_Favorite:SetListener(EventTriggerType.PointerClick, this.OnClick_Favorite);
    scrollItems_Collection[itemIndex].event_NotFavorite = scrollItems[itemIndex]:Find("Image_NotFavorite"):GetComponent("UIEvent");
    scrollItems_Collection[itemIndex].event_NotFavorite:SetListener(EventTriggerType.PointerClick, this.OnClick_NotFavorite);
  end
end

function UIRelationship.OnItemChange_ScrollContent_Collection(dataIndex, itemIndex)
  local relationshipData = Relationship.CollectionScrollItemDatas[dataIndex + 1];
  local mainWarriorId = relationshipData.mainWarriorId;
  local owned = Relationship.GetHighestTurnOwnWarriorNpcId(mainWarriorId) ~= 0;
  local favorites = Setting.client.relationshipFavoriteWarriors;
  local isFavorite = table.Contains(favorites, mainWarriorId);
  local npcId;

  if not owned then
    npcId = collectWarriorSortDatas[mainWarriorId].ids[1];
  else
    npcId = Relationship.GetHighestTurnOwnWarriorNpcId(mainWarriorId);
  end

  this.JmpDrawRole(npcId, scrollItems_Collection[itemIndex].jmpDrawer_Warrior, 0, 0, owned);
  scrollItems_Collection[itemIndex].text_Name.text = npcDatas[npcId].name;
  scrollItems_Collection[itemIndex].event_Check.parameter = dataIndex + 1;
  scrollItems_Collection[itemIndex].event_Favorite.gameObject:SetActive(isFavorite);
  scrollItems_Collection[itemIndex].event_Favorite.parameter = { itemIndex, mainWarriorId };
  scrollItems_Collection[itemIndex].event_NotFavorite.gameObject:SetActive(not isFavorite);
  scrollItems_Collection[itemIndex].event_NotFavorite.parameter = { itemIndex, mainWarriorId };

  return true;
end

function UIRelationship.InitRelatedWarriorScroll()
  scrollContent_RelatedWarrior = uiController:FindScrollContent("ScrollContent_RelatedWarrior");
  scrollContent_RelatedWarrior.onInitialize = this.OnInitialize_ScrollContent_RelatedWarrior;
  scrollContent_RelatedWarrior.onItemChange = this.OnItemChange_ScrollContent_RelatedWarrior;
  scrollContent_RelatedWarrior:Initialize(1);
end

function UIRelationship.OnInitialize_ScrollContent_RelatedWarrior(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_RelatedWarrior[itemIndex] = {};
    scrollItems_RelatedWarrior[itemIndex].jmpDrawer_RelatedWarrior = scrollItems[itemIndex]:Find("RawImage_RelatedWarrior").gameObject:AddComponent(typeof(JmpDrawer));
    scrollItems_RelatedWarrior[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_RelatedWarrior[itemIndex].text_Turn = scrollItems[itemIndex]:Find("Text_Turn"):GetComponent("Text");
    scrollItems_RelatedWarrior[itemIndex].text_Star = scrollItems[itemIndex]:Find("Text_Star"):GetComponent("Text");
    scrollItems_RelatedWarrior[itemIndex].text_DontHave = scrollItems[itemIndex]:Find("Text_DontHave"):GetComponent("Text");
  end
end

function UIRelationship.OnItemChange_ScrollContent_RelatedWarrior(dataIndex, itemIndex)
  local relationshipData = Relationship.CollectionScrollItemDatas[Relationship.CurrentRelationshipIndex];
  local warriorId = relationshipData.relatedWarriorIds[dataIndex + 1];
  local owned = Relationship.GetHighestTurnOwnWarriorNpcId(warriorId) ~= 0;
  local npcId;

  if owned then
    npcId = Relationship.GetHighestTurnOwnWarriorNpcId(warriorId);
    local relatedWarriorData = Relationship.GetOwnWarriorData(npcId);
    scrollItems_RelatedWarrior[itemIndex].text_Turn.text = string.format(string.Get(24642), relatedWarriorData.turn);
    scrollItems_RelatedWarrior[itemIndex].text_Star.text = string.format(string.Get(24643), relatedWarriorData.upgradeLv);
  else
    npcId = collectWarriorSortDatas[warriorId].ids[1];
  end

  this.JmpDrawRole(npcId, scrollItems_RelatedWarrior[itemIndex].jmpDrawer_RelatedWarrior, 0, 0, owned);
  scrollItems_RelatedWarrior[itemIndex].text_Name.text = npcDatas[npcId].name;
  scrollItems_RelatedWarrior[itemIndex].text_Turn.gameObject:SetActive(owned);
  scrollItems_RelatedWarrior[itemIndex].text_Star.gameObject:SetActive(owned);
  scrollItems_RelatedWarrior[itemIndex].text_DontHave.gameObject:SetActive(not owned);

  return true;
end

function UIRelationship.InitRelationshipBonusScroll()
  scrollContent_RelationshipBonus = uiController:FindScrollContent("ScrollContent_RelationshipBonus");
  scrollContent_RelationshipBonus.onInitialize = this.OnInitialize_ScrollContent_RelationshipBonus;
  scrollContent_RelationshipBonus.onItemChange = this.OnItemChange_ScrollContent_RelationshipBonus;
  scrollContent_RelationshipBonus:Initialize(1);
end

function UIRelationship.OnInitialize_ScrollContent_RelationshipBonus(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_RelationshipBonus[itemIndex] = {};
    scrollItems_RelationshipBonus[itemIndex].gameObject_Title = scrollItems[itemIndex]:Find("Title");
    scrollItems_RelationshipBonus[itemIndex].text_Title = scrollItems[itemIndex]:Find("Title"):Find("Text_Title"):GetComponent("Text");
    scrollItems_RelationshipBonus[itemIndex].gameObject_Bonus = scrollItems[itemIndex]:Find("Bonus");
    scrollItems_RelationshipBonus[itemIndex].gameObject_ActiveBackground = scrollItems[itemIndex]:Find("Bonus"):Find("Image_ActiveBackground");
    scrollItems_RelationshipBonus[itemIndex].text_Property = scrollItems[itemIndex]:Find("Bonus"):Find("Text_Property"):GetComponent("Text");
    scrollItems_RelationshipBonus[itemIndex].text_PropertyValue = scrollItems[itemIndex]:Find("Bonus"):Find("Text_PropertyValue"):GetComponent("Text");
  end
end

function UIRelationship.OnItemChange_ScrollContent_RelationshipBonus(dataIndex, itemIndex)
  local bonusData = Relationship.RelationshipBonusScrollItemDatas[dataIndex + 1];
  local isTitle = bonusData.titleId ~= nil;
  local currentRelationship = Relationship.CollectionScrollItemDatas[Relationship.CurrentRelationshipIndex];
  local isHavingMainWarrior = Relationship.GetHighestTurnOwnWarriorNpcId(currentRelationship.mainWarriorId) ~= 0;

  scrollItems_RelationshipBonus[itemIndex].gameObject_Title.gameObject:SetActive(isTitle);
  scrollItems_RelationshipBonus[itemIndex].gameObject_Bonus.gameObject:SetActive(not isTitle);

  if isTitle then
    scrollItems_RelationshipBonus[itemIndex].text_Title.text = Relationship.GetRelationshipBonusText(bonusData.titleId);
  else
    local isActive = Relationship.CheckCondition(bonusData.conditionType, bonusData.conditionValue);
    isActive = isActive and isHavingMainWarrior;
    scrollItems_RelationshipBonus[itemIndex].text_Property.text = string.format(string.Get(24641), bonusData.conditionValue, ItemData.GetAttrText(bonusData.propertiesId));
    scrollItems_RelationshipBonus[itemIndex].text_PropertyValue.text = string.format(string.Get(24682), bonusData.propertiesValue);
    scrollItems_RelationshipBonus[itemIndex].gameObject_ActiveBackground.gameObject:SetActive(isActive);
  end

  return true;
end

function UIRelationship.InitRelationshipPlayerBonusScroll()
  scrollContent_RelationshipPlayerBonus = uiController:FindScrollContent("ScrollContent_RelationshipPlayerBonus");
  scrollContent_RelationshipPlayerBonus.onInitialize = this.OnInitialize_ScrollContent_RelationshipPlayerBonus;
  scrollContent_RelationshipPlayerBonus.onItemChange = this.OnItemChange_ScrollContent_RelationshipPlayerBonus;
  scrollContent_RelationshipPlayerBonus:Initialize(1);
end

function UIRelationship.OnInitialize_ScrollContent_RelationshipPlayerBonus(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_RelationshipPlayerBonus[itemIndex] = {};
    scrollItems_RelationshipPlayerBonus[itemIndex].gameObject_ActiveBackground = scrollItems[itemIndex]:Find("Image_ActiveBackground");
    scrollItems_RelationshipPlayerBonus[itemIndex].text_Property = scrollItems[itemIndex]:Find("Text_Property"):GetComponent("Text");
    scrollItems_RelationshipPlayerBonus[itemIndex].text_PropertyValue = scrollItems[itemIndex]:Find("Text_PropertyValue"):GetComponent("Text");
  end
end

function UIRelationship.OnItemChange_ScrollContent_RelationshipPlayerBonus(dataIndex, itemIndex)
  local relationshipPlayerBonusData = Relationship.RelationshipPlayerBonusScrollItemDatas[dataIndex + 1];
  local currentRelationship = Relationship.CollectionScrollItemDatas[Relationship.CurrentRelationshipIndex];
  local isHundredActive = BitFlag.Get(currentRelationship.hundredPercentPrizeFlag);
  local isFiftyActive = BitFlag.Get(currentRelationship.fiftyPercentPrizeFlag);

  if relationshipPlayerBonusData.titleId == 1 then
    currentRelationshipIndex = 1;
    scrollItems_RelationshipPlayerBonus[itemIndex].text_Property.text = string.Get(24693);
    scrollItems_RelationshipPlayerBonus[itemIndex].text_PropertyValue.text = "";
    scrollItems_RelationshipPlayerBonus[itemIndex].gameObject_ActiveBackground.gameObject:SetActive(false);
  elseif relationshipPlayerBonusData.titleId == 2 then
    currentRelationshipIndex = 2;
    scrollItems_RelationshipPlayerBonus[itemIndex].text_Property.text = string.Get(24846);
    scrollItems_RelationshipPlayerBonus[itemIndex].text_PropertyValue.text = "";
    scrollItems_RelationshipPlayerBonus[itemIndex].gameObject_ActiveBackground.gameObject:SetActive(false);
  else
    scrollItems_RelationshipPlayerBonus[itemIndex].text_Property.text = ItemData.GetAttrText(relationshipPlayerBonusData.bonusType);
    scrollItems_RelationshipPlayerBonus[itemIndex].text_PropertyValue.text = string.format(string.Get(24682), relationshipPlayerBonusData.bonusAmount);
    if currentRelationshipIndex == 1 then
      scrollItems_RelationshipPlayerBonus[itemIndex].gameObject_ActiveBackground.gameObject:SetActive(isHundredActive);
    elseif currentRelationshipIndex == 2 then
      scrollItems_RelationshipPlayerBonus[itemIndex].gameObject_ActiveBackground.gameObject:SetActive(isFiftyActive);
    end
  end

  return true;
end

-- Helper Functions

function UIRelationship.ResetPanelState()
  gameObject_RelationshipPanel:SetActive(false);
  gameObject_CollectionPanel:SetActive(true);
  gameObject_RelationshipBonusScrollRect.gameObject:SetActive(true);
  gameObject_RelationshipPlayerBonusScrollRect.gameObject:SetActive(false);
  gameObject_RelationshipBonusActiveBackground:SetActive(true);
  gameObject_RelationshipPlayerBonusActiveBackground:SetActive(false);
  gameObject_RelationshipBonusActiveText:SetActive(true);
  gameObject_RelationshipPlayerBonusActiveText:SetActive(false);
end

function UIRelationship.UpdateRelationshipPanel()
  Relationship.UpdateCurrentBonusCondition();
  this.UpdateRelatedWarriorScroll();
  this.UpdateRelationshipBonusScroll();
  this.UpdateRelationshipPlayerBonusScroll();
  this.UpdateRole();
  this.UpdatePrize();
end

function UIRelationship.JmpDrawRole(npcId, jmpDrawer_Role, uiOffsetX, uiOffsetY, owned)
  local npcData = npcDatas[npcId];
  local jmpOffsetX, jmpOffsetY = npcData:GetOffset();

  jmpDrawer_Role:GenerateAndDraw(npcData:GetAtlasParams(ERolePose.Prepare + 1));
  jmpDrawer_Role.offsetX = jmpOffsetX + uiOffsetX;
  jmpDrawer_Role.offsetY = jmpOffsetY + uiOffsetY;

  if owned then
    LuaHelper.SetColor(jmpDrawer_Role.gameObject:GetComponent("RawImage"), Color.White);
  else
    LuaHelper.SetColor(jmpDrawer_Role.gameObject:GetComponent("RawImage"), Color.Black);
  end
end

function UIRelationship.UpdateCollectionScroll()
  Relationship.SortRelationshipData();
  scrollContent_Collection:Reset(table.Count(relationshipDatas));
end

function UIRelationship.UpdateRelatedWarriorScroll()
  local currentRelationship = Relationship.CollectionScrollItemDatas[Relationship.CurrentRelationshipIndex];
  scrollContent_RelatedWarrior:Reset(table.Count(currentRelationship.relatedWarriorIds));
end

function UIRelationship.UpdateRelationshipBonusScroll()
  Relationship.SetRelationshipBonusScrollItemData();
  scrollContent_RelationshipBonus:Reset(table.Count(Relationship.RelationshipBonusScrollItemDatas));
end

function UIRelationship.UpdateRelationshipPlayerBonusScroll()
  Relationship.SetRelationshipPlayerBonusScrollItemData();
  scrollContent_RelationshipPlayerBonus:Reset(table.Count(Relationship.RelationshipPlayerBonusScrollItemDatas));
end

function UIRelationship.UpdateRole()
  local currentRelationship = Relationship.CollectionScrollItemDatas[Relationship.CurrentRelationshipIndex];
  local mainWarriorId = currentRelationship.mainWarriorId;
  local owned = Relationship.GetHighestTurnOwnWarriorNpcId(mainWarriorId) ~= 0;
  local turn = Relationship.GetHighestTurnNumber(mainWarriorId);
  local npcId;

  if not owned then
    npcId = collectWarriorSortDatas[mainWarriorId].ids[1];
  else
    npcId = Relationship.GetHighestTurnOwnWarriorNpcId(mainWarriorId);
  end

  this.JmpDrawRole(npcId, rawImage_Warrior, 0, 0, owned);
  text_Name.text = npcDatas[npcId].name;
  text_DontHave.gameObject:SetActive(not owned);
  uiCareer:SetCareer(turn, npcDatas[npcId].element, ECareer.None, true);
  slider_CompletePercentage.value = Relationship.CurrentBonusCondition.CompletePercentage;
  text_Percentage.text = string.format(string.Get(24649), slider_CompletePercentage.value);
end

function UIRelationship.UpdatePrize()
  local currentRelationship = Relationship.CollectionScrollItemDatas[Relationship.CurrentRelationshipIndex];
  local isFiftyPercentPrizeAvailable = currentRelationship.fiftyPercentPrizeFlag ~= 0 and not BitFlag.Get(currentRelationship.fiftyPercentPrizeFlag);
  local isHundredPercentPrizeAvailable = currentRelationship.hundredPercentPrizeFlag ~= 0 and not BitFlag.Get(currentRelationship.hundredPercentPrizeFlag);
  local isHavingMainWarrior = Relationship.GetHighestTurnOwnWarriorNpcId(currentRelationship.mainWarriorId) ~= 0;

  this.SetPrizeImageState(event_FiftyPercentGift, rawImage_FiftyPercentGift, 50, isFiftyPercentPrizeAvailable, isHavingMainWarrior);
  this.SetPrizeImageState(event_HundredPercentGift, rawImage_HundredPercentGift, 100, isHundredPercentPrizeAvailable, isHavingMainWarrior);
end

function UIRelationship.SetPrizeImageState(event, image, percentage, isAvailable, isHavingMainWarrior)
  local completePercentage = Relationship.CurrentBonusCondition.CompletePercentage;

  if isAvailable and isHavingMainWarrior then
    event.gameObject:SetActive(true);
    if completePercentage >= percentage then
      LuaHelper.SetColor(image, Color.White);
      event.enabled = true;
    else
      LuaHelper.SetColor(image, Color.Gray);
      event.enabled = false;
    end
  else
    event.gameObject:SetActive(false);
  end
end

-- UI Event Handlers

function UIRelationship.OnClick_Close()
  UI.Close(this);
end

function UIRelationship.OnClick_Check(uiEvent)
  gameObject_RelationshipPanel:SetActive(true);
  gameObject_CollectionPanel:SetActive(false);
  Relationship.CurrentRelationshipIndex = uiEvent.parameter;
  this.UpdateRelationshipPanel();
end

function UIRelationship.OnClick_Return()
  this.ResetPanelState();
end

function UIRelationship.OnClick_Favorite(uiEvent)
  local itemIndex = uiEvent.parameter[1];
  local warriorId = uiEvent.parameter[2];

  scrollItems_Collection[itemIndex].event_Favorite.gameObject:SetActive(false);
  scrollItems_Collection[itemIndex].event_NotFavorite.gameObject:SetActive(true);

  Relationship.RemoveFavorite(warriorId);
end

function UIRelationship.OnClick_NotFavorite(uiEvent)
  local itemIndex = uiEvent.parameter[1];
  local warriorId = uiEvent.parameter[2];

  scrollItems_Collection[itemIndex].event_Favorite.gameObject:SetActive(true);
  scrollItems_Collection[itemIndex].event_NotFavorite.gameObject:SetActive(false);

  Relationship.AddFavorite(warriorId);
end

function UIRelationship.OnClick_Next()
  Relationship.CurrentRelationshipIndex = Relationship.CurrentRelationshipIndex + 1;
  local relationshipDataCount = table.Count(relationshipDatas);

  if Relationship.CurrentRelationshipIndex > relationshipDataCount then
    Relationship.CurrentRelationshipIndex = Relationship.CurrentRelationshipIndex - relationshipDataCount;
  end

  this.UpdateRelationshipPanel();
end

function UIRelationship.OnClick_Prev()
  Relationship.CurrentRelationshipIndex = Relationship.CurrentRelationshipIndex - 1;
  local relationshipDataCount = table.Count(relationshipDatas);

  if Relationship.CurrentRelationshipIndex <= 0 then
    Relationship.CurrentRelationshipIndex = Relationship.CurrentRelationshipIndex + relationshipDataCount;
  end

  this.UpdateRelationshipPanel();
end

function UIRelationship.OnClick_FiftyPercentGift()
  sendBuffer:Clear();
  sendBuffer:WriteUInt16(Relationship.CollectionScrollItemDatas[Relationship.CurrentRelationshipIndex].mainWarriorId);
  sendBuffer:WriteByte(1);
  Network.Send(112, 2, sendBuffer);
end

function UIRelationship.OnClick_HundredPercentGift()
  sendBuffer:Clear();
  sendBuffer:WriteUInt16(Relationship.CollectionScrollItemDatas[Relationship.CurrentRelationshipIndex].mainWarriorId);
  sendBuffer:WriteByte(2);
  Network.Send(112, 2, sendBuffer);
end

function UIRelationship.OnClick_Help()
  UI.Open(UITutorialHint, 81, this);
end

function UIRelationship.OnClick_RelationshipBonus()
  gameObject_RelationshipBonusScrollRect.gameObject:SetActive(true);
  gameObject_RelationshipPlayerBonusScrollRect.gameObject:SetActive(false);
  gameObject_RelationshipBonusActiveBackground:SetActive(true);
  gameObject_RelationshipPlayerBonusActiveBackground:SetActive(false);
  gameObject_RelationshipBonusActiveText:SetActive(true);
  gameObject_RelationshipPlayerBonusActiveText:SetActive(false);
end

function UIRelationship.OnClick_RelationshipPlayerBonus()
  gameObject_RelationshipBonusScrollRect.gameObject:SetActive(false);
  gameObject_RelationshipPlayerBonusScrollRect.gameObject:SetActive(true);
  gameObject_RelationshipBonusActiveBackground:SetActive(false);
  gameObject_RelationshipPlayerBonusActiveBackground:SetActive(true);
  gameObject_RelationshipBonusActiveText:SetActive(false);
  gameObject_RelationshipPlayerBonusActiveText:SetActive(true);
end

-- Network Event Handlers

function UIRelationship.ReceiveWarriorData()
  this.UpdateCollectionScroll();
end

function UIRelationship.ReceiveReward()
  this.UpdatePrize();
end
