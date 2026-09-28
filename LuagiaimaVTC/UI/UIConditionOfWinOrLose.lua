      
UIConditionOfWinOrLose = {};
local this = UIConditionOfWinOrLose;

this.name = "UIConditionOfWinOrLose";
this.uiController = nil;

--ui
local gameObject_View;

local image_Mask;

local text_Description;
local text_Title;

local scrollItems_Choose = {};

--var
local chooses;
local callback = nil;

function UIConditionOfWinOrLose.Initialize(go)
 local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onClose = this.OnClose;

  this.uiController:SetFadeIn(0.1, this.FadeIn);
  this.uiController:SetFadeOut(0.1, this.FadeOut);

  gameObject_View = uiController:FindGameObject("View");

  image_Mask = uiController:FindImage("Image_Mask");
 
  text_Description = uiController:FindText("Text_Description");
  text_Title = uiController:FindText("Text_Title");


  local tempEvent = uiController:FindEvent("Image_Mask");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Close);

  tempEvent = uiController:FindEvent("Image_BG");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Next);

end

function ShowWinLoseCondition(callback, message, showRole, showIcon)
  UI.Close(UIConditionOfWinOrLose);
  UI.Open(UIConditionOfWinOrLose,
  callback,
  message);
end

function UIConditionOfWinOrLose.OnOpen(callbackFunction, message)
  text_Description.text = message;
  text_Title.text = string.Get(21505);
  
  callback = callbackFunction;

  return true;
end

function UIConditionOfWinOrLose.OnClose()
  return true;
end

function UIConditionOfWinOrLose.OnClick_Next()
  if callback == nil then return end

  if chooses ~= nil then
  else
    callback();
  end
end

function UIConditionOfWinOrLose.OnClick_Close()
  if callback == nil then return end

  if chooses ~= nil then
    callback(0);
  else
    callback();
  end
end
