UIEmotion = {}
local this = UIEmotion;

this.name = "UIEmotion";
this.uiController = nil;

function UIEmotion.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;

  local emojiRoot = uiController:FindGameObject("Image_ContentBG");
  local tempEvent;

  for i = 1, 25 do
    tempEvent = emojiRoot.transform:Find(string.Concat("Image_Emoji (", i, ")")):GetComponent("UIEvent");
    tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Emoji);
    tempEvent.parameter = 99 + i;
  end

  tempEvent = uiController:FindEvent("Image_Mask");
  tempEvent:SetListener(EventTriggerType.PointerClick, this.OnClick_Close);
end

function UIEmotion.OnOpen()
  return true;
end

function UIEmotion.OnClick_Close()
  UI.Close(UIEmotion);
end

function UIEmotion.OnClick_Emoji(uiEvent)
  --C:032-001 <使用表情符號> +表情ID(1)
  sendBuffer:Clear()
  sendBuffer:WriteByte(uiEvent.parameter);
  Network.Send(32, 1, sendBuffer);

  Role.player:PlayEmoji(uiEvent.parameter);

  --GoogleAnalytics.Call("LogEvent", this.name, GAEventAction.ClickButton, GALabel.Emotion, uiEvent.parameter);

  UI.Close(UIEmotion);
end
