local json = require 'cjson'

UIHotkey = {}
local this = UIHotkey;
this.name = "UIHotkey";
this.uiController = nil;

local scrollContent_Hotkeys = {};
local scrollItems_Hotkeys = {};

local image_SkillOnClear;
local image_ItemOnClear;

local event_SkillEdit;
local event_ItemEdit;

local scrollRect_Main;
local scrollRect_Sub;

local root_SkillTree;
local root_ItemInfo;
local root_Main;
local root_Sub;
local mask_Main;
local mask_Sub;
local mask_Side;
local sortingOrder;

function UIHotkey.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onVisible = this.OnVisible;
  this.uiController.onClose = this.OnClose;

  image_SkillOnClear = uiController:FindImage("Image_SkillOnClear");
  image_ItemOnClear = uiController:FindImage("Image_ItemOnClear");
  scrollRect_Main = uiController:FindGameObject("ScrollRect_Main"):GetComponent("RectTransform");
  scrollRect_Sub = uiController:FindGameObject("ScrollRect_Sub"):GetComponent("RectTransform");
  root_SkillTree = uiController:FindGameObject("Root_SkillTree"):GetComponent("RectTransform");
  root_ItemInfo = uiController:FindGameObject("Root_ItemInfo"):GetComponent("RectTransform");
  root_Main = uiController:FindGameObject("Root_Main"):GetComponent("RectTransform");
  root_Sub = uiController:FindGameObject("Root_Sub"):GetComponent("RectTransform");
  mask_Main = uiController:FindGameObject("Mask_Main");
  mask_Sub = uiController:FindGameObject("Mask_Sub");
  mask_Side = uiController:FindGameObject("Mask_Side");

  event_SkillEdit = uiController:FindEvent("Image_SkillOnClear");
  event_SkillEdit:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchEditMode);
  event_SkillEdit.parameter = EHotkeyUI.Main;

  event_ItemEdit = uiController:FindEvent("Image_ItemOnClear");
  event_ItemEdit:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchEditMode);
  event_ItemEdit.parameter = EHotkeyUI.Sub;

  scrollContent_Hotkeys[EHotkeyUI.Main] = this.uiController:FindScrollContent("ScrollContent_Main");
  scrollContent_Hotkeys[EHotkeyUI.Main].onInitialize = this.GetHotkeyInitializeFunction(EHotkeyUI.Main);
  scrollContent_Hotkeys[EHotkeyUI.Main].onItemChange = this.GetHotkeyItemChangeFunction(EHotkeyUI.Main);
  scrollContent_Hotkeys[EHotkeyUI.Main].onRootChange = this.GetHotkeyRootChangeFunction(EHotkeyUI.Main);
  Hotkey.SetCurrentPage(EHotkeyUI.Main, 0);
  scrollContent_Hotkeys[EHotkeyUI.Main]:Initialize(1);

  scrollContent_Hotkeys[EHotkeyUI.Sub] = this.uiController:FindScrollContent("ScrollContent_Sub");
  scrollContent_Hotkeys[EHotkeyUI.Sub].onInitialize = this.GetHotkeyInitializeFunction(EHotkeyUI.Sub);
  scrollContent_Hotkeys[EHotkeyUI.Sub].onItemChange = this.GetHotkeyItemChangeFunction(EHotkeyUI.Sub);
  scrollContent_Hotkeys[EHotkeyUI.Sub].onRootChange = this.GetHotkeyRootChangeFunction(EHotkeyUI.Sub);
  Hotkey.SetCurrentPage(EHotkeyUI.Sub, 0);
  scrollContent_Hotkeys[EHotkeyUI.Sub]:Initialize(1);
end

