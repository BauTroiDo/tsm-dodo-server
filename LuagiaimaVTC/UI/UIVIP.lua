UIVIP = {}
local this = UIVIP;

this.name = "UIVIP";
this.uiController = nil;

local transform_FrameRect;
local scrollContent_VIP;
local scrollItems_VIP = {};
local slider_Exp;
local gameObject_Effective;
--local gameObject_Received;
local text_VIPLevel;
local text_Content;
local text_Prev;
local text_Next;
local text_BaseContent;
local exp = 0;
this.VIPLV = 1;

EVIPPermission = {
  LV = 0,
  Dungeon = 1,
  Card = 2,
  Mount = 3,
  EpicDungeon = 4,
}

function UIVIP.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onVisible = this.OnVisible;

  transform_FrameRect = uiController:FindGameObject("FrameRect").transform;
  gameObject_Effective = uiController:FindGameObject("Text_Effective");
  --gameObject_Received = uiController:FindGameObject("Text_Received");
  text_BaseContent = uiController:FindText("Text_BaseContent");
  text_VIPLevel = uiController:FindText("Text_VIPLevel");
  text_Content = uiController:FindGameObject("Text_Content");
  text_Prev = uiController:FindText("Text_Prev");
  text_Next = uiController:FindText("Text_Next");
  slider_Exp = uiController:FindSlider("Slider_Exp");

  scrollContent_VIP = uiController:FindScrollContent("ScrollContent_VIP");
  scrollContent_VIP.onInitialize = this.OnInitialize_ScrollContent_VIP;
  scrollContent_VIP.onItemChange = this.OnItemChange_ScrollContent_VIP;
  scrollContent_VIP:Initialize(1);

  local tempEvent;
  tempEvent = uiController:FindEvent("Image_AddGold");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_AddGold);
end

function UIVIP.OnVisible(visible)
  if visible then
    UIFrame.Active(transform_FrameRect, string.Get(25042), this.OnClick_Close, this.OnClick_Help);
  end
end

function UIVIP.OnClick_Help(checkShow)
  if checkShow ~= true then
    UI.Open(UITutorialHint, 84, this);
  end
  return true;
end

function UIVIP.OnOpen()
  this.UpdateUI(true);
  return true;
end

function UIVIP.UpdateUI(reset)
  if reset then
    scrollContent_VIP:Reset(table.Count(vipRewardDatas), this.VIPLV - 1);
  else
    scrollContent_VIP:Refresh();
  end

  this.UpdateInfo();
end

function UIVIP.UpdateInfo()
  local currentDataExp = 0;

  if vipPrivilegesDatas[this.VIPLV + 1] ~= nil then
    local diffExp = vipPrivilegesDatas[this.VIPLV + 1].exp - exp;
    if diffExp <= 0 then
      text_BaseContent.text = "";
    else
      text_BaseContent.text = string.format(string.Get(25046), diffExp, this.VIPLV + 1);
    end
  else
    text_BaseContent.text = "";
  end

  text_VIPLevel.text = string.format(string.Get(25043), this.VIPLV);

  if this.VIPLV <= 0 then
    slider_Exp.value = exp / vipPrivilegesDatas[this.VIPLV + 1].exp;
    gameObject_Effective:SetActive(false);
  elseif this.VIPLV == table.Count(vipPrivilegesDatas) then
    currentDataExp = vipPrivilegesDatas[this.VIPLV].exp;
    slider_Exp.value = 1;
    gameObject_Effective:SetActive(true);
  else
    slider_Exp.value = exp / vipPrivilegesDatas[this.VIPLV + 1].exp;
    gameObject_Effective:SetActive(true);
  end
end

function UIVIP.OnClick_Close()
  UI.Close(UIVIP);

  return true;
end

function UIVIP.OnClick_AddGold()
  UI.Open(UIMall, 3);
end

function UIVIP.OnClick_GetArawd(uiEvent)
  local index = uiEvent.parameter;
  if BitFlag.Get(vipRewardDatas[index].flag) then
    ShowCenterMessage(string.Get(98017));
    return;
  end

  if index > this.VIPLV then
    ShowCenterMessage(string.Get(24235));
    return;
  end

  sendBuffer:Clear()
  sendBuffer:WriteByte(index);
  Network.Send(75, 1, sendBuffer);
end

