
UIHouseExpand = {}
local this = UIHouseExpand;
this.name = "UIHouseExpand";
this.uiController = nil;

local text_Expand;
local rawImage_Hammer;
local decimalPointCount = 1;
local decimalPointMaxNumber = 6;

function UIHouseExpand.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;

  text_Expand = uiController:FindText("Text_Expand");
  rawImage_Hammer = uiController:FindRawImage("RawImage_Hammer");
  rawImage_Hammer:SetLoopEndCallBack(this.OnExpandHouse);
end

function UIHouseExpand.OnOpen()
  this.InitializeExpandAnimation();
  this.StartExpandAnimation();
  return true
end

function UIHouseExpand.InitializeExpandAnimation()
  rawImage_Hammer.enabled = false;
  rawImage_Hammer:ShiftUV(1, 6, 0.3, 0);
  rawImage_Hammer:ChangeOrder(1);
end

function UIHouseExpand.StartExpandAnimation()
  rawImage_Hammer.enabled = true;
  rawImage_Hammer:ShiftUV(1, 6, 0.3, 2);
  if not CGTimer.ContainsListener(this.UpdateExpandText) then
    CGTimer.AddListener(this.UpdateExpandText, 0.3 , true);
  end
end

function UIHouseExpand.OnExpandHouse()
  CGTimer.RemoveListener(this.UpdateExpandText);
  UI.Close(this);
  UI.Close(UIHouseMap);
  HouseManager.SendExpandHouse();
end

function UIHouseExpand.UpdateExpandText()
  text_Expand.text = string.Get(23583);
  
  for i = 1, decimalPointCount do
    text_Expand.text = string.Concat(text_Expand.text, ".");
  end

  decimalPointCount = decimalPointCount % decimalPointMaxNumber + 1;
end