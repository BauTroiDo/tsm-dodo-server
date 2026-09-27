ETreasureTag = {
  Combination = 1,
  Strength = 2,
  Upgrade = 3
}

UITreasure = {}
local this = UITreasure;

this.name = "UITreasure";
this.uiController = nil;

local transform_FrameRect;

--卡片
local gameObject_Card;
local scrollContent_CardView;
local scrollItems_CardView = {};

--組合
local gameObject_Combination;
local gameObject_CombLvRoot;
local scrollContent_CombinationT;
local scrollItems_CombinationT = {};
local scrollContent_CombinationC;
local scrollItems_CombinationC = {};
local image_CombBtn;
local text_CTitle;
local text_CombLv;
local text_CombBtn;
local text_CombinationValue = {};

--強化+升星
local gameObject_LeftRoot;
local text_Title;
local text_AttChange;
local text_Submit;
local image_Arrow;
local items = {};

--強化
local gameObject_Strength;
local text_LvValue;
local text_StrengthValue = {};

--升星
local gameObject_Upgrade;
local gameObject_StarRoot;
local slider_Exp;
local text_Exp;
local text_UpgradeValue = {};
local stars = {};
local itemInNeed = {};

--showDetail
local gameObject_Detail;
local detailRoot;
local detailItems = {};

--var
local currentTag = ETreasureTag.Combination;
local currentSelectComb = 1;
local currentSelectCombData = nil;
local currentSelectCard = 1;
local currentSelectCardData = nil;
local showDetail = false;
local clickTime = 0;

local tagData = {};

