UIAgeAlert = {}
local this = UIAgeAlert;

this.name = "UIAgeAlert";
this.uiController = nil;
local rawImage;

function UIAgeAlert.Initialize(go)
  local uiController = go:GetComponent("UIController");
  this.uiController = uiController;
  this.uiController.onOpen = this.OnOpen;

  local rawImageTransform = LuaHelper.FindChildRecursively(go.transform, "RawImage_Age", false);
  rawImage = rawImageTransform.gameObject:GetComponent(typeof(RawImage));
  rawImage.enabled = false;
end

function UIAgeAlert.OnOpen()
  local flag = CGResourceManager.DownloadText("AgeLimit.dat", false);
  if flag ~= nil and flag == "1" then
    log("UIAgeAlert rawImage.Enabled true");
    rawImage.enabled = true;
  else
    log("UIAgeAlert rawImage.Enabled false");
    rawImage.enabled = false;
  end

  return true;
end

function UIAgeAlert.SetRawImage(bShow)
  rawImage.enabled = bShow;
end