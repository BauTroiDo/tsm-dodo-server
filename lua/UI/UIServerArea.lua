UIServerArea = {}
local this = UIServerArea;

EAeraStatus = {
  Good = 1,
  Crowded = 2,
  Full = 3
}

this.name = "UIServerArea";
this.uiController = nil;

local uiController;
local transform_FrameRect;
local area_button;
local gameObject_Area;
local area_RectTransform;
local text_CurrentArea;
local text_CurrentServer;
local gameObject_AreaPanel;
local gameObject_ServerPanel;
local wrapButon = {};
local scrollContent_Server;
local scrollItems_Server = {};
local image_SwitchServerPanel;
local image_SwitchAreaPanel;
local switchServerIndex;

this.instances = {};

function UIServerArea.Initialize(go)
  uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onVisible = this.OnVisible;

  transform_FrameRect = uiController:FindGameObject("FrameRect").transform;
  area_button = uiController:FindGameObject("Image_warp");
  gameObject_Area = uiController:FindGameObject("Areas");
  area_RectTransform = gameObject_Area:GetComponent("RectTransform");
  text_CurrentArea = uiController:FindText("Text_CurrentArea").gameObject:GetComponent("Text");
  text_CurrentServer = uiController:FindText("Text_CurrentServer").gameObject:GetComponent("Text");
  gameObject_AreaPanel = uiController:FindGameObject("Area");
  gameObject_ServerPanel = uiController:FindGameObject("Server");
  image_SwitchServerPanel = uiController:FindImage("Image_SwitchServerPanel");
  image_SwitchAreaPanel = uiController:FindImage("Image_SwitchAreaPanel");
  
  local tempEvent = uiController:FindEvent("Image_SwitchServerPanel");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchServerPanel);
  local tempEvent = uiController:FindEvent("Image_SwitchAreaPanel");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_SwitchAreaPanel);

  this.InitServerScroll();
end

function UIServerArea.OnOpen(tag)
  text_CurrentArea.text = string.Concat(string.Get(10126), ":", SceneManager.instanceId);

  if Network.IsInActivityServer() then
    text_CurrentServer.text = string.Concat(string.Get(24749));
  else
    local serverName = Network.GetServerName();
    text_CurrentServer.text = string.Concat(string.Get(24744), ":", serverName);
  end
  
  this.UpdateServerScroll();
  this.InitUI();
  return true;
end

function UIServerArea.OnVisible(visible)
  if visible then
    --1--------------------------------------------------------------
    if LuaHelper.CheckDefine("DemoCode") then
      local code = DemoCode.GetBitxor(20);
      code = code * 10;
    end
    ---2--------------------------------------------------------------
    UIFrame.Active(transform_FrameRect, string.Get(24743), this.OnClick_Close, nil, this.OnClick_Tag, nil, nil);

    this.UpdateUI();
  end
end

function UIServerArea.UpdateUI()
  if this.instances == nil then return end

  for k, v in pairs(wrapButon) do
    wrapButon[k].go:SetActive(false);
  end
  
  for k, v in pairs(this.instances) do
      if wrapButon[k] == nil then
        wrapButon[k] = {};
        wrapButon[k].go = GameObject.Instantiate(area_button);
        wrapButon[k].go.transform:SetParent(gameObject_Area.transform);
        wrapButon[k].go:GetComponent("RectTransform").localScale = Vector3.one;

        wrapButon[k].event = wrapButon[k].go:GetComponent("UIEvent");
        wrapButon[k].event:SetListener(EventTriggerType.PointerClick, this.OnClick_Area);

        wrapButon[k].text = wrapButon[k].go.transform:Find("Text_1"):GetComponent("Text");
        wrapButon[k].imgae = wrapButon[k].go.transform:Find("Image_1"):GetComponent("Image");
      end
      wrapButon[k].event.parameter = v.index;
      wrapButon[k].text.text = string.Concat(string.Get(10126), v.index);
      
      -- 分區狀態
      if v.currentPlayers ~= nil and v.maxPlayers ~= nil then 
        local x  =  (v.currentPlayers / v.maxPlayers) * 100 ;
        if x > 80 then  
          wrapButon[k].imgae.sprite = UI.GetSeverLight(EAeraStatus.Full);
        elseif x <= 80 and x > 60 then
          wrapButon[k].imgae.sprite = UI.GetSeverLight(EAeraStatus.Crowded);
        else
          wrapButon[k].imgae.sprite = UI.GetSeverLight(EAeraStatus.Good);
        end
      else
        wrapButon[k].imgae.sprite = UI.GetSeverLight(EAeraStatus.Good);
      end

      if SceneManager.instanceId ~= v.index then
        wrapButon[k].go:SetActive(true);
      else
        wrapButon[k].go:SetActive(false);
      end
  end

  area_button:SetActive(false);
  area_RectTransform.sizeDelta = Vector2.New(300, table.Count(this.instances) * 60);
  area_RectTransform.transform.localPosition = Vector3.New(0, (table.Count(this.instances) * -50) / 2, 0);
end