function UITreasure.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onVisible = this.OnVisible;

  transform_FrameRect = uiController:FindGameObject("FrameRect").transform;

  local tempEvent;

    --region leftRoot
  gameObject_LeftRoot = uiController:FindGameObject("LeftRoot");
  gameObject_CombLvRoot = uiController:FindGameObject("LvRoot");

  text_Title = uiController:FindText("Text_Title");
  text_AttChange = uiController:FindText("Text_AttChange");
  text_Submit = uiController:FindText("Text_Submit");

  image_Arrow = uiController:FindImage("Image_Arrow");

  local root = uiController:FindGameObject("ItemRoot").transform;

  for i = 1, 3 do
    items[i] = {};
    items[i].itemBG = UIItemController.New(root:Find(string.Concat("Image_ItemBG (", i, ")")));
    items[i].gameObject = root:Find(string.Concat("Image_ItemBG (", i, ")")).gameObject;
    items[i].text_Name = root:Find(string.Concat("Image_ItemBG (", i, ")")):Find("Text_Name"):GetComponent("Text");
    items[i].text_Count = root:Find(string.Concat("Image_ItemBG (", i, ")")):Find("Text_Count"):GetComponent("Text");
  end

  tempEvent = uiController:FindEvent("Image_Submit");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Submit);
  --endregion

  --region card
  gameObject_Card = uiController:FindGameObject("Card");

  scrollContent_CardView = uiController:FindScrollContent("ScrollContent_CardView");
  scrollContent_CardView.onInitialize = this.OnInitialize_ScrollContent_CardView;
  scrollContent_CardView.onItemChange = this.OnItemChange_ScrollContent_CardView;
  scrollContent_CardView:Initialize(1);
  --endregion

  --region combination
  gameObject_Combination = uiController:FindGameObject("Combination");

  image_CombBtn = uiController:FindImage("Image_CombinationBtn");

  text_CTitle = uiController:FindText("Text_CTitle");
  text_CombLv = uiController:FindText("Text_CombLv");
  text_CombBtn = uiController:FindText("Text_CombBtn");

  for i = 1, 3 do
    text_CombinationValue[i] = uiController:FindText(string.Concat("Text_V", i));
  end

  scrollContent_CombinationT = uiController:FindScrollContent("ScrollContent_CombinationT");
  scrollContent_CombinationT.onInitialize = this.OnInitialize_ScrollContent_CombinationT;
  scrollContent_CombinationT.onItemChange = this.OnItemChange_ScrollContent_CombinationT;
  scrollContent_CombinationT:Initialize(1);

  scrollContent_CombinationC = uiController:FindScrollContent("ScrollContent_CombinationC");
  scrollContent_CombinationC.onInitialize = this.OnInitialize_ScrollContent_CombinationC;
  scrollContent_CombinationC.onItemChange = this.OnItemChange_ScrollContent_CombinationC;
  scrollContent_CombinationC:Initialize(1);

  tempEvent = uiController:FindEvent("Image_Detail");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_OpenDetail);
  --endregion

    --region strength
  gameObject_Strength = uiController:FindGameObject("Strength");

  text_LvValue = uiController:FindText("Text_LvValue");

  root = uiController:FindGameObject("STextRoot").transform;
  for i = 1, 3 do
    text_StrengthValue[i] = root:Find(string.Concat("Text_V", i)):GetComponent("Text");
  end
  --endregion

    --region upgrade
  gameObject_Upgrade = uiController:FindGameObject("Upgrade");
  gameObject_StarRoot = uiController:FindGameObject("StarRoot");

  root = uiController:FindGameObject("StarBe").transform;
  local afRoot = uiController:FindGameObject("StarAf").transform;
  stars.be = {};
  stars.af = {};

  for i = 1, Treasure.MAX_STAR do
    stars.be[i] = root:Find(string.Concat("Image_Star (", i, ")")):GetComponent("Image");
    stars.af[i] = afRoot:Find(string.Concat("Image_Star (", i, ")")):GetComponent("Image");
  end

  root = uiController:FindGameObject("UTextRoot").transform;
  for i = 1, 3 do
    text_UpgradeValue[i] = {};
    text_UpgradeValue[i].str = root:Find(string.Concat("Text_V", i)):GetComponent("Text");
    text_UpgradeValue[i].lv = root:Find(string.Concat("Text_V", i)):Find("Image_Star"):Find("Text_Lv"):GetComponent("Text");
  end

  text_Exp = uiController:FindText("Text_Exp");
  slider_Exp = uiController:FindSlider("Slider_Exp");

  tempEvent = uiController:FindEvent("Image_10Time");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Upgrade);
  tempEvent.parameter = 10;
  tempEvent = uiController:FindEvent("Image_5Time");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Upgrade);
  tempEvent.parameter = 5;
  --endregion

    --region Setting
  gameObject_Detail = uiController:FindGameObject("Detail");
  detailRoot = UITreasureDetail.New(gameObject_Detail.transform);

  --endregion
end

function UITreasure.OnOpen()
  currentTag = ETreasureTag.Combination;
  currentSelectComb = 1;
  currentSelectCard = 1;
  slider_Exp.maxValue = 100;
  showDetail = false;
  clickTime = 0;

  for i = 1, 3 do
    itemInNeed[i] = {};
  end

  return true;
end

function UITreasure.OnVisible(visible)
  if visible then
    tagData[ETreasureTag.Combination] = { string.Get(24589), nil };
    tagData[ETreasureTag.Strength] = { string.Get(24587), nil };
    tagData[ETreasureTag.Upgrade] = { string.Get(24588), nil };

    UIFrame.Active(transform_FrameRect, string.Get(24586), this.OnClick_Close, this.OnClick_Help, this.OnClick_Tag, tagData, currentTag);

    this.UpdateUI(true);
  end
end

function UITreasure.OnClick_Help(checkShow)
  if checkShow ~= true then
    UI.Open(UITutorialHint, 80, this);
  end

  return true;
end

function UITreasure.OnClick_Tag(tag)
  currentTag = tag;
  currentSelectComb = 1;
  currentSelectCard = 1;
  showDetail = false;
  clickTime = 0;

  this.UpdateUI(true);

  return true;
end

function UITreasure.OnClick_Close()
  UI.Close(this);

  return true;
end

  --region update
