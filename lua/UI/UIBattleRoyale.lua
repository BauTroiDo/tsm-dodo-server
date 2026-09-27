UIBattleRoyale = {}
local this = UIBattleRoyale;
this.name = "UIBattleRoyale";

EBattleRoyalFunction =
{
  Vocation = 1,
  Seal = 2,
  Match = 3
}

local currentFunction;

local gameObject_VocationFrameRect;
local gameObject_SealFrameRect;
local gameObject_Matching;
local gameObject_Mask;

local scrollContent_ChooseVocation;
local scrollContent_SealSkill;

local scrollItems_SealSkill = {};
local scrollItems_BattleRoyaleVocation = {};
local scrollItems_VocationSkill = {};

local text_ParticipateInfo;
local text_MatchingTime;
local text_MatchingCountDown;

local uiUpdateVocation;

local functionGameObject = {};
local uiTitle = {};

function UIBattleRoyale.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onClose = this.OnClose;
  this.uiController.onVisible = this.OnVisible;

  gameObject_VocationFrameRect = uiController:FindGameObject("VocationFrameRect");
  gameObject_SealFrameRect = uiController:FindGameObject("SealFrameRect");
  gameObject_Matching = uiController:FindGameObject("Image_Matching");
  gameObject_Mask = uiController:FindGameObject("Image_Mask");

  table.insert(functionGameObject, EBattleRoyalFunction.Vocation, gameObject_VocationFrameRect);
  table.insert(functionGameObject, EBattleRoyalFunction.Seal, gameObject_SealFrameRect);
  table.insert(functionGameObject, EBattleRoyalFunction.Match, gameObject_Matching);

  text_ParticipateInfo = uiController:FindText("Text_ParticipateInfo");
  text_MatchingTime = uiController:FindText("Text_MatchingTime");
  text_MatchingCountDown = uiController:FindText("Text_MatchingCountDown");

  scrollContent_ChooseVocation = uiController:FindScrollContent("ScrollContent_ChooseVocation");
  scrollContent_ChooseVocation.onInitialize = this.OnInitialize_ScrollContent_ChooseVocation;
  scrollContent_ChooseVocation.onItemChange = this.OnItemChange_ScrollContent_ChooseVocation;
  scrollContent_ChooseVocation:Initialize(1);

  scrollContent_SealSkill = uiController:FindScrollContent("ScrollContent_SealSkill");
  scrollContent_SealSkill.onInitialize = this.OnInitialize_ScrollContentSealSkill;
  scrollContent_SealSkill.onItemChange = this.OnItemChange_ScrollContentSealSkill;
  scrollContent_SealSkill:Initialize(1);

  local tempEvent = uiController:FindEvent("Image_LeaveQueue");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_LeaveQueue);

  uiTitle[EBattleRoyalFunction.Vocation] = string.Get(23510);
  uiTitle[EBattleRoyalFunction.Seal] = string.Get(23511);
end

function UIBattleRoyale.OnOpen(openKind)
  if not BattleRoyale.GetBattleRoyaleState() and not BattleRoyale.CheckInQueue() then return false end
  currentFunction = openKind;
  this.UpdateUI();
  return true;
end

function UIBattleRoyale.UpdateUI(updateKind)
  if updateKind then
    currentFunction = updateKind;
  end
  for index = 1, table.Count(functionGameObject) do
    functionGameObject[index]:SetActive(index == currentFunction);
  end

  gameObject_Mask:SetActive(currentFunction ~= EBattleRoyalFunction.Match);
  this.UpdateVocation();
  this.UpdateSealSkill();
end

function UIBattleRoyale.UpdateVocation()
  if scrollContent_ChooseVocation ~= nil and currentFunction == EBattleRoyalFunction.Vocation then
    scrollContent_ChooseVocation:Reset(table.Count(BattleRoyale.GetVocationOption()));
  end
end

