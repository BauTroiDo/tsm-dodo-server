UICardController = {}
UICardController.__index = UICardController;

function UICardController.New(parent)
  local self = {};
  setmetatable(self, UICardController);

  self.cardData = nil;  

  self.gameObject = poolMgr:Get("BaseCard");
  self.transform = self.gameObject.transform;
  
  self.rectTransform = self.gameObject:GetComponent("RectTransform");
  self.rectTransform:SetParent(parent);
  self.rectTransform.anchorMin = Vector2.zero;
  self.rectTransform.anchorMax = Vector2.one;
  self.rectTransform.anchoredPosition = Vector2.zero;
  self.rectTransform.sizeDelta = Vector2.zero;
  self.rectTransform.localScale = Vector2.one;

  self.image_BG = self.transform:Find("Image_BG"):GetComponent("RawImage");
  self.image_Frame = self.transform:Find("Image_Frame"):GetComponent("RawImage");
  self.image_Element = self.transform:Find("Image_Element"):GetComponent("RawImage");
  self.text_Element = self.image_Element.transform:Find("Text"):GetComponent("Text");
  self.image_EFrame = self.transform:Find("Image_EFrame"):GetComponent("RawImage");
  self.text_Count = self.image_EFrame.transform:Find("Text"):GetComponent("Text");
  local rootStars = self.transform:Find("Root_Stars");
  self.image_Stars = {};
  for i = 1, 5 do 
    self.image_Stars[i] = rootStars.transform:Find(string.Concat("RawImage", i)):GetComponent("RawImage");
  end
  self.text_Name = self.transform:Find("Text_Name"):GetComponent("Text");

  return self;
end

function UICardController:Destroy()
  poolMgr:Release("BaseCard", self.gameObject);
end

function UICardController:LoadFrames()
  TextureManager.SetPng(ETextureUseType.UI, "CardCareer", self.image_Element);
  TextureManager.SetPng(ETextureUseType.UI, "CardCareerFrame", self.image_EFrame);
  for i = 1, 5 do
    TextureManager.SetPng(ETextureUseType.UI, "CardStar", self.image_Stars[i]);    
  end
end

function UICardController:SetData(cardId, count)
  self.cardData = collectCardSortDatas[cardId];
  if self.cardData == nil or npcDatas[self.cardData.warriorId] == nil then 
    return 
  end
  self:LoadFrames();
  TextureManager.SetPng(ETextureUseType.UI, string.Concat("C", self.cardData.warriorId), self.image_BG);
  self.text_Name.text = npcDatas[self.cardData.warriorId].name;
  local rank = npcDatas[self.cardData.warriorId].rare;
  if rank <= 3 then 
    TextureManager.SetPng(ETextureUseType.UI, "CardFrame_0", self.image_Frame);
  elseif rank == 4 then      
    TextureManager.SetPng(ETextureUseType.UI, "CardFrame_1", self.image_Frame);
  else
    TextureManager.SetPng(ETextureUseType.UI, "CardFrame_2", self.image_Frame);
  end
  
  for i = 1, 5 do 
    self.image_Stars[i].gameObject:SetActive(rank >= i);
  end

  self.image_Element.color = Color.CareerKind[npcDatas[self.cardData.warriorId].element][1];
  self.text_Element.text = string.GetElement(npcDatas[self.cardData.warriorId].element);
  if count ~= nil then 
    self.text_Count.text = string.Concat("x", count);
  else
    self.text_Count.text = "";
  end
end