function UITreasure.UpdateUI(reset)
  gameObject_Combination:SetActive(currentTag == ETreasureTag.Combination);
  gameObject_Detail:SetActive(showDetail);
  gameObject_Strength:SetActive(currentTag == ETreasureTag.Strength);
  gameObject_Upgrade:SetActive(currentTag == ETreasureTag.Upgrade);
  gameObject_LeftRoot:SetActive(Contains(currentTag, ETreasureTag.Strength, ETreasureTag.Upgrade));
  gameObject_Card:SetActive(Contains(currentTag, ETreasureTag.Strength, ETreasureTag.Upgrade));

  this.UpdateCombinationText(reset);
  this.UpdateDetail();
  this.UpdateCardPage(reset);
  this.UpdateStrength();
  this.UpdateUpgrade();
end

function UITreasure.UpdateCombinationText(reset)
  if currentTag ~= ETreasureTag.Combination then return end

  if reset then
    scrollContent_CombinationT:Reset(table.Count(Treasure.treasureCombination));
  else
    scrollContent_CombinationT:Refresh(table.Count(Treasure.treasureCombination));
  end

  this.UpdateCombinationCard(reset);
end

function UITreasure.UpdateCombinationCard(reset)
  if currentTag ~= ETreasureTag.Combination then return end

  currentSelectCombData = Treasure.treasureCombination[currentSelectComb];
  local lv = Treasure.treasureCombination[currentSelectComb].level;

  text_CTitle.text = string.Get(currentSelectCombData.string);
  text_CombLv.text = lv;

  if lv > Treasure.MAX_LEVEL - 2 then
    lv = Treasure.MAX_LEVEL - 2;
  end

  if lv == 0 then
    lv = 1;
  end

  for i = 0, 2 do
    text_CombinationValue[i + 1].text = string.Concat(string.Get(71154), lv + i, " ",
      Treasure.GetCombAttrText(currentSelectComb, lv + i));
  end

  if reset then
    scrollContent_CombinationC:Reset(table.Count(currentSelectCombData.treasureId));
  else
    scrollContent_CombinationC:Refresh(table.Count(currentSelectCombData.treasureId));
  end
end

function UITreasure.UpdateCardPage(reset)
  if not Contains(currentTag, ETreasureTag.Strength, ETreasureTag.Upgrade) then return end

  if reset then
    scrollContent_CardView:Reset(table.Count(Treasure.treasureItems));
  else
    scrollContent_CardView:Refresh(table.Count(Treasure.treasureItems));
  end
end

function UITreasure.UpdateStrength()
  if currentTag ~= ETreasureTag.Strength then return end
  currentSelectCardData = Treasure.treasureItems[currentSelectCard];
  local data = currentSelectCardData.lvData;
  local now = currentSelectCardData.level;
  local next = Treasure.MAX_LEVEL;

  if now < Treasure.MAX_LEVEL then
    next = now + 1;
  end

  itemInNeed[1].itemId = currentSelectCardData.itemId;
  itemInNeed[1].name = currentSelectCardData.name;
  itemInNeed[1].count = currentSelectCardData.lvData[next].count;
  itemInNeed[1].hasQunat = Treasure.GetCardCount(currentSelectCardData, now);

  itemInNeed[2].itemId = 29869;
  itemInNeed[2].name = itemDatas[itemInNeed[2].itemId].name;
  itemInNeed[2].count = currentSelectCardData.lvData[next].itemCount;
  itemInNeed[2].hasQunat = Item.GetItemCount(itemInNeed[2].itemId);

  itemInNeed[3].itemId = 65351;
  itemInNeed[3].name = itemDatas[65351].name;
  itemInNeed[3].count = currentSelectCardData.lvData[next].moneyCount;
  itemInNeed[3].hasQunat = Role.GetPlayerGold();

  this.UpdateLeftRoot(data, now, next);

  if now == next then
    text_LvValue.text = string.Get(20170);
  else
    text_LvValue.text = string.Concat(now, "->", next);
  end

  if now == 0 then
    text_Submit.text = string.Get(24635);
  else
    text_Submit.text = string.Get(24587);
  end

  if now == 0 then
    now = 1;
  elseif now > Treasure.MAX_LEVEL - 2 then
    now = Treasure.MAX_LEVEL - 2;
  end

  for i = 0, 2 do
    local str = string.Concat(string.Get(71154), (now + i), " ", Treasure.GetCardAttrText(data, now + i));

    if now + i == currentSelectCardData.level then
      str = string.GetColorText(str, Color.DarkGreen);
    end

    text_StrengthValue[i + 1].text = str;
  end
