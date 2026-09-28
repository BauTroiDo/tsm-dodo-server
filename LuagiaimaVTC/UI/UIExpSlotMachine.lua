UIExpSlotMachine = {}
local this = UIExpSlotMachine;

this.name = "UIExpSlotMachine";
this.uiController = nil;

local transform_FrameRect;
local transform_Result;
local rawImage_Machine;
local rawImage_Drawbar;
local text_Exp;
local text_FollowNpc;
local event_Stop;
local scrollContent_Award;
local scrollItems_Award = {};
local scrollContent_Result;
local scrollItems_Result = {};
local scrollContent_Items = {};
local scrollItems_Items = {};
local slider_Exp;
local text_NpcExp;
local text_FullExp;
local text_LvValue;

local awardsCount = 0;
local followNpcName = {};
local currentfollowNpcIndex = 1;
local currentSlot = 0;
local startTime = 0;
local stopTime = 0;
local rollingState = false;
local stopRolling = false;
local slotTransforms;
local stopStates = {false, false, false};

local awardsIcon = {"icon015","icon016","icon017","icon018","icon022","icon024","icon025","icon026","icon055"};
local result = {};
local total = {};
local animTime = 2;

function UIExpSlotMachine.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onVisible = this.OnVisible;

  transform_FrameRect = uiController:FindGameObject("FrameRect").transform;
  transform_Result = uiController:FindGameObject("Result").transform;

  rawImage_Machine = uiController:FindRawImage("RawImage_Machine");
  rawImage_Drawbar = uiController:FindRawImage("RawImage_Drawbar");

  text_Exp = uiController:FindText("Text_Exp");
  text_FollowNpc = uiController:FindText("Text_FollowNpc");
  text_NpcExp = uiController:FindText("Text_T5_ExpValue");
  text_FullExp = uiController:FindText("Text_T5_ExpFullValue");
  text_LvValue = uiController:FindText("Text_LvValue");

  slider_Exp = uiController:FindSlider("Slider_Exp");

  event_Stop = uiController:FindEvent("Image_Stop");
  event_Stop:SetListener(EventTriggerType.PointerClick, this.OnClick_Stop);

  local tempEvent;

  tempEvent = uiController:FindEvent("Image_Tenfold");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Tenfold);

  tempEvent = uiController:FindEvent("Image_One");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_One);

  tempEvent = uiController:FindEvent("Image_Left");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Left);

  tempEvent = uiController:FindEvent("Image_Right");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Right);

  tempEvent = uiController:FindEvent("Image_Close");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_CloseResult);

  scrollContent_Award = uiController:FindScrollContent("ScrollContent_Award");
  scrollContent_Award.onInitialize = this.OnInitialize_ScrollContent_Award;
  scrollContent_Award.onItemChange = this.OnItemChange_ScrollContent_Award;
  scrollContent_Award:Initialize(1);

  scrollContent_Result = uiController:FindScrollContent("ScrollContent_Result");
  scrollContent_Result.onInitialize = this.OnInitialize_ScrollContent_Result;
  scrollContent_Result.onItemChange = this.OnItemChange_ScrollContent_Result;
  scrollContent_Result:Initialize(1);

  for i = 1, 3 do
    currentSlot = i;
    scrollContent_Items[i] = uiController:FindScrollContent(string.Concat("ScrollContent_Item (", i, ")"));
    scrollItems_Items[i] = {};
    scrollContent_Items[i].onInitialize = this.OnInitialize_ScrollContent_Items;
    if i == 1 then
      scrollContent_Items[i].onItemChange = this.OnItemChange_ScrollContent_Items_Slot1;
    elseif i == 2 then
      scrollContent_Items[i].onItemChange = this.OnItemChange_ScrollContent_Items_Slot2;
    else
      scrollContent_Items[i].onItemChange = this.OnItemChange_ScrollContent_Items_Slot3;
    end
    scrollContent_Items[i]:Initialize(1);
  end

  rawImage_Drawbar:SetLoopEndCallBack(this.DrawbarCallBack);
end