function UIVIP.CheckRedDot()
  local result = false;
  for i, v in pairs(vipRewardDatas) do
    if not BitFlag.Get(v.flag) and i < this.VIPLV then
      result = true;
      return result;
    end
  end

  return result;
end
function UIVIP.CalVIPLevel(exp)
  local lv = 0;

  for i, v in pairs(vipPrivilegesDatas) do
    if v.exp > exp  then
      lv = i - 1;
      return lv;
    elseif v.exp == exp then
      lv = i;
      return lv;
    end
  end

  lv = table.Count(vipPrivilegesDatas);
  return lv;
end

function UIVIP.SetVIPExp(data)
  exp = data:ReadInt32();
  this.VIPLV = this.CalVIPLevel(exp);

  if UI.IsVisible(UIVIP) then
    this.UpdateUI();
  end

  if this.CheckRedDot() then
    RedDot.Add(ERedDot.VIP);
  else
    RedDot.Remove(ERedDot.VIP);
  end
end

function UIVIP.OnInitialize_ScrollContent_VIP(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_VIP[itemIndex] = {};
    scrollItems_VIP[itemIndex].awardItem = UIItemController.New(scrollItems[itemIndex]:Find("Image_Item"));
    scrollItems_VIP[itemIndex].content = scrollItems[itemIndex]:Find("ScrollRect"):Find("VIPView"):Find("ScrollContent"):Find("Text_Content"):GetComponent("Text");
    scrollItems_VIP[itemIndex].redDot = scrollItems[itemIndex]:Find("Image_Button"):Find("Image_RedDot").gameObject;
    scrollItems_VIP[itemIndex].received = scrollItems[itemIndex]:Find("Text_Received").gameObject;
    scrollItems_VIP[itemIndex].awardState = scrollItems[itemIndex]:Find("Image_Button"):Find("Text_Msg"):GetComponent("Text");
    scrollItems_VIP[itemIndex].event = scrollItems[itemIndex]:Find("Image_Button"):GetComponent("UIEvent");
    scrollItems_VIP[itemIndex].event:SetListener(EventTriggerType.PointerClick, this.OnClick_GetArawd);
    scrollItems_VIP[itemIndex].event.parameter = itemIndex;
  end
end

function UIVIP.OnItemChange_ScrollContent_VIP(dataIndex, itemIndex)
  local index = dataIndex + 1;

  scrollItems_VIP[itemIndex].awardItem:SetItem(vipRewardDatas[index].itemId, vipRewardDatas[index].count);
  scrollItems_VIP[itemIndex].awardItem:SetEvent(this.OnClick_ItemInfo, vipRewardDatas[index].itemId);
  scrollItems_VIP[itemIndex].event.parameter = index;
  scrollItems_VIP[itemIndex].redDot:SetActive(not BitFlag.Get(vipRewardDatas[index].flag) and index <= this.VIPLV);
  scrollItems_VIP[itemIndex].content.text = string.Get(vipRewardDatas[index].info);
  if BitFlag.Get(vipRewardDatas[index].flag) then
    scrollItems_VIP[itemIndex].awardState.transform.parent.gameObject:SetActive(false);
    scrollItems_VIP[itemIndex].awardState.text = string.Get(98017);  --已領取
    scrollItems_VIP[itemIndex].received:SetActive(true);
  else
    if index <= this.VIPLV then
      scrollItems_VIP[itemIndex].awardState.transform.parent.gameObject:SetActive(true);
      scrollItems_VIP[itemIndex].awardState.text = string.Get(21062);  --可領取
      scrollItems_VIP[itemIndex].received:SetActive(false);
    else
      scrollItems_VIP[itemIndex].awardState.transform.parent.gameObject:SetActive(false);
      scrollItems_VIP[itemIndex].received:SetActive(false);
    end
  end

  return true;
end

function UIVIP.GetVIPLevel(kind)
  if this.VIPLV == 0 then
    return 0;
  end

  if kind == EVIPPermission.LV then
    return this.VIPLV;
  elseif kind == EVIPPermission.Dungeon then
    return vipPrivilegesDatas[this.VIPLV].dungeon;
  elseif kind == EVIPPermission.Card then
    return vipPrivilegesDatas[this.VIPLV].card;
  elseif kind == EVIPPermission.Mount then
    return vipPrivilegesDatas[this.VIPLV].mount;
  elseif kind == EVIPPermission.EpicDungeon then
    return vipPrivilegesDatas[this.VIPLV].epicDungeon;
  else
    return 0;
  end
end