end

function UITreasure.UpdateUpgrade()
  if currentTag ~= ETreasureTag.Upgrade then return end
  currentSelectCardData = Treasure.treasureItems[currentSelectCard];

  local data = currentSelectCardData.starData;
  local now = currentSelectCardData.star;
  local next = Treasure.MAX_STAR;

  if now < Treasure.MAX_STAR then
    next = now + 1;
  end

  image_Arrow.enabled = now ~= Treasure.MAX_STAR;

  for i = 1, Treasure.MAX_STAR do
    if now >= i then
      stars.be[i].sprite = UI.GetSprite("icon153");
    else
      stars.be[i].sprite = UI.GetSprite("icon201");
    end

    stars.af[i].enabled = now ~= Treasure.MAX_STAR;

    if now ~= Treasure.MAX_STAR then
      if next >= i then
        stars.af[i].sprite = UI.GetSprite("icon153");
      else
        stars.af[i].sprite = UI.GetSprite("icon201");
      end
    end
  end

  itemInNeed[1].itemId = 29870;
  itemInNeed[1].name = itemDatas[itemInNeed[1].itemId].name;
  itemInNeed[1].count = currentSelectCardData.starData[next].itemCount;
  itemInNeed[1].hasQunat = Item.GetItemCount(itemInNeed[1].itemId);

  itemInNeed[2].itemId = 65351;
  itemInNeed[2].name = itemDatas[itemInNeed[2].itemId].name;
  itemInNeed[2].count = currentSelectCardData.starData[next].moneyCount;
  itemInNeed[2].hasQunat = Role.GetPlayerGold();

  itemInNeed[3].itemId = 0;
  itemInNeed[3].name = 0;
  itemInNeed[3].count = 0;
  itemInNeed[3].hasQunat = 0;

  this.UpdateLeftRoot(data, now, next);

  text_Submit.text = string.Get(24588);

  if now == 0 then
    now = 1;
  elseif now > Treasure.MAX_STAR - 2 then
    now = Treasure.MAX_STAR - 2;
  end

  for i = 0, 2 do
    local str = Treasure.GetCardAttrText(data, now + i);

    if now + i == currentSelectCardData.star then
      str = string.GetColorText(str, Color.DarkGreen);
    end

    text_UpgradeValue[i + 1].str.text = str;
    text_UpgradeValue[i + 1].lv.text = now + i;
  end

  slider_Exp.value = currentSelectCardData.exp;
  text_Exp.text = string.Concat(currentSelectCardData.exp, "%");
end

function UITreasure.UpdateLeftRoot(data, now, next)
  if not Contains(currentTag, ETreasureTag.Strength, ETreasureTag.Upgrade) then return end

  local attChange = "";

  if now == 0 then
    attChange = "";
  elseif (currentTag == ETreasureTag.Strength and now == Treasure.MAX_LEVEL) or
    (currentTag == ETreasureTag.Upgrade and now == Treasure.MAX_STAR) then
    attChange = Treasure.GetCardAttrText(data, now);
  else
    attChange = string.Concat(Treasure.GetCardAttrText(data, now),
      "->", string.GetColorText(data[next].value, Color.DarkGreen));
  end

  for i = 1, 3 do
    items[i].itemBG:SetItem(itemInNeed[i].itemId);

    if itemInNeed[i].itemId ~= 0 then
      items[i].text_Name.text = itemInNeed[i].name;

      local quant = "";
      if itemInNeed[i].hasQunat < itemInNeed[i].count then
        quant = string.GetColorText(itemInNeed[i].hasQunat, Color.Red);
      else
        quant = itemInNeed[i].hasQunat;
      end

      if itemInNeed[i].itemId == 65351 then
        items[i].text_Count.text = itemInNeed[i].count;
      else
        items[i].text_Count.text = string.Concat(quant, "/", itemInNeed[i].count);
      end

      if currentTag == ETreasureTag.Strength then
        if Treasure.IsMaxLevel(currentSelectCard) then
          items[i].text_Count.text = "-";
        end
      elseif currentTag == ETreasureTag.Upgrade then
        if currentSelectCardData.star == Treasure.MAX_STAR then
          items[i].text_Count.text = "-";
        end
      end
    else
      items[i].text_Name.text = "";
      items[i].text_Count.text = "";
    end
  end

  items[3].gameObject:SetActive(currentTag == ETreasureTag.Strength);

  text_Title.text = currentSelectCardData.name;
  text_AttChange.text = attChange;
