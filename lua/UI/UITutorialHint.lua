
UITutorialHint = {}
local this = UITutorialHint;

this.name = "UITutorialHint";
this.uiController = nil;

--ui
local text_Title;
local text_Content;

local closeFunction = nil;

function UITutorialHint.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onClose = this.OnClose;

  text_Title = uiController:FindText("Text_Title");
  text_Content = uiController:FindText("Text_Content");

  local tempEvent = uiController:FindEvent("Image_Mask");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Close);
end

function UITutorialHint.OnOpen(tutorialId, senderUI, title, content, callback)
  if tutorialDatas[tutorialId] ~= nil then
    title = string.Get(tutorialDatas[tutorialId].name);
    content = string.Get(tutorialDatas[tutorialId].content);
  end

  if title == nil or content == nil then return false end

  text_Title.text = title;
  text_Content.text = string.Concat("\n", content);
  text_Content.rectTransform:SetInsetAndSizeFromParentEdge(Edge.Top, 0, 0);

  if senderUI ~= nil and senderUI.uiController ~= nil then
    this.uiController.canvas.sortingOrder = senderUI.uiController.canvas.sortingOrder + 2;
  else
    this.uiController.canvas.sortingOrder = 300;
  end

  closeFunction = callback;

  return true;
end

function UITutorialHint.OnClose()
  if closeFunction ~= nil then
    closeFunction();
    closeFunction = nil;
  end

  return true;
end

function UITutorialHint.OnClick_Close()
  UI.Close(UITutorialHint);
end