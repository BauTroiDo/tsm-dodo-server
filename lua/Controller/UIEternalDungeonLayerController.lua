UIEternalDungeonLayerController = {}
UIEternalDungeonLayerController.__index = UIEternalDungeonLayerController;

function UIEternalDungeonLayerController.New(parent)
  local self = {};
  setmetatable(self, UIEternalDungeonLayerController);

  self.gameObject = poolMgr:Get("BaseUIEternalDungeonLayer");
  self.transform = self.gameObject.transform;

  self.rectTransform = self.gameObject:GetComponent("RectTransform");
  self.rectTransform:SetParent(parent);
  self.rectTransform.anchorMin = Vector2.zero;
  self.rectTransform.anchorMax = Vector2.one;
  self.rectTransform.anchoredPosition = Vector2.zero;
  self.rectTransform.sizeDelta = Vector2.zero;
  self.rectTransform.localScale = Vector2.one;
  
  self.clickObject = self.transform:Find("Image_Click").gameObject;
  self.clickImage = self.clickObject:GetComponent("Image");
  self.clickEvent = self.clickObject:GetComponent("UIEvent");
  self.clickObject:SetActive(true);
  self.layerText = self.transform:Find("Text_LayerText"):GetComponent("Text");
  self.clearObject = self.transform:Find("Image_ClearLayer").gameObject;
  self.clearText = self.clearObject.transform:Find("Text_ClearText"):GetComponent("Text");

  return self;
end

function UIEternalDungeonLayerController:Destroy()
    poolMgr:Release("BaseUIEternalDungeonLayer", self.gameObject);
end

function UIEternalDungeonLayerController:SetClickEvent(eventHandler, eventParameter)
    self.clickEvent:ClearListener();  
    if eventHandler ~= nil then
        self.clickEvent:SetListener(EventTriggerType.PointerClick, eventHandler);
        self.clickEvent.parameter = eventParameter;
    end
end
  
function UIEternalDungeonLayerController:SetEnable(index)
    self.clickEvent.enabled = index <= RoleCount.Get(ERoleCount.EternalDungeon)+1;
    self.clearObject:SetActive(index <= RoleCount.Get(ERoleCount.EternalDungeon));
end

function UIEternalDungeonLayerController:SetIndex(index)
    self.gameObject.name = "Layer ("..index..")";
    self.layerText.text = index;
    self.index = index;
    self.clearText.text = string.Get(23319);
end

function UIEternalDungeonLayerController:SetClick(active)
    if active then
        self.clickImage.sprite = UI.GetSprite("but206H");
    else
        if self.index <= RoleCount.Get(ERoleCount.EternalDungeon)+1 then
            self.clickImage.sprite = UI.GetSprite("but206D");
        else
            self.clickImage.sprite = UI.GetSprite("but206L");
        end
    end
end

function UIEternalDungeonLayerController:GetIndex()
    return self.index;
end

function UIEternalDungeonLayerController:SetActive(active)
    self.gameObject:SetActive(active);
end
  