end

function UITreasure.UpdateDetail()
  gameObject_Detail:SetActive(showDetail);

  if currentTag ~= ETreasureTag.Combination then return end

  detailRoot:ShowInfo();
end

  --endregion

  --region scrollItems
function UITreasure.OnInitialize_ScrollContent_CombinationT(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_CombinationT[itemIndex] = {};
    scrollItems_CombinationT[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_CombinationT[itemIndex].LvRoot = scrollItems[itemIndex]:Find("GameObject_Lv").gameObject;
    scrollItems_CombinationT[itemIndex].text_Lv = scrollItems_CombinationT[itemIndex].LvRoot.transform:Find("Text_Lv"):GetComponent("Text");
    scrollItems_CombinationT[itemIndex].image_Select = scrollItems[itemIndex]:Find("Image_SelectBut"):GetComponent("Image");
    scrollItems_CombinationT[itemIndex].event_Select = scrollItems[itemIndex]:GetComponent("UIEvent");
    scrollItems_CombinationT[itemIndex].event_Select:SetListener(EventTriggerType.PointerClick, this.OnClick_Combination);
  end
end

function UITreasure.OnItemChange_ScrollContent_CombinationT(dataIndex, itemIndex)
  local index = dataIndex + 1;
  local data = Treasure.treasureCombination[index];

  scrollItems_CombinationT[itemIndex].event_Select.parameter = index;

  if data ~= nil then
    scrollItems_CombinationT[itemIndex].text_Name.text = string.Get(data.string);
    scrollItems_CombinationT[itemIndex].text_Lv.text = data.level;
    scrollItems_CombinationT[itemIndex].image_Select.enabled = currentSelectComb == index;
  end

  return true;
end

function UITreasure.OnInitialize_ScrollContent_CombinationC(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_CombinationC[itemIndex] = {};
    scrollItems_CombinationC[itemIndex].cardController = UITreasureCard.New(scrollItems[itemIndex]);
  end
end

function UITreasure.OnItemChange_ScrollContent_CombinationC(dataIndex, itemIndex)
  local index = dataIndex + 1;
  local treasureId = currentSelectCombData.treasureId[index];

  if treasureId ~= nil or treasureId ~= 0 then
    scrollItems_CombinationC[itemIndex].cardController:SetData(treasureId);

    return true;
  else
    return false;
  end
end

function UITreasure.OnInitialize_ScrollContent_CardView(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_CardView[itemIndex] = {};
    scrollItems_CardView[itemIndex].cardController = UITreasureCard.New(scrollItems[itemIndex]:Find("CardRoot"));
    scrollItems_CardView[itemIndex].image_Select = scrollItems[itemIndex]:Find("Image_Select"):GetComponent("Image");
  end
end

function UITreasure.OnItemChange_ScrollContent_CardView(dataIndex, itemIndex)
  local index = dataIndex + 1;

  scrollItems_CardView[itemIndex].cardController:SetData(index, this.OnClick_Card);
  scrollItems_CardView[itemIndex].image_Select.enabled = currentSelectCard == index;

  return true;
end

  --endregion

  --region onclick
function UITreasure.OnClick_OpenDetail()
  showDetail = not showDetail;

  this.UpdateDetail();
end

function UITreasure.OnClick_Combination(uiEvent)
  currentSelectComb = uiEvent.parameter;

  this.UpdateCombinationText();
  this.UpdateCombinationCard(true);
end

function UITreasure.OnClick_Card(uiEvent)
  currentSelectCard = uiEvent.parameter;

  this.UpdateUI();
end

function UITreasure.OnClick_Submit()
  if FightField.isInBattle then
    ShowCenterMessage(string.Get(21806));
    return;
  end

  if clickTime ~= 0 and (CGTimer.serverTime - clickTime).TotalSeconds < 1 then
    ShowCenterMessage(string.Get(22841));
    return;
  else
    clickTime = CGTimer.serverTime;
  end

  if currentTag == ETreasureTag.Strength then
    if Treasure.IsMaxLevel(currentSelectCard) then
      ShowCenterMessage(string.Get(21684));
      return;
    end

    if itemInNeed[1].count > itemInNeed[1].hasQunat or itemInNeed[2].count > itemInNeed[2].hasQunat then
      ShowCenterMessage(string.Get(24591));
      return;
    end

    if itemInNeed[3].count > itemInNeed[3].hasQunat then
      ShowCenterMessage(string.Get(22314));
      return;
    end

    --C: 123-002 <升階> +祕寶ID(2) +Count(4) +素材背包Idx(2)
    sendBuffer:Clear();
    sendBuffer:WriteUInt16(currentSelectCard);
    sendBuffer:WriteInt32(1);
    sendBuffer:WriteUInt16(Item.GetItemIndex(itemInNeed[2].itemId));
    Network.Send(123, 2, sendBuffer);

  elseif currentTag == ETreasureTag.Upgrade then
    if currentSelectCardData.level == 0 then
      ShowCenterMessage(string.Get(24636));
      return;
    end

    if currentSelectCardData.star == Treasure.MAX_STAR then
      ShowCenterMessage(string.Get(21684));
      return;
    end

    if itemInNeed[1].count > itemInNeed[1].hasQunat then
      ShowCenterMessage(string.Get(24592));
      return;
    end

    if itemInNeed[2].count > itemInNeed[2].hasQunat then
      ShowCenterMessage(string.Get(22314));
      return;
    end

    --C: 123-003 <升星> +祕寶ID(2) +Count(4) +素材背包Idx(2)
    sendBuffer:Clear();
    sendBuffer:WriteUInt16(currentSelectCard);
    sendBuffer:WriteInt32(1);
    sendBuffer:WriteUInt16(Item.GetItemIndex(itemInNeed[1].itemId));
    Network.Send(123, 3, sendBuffer);
  end
end

function UITreasure.OnClick_Upgrade(uiEvent)
  if FightField.isInBattle then
    ShowCenterMessage(string.Get(21806));
    return;
  end

  if clickTime ~= 0 and (CGTimer.serverTime - clickTime).TotalSeconds < 1 then
    ShowCenterMessage(string.Get(22841));
    return;
  else
    clickTime = CGTimer.serverTime;
  end

  if currentSelectCardData.level == 0 then
    ShowCenterMessage(string.Get(24636));
    return;
  end

  if currentSelectCardData.star == Treasure.MAX_STAR then
    ShowCenterMessage(string.Get(21684));
    return;
  end

  local times = uiEvent.parameter;

  if itemInNeed[1].count > itemInNeed[1].hasQunat then
    ShowCenterMessage(string.Get(24592));
    return;
  end

  if itemInNeed[2].count > itemInNeed[2].hasQunat then
    ShowCenterMessage(string.Get(22314));
    return;
  end

  --C: 123-003 <升星> +祕寶ID(2) +Count(4) +素材背包Idx(2)
  sendBuffer:Clear();
  sendBuffer:WriteUInt16(currentSelectCard);
  sendBuffer:WriteInt32(times);
  sendBuffer:WriteUInt16(Item.GetItemIndex(itemInNeed[1].itemId));
  Network.Send(123, 3, sendBuffer);
end

  --endregion