function UIBattleRoyale.UpdateSealSkill()
  if scrollContent_SealSkill ~= nil and currentFunction == EBattleRoyalFunction.Seal then
    scrollContent_SealSkill:Reset(table.Count(BattleRoyale.GetSealSkillOption()));
  end
end

function UIBattleRoyale.UpdateParticipate(info)
  text_ParticipateInfo.text = info;
end

function UIBattleRoyale.OnInitialize_ScrollContent_ChooseVocation(scrollItems)
  for itemIndex = 0, scrollItems.Length-1 do
    scrollItems_BattleRoyaleVocation[itemIndex] = {};
    local scrollItem = scrollItems_BattleRoyaleVocation[itemIndex];
    scrollItem.vocationNameText = scrollItems[itemIndex]:Find("Text_VocationText"):GetComponent("Text");
    scrollItem.skillScrollContent = scrollItems[itemIndex]:Find("ScrollRect_VocationSkill"):Find("ScrollContent_VocationSkill"):GetComponent("UIScrollContent");
    scrollItem.selectButton = scrollItems[itemIndex]:Find("Image_SelectVocation"):GetComponent("UIEvent");
    scrollItem.disableImage = scrollItems[itemIndex]:Find("Image_VocationDisable"):GetComponent("Image");
    scrollItem.selectButton:SetListener(EventTriggerType.PointerClick, this.OnClickSelectVocation);
    scrollItem.skillScrollContent.onInitialize = this.GetVocationSkillInitializeFunction(itemIndex + 1);
    scrollItem.skillScrollContent.onItemChange = this.GetVocationSkillItemChangeFunction(itemIndex + 1);
    scrollItem.skillScrollContent:Initialize(1);
  end
end

function UIBattleRoyale.OnItemChange_ScrollContent_ChooseVocation(dataIndex, itemIndex)
  local index = dataIndex + 1;
  local vocation = BattleRoyale.GetVocationOption();
  uiUpdateVocation = vocation[index];
  if uiUpdateVocation == nil then
    return false
  end

  scrollItems_BattleRoyaleVocation[itemIndex].vocationNameText.text = uiUpdateVocation.name;
  if BattleRoyale.GetVocation() == nil then
    scrollItems_BattleRoyaleVocation[itemIndex].selectButton.gameObject:SetActive(true);
    scrollItems_BattleRoyaleVocation[itemIndex].selectButton.parameter = uiUpdateVocation.id;
  else
    scrollItems_BattleRoyaleVocation[itemIndex].selectButton.gameObject:SetActive(false);
  end

  scrollItems_BattleRoyaleVocation[itemIndex].skillScrollContent:Reset(table.Count(uiUpdateVocation:GetSkill()));
  scrollItems_BattleRoyaleVocation[itemIndex].disableImage.enabled = not uiUpdateVocation.show;
  return true;
end

function UIBattleRoyale.GetVocationSkillInitializeFunction(vocationIndex)
  local result = function(scrollItems, index)
    if index == nil then
      index = vocationIndex;
      scrollItems_VocationSkill[index] = {};
      for itemIndex = 0, scrollItems.Length-1 do
        scrollItems_VocationSkill[index][itemIndex] = {};
        local scrollItem = scrollItems_VocationSkill[index][itemIndex];
        scrollItem.skillName = scrollItems[itemIndex]:Find("Image_VocationSkillBG"):Find("Text_SkillName"):GetComponent("Text");
        scrollItem.skillDescription = scrollItems[itemIndex]:Find("Image_VocationSkillBG"):Find("Text_SkillDescription"):GetComponent("Text");
        scrollItem.skillImage = scrollItems[itemIndex]:Find("Image_VocationSkillBG"):Find("Image_SkillIcon"):GetComponent("Image");
      end
    end
    return true;
  end
  return result;
end