function UIHotkey.GetHotkeyInitializeFunction(uiIndex)
  local result = function(scrollItems, index)
    if index == nil then
      index = uiIndex;
      scrollItems_Hotkeys[index] = {};
      for itemIndex = 0, scrollItems.Length-1 do
        local tempTransform = scrollItems[itemIndex]:Find(string.Concat("Image_Btn"));
        scrollItems_Hotkeys[index][itemIndex] = {};
        scrollItems_Hotkeys[index][itemIndex].btn = {};
        scrollItems_Hotkeys[index][itemIndex].btn.image_Btn = tempTransform:GetComponent("Image");
        scrollItems_Hotkeys[index][itemIndex].btn.text_Name = tempTransform:Find("Text_Name"):GetComponent("Text");
        scrollItems_Hotkeys[index][itemIndex].btn.text_Lv = tempTransform:Find("Text_Lv"):GetComponent("Text");
        scrollItems_Hotkeys[index][itemIndex].btn.rawimgae_Icon = tempTransform:Find("RawImage_Icon"):GetComponent("RawImage");
        scrollItems_Hotkeys[index][itemIndex].btn.image_Select = tempTransform:Find("Image_Select"):GetComponent("Image");
        scrollItems_Hotkeys[index][itemIndex].btn.event_Use = tempTransform:GetComponent("UIEvent");
        scrollItems_Hotkeys[index][itemIndex].btn.event_Use:SetListener(EventTriggerType.PointerClick, this.OnClick_Hotkey);
        scrollItems_Hotkeys[index][itemIndex].btn.event_Use.parameter = {};
        scrollItems_Hotkeys[index][itemIndex].btn.event_Use.parameter.uiIndex = index;
      end
    end
    return true;
  end
  return result;
end

function UIHotkey.GetHotkeyItemChangeFunction(uiIndex)
  local result = function(dataIndex, itemIndex, index)
    if index == nil then
      index = uiIndex;
    end

    local hotkeyIndex = dataIndex + 1;
    scrollItems_Hotkeys[index][itemIndex].btn.event_Use.parameter.keyId = hotkeyIndex;

    if Hotkey.GetHotkeyUseType(index, hotkeyIndex) == EHotKeyIdType.Equip and Hotkey.IsHotkeyIdDataEmpty(index, hotkeyIndex) then
      Hotkey.SetEquipIcon(hotkeyIndex, scrollItems_Hotkeys[index][itemIndex].btn.image_Btn);
    else
      scrollItems_Hotkeys[index][itemIndex].btn.image_Btn.sprite = UI.GetSprite("bod023");
    end

    if Hotkey.IsHotkeyIdDataEmpty(index, hotkeyIndex) then
      scrollItems_Hotkeys[index][itemIndex].btn.rawimgae_Icon.enabled = false;
      scrollItems_Hotkeys[index][itemIndex].btn.text_Lv.text = "";
    else
      scrollItems_Hotkeys[index][itemIndex].btn.rawimgae_Icon.enabled = true;
      scrollItems_Hotkeys[index][itemIndex].btn.text_Lv.text = Hotkey.GetNumberText(index, hotkeyIndex);
      Hotkey.SetIcon(index, hotkeyIndex, scrollItems_Hotkeys[index][itemIndex].btn.rawimgae_Icon);
      LuaHelper.SetColor(scrollItems_Hotkeys[index][itemIndex].btn.rawimgae_Icon, Hotkey.GetColor(index, hotkeyIndex));
    end

    local editFlag = Hotkey.CheckEditPermission(index);
    scrollItems_Hotkeys[index][itemIndex].btn.image_Select.enabled = editFlag;
    if editFlag then
      if Hotkey.GetEditMode() == EHotkeyEditMode.Clear then
        scrollItems_Hotkeys[index][itemIndex].btn.image_Select.sprite = UI.GetSprite("bod109");
      elseif Hotkey.GetEditMode() == EHotkeyEditMode.Setting then
        scrollItems_Hotkeys[index][itemIndex].btn.image_Select.sprite = UI.GetSprite("bod025");
      end
    end

    return true
  end
  return result
end

function UIHotkey.GetHotkeyRootChangeFunction(uiIndex)
  local result = function(dataIndex, index)
    if index == nil then
      index = uiIndex;
      Hotkey.SetCurrentPage(index, dataIndex);
    end
  end
  return result
end

function UIHotkey.OnEnable(enabled)
  this.uiController.canvas.enabled = enabled;
end

function UIHotkey.OnOpen()
  this.SetSortOrder();
  this.uiController.canvas.enabled = true;
  this.UpdateUI(true);
  return true;
end

