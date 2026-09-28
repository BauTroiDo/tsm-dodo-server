UIBag = {}
local this = UIBag;

this.name = "UIBag";
this.uiController = nil;

--ui
local transform_Anchor;
local image_BG;
local event_Mask;
local gameObject_Category;
local imageCategory = {};
local text_Count;
local image_Refresh;

--var
local currentClickEvent = nil;
local currentFilter = nil;
local currentCategory = EThingsCategory.All;
local interactable = true;
local multiSelectCount = 0;
local currentSelections = {};

local scrollContent_Collection;
local scrollItems_Collection = {};

local isInitialize = false;
local bagCategory = {};
local currentBagKind = EThings.Bag;
local cancelBlink = false;
local showIndex = false;

function UIBag.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onClose = this.OnClose;

  transform_Anchor = uiController:FindGameObject("Anchor").transform;
  gameObject_Category = uiController:FindGameObject("Category").transform;

  image_BG = uiController:FindImage("Image_BG");

  text_Count = uiController:FindText("Text_Count");

  image_Refresh = uiController:FindEvent("Image_Refresh");
  image_Refresh:SetListener(EventTriggerType.PointerClick, this.OnClick_Refresh);

  event_Mask = uiController:FindEvent("Image_Mask");
  event_Mask:SetListener(EventTriggerType.PointerClick, this.OnClick_Close);

  local event = uiController:FindEvent("Image_Close");
  event:SetListener(EventTriggerType.PointerClick, this.OnClick_Close);

  event = uiController:FindEvent("Image_All");
  event:SetListener(EventTriggerType.PointerClick, this.OnClick_Category);
  event.parameter = EThingsCategory.All;

  event = uiController:FindEvent("Image_Equipment");
  event:SetListener(EventTriggerType.PointerClick, this.OnClick_Category);
  event.parameter = EThingsCategory.Equip;

  event = uiController:FindEvent("Image_Props");
  event:SetListener(EventTriggerType.PointerClick, this.OnClick_Category);
  event.parameter = EThingsCategory.Props;

  event = uiController:FindEvent("Image_Material");
  event:SetListener(EventTriggerType.PointerClick, this.OnClick_Category);
  event.parameter = EThingsCategory.Material;

  imageCategory[EThingsCategory.All] = uiController:FindImage("Image_All");
  imageCategory[EThingsCategory.Equip] = uiController:FindImage("Image_Equipment");
  imageCategory[EThingsCategory.Props] = uiController:FindImage("Image_Props");
  imageCategory[EThingsCategory.Material] = uiController:FindImage("Image_Material");

  scrollContent_Collection = uiController:FindScrollContent("ScrollContent_Collection");
  scrollContent_Collection.onInitialize = this.OnInitialize_ScrollContent_Collection;
  scrollContent_Collection.onItemChange = this.OnItemChange_ScrollContent_Collection;
  scrollContent_Collection:Initialize(1);

  isInitialize = true;
end

function UIBag.OnOpen(clickEvent, anchorPosition, showBG, selectCount, filter, showCategory, showRefresh, bagkind)
  interactable = true;

  currentClickEvent = clickEvent;
  currentFilter = filter;

  if anchorPosition ~= nil then
    transform_Anchor.localPosition = anchorPosition;
  else
    transform_Anchor.localPosition = Vector3.zero;
  end

  image_BG.gameObject:SetActive(showBG ~= false);
  gameObject_Category.gameObject:SetActive(showCategory ~= false);
  image_Refresh.gameObject:SetActive(showRefresh ~= false);

  if showBG ~= false then
    if showCategory ~= false then
      image_BG.rectTransform.offsetMax = Vector2.New(10, 58);
    else
      image_BG.rectTransform.offsetMax = Vector2.New(10, 9);
    end
  end

  if bagkind ~= nil then
    currentBagKind = bagkind;
  else
    currentBagKind = EThings.Bag;
  end

  event_Mask.gameObject:SetActive(showBG ~= false);

  if selectCount ~= nil then
    multiSelectCount = selectCount;
  else
    multiSelectCount = 0;
  end

  currentCategory = EThingsCategory.All;

  this.ClearSelect(false);

  return true;
end

function UIBag.OnClose()
  RedDot.Remove(ERedDot.Bag);

  Item.ClearRedDot();

  return true;
end

