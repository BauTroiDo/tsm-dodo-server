UICareerController = {}
UICareerController.__index = UICareerController;

function UICareerController.New(parent)
  local self = {};
  setmetatable(self, UICareerController);

  self.gameObject = poolMgr:Get("BaseUICareer");
  self.transform = self.gameObject.transform;
  
  self.rectTransform = self.gameObject:GetComponent("RectTransform");
  self.rectTransform:SetParent(parent.transform);
  self.rectTransform:SetSiblingIndex(0);
  self.rectTransform.localScale = Vector3.one;
  self.rectTransform.anchorMin = Vector2.New(0.5, 0.5);
  self.rectTransform.anchorMax = Vector2.New(0.5, 0.5);
  self.rectTransform.anchoredPosition = Vector2.zero;

  self.image_Career = self.transform:GetComponent("Image");
  self.image_Frame = self.transform:Find("Image_Frame"):GetComponent("Image");
  self.text_Career = self.transform:Find("Text_Career"):GetComponent("Text");
  self.outline = self.text_Career:GetComponent("Outline8");
  self.gradientColor = self.text_Career:GetComponent("GradientColor");

  return self;
end

function UICareerController:Destroy()
  poolMgr:Release("BaseUICareer", self.gameObject);
end

function UICareerController:SetCareer(turn, element, career, isNpc, turn3Element)
  if element ~= nil then
    if turn == 0 then
      self.image_Frame.sprite = UI.GetSprite("icon039");
    elseif turn == 1 then
      self.image_Frame.sprite = UI.GetSprite("icon039B");
      
    elseif turn == 2 then
      self.image_Frame.sprite = UI.GetSprite("icon039C");
      
    elseif turn == 3 then
      if turn3Element == 7 then 
        self.image_Frame.sprite = UI.GetSprite("icon078");
      elseif turn3Element == 8 then 
        self.image_Frame.sprite = UI.GetSprite("icon079");
      else
        self.image_Frame.sprite = UI.GetSprite("icon039D");
      end
    end
  
    if turn == 0 then
      self.text_Career.text = string.GetElement(element);
    elseif turn >= 1 then
      if isNpc then
        self.text_Career.text = string.GetElement(element);
      else
        self.text_Career.text = string.GetCareer(career);
      end
    end

    self:SetColor(element);

    self.gameObject:SetActive(true);
  else
    self.gameObject:SetActive(false);
  end
  
  self.image_Career.sprite = UI.GetSprite("icon038");
  self.text_Career.color = Color.White;
end

function UICareerController:SetColor(element)
  self.image_Career.color = Color.CareerKind[element][1];
  self.outline.effectColor = Color.CareerKind[element][2];
  self.gradientColor.colorBottom = Color.CareerKind[element][3];
end

function UICareerController:SetCareerForDispatch(element)
  self.image_Career.sprite = UI.GetSprite("icon061");
  self.text_Career.color = LuaHelper.GetColor(188, 190, 187, 255);
  self.image_Career.color = Color.White;
  self.outline.effectColor = Color.Gray;
  self.gradientColor.colorBottom = Color.White;
  self.text_Career.text = string.GetElement(element);
end

function UICareerController:SetGray()
  self.image_Career.sprite = UI.GetSprite("icon061");
  self.text_Career.color = LuaHelper.GetColor(188, 190, 187, 255);
  self.image_Career.color = Color.White;
  self.outline.effectColor = Color.Gray;
  self.gradientColor.colorBottom = Color.White;
end