function UIBattleRoyale.GetVocationSkillItemChangeFunction(vocationIndex)
  local result = function(dataIndex, itemIndex, index)
    if index == nil then
      index = vocationIndex;
    end
    local skillIndex = dataIndex + 1;
    local skillData = uiUpdateVocation:GetSkill();

    if skillData == nil then return false end
    local scrollItem = scrollItems_VocationSkill[index][itemIndex];
    scrollItem.skillName.text = skillData[skillIndex].name;
    scrollItem.skillDescription.text = skillData[skillIndex].description;
    scrollItem.skillImage.sprite = UI.GetSprite(skillData[skillIndex].icon);
    return true;
  end
  return result;
end

function UIBattleRoyale.OnVisible(visible)
  if visible then
    if currentFunction == EBattleRoyalFunction.Seal then
      UIFrame.Active(gameObject_SealFrameRect.transform, uiTitle[currentFunction], this.OnClose, this.OnClick_Help);
    elseif currentFunction == EBattleRoyalFunction.Vocation then
      if BattleRoyale.GetVocation() == nil then
        UIFrame.Active(gameObject_VocationFrameRect.transform, uiTitle[currentFunction], nil, this.OnClick_Help);
      else
        UIFrame.Active(gameObject_VocationFrameRect.transform, uiTitle[currentFunction], this.OnClose, this.OnClick_Help);
      end
    elseif currentFunction == EBattleRoyalFunction.Match then
      UIBattleRoyale.UpdateMatchingTime();
    end
  end
end

function UIBattleRoyale.OnClose()
  UI.Close(this);
  return true;
end

function UIBattleRoyale.OnClickSelectVocation(uiEvent)
  BattleRoyale.SendSelectVocation(uiEvent.parameter);
end

function UIBattleRoyale.OnInitialize_ScrollContentSealSkill(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_SealSkill[itemIndex] = {};
    local scrollItem = scrollItems_SealSkill[itemIndex];
    scrollItem.description = scrollItems[itemIndex]:Find("Text_SealSkillDescription"):GetComponent("Text");
    scrollItem.selectButton = scrollItems[itemIndex]:Find("Image_SelectSealSkill"):GetComponent("UIEvent");
    scrollItem.selectButton:SetListener(EventTriggerType.PointerClick, this.OnClickSelectSealSkill);
  end
end

function UIBattleRoyale.OnItemChange_ScrollContentSealSkill(dataIndex, itemIndex)
  local index = dataIndex + 1;
  local data = BattleRoyale.GetSealSkillOption()[index];
  if data == nil then
    return false
  end
  local scrollItem = scrollItems_SealSkill[itemIndex];
  scrollItem.description.text = data.description;
  scrollItem.selectButton.parameter = data.id;
  return true
end

function UIBattleRoyale.OnClickSelectSealSkill(uiEvent)
  BattleRoyale.SendSelectSealSkill(uiEvent.parameter);
end

function UIBattleRoyale.CheckVocation()
  if UI.IsVisible(this) and currentFunction == EBattleRoyalFunction.Vocation then
    UI.Close(this);
    return
  end
  if not UI.IsVisible(this) then
    UI.Open(this, EBattleRoyalFunction.Vocation);
  end
end

--C:110-003 <離開大逃殺排隊>
function UIBattleRoyale.OnClick_LeaveQueue()
  if UI.GetCurrentUI() ~= this then
    return
  end
  Network.Send(110, 3);
end

function UIBattleRoyale.SetQueueCount(content)
  text_ParticipateInfo.text = content;
end

function UIBattleRoyale.UpdateMatchingTime(time)
  if time == nil then
    text_MatchingCountDown.text = "";
  else
    text_MatchingTime.text = string.format(string.Get(24379), string.GetSecondSymbol(math.ceil(time.TotalSeconds)));
  end
end

function UIBattleRoyale.UpdateMatchCountDown(time)
  if time == nil then
    text_MatchingCountDown.text = "";
  else
    text_MatchingCountDown.text = string.format(string.Get(24378), string.GetSecondSymbol(math.ceil(time.TotalSeconds)));
  end
end