function UIExpSlotMachine.OnInitialize_ScrollContent_Award(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Award[itemIndex] = {};
    scrollItems_Award[itemIndex].items = {}
    for i = 1, 3 do
      scrollItems_Award[itemIndex].items[i] = scrollItems[itemIndex]:Find(string.Concat("Image_Item (", i, ")")):GetComponent("Image");
    end

    scrollItems_Award[itemIndex].text_Exp = scrollItems[itemIndex]:Find("Text_Exp"):GetComponent("Text");
  end
end

function UIExpSlotMachine.OnItemChange_ScrollContent_Award(dataIndex, itemIndex)
  for i = 1, 3 do
      scrollItems_Award[itemIndex].items[i].sprite = UI.GetSprite(awardsIcon[fNpcSlotDatas[dataIndex + 1].icons[i]]);
  end
  
  scrollItems_Award[itemIndex].text_Exp.text = fNpcSlotDatas[dataIndex + 1].exp;

  return true;
end

function UIExpSlotMachine.OnInitialize_ScrollContent_Result(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Result[itemIndex] = {};
    scrollItems_Result[itemIndex].items = {}
    for i = 1, 3 do
      scrollItems_Result[itemIndex].items[i] = scrollItems[itemIndex]:Find(string.Concat("Image_Item (", i, ")")):GetComponent("Image");
    end

    scrollItems_Result[itemIndex].text_Exp = scrollItems[itemIndex]:Find("Text_Exp"):GetComponent("Text");
  end
end

function UIExpSlotMachine.OnItemChange_ScrollContent_Result(dataIndex, itemIndex)
  for i = 1, 3 do
      scrollItems_Result[itemIndex].items[i].sprite = UI.GetSprite(awardsIcon[total[itemIndex + 1].awardImages[i]]);
  end
  
  scrollItems_Result[itemIndex].text_Exp.text = total[itemIndex + 1].exp;

  return true;
end

function UIExpSlotMachine.OnInitialize_ScrollContent_Items(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Items[currentSlot][itemIndex] = {};
    scrollItems_Items[currentSlot][itemIndex].items = scrollItems[itemIndex]:Find(string.Concat("Image_Item")):GetComponent("Image");
  end
end

function UIExpSlotMachine.OnItemChange_ScrollContent_Items_Slot1(dataIndex, itemIndex)
  dataIndex = math.abs(dataIndex);

  local count = math.fmod(dataIndex, awardsCount) + 1;
  scrollItems_Items[1][itemIndex].items.sprite = UI.GetSprite(awardsIcon[count]);
  if stopRolling and math.fmod(count + awardsCount - 3, awardsCount) + 1 == result[1] and DateTime.Subtract(CGTimer.serverTime, stopTime).TotalSeconds > 1 then
    stopStates[1] = true;
  end

  return true;
end

function UIExpSlotMachine.OnItemChange_ScrollContent_Items_Slot2(dataIndex, itemIndex)
  dataIndex = math.abs(dataIndex);

  local count = math.fmod(dataIndex, awardsCount) + 1;
  scrollItems_Items[2][itemIndex].items.sprite = UI.GetSprite(awardsIcon[count]);
  if stopRolling and math.fmod(count + awardsCount - 3, awardsCount) + 1 == result[2] and DateTime.Subtract(CGTimer.serverTime, stopTime).TotalSeconds > 1.5 then
    stopStates[2] = true;
  end

  return true;
end

function UIExpSlotMachine.OnItemChange_ScrollContent_Items_Slot3(dataIndex, itemIndex)
  dataIndex = math.abs(dataIndex);

  local count = math.fmod(dataIndex, awardsCount) + 1;
  scrollItems_Items[3][itemIndex].items.sprite = UI.GetSprite(awardsIcon[count]);
  if stopRolling and math.fmod(count + awardsCount - 3, awardsCount) + 1 == result[3] and DateTime.Subtract(CGTimer.serverTime, stopTime).TotalSeconds > 2 then
    stopStates[3] = true;
  end

  return true;
end

function UIExpSlotMachine.OnOpen()
  TextureManager.SetPng(ETextureUseType.UI, "ExpSlotMachine01",  rawImage_Machine);
  TextureManager.SetPng(ETextureUseType.UI, "icon_Pull",  rawImage_Drawbar);

  slotTransforms = {scrollContent_Items[1].gameObject.transform, scrollContent_Items[2].gameObject.transform, scrollContent_Items[3].gameObject.transform};

  rawImage_Drawbar.uvRect = Rect.New(0, 0.75, 1, 0.25);
  rawImage_Drawbar:StopShiftUV();

  currentfollowNpcIndex = 1;
  awardsCount = table.Count(awardsIcon);
  this.OnClick_CloseResult();
  this.GetFollowNpc();
  this.SetFollowNpc(0);
  this.UpdateTotalExp();
  this.UpdateAwardPage();
  this.UpdateItemsPage();
  return true;
end

function UIExpSlotMachine.OnVisible(visible)
  if visible then
    UIFrame.Active(transform_FrameRect, string.Get(21945), this.OnClose, this.OnClick_Help);
  end
end

function UIExpSlotMachine.OnClick_Help(checkShow)
  if checkShow ~= true then
    UI.Open(UITutorialHint, 53, this);
  end

  return true;
end

function UIExpSlotMachine.OnClose()
  if rollingState then 
    ShowCenterMessage(string.Get(71218));
    return;
  end

  UI.Close(this);
  return true;
end

function UIExpSlotMachine.OnClick_Tenfold()
  if this.CheckPull() then return end

  if followNpcName[currentfollowNpcIndex].turn >= 2 then
    UISell.Launch(66, nil, followNpcName[currentfollowNpcIndex].fIndex, followNpcName[currentfollowNpcIndex].npcId);
  else
    UISell.Launch(38, nil, followNpcName[currentfollowNpcIndex].fIndex, followNpcName[currentfollowNpcIndex].npcId);
  end
end

function UIExpSlotMachine.OnClick_One()
  if this.CheckPull() then return end

  if followNpcName[currentfollowNpcIndex].turn >= 2 then
    UISell.Launch(65, nil, followNpcName[currentfollowNpcIndex].fIndex, followNpcName[currentfollowNpcIndex].npcId);
  else
    UISell.Launch(37, nil, followNpcName[currentfollowNpcIndex].fIndex, followNpcName[currentfollowNpcIndex].npcId);
  end
end

function UIExpSlotMachine.CheckPull()
  if rollingState then
    ShowCenterMessage(string.Get(71218));
    return true;
  end

  if followNpcName[currentfollowNpcIndex] == nil then
    ShowCenterMessage(string.Get(80438));
    return true;
  end

  if Role.fightNpc ~= nil then  --出戰武將不可選取
    if followNpcName[currentfollowNpcIndex].npcId == Role.fightNpc.npcId then
      ShowCenterMessage(string.Get(80434));
      return true;
    end
  end

  if Contains(followNpcName[currentfollowNpcIndex].canGrow, 0, 2) then
    --任務給的NPC不可選取
    ShowCenterMessage(string.Get(80433));
    return true;
  end

  local npcData = npcDatas[followNpcName[currentfollowNpcIndex].npcId];
  if npcData ~= nil and npcData.kind == 53 then  --養成武將不可選取
    ShowCenterMessage(string.Get(80433));
    return true;
  end

  local followNpc = Role.GetRole(EHuman.FollowNpc, Role.playerId, followNpcName[currentfollowNpcIndex].fIndex);
  local level = followNpc:GetAttribute(EAttribute.Lv);
  if followNpcName[currentfollowNpcIndex].turn <= 2 and level >= 180 then
    --武將等級180以上無法選取
    ShowCenterMessage(string.Get(80432));
    return true;
  end

  return false;
end

function UIExpSlotMachine.OnClick_Left()
  if rollingState then
    ShowCenterMessage(string.Get(71218));
    return;
  end

  this.SetFollowNpc(-1);
end

function UIExpSlotMachine.OnClick_Right()
  if rollingState then
    ShowCenterMessage(string.Get(71218));
    return;
  end

  this.SetFollowNpc(1);
end

function UIExpSlotMachine.OnClick_CloseResult()
  transform_Result.gameObject:SetActive(false);
end

function UIExpSlotMachine.Pull()
  rawImage_Drawbar:ShiftUV(1, 4, 0.1, 0);

  event_Stop.gameObject:SetActive(true);
  startTime = CGTimer.serverTime;
  rollingState = true;

  CGTimer.AddListener(this.Rolling, 0.001);
end

function UIExpSlotMachine.DrawbarCallBack()
  rawImage_Drawbar.enabled = true;
  rawImage_Drawbar.uvRect = Rect.New(0, 0.75, 1, 0.25);
end

function UIExpSlotMachine.UpdateAwardPage()
  scrollContent_Award:Reset(table.Count(fNpcSlotDatas));
end

function UIExpSlotMachine.UpdateResultPage()
  scrollContent_Result:Reset(table.Count(total));
end

function UIExpSlotMachine.UpdateItemsPage()
  for i = 1, 3 do 
    currentSlot = i;
    scrollContent_Items[i]:Reset(awardsCount);
  end

  event_Stop.gameObject:SetActive(false);
end

function UIExpSlotMachine.UpdateTotalExp()
  --C:076-005 <拉拉更新彩金>
    Network.Send(76, 5);
end

function UIExpSlotMachine.GetFollowNpc()
  table.Clear(followNpcName);

  local index = 0;
  for i = 1, Role.maxFollowNpc do
    local followNpc = Role.GetRole(EHuman.FollowNpc, Role.playerId, i);

    if followNpc ~= nil then
      index = index + 1;
      followNpcName[index] = {name = followNpc.name,
                              npcId = followNpc.npcId,
                              fIndex = i,
                              canGrow = followNpc.data.canGrow,
                              turn = followNpc:GetAttribute(EAttribute.Turn)};
    end
  end
end

function UIExpSlotMachine.SetFollowNpc(index)
  text_FollowNpc.text = "";
  if followNpcName[currentfollowNpcIndex] == nil then return end

  if currentfollowNpcIndex + index < 1 then
    currentfollowNpcIndex = table.Count(followNpcName);
  elseif currentfollowNpcIndex + index > table.Count(followNpcName) then
    currentfollowNpcIndex = 1;
  else
    currentfollowNpcIndex = currentfollowNpcIndex + index;
  end

  text_FollowNpc.text = followNpcName[currentfollowNpcIndex].name;

  this.SetExpSlider();
end

function UIExpSlotMachine.OnClick_Stop()
  stopRolling = true;
  stopTime = CGTimer.serverTime;
end

function UIExpSlotMachine.SetStopState(state)
  for i = 1, 3 do 
    stopStates[i] = state;
  end
end

function UIExpSlotMachine.Rolling()
  if DateTime.Subtract(CGTimer.serverTime, startTime).TotalSeconds > animTime then
    this.SetStopState(true);
  end

  if not stopStates[1] then
    slotTransforms[1].localPosition = Vector3.New(-26, slotTransforms[1].localPosition.y - 40, 0);
  else
    slotTransforms[1].localPosition = Vector3.New(-26, result[1] * 50 - 24, 0);
  end

  if not stopStates[2] then
    slotTransforms[2].localPosition = Vector3.New(-26, slotTransforms[2].localPosition.y - 43, 0);
  else
    slotTransforms[2].localPosition = Vector3.New(-26, result[2] * 50 - 24, 0);
  end

  if not stopStates[3] then
    slotTransforms[3].localPosition = Vector3.New(-26, slotTransforms[3].localPosition.y - 47, 0);
  else
    slotTransforms[3].localPosition = Vector3.New(-26, result[3] * 50 - 24, 0);
  end

  if stopStates[1] and stopStates[2] and stopStates[3] then
    CGTimer.RemoveListener(this.Rolling);
    this.UpdateResultPage();
    CGTimer.DoFunctionDelay(1,
      function()
        rollingState = false;
        this.SetStopState(false);
        stopRolling = false;
        transform_Result.gameObject:SetActive(true);
        this.UpdateItemsPage();
        this.UpdateTotalExp();
      end
    );
  end
end

function UIExpSlotMachine.ReceiveExp(data)
  text_Exp.text = data:ReadInt32();

  this.SetExpSlider();
end

function UIExpSlotMachine.ResetSlider()
  slider_Exp.maxValue = 0;
  slider_Exp.value = 0;
  text_NpcExp.text = "";
  text_FullExp.text = "";
  text_LvValue.text = "";
end

function UIExpSlotMachine.SetExpSlider()
  if followNpcName[currentfollowNpcIndex] == nil then
    this.ResetSlider();
    return;
  end
  
  local followNpc = Role.GetRole(EHuman.FollowNpc, Role.playerId, followNpcName[currentfollowNpcIndex].fIndex);
  
  if followNpc == nil then
    this.ResetSlider();
    return;
  end
 
  local turn = followNpc:GetAttribute(EAttribute.Turn);
  local level = followNpc:GetAttribute(EAttribute.Lv);
  local exp = followNpc:GetAttribute(EAttribute.Exp);

  if turn == 2 then
    TextureManager.SetPng(ETextureUseType.UI, "ExpSlotMachine02", rawImage_Machine);
    turn = 1;
  else
    TextureManager.SetPng(ETextureUseType.UI, "ExpSlotMachine01", rawImage_Machine);
    turn = 0;
  end

  if turn >= 3 then
    level = level - Role.playerMaxLv;
    exp = followNpc:GetAttribute(EAttribute.Turn3Exp);
  end

  if level == Role.playerMaxLv then
    slider_Exp.maxValue = 1;
    slider_Exp.value = 1;

    text_NpcExp.text = "100%";
    text_FullExp.text = string.Get(40475);
  else
    slider_Exp.maxValue = Calc.GetUpExp(level + 1, turn);
    slider_Exp.value = exp - Calc.GetNeedTExp(level, turn);

    text_NpcExp.text = string.Concat(math.floor((slider_Exp.value / slider_Exp.maxValue) * 100), "%");
    text_FullExp.text = string.Concat(slider_Exp.value, "/", slider_Exp.maxValue);
  end

  text_LvValue.text = level;
end

function UIExpSlotMachine.OpenLottery(data)
  result = {};
  total = {}
  local count = data:ReadByte();
  local maxExp = 0;

  for i = 1, count do
    local award = {};
    award.kind = data:ReadByte();
    award.exp = data:ReadInt32();
    award.awardImages = {};
    for i = 1, 3 do
      award.awardImages[i] = data:ReadByte();
    end

    if award.exp >= maxExp then
      for i = 1, 3 do
        result[i] = award.awardImages[i];
      end

      maxExp = award.exp;
    end
    
    table.insert(total, award);
  end

  this.Pull();
end