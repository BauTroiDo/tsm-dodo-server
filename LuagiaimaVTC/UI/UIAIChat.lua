UIAIChat = {}
local this = UIAIChat;

this.name = "UIAIChat";
this.uiController = nil;
local transform_FrameRect;
local scrollContent_List;
local scrollItems_List = {};
local scrollContent_Dialog;
local scrollItems_Dialog = {};
local inputField_Message;
local chats = {};
this.currentMessages = {};
this.maxMessage = 300;
local nextSpeakTime = 0;
this.currentTalk = -1;
local nextSpeakInterval = 3;
this.bNextTalk = false;
local responseDatas = {24897, 24898, 24899};

local EChatSide = {
  Right = 1;
  Left = 2;
}

function UIAIChat.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onVisible = this.OnVisible;
  
  transform_FrameRect = uiController:FindGameObject("FrameRect").transform;
  scrollContent_List = uiController:FindScrollContent("ScrollContent_List");
  scrollContent_List.onInitialize = this.OnInitialize_ScrollContent_List;
  scrollContent_List.onItemChange = this.OnItemChange_ScrollContent_List;
  scrollContent_List:Initialize(1);
  
  scrollContent_Dialog = uiController:FindScrollContent("ScrollContent_Talk");
  scrollContent_Dialog.onInitialize = this.OnInitialize_ScrollContent_Dialog;
  scrollContent_Dialog.onItemChange = this.OnItemChange_ScrollContent_Dialog;
  scrollContent_Dialog:Initialize(1);
  
  inputField_Message = uiController:FindInputField("InputField_Message");
  
  local tempEvent;
  tempEvent = uiController:FindEvent("Image_Send");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Send);
  tempEvent = uiController:FindEvent("Image_Mask");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Close);
end

function UIAIChat.OnVisible(visible)
  if visible then
    UIFrame.Active(transform_FrameRect, string.Get(24868), this.OnClick_Close, this.OnClick_Help);
    this.UpdateUI();
  end
end

