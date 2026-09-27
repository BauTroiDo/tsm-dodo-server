UIMainFunction = {}
local this = UIMainFunction;

this.name = "UIMainFunction";
this.uiController = nil;

local transform_FunctionTop;

local image_Mask;
local image_FunctionBG;
local image_Dot;

this.showAll = false;
this.showRedDot = false;

function UIMainFunction.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onVisible = this.OnVisible;

  Function.PrefabInitialize(1, uiController:FindGameObject("Function_Top").transform);

  image_Mask = uiController:FindImage("Image_Mask");
  image_FunctionBG = uiController:FindImage("Image_FunctionBG");

  Function.PrefabInitialize(3, image_FunctionBG.transform);

  local tempEvent;

  tempEvent = uiController:FindEvent("Image_Mask");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Switch);
  
  tempEvent = uiController:FindEvent("Image_Switch");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Switch);
  
  image_Dot = uiController:FindGameObject("Image_Dot");
  image_Dot:SetActive(false);
end

function UIMainFunction.OnVisible()
  Function.UpdateVisible();

  this.SetShowAll(false);

  return true;
end

function UIMainFunction.SetShowAll(value)
  this.showAll = value;

  image_Mask.enabled = this.showAll;
  image_FunctionBG.enabled = this.showAll;

  Function.UpdateVisible();
end

function UIMainFunction.OnClick_Switch()
  if Role.player ~= nil and Role.player.data.isWatching then return end
  
  this.SetShowAll(not this.showAll);
end

function UIMainFunction.UpdateRedDot()
  image_Dot:SetActive(this.showRedDot);
end