function UIServerArea.InitServerScroll()
  scrollContent_Server = uiController:FindScrollContent("ScrollContent_Server");
  scrollContent_Server.onInitialize = this.OnInitialize_ScrollContent_Server;
  scrollContent_Server.onItemChange = this.OnItemChange_ScrollContent_Server;
  scrollContent_Server:Initialize(1);
end

function UIServerArea.OnInitialize_ScrollContent_Server(scrollItems)
  for itemIndex = 0, scrollItems.Length - 1 do
    scrollItems_Server[itemIndex] = {};
    scrollItems_Server[itemIndex].text_ServerName = scrollItems[itemIndex]:Find("Text_ServerName"):GetComponent("Text");
    scrollItems_Server[itemIndex].event_Switch = scrollItems[itemIndex]:Find("Image_Switch"):GetComponent("UIEvent");
    scrollItems_Server[itemIndex].event_Switch:SetListener(EventTriggerType.PointerClick, this.OnClick_Switch);
    scrollItems_Server[itemIndex].image_Crowd = scrollItems[itemIndex]:Find("Image_Crowd"):GetComponent("Image");
  end
end

function UIServerArea.OnItemChange_ScrollContent_Server(dataIndex, itemIndex)

  local serverList = Network.GetAllServerInDB();
  local serverId = serverList[dataIndex + 1];
  local serverName;

  for key, value in pairs(Network.servers) do
    if value.id == serverId then
      serverName = Network.servers[key].name;
    end
  end

  scrollItems_Server[itemIndex].text_ServerName.text = serverName;
  scrollItems_Server[itemIndex].event_Switch.parameter = dataIndex + 1;
  scrollItems_Server[itemIndex].image_Crowd.sprite = UI.GetSeverLight(EAeraStatus.Good);

  return true;
end

function UIServerArea.UpdateServerScroll()
  local servers = Network.GetAllServerInDB();
  local amount = table.Count(servers);
  scrollContent_Server:Reset(amount);
end

function UIServerArea.InitUI()
  gameObject_AreaPanel:SetActive(false);
  gameObject_ServerPanel:SetActive(true);
  image_SwitchServerPanel.sprite = UI.GetSprite("kin001L");
  image_SwitchAreaPanel.sprite = UI.GetSprite("kin001D");
  scrollContent_Server.gameObject:SetActive(not Network.InUnbound);
end

function UIServerArea.OnClick_Area(uiEvent)
  ---2--------------------------------------------------------------
  if LuaHelper.CheckDefine("DemoCode") then
    local area =  DemoCode.CalTriangleArea(100, 100)
    area = area * 1 + 2;
    if area > DemoCode.GetBitxor(20) then  --test avg
      local avg = DemoCode.CalAvg(10, 20, 30);
    end
  end
  --3----------------------------------------------------
  if SceneManager.instanceId == uiEvent.parameter then return end

  if not Team.IsAlone(Role.playerId) then
    ShowCenterMessage(string.Get(20728));
    return;
  end

  --C:007-002 <切換分區> +分區(2)
  sendBuffer:Clear();
  sendBuffer:WriteUInt16(uiEvent.parameter);
  Network.Send(7, 2, sendBuffer);
end

function UIServerArea.OnClick_SwitchServerPanel()
  gameObject_AreaPanel:SetActive(false);
  gameObject_ServerPanel:SetActive(true);
  image_SwitchServerPanel.sprite = UI.GetSprite("kin001L");
  image_SwitchAreaPanel.sprite = UI.GetSprite("kin001D");
end

function UIServerArea.OnClick_SwitchAreaPanel()
  gameObject_AreaPanel:SetActive(true);
  gameObject_ServerPanel:SetActive(false);
  image_SwitchServerPanel.sprite = UI.GetSprite("kin001D");
  image_SwitchAreaPanel.sprite = UI.GetSprite("kin001L");
end

function UIServerArea.OnClick_Switch(uiEvent)
  local serverList = Network.GetAllServerInDB();
  if serverList == nil then return end;

  local switchServerId = serverList[uiEvent.parameter];
  if switchServerId == Setting.client.lastServerId then
    ShowCenterMessage(string.Get(24748));
    return;
  end

  for key, value in pairs(Network.servers) do
    if value.id == switchServerId then
      switchServerIndex = key;
    end
  end
  if switchServerIndex == nil then return end;

  sendBuffer:Clear();
  sendBuffer:WriteUInt16(switchServerId);
  Network.Send(1, 100, sendBuffer);
end

function UIServerArea.DoConnect(verifyCode, switchServerId)

  for key, value in pairs(Network.servers) do
    if value.id == switchServerId then
      switchServerIndex = key;
    end
  end

  Network.Disconnect();
  Network.serverId = Network.servers[switchServerIndex].id;
  Network.host = Network.servers[switchServerIndex].host;
  Network.port = Network.servers[switchServerIndex].port;
  Network.loginKind = ELogin.ChangeArea;
  Network.connectCode = verifyCode;
  
  CGTimer.DoFunctionDelay(1.5,Network.Connect);
end

function UIServerArea.OnClick_Close()
  UI.Close(UIServerArea);

  return true;
end
