UIAccount = {}
local this = UIAccount;

this.name = "UIAccount";
this.uiController = nil;

local transform_FrameRect;

local text_Kind;
local text_Account;

local image_FBLock;

local scrollContent_Mission;
local scrollItems_Mission = {};

local event_FB;

function UIAccount.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;
  this.uiController.onVisible = this.OnVisible;
  transform_FrameRect = uiController:FindGameObject("FrameRect").transform;

  text_Kind = uiController:FindText("Text_Kind");
  text_Account = uiController:FindText("Text_Account");

  image_FBLock = uiController:FindImage("Image_FBLock");

  event_FB = uiController:FindEvent("Image_FB");
  event_FB:SetListener(EventTriggerType.PointerClick, this.OnClick_Bind);
  event_FB.parameter = EAccount.FB;
end

function UIAccount.OnOpen()
  return true;
end

function UIAccount.OnVisible(visible)
  if visible then
    UIFrame.Active(transform_FrameRect, string.Get(21088), this.OnClick_Close);
    this.UpdateUI();
  end
end

function UIAccount.UpdateUI()
  text_Account.text = Network.account;

  if Network.accountKind == EAccount.FB then
    text_Kind.text = string.Get(21129);

    image_FBLock.gameObject:SetActive(true);
    event_FB:SetColor(Color.LightGray);
  else
    if string.find(Network.account, "@fast") ~= nil then
      text_Kind.text = string.Get(21128);
    else
      text_Kind.text = string.Get(21127);
    end

    image_FBLock.gameObject:SetActive(false);
    event_FB:SetColor(Color.White);
  end
end

function UIAccount.OnClick_Close(uiEvent)
  UI.Close(UIAccount);
  return true;
end

function UIAccount.OnClick_Bind(uiEvent)
  if Network.accountKind == uiEvent.parameter then return end
  
  if uiEvent.parameter == EAccount.FB then
    CGFacebook.Login(
      function(errorMessage)
        if string.IsNullOrEmpty(errorMessage) then
          logError(CGFacebook.userId);
          logError(CGFacebook.accessToken);
          
          --C:001-003 <帳號綁定> +綁定方式(1)
          --綁定方式 1:FB +L(1) +UserId(L) +L(1) +AccessToken(L)
          sendBuffer:Clear();
          sendBuffer:WriteByte(EAccount.FB);
          sendBuffer:WriteStringWithByteL(CGFacebook.userId);
          sendBuffer:WriteStringWithByteL(CGFacebook.accessToken);
          Network.Send(1, 3, sendBuffer);
        else
          logError(errorMessage);
        end
      end,
      "public_profile"
    );
  end
end