function UIHotkey.UpdateUI(reset)
  if sortingOrder ~= nil then
    this.uiController.canvas.sortingOrder = sortingOrder;
  end
  
  if Hotkey.currentDisplayMode == EHotkeyDisplayMode.Both then
    scrollRect_Main:SetParent(root_Main);
    scrollRect_Sub:SetParent(root_Sub);
  elseif Hotkey.currentDisplayMode == EHotkeyDisplayMode.SingleItem then
    scrollRect_Sub:SetParent(root_ItemInfo);
  elseif Hotkey.currentDisplayMode == EHotkeyDisplayMode.SingleSkill then
    scrollRect_Main:SetParent(root_SkillTree);
  end

  this.UpdatePos();
  this.UpdateHotkey(EHotkeyUI.Main, reset);
  this.UpdateHotkey(EHotkeyUI.Sub, reset);
  this.UpdateEditMode(EHotkeyUI.Main);
  this.UpdateEditMode(EHotkeyUI.Sub);
end

function UIHotkey.SetSortOrder(sortOrder)
  if sortOrder ~= nil then
    sortingOrder = sortOrder + 1;
  end
end

function UIHotkey.UpdateHotkey(uiIndex, reset)
  if Hotkey.GetDisplayFlag(uiIndex) then
    local mainCount = Hotkey.GetCurrentKeyCount(uiIndex);
    if reset then
      scrollContent_Hotkeys[uiIndex]:Reset(mainCount, Hotkey.GetCurrentPage(uiIndex));
    else
      scrollContent_Hotkeys[uiIndex]:Refresh(mainCount);
    end
  end
end

function UIHotkey.UpdatePos()
  image_SkillOnClear.gameObject:SetActive(Hotkey.currentDisplayMode == EHotkeyDisplayMode.SingleSkill and Hotkey.IsPlayer());
  image_ItemOnClear.gameObject:SetActive(Hotkey.currentDisplayMode == EHotkeyDisplayMode.SingleItem and Hotkey.IsPlayer());
  root_SkillTree.gameObject:SetActive(Hotkey.currentDisplayMode == EHotkeyDisplayMode.SingleSkill);
  root_ItemInfo.gameObject:SetActive(Hotkey.currentDisplayMode == EHotkeyDisplayMode.SingleItem);
  root_Main.gameObject:SetActive(Hotkey.currentDisplayMode == EHotkeyDisplayMode.Both);
  root_Sub.gameObject:SetActive(Hotkey.currentDisplayMode == EHotkeyDisplayMode.Both);
  mask_Main:SetActive(Hotkey.currentDisplayMode == EHotkeyDisplayMode.Both);
  mask_Side:SetActive(Hotkey.currentDisplayMode == EHotkeyDisplayMode.Both);
  mask_Sub:SetActive(Hotkey.currentDisplayMode == EHotkeyDisplayMode.Both);
  scrollRect_Main.offsetMin = Vector2.New(5, 5); -- Left, Bottom
  scrollRect_Main.offsetMax = Vector2.New(-5, -5); -- Right, Top
  scrollRect_Sub.offsetMin = Vector2.New(5, 5);
  if Hotkey.currentDisplayMode == EHotkeyDisplayMode.Both then
    scrollRect_Sub.offsetMax = Vector2.New(-5, -11);
  else
    scrollRect_Sub.offsetMax = Vector2.New(-5, -5);
  end
end

function UIHotkey.OnClick_Hotkey(uiEvent)
  local parameter = uiEvent.parameter;
  Hotkey.UseHotkey(parameter.uiIndex, parameter.keyId);
end

function UIHotkey.OnClick_SwitchEditMode(uiEvent)
  Hotkey.SwitchEditMode();
  this.UpdateEditMode(uiEvent.parameter);
  this.UpdateHotkey(uiEvent.parameter);
end

function UIHotkey.UpdateEditMode(uiIndex)
  local sprite;
  if Hotkey.GetEditMode() == EHotkeyEditMode.Clear then
    sprite = UI.GetSprite("but168L");
  elseif Hotkey.GetEditMode() == EHotkeyEditMode.Setting then
    sprite = UI.GetSprite("but168D");
  end

  if uiIndex == EHotkeyUI.Main then
    image_SkillOnClear.sprite = sprite;
  elseif uiIndex == EHotkeyUI.Sub then
    image_ItemOnClear.sprite = sprite;
  end
end

function UIHotkey.IsCurrentRole(role)
  return this.currentRole == role;
end