function UIBag.UpdateUI(refresh)
  if not isInitialize then return end

  --處理filter
  if currentFilter ~= nil then
    bagCategory = Item.GetBagByFilter(currentFilter, currentCategory, currentBagKind);
  else
    bagCategory = Item.GetBagByCategory(currentCategory, currentBagKind);
  end
  
  local showCount;
  local slotCountInRow = 5;
  if currentBagKind == EThings.UniqueBlood then
    showCount = math.min((math.ceil(Item.currentUniqueBloodGrid / 5) + 1) * 5, Item.maxUniqueBloodBagGrid);
  elseif currentBagKind ~= EThings.OutfitStyleInCollection then
    showCount = math.min((math.ceil(Item.currentThingGrid / 5) + 1) * 5, Item.MaxThingGrid);
  else
    showCount = CollectStyle.GetTotalCollectionDataCount()* slotCountInRow;
  end
  
  if refresh ~= false then
    scrollContent_Collection:Refresh(showCount);
  else
    scrollContent_Collection:Reset(showCount);
  end

  this.UpdateCategoryIcon();
  if currentBagKind == EThings.UniqueBlood then
    text_Count.text = string.Concat(Item.GetBagCount(currentBagKind, 0), "/", Item.currentUniqueBloodGrid);
  elseif currentBagKind ~= EThings.OutfitStyleInCollection then
    text_Count.text = string.Concat(Item.GetBagCount(currentBagKind, 0), "/", Item.currentThingGrid);
  else
    text_Count.text = "";
  end
end

function UIBag.SetInteractable(value)
  interactable = value;
end

function UIBag.GetSelect()
  return currentSelections;
end

function UIBag.ClearSelect(trigEvent)
  for k, v in pairs(currentSelections) do
    currentSelections[k] = false;
  end

  this.UpdateUI(false);

  if trigEvent ~= false and currentClickEvent ~= nil then
    currentClickEvent(currentSelections);
  end
end

function UIBag.OnClick_Close()
  UI.Close(UIBag);
end

function UIBag.OnClick_Item(uiEvent)
  if not interactable then return end

  local itemSave = uiEvent.parameter;
  if itemSave == nil then return end

  if multiSelectCount > 0 then
    if currentSelections[itemSave.bagIndex] == true then
      currentSelections[itemSave.bagIndex] = false;
    else
      if multiSelectCount > 1 then
        local count = 0;
        for i = 1, Item.MaxThingGrid do
          if currentSelections[i] then
            count = count + 1;
          end
        end

        if count >= multiSelectCount then return end
      else
        for i = 1, Item.MaxThingGrid do
          if currentSelections[i] then
            currentSelections[i] = false;
          end
        end
      end

      currentSelections[itemSave.bagIndex] = true;
    end

    this.UpdateUI();

    if currentClickEvent ~= nil then
      currentClickEvent(currentSelections);
    end
  else
    if currentClickEvent ~= nil then
      currentClickEvent(itemSave.bagIndex, itemSave.Id);
    else
      UI.Open(UIItemInfo, currentBagKind, itemSave.bagIndex);
    end
  end
end

function UIBag.GetItemSave(index)
  local itemSave;

  if currentFilter ~= nil then
    itemSave = Item.GetBag(EThings.Filter)[index];
  else
    itemSave = Item.GetBag(EThings.Category)[index];
  end

  return itemSave;
end

function UIBag.OnClick_UnLock(uiEvent)
  if not interactable then return end

  if currentBagKind == EThings.UniqueBlood then
    ShowCenterMessage(string.Get(24075));
  else
    UISell.Launch(3);
  end
end

function UIBag.SetBlink(bagIndex, time)
  local itemController = this.GetVisualItem(bagIndex);

  if itemController == nil then return end

  cancelBlink = false;
  itemController:SetIcon(false);
  itemController:SetLight(false);

  CGTimer.DoFunctionDelay(time,
    function()
      if cancelBlink then
        cancelBlink = false;
      else
        itemController:SetIcon(true);

        if itemController.itemSave ~= nil and itemController.itemSave.stoneLv ~= nil and itemController.itemSave.stoneLv > 0 then
          itemController:SetLight(true);
        end
        itemController:SetRedDot(true);
      end
    end
  );
end

function UIBag.SkidBlink()
  cancelBlink = true;
end

function UIBag.OnInitialize_ScrollContent_Collection(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Collection[itemIndex] = UIItemController.New(scrollItems[itemIndex]);
  end
end

function UIBag.OnItemChange_ScrollContent_Collection(dataIndex, itemIndex)
  local itemSave = bagCategory[dataIndex + 1];
  local visualIndex = dataIndex + 1;

  if itemSave ~= nil then
    scrollItems_Collection[itemIndex]:SetItem(itemSave.Id, itemSave.quant, itemSave);
    scrollItems_Collection[itemIndex]:SetEvent(this.OnClick_Item, itemSave, visualIndex);
    scrollItems_Collection[itemIndex]:SetSelect(currentSelections[itemSave.bagIndex]);
    scrollItems_Collection[itemIndex]:SetRedDot(itemSave.redDot);

    if itemSave.inUse then
      scrollItems_Collection[itemIndex]:SetInUse(true);
    end
  else
    scrollItems_Collection[itemIndex]:SetItem(0);
    scrollItems_Collection[itemIndex]:SetEvent(this.OnClick_Item);
    scrollItems_Collection[itemIndex]:SetSelect(false);
    scrollItems_Collection[itemIndex]:SetRedDot(false);

    if (dataIndex + 1 > Item.currentThingGrid and currentBagKind ~= EThings.UniqueBlood) or
      (dataIndex + 1 > Item.currentUniqueBloodGrid and currentBagKind == EThings.UniqueBlood)
        and currentBagKind ~= EThings.OutfitStyleInCollection then
      scrollItems_Collection[itemIndex]:SetLock(true);
      scrollItems_Collection[itemIndex]:SetEvent(this.OnClick_UnLock);
    else
      scrollItems_Collection[itemIndex]:SetLock(false);
      scrollItems_Collection[itemIndex]:SetEvent(this.OnClick_Item);
    end
  end

  return true;
end

function UIBag.OnClick_Category(uiEvent)
  local index = uiEvent.parameter;

  if currentCategory == index then return end

  currentCategory = index;

  if multiSelectCount > 0 then
    this.ClearSelect();
  end

  this.UpdateUI(false);
end

function UIBag.UpdateCategoryIcon()
  imageCategory[EThingsCategory.All].sprite = UI.GetSprite("but061D");
  imageCategory[EThingsCategory.Equip].sprite = UI.GetSprite("but060D");
  imageCategory[EThingsCategory.Props].sprite = UI.GetSprite("but059D");
  imageCategory[EThingsCategory.Material].sprite = UI.GetSprite("but058D");

  if currentCategory == EThingsCategory.All then
    imageCategory[currentCategory].sprite = UI.GetSprite("but061L");
  elseif currentCategory == EThingsCategory.Equip then
    imageCategory[currentCategory].sprite = UI.GetSprite("but060L");
  elseif currentCategory == EThingsCategory.Props then
    imageCategory[currentCategory].sprite = UI.GetSprite("but059L");
  elseif currentCategory == EThingsCategory.Material then
    imageCategory[currentCategory].sprite = UI.GetSprite("but058L");
  end
end

function UIBag.OnClick_Refresh()
  if UI.IsVisible(UINpcStore) then
    UINpcStore.ClearSell(true);
  end

  local bag, bagSize, quantSize = Item.GetBag(currentBagKind);
  local stackable = {};
  local index = 0;
  local change = {};

  for k, v in pairs(bag) do
    if not Item.IsTypeOfEquips(itemDatas[v.Id].fitType) then
      table.insert(stackable, bag[k]);
    end
  end

  if table.Count(stackable) < 0 then return end

  local count = table.Count(stackable);

  for i = 1, count do
    for j = i + 1, count do
      if stackable[i] ~= nil and stackable[i].Id == stackable[j].Id and stackable[i].quant < quantSize and stackable[j].quant < quantSize then

        local leftSize = math.min(quantSize - stackable[i].quant, stackable[j].quant);

        if leftSize > 0 then
          index = index + 1;
          change[index] = {};
          change[index].to = stackable[i].bagIndex;
          change[index].from = stackable[j].bagIndex;
          change[index].quant = leftSize;
        end
      end
    end
  end

  local changeCount = table.Count(change);

  if changeCount > 0 then
    --C:023-010 <移動背包物品> +原始背包索引(1) +數量(1) +目標背包索引(1)
    sendBuffer:Clear();
    sendBuffer:WriteByte(changeCount);
    for k, v in pairs(change) do
      sendBuffer:WriteByte(change[k].from);
      sendBuffer:WriteInt32(change[k].quant);
      sendBuffer:WriteByte(change[k].to);
    end
    Network.Send(23, 10, sendBuffer);

    table.Clear(change);
  else
    ShowCenterMessage(string.Get(20736));
  end
end

function UIBag.GetVisualItem(bagIndex)
  local bagCategory = {};
  local index;

  if currentFilter ~= nil then
    bagCategory = Item.GetBagByFilter(currentFilter, currentCategory, currentBagKind);
    if bagCategory == nil then return end

    for k, v in pairs(bagCategory) do
      if v.bagIndex == bagIndex then
        index = k;
      end
    end

  else
    local item = this.GetItemSave(index);
    if item == nil then return end

    index = item.bagIndex;
  end

  if index == nil or index <= 0 then return end

  local itemController;
  for k, v in pairs(scrollItems_Collection) do
    if v:GetVisualIndex() == index then
      itemController = v;
      break;
    end
  end

  return itemController;
end

function UIBag.GetCurrentCategory()
  return currentCategory;
end