function UIAIChat.OnInitialize_ScrollContent_Dialog(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Dialog[itemIndex] = {};
    scrollItems_Dialog[itemIndex].left = scrollItems[itemIndex]:Find("Image_Left").gameObject;
    scrollItems_Dialog[itemIndex].left_Elf = UIItemController.New(scrollItems[itemIndex]:Find("Image_Left"):Find("Image_Elf"), this.OnClick_ElfIcon);
    scrollItems_Dialog[itemIndex].left_dialog = scrollItems[itemIndex]:Find("Image_Left"):Find("BG"):Find("Dialog"):GetComponent("Text");
    scrollItems_Dialog[itemIndex].left_bg = scrollItems[itemIndex]:Find("Image_Left"):Find("BG").gameObject;
    scrollItems_Dialog[itemIndex].right = scrollItems[itemIndex]:Find("Image_Right").gameObject;
    scrollItems_Dialog[itemIndex].right_Elf = HeadIconController.New(scrollItems[itemIndex]:Find("Image_Right"):Find("Image_Elf"));
    scrollItems_Dialog[itemIndex].right_dialog = scrollItems[itemIndex]:Find("Image_Right"):Find("BG"):Find("Dialog"):GetComponent("Text");
    scrollItems_Dialog[itemIndex].right_bg = scrollItems[itemIndex]:Find("Image_Right"):Find("BG").gameObject;
  end
end

function UIAIChat.OnItemChange_ScrollContent_Dialog(dataIndex, itemIndex)
  local index = dataIndex + 1;
  local message = chats[this.currentTalk][index];
  scrollItems_Dialog[itemIndex].right:SetActive(message.side == EChatSide.Right);
  scrollItems_Dialog[itemIndex].right_bg:SetActive(message.side == EChatSide.Right);
  scrollItems_Dialog[itemIndex].left:SetActive(message.side == EChatSide.Left);
  scrollItems_Dialog[itemIndex].left_bg:SetActive(message.side == EChatSide.Left);
  scrollItems_Dialog[itemIndex].left_Elf:SetItem(aiChatDatas[this.currentTalk].icon, 0, nil);
  scrollItems_Dialog[itemIndex].right_Elf:ChangeIcon(Role.player.data.sex, Role.player.data.headStyle);
  scrollItems_Dialog[itemIndex].right_Elf:ChangeIconColor(EHeadIconPart.Hair, Role.player.data:GetColorTint(EColorPart.Hair));
  scrollItems_Dialog[itemIndex].right_Elf:ChangeIconColor(EHeadIconPart.Skin, Role.player.data:GetColorTint(EColorPart.Skin));
  if message.side == EChatSide.Right then
    scrollItems_Dialog[itemIndex].right_dialog.text = message.message;
  else
    scrollItems_Dialog[itemIndex].left_dialog.text = message.message;
  end
  
  return true;
end

function UIAIChat.OnInitialize_ScrollContent_List(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_List[itemIndex] = {};
    scrollItems_List[itemIndex].event_BG = scrollItems[itemIndex]:GetComponent("UIEvent");
    scrollItems_List[itemIndex].event_BG:SetListener(EventTriggerType.PointerClick, this.OnClick_Elf);
    scrollItems_List[itemIndex].event_BG.parameter = itemIndex + 1;
    scrollItems_List[itemIndex].image_Elf = scrollItems[itemIndex]:Find("Image_Elf"):GetComponent("UIRawImage");
    scrollItems_List[itemIndex].text_Name = scrollItems[itemIndex]:Find("Text_Name"):GetComponent("Text");
    scrollItems_List[itemIndex].text_Stats = scrollItems[itemIndex]:Find("Text_Stats"):GetComponent("Text");
    scrollItems_List[itemIndex].state = scrollItems[itemIndex]:Find("Image_ON"):GetComponent("Image");
    scrollItems_List[itemIndex].used = scrollItems[itemIndex]:Find("Image_Used").gameObject;
  end
end

function UIAIChat.OnItemChange_ScrollContent_List(dataIndex, itemIndex)
  local index = dataIndex + 1;
  local aiChatData = aiChatDatas[index];
  if aiChatData == nil then return true end
  
  local elfData = elfDatas[aiChatData.elfId];
  if elfData == nil then  logError(" aiChatData.elfId : " .. aiChatData.elfId); return end

  TextureManager.SetPng(ETextureUseType.None, string.Concat("elf", elfData.Id, "_2"), scrollItems_List[itemIndex].image_Elf);
  scrollItems_List[itemIndex].image_Elf.rectTransform.sizeDelta = Vector2.one * scrollItems_List[itemIndex].image_Elf.texture.width;
  scrollItems_List[itemIndex].image_Elf:ShiftUV(1, 4, 0.5);
  scrollItems_List[itemIndex].image_Elf.enabled = true;
  scrollItems_List[itemIndex].text_Name.text = string.Get(elfData.name);
  scrollItems_List[itemIndex].event_BG.parameter = index;
  scrollItems_List[itemIndex].used:SetActive(aiChatData.used);

  if this.CanTalk(index, false) then
    scrollItems_List[itemIndex].state.sprite = UI.GetSprite("server_2");
    scrollItems_List[itemIndex].text_Stats.text =  string.GetColorText(string.Get(24867), Color.DarkGreen);
  else
    if this.CheckOpenDay(aiChatData) then
      if not this.CheckHaveElf(aiChatData) then
        scrollItems_List[itemIndex].state.sprite = UI.GetSprite("server_1");
        scrollItems_List[itemIndex].text_Stats.text = string.GetColorText(string.Get(24847), Color.Gray);
      end
    else
      scrollItems_List[itemIndex].state.sprite = UI.GetSprite("server_4");
      scrollItems_List[itemIndex].text_Stats.text =  string.GetColorText(string.Get(24919), Color.Red);
    end
  end

  return true;
end

function UIAIChat.OnClick_Send()
  if this.bNextTalk then
    ShowCenterMessage(string.Get(24876));
    return;
  end
  
  local message = inputField_Message.text;
  local length = string.GetLength(message);
  if length <= 0 then return end
  
  if length > 60 then
    ShowCenterMessage(string.Get(21185));
    return;
  end
  
  if CGTimer.time < nextSpeakTime then
    ShowCenterMessage(string.Get(20499));
    return;
  end
  
  if not TextChecker.Check(message, Chat.chatRegexPattern, 0, 0) then
    ShowCenterMessage(string.Get(40327));
    return;
  end
  
  for k, v in pairs(tabooDatas) do
    message = string.gsub(message, string.GetNocase(v),
      function(s)
        local count = string.Count(s);
        local result = "";
        for i = 1, count do
          result = string.Concat(result, "*");
        end
        return result;
      end
    );
  end--檢查禁用字

  sendBuffer:Clear();
  local aiChatData = aiChatDatas[this.currentTalk];
  if aiChatData == nil then return end
  
  sendBuffer:WriteUInt16(aiChatData.talkId);
  sendBuffer:WriteStringWithByteL(message, DataManager.encoding_Unicode);
  if sendBuffer.length > 300 then
    ShowCenterMessage(string.Get(20507));
    return;
  end
  
  Network.Send(2, 8, sendBuffer);
  
  this.AddMessage(EChatSide.Right, aiChatData.talkId, message);
  CGTimer.DoFunctionDelay(1,
    function()
      local index = math.random(1, 3);
      if responseDatas[index] ~= nil then
        this.AddMessage(EChatSide.Left, aiChatData.talkId, string.Get(responseDatas[index]));
      end
    end
  );
  Chat.AddMessage(EChannel.AI, message, Role.playerId, Role.player.name);
  inputField_Message.text = "";
  this.UpdateChatContent();
  nextSpeakTime = CGTimer.time + nextSpeakInterval;
  
  this.bNextTalk = true;
  CGTimer.DoCountdown(this.ResetNextTalk , 120);
  
  return true;
end

function UIAIChat.ResetNextTalk()
  this.bNextTalk = false;
end

function UIAIChat.AddMessage(chatSide, id, message, addChatChennel)
  local npcId = tonumber(id);
  local messageInfo = {};
  messageInfo.side = chatSide;
  messageInfo.npcId = npcId;
  local talkId = aiChatDatas[npcId];
  if talkId == nil or talkId.no == nil then logError(" npcId is not inaiChatDatas"); return end
  
  if chatSide == EChatSide.Left then
    local aiChatData = aiChatDatas[npcId];
    if aiChatData ~= nil then
      local elfData = elfDatas[aiChatData.elfId];
      messageInfo.message = string.Concat(string.Get(elfData.name), ":", message);
      local tableCount = table.Count(chats[talkId.no]);
      if math.fmod(tableCount, 2) == 0 then
        table.remove(chats[talkId.no], tableCount);
      end
    end
  else
    messageInfo.message = message;
  end
  
  if addChatChennel then
    Chat.AddMessage(EChannel.AI, messageInfo.message, id);
  end
  
  if chats[talkId.no] == nil then
    chats[talkId.no] = {};
  end
  
  table.insert(chats[talkId.no], messageInfo);
  if table.maxn(chats[talkId.no]) > this.maxMessage then
    table.remove(chats[talkId.no], 1);
  end
  
  if UI.IsVisible(UIAIChat) then
    this.UpdateChatContent();
  end
end

function UIAIChat.Clear()
  this.bNextTalk = false;
  table.Clear(chats);
end

function UIAIChat.OnClick_ElfIcon()
end

function UIAIChat.OnClick_Elf(uiEvent)
  local index = uiEvent.parameter;
  if not this.CanTalk(index, true) then return end
  
  for k, v in pairs(aiChatDatas) do
    v.used = false;
  end
  
  this.currentTalk = index;
  aiChatDatas[this.currentTalk].used = true;
  
  this.UpdateUI();
end

function UIAIChat.CheckHaveElf(aiChatData)
  local elfId = aiChatData.elfId;
  return BitFlag.Get(elfDatas[elfId].flag)
end

function UIAIChat.CheckOpenDay(aiChatData)
  local week = UIActivity.GetDayOfWeek(tostring(CGTimer.serverTime.DayOfWeek));
  return bit.band(aiChatData.openDay, week) ~= week;
end

function UIAIChat.CanTalk(index, showMessage)
  local aiChatData = aiChatDatas[index];
  local week = UIActivity.GetDayOfWeek(tostring(CGTimer.serverTime.DayOfWeek));
  if bit.band(aiChatData.openDay, week) ~= week then
    if showMessage then
      ShowCenterMessage(string.Get(24875));
    end
    return false;
  end
  
  if aiChatData.haveToTalk == 1 then
    local elfId = aiChatData.elfId;
    if elfDatas[elfId] == nil then
      logError(" elfId not in elfDatas ");
    end
    
    if not BitFlag.Get(elfDatas[elfId].flag) then
      if showMessage then
        ShowCenterMessage(string.Get(24874)); --未擁有精靈，不可對話
      end
      return false;
    end
  end
  
  return true;
end

function UIAIChat.GetFirstCurrentTalk()
  for i = 1, table.Count(aiChatDatas) do
    if this.CanTalk(i, false) then
      return i;
    end
  end
  
  return 0;
end

function UIAIChat.GetCurrentTalk()
  local npcId;
  if UIAIChat.currentTalk < 0 then
    npcId = UIAIChat.GetFirstCurrentTalk();
  else
    npcId = UIAIChat.currentTalk;
  end
  
  return npcId;
end

function UIAIChat.OnClick_Help(checkShow)
  if checkShow ~= true then
  UI.Open(UITutorialHint, 82, this);
  end
  
  return true;
end

function UIAIChat.OnOpen()
  Network.Send(1, 16); --開介面時先去要一次Sever時間校正
  
  for k, v in pairs(scrollItems_List) do
    v.used:SetActive(false);
  end
  
  this.currentTalk = this.GetCurrentTalk();
  if this.currentTalk > 0 then
    if aiChatDatas[this.currentTalk] ~= nil then
      aiChatDatas[this.currentTalk].used = true;
    end
  end
  
  scrollContent_List:Refresh(table.Count(aiChatDatas));
  return true;
end


function UIAIChat.UpdateUI()
  scrollContent_List:Refresh(table.Count(aiChatDatas));
  this.UpdateChatContent();
end

function UIAIChat.UpdateChatContent()
  local count = table.Count(chats[this.currentTalk]);
  scrollContent_Dialog:Reset(count, count);
end

function UIAIChat.OnClick_Close()
  inputField_Message.text = "";
  UI.Close(UIAIChat);

  return true;
end