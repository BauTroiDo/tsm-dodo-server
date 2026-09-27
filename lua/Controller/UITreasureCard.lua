UITreasureCard = {}
UITreasureCard.__index = UITreasureCard;

function UITreasureCard.New(parent)
  local self = {};
  setmetatable(self, UITreasureCard);

  self.treasureData = nil;

  self.gameObject = poolMgr:Get("BaseTreasureCard");
  self.transform = self.gameObject.transform;
  
  self.rectTransform = self.gameObject:GetComponent("RectTransform");
  self.rectTransform:SetParent(parent);
  self.rectTransform.anchorMin = Vector2.zero;
  self.rectTransform.anchorMax = Vector2.one;
  self.rectTransform.anchoredPosition = Vector2.zero;
  self.rectTransform.sizeDelta = Vector2.zero;
  self.rectTransform.localScale = Vector2.one;

  self.image_BG = self.transform:Find("Image_BG"):GetComponent("RawImage");
  self.root_Content = self.transform:Find("Root_Content");
  self.image_Pic = self.root_Content.transform:Find("Image_Pic"):GetComponent("RawImage");
  self.image_Frame = self.root_Content.transform:Find("Image_Frame"):GetComponent("RawImage");
  self.image_Gray = self.root_Content.transform:Find("Image_Frame"):Find("Image_Gray"):GetComponent("Image");
  self.event_Frame = self.image_Frame.gameObject:GetComponent("UIEvent");
  self.text_Level = self.image_Frame.transform:Find("Text_Level"):GetComponent("Text");
  self.text_canStar = self.image_Frame.transform:Find("Text_CanStar"):GetComponent("Text");
  self.gameObject_RedDot = self.root_Content.transform:Find("Image_RedDot").gameObject;
  self.gameObject_RedDot:SetActive(false);

  local starRoot = self.image_Frame.transform:Find("StarRoot");

  self.stars = {};

  for i = 1, 6 do
    self.stars[i] = starRoot:Find(string.Concat("Image_Star (", i, ")")):GetComponent("Image");
  end

  return self;
end

function UITreasureCard:OnClick()
  if self.clickEvent ~= nil then 
    self.clickEvent(self.treasureData.id);
  end
end

function UITreasureCard:Destroy()
  self:SetLevelText(true);
  poolMgr:Release("BaseTreasureCard", self.gameObject);
end

function UITreasureCard:SetData(cardId, clickEvent)
  self.treasureData = Treasure.treasureItems[cardId];
  if self.treasureData == nil then
    self:SetShow(false);
    return;
  end

  self.text_Level.text = self.treasureData.level;
  self.text_canStar.text = string.Get(24656);
  self.text_canStar.enabled = self.treasureData.level == 0;
  self.image_Gray.enabled = self.treasureData.count == 0;

  self:SetStarRoot(true);

  for i = 1, 6 do
    if i <= self.treasureData.star then
      self.stars[i].sprite = UI.GetSprite("icon153");
    else
      self.stars[i].sprite = UI.GetSprite("icon201");
    end
  end

  if clickEvent ~= nil then 
    self.event_Frame:SetListener(EventTriggerType.PointerClick, clickEvent);
    self.event_Frame.parameter = cardId;
  end

  TextureManager.SetPng(ETextureUseType.UI, tostring(self.treasureData.cardTexture), self.image_Pic);

  local rank = self.treasureData.quality;
  TextureManager.SetPng(ETextureUseType.UI, string.Concat("TreasureFrame_", rank), self.image_Frame);
end

function UITreasureCard:SetShow(isVisible)
  self.root_Content.gameObject:SetActive(isVisible);
  if not isVisible then
    TextureManager.SetPng(ETextureUseType.UI, "CardSmallBG", self.image_BG);
  end
end

function UITreasureCard:SetGray(enabled)
  self.image_Gray.enabled = enabled;
end

function UITreasureCard:SetStarRoot(enabled)
  for i = 1, 6 do
    self.stars[i].enabled = enabled;
  end
end

function UITreasureCard:SetLevelText(enabled)
  self.text_Level.enabled = enabled;
end

function UITreasureCard:SetRedDot(active)
  self.gameObject_RedDot:SetActive(active);
end

function UITreasureCard:SetCantUse(active)
  self.text_canStar.enabled = active;
end