EHeadIconPart = {
  Hair = 1,
  Skin = 2,
  Accessories = 3,
  Eyes = 4,
  HairLight = 5,
}

HeadIconController = {}
HeadIconController.__index = HeadIconController;

local baseHairColor = LuaHelper.GetColor(135, 73, 61, 255);
local baseHairLightColor = LuaHelper.GetColor(224, 141, 96, 255);
local baseBodyColor = LuaHelper.GetColor(240, 217, 173, 255);

function HeadIconController.New(parent)
  local self = {};
  setmetatable(self, HeadIconController);

  self.gameObject = poolMgr:Get("BaseHeadIcon");
  self.transform = self.gameObject.transform;
  
  self.rectTransform = self.gameObject:GetComponent("RectTransform");
  self.rectTransform:SetParent(parent);
  self.rectTransform.anchorMin = Vector2.zero;
  self.rectTransform.anchorMax = Vector2.one;
  self.rectTransform.anchoredPosition = Vector2.zero;
  self.rectTransform.sizeDelta = Vector2.zero;
  self.rectTransform.localScale = Vector2.one;

  self.rawImage_BG = self.transform:GetComponent("RawImage");
  self.rawImage_Light = self.transform:Find("RawImage_Light"):GetComponent("RawImage");
  self.rawImage_Mask = self.transform:Find("RawImage_Mask"):GetComponent("RawImage");
  self.rawImage_Layers = {};
  for i = 1, 5 do
    self.rawImage_Layers[i] = self.rawImage_Mask.transform:Find(string.Concat("RawImage_Layer (", i, ")")):GetComponent("RawImage");
  end
  
  self.isGray = false;

  self:SetHighLight(false);

  return self;
end

function HeadIconController:Destroy()
  poolMgr:Release("BaseHeadIcon", self.gameObject);

  for i = 1, 5 do
    self.rawImage_Layers[i].texture = nil;
  end
end

function HeadIconController:ChangeIcon(sex, style)
  if Between(style, Role.hairStyleCount[0][sex], (Role.hairStyleCount[0][sex] * 2) - 1) then
    style = style - Role.hairStyleCount[0][sex];
  end

  for i = 1, 5 do
    if sex == 1 then
      TextureManager.SetPng(ETextureUseType.UI, string.Concat("Head_Male_", style + 1, "_", i), self.rawImage_Layers[i]);
    else
      TextureManager.SetPng(ETextureUseType.UI, string.Concat("Head_Female_", style + 1, "_", i), self.rawImage_Layers[i]);
    end
  end
end

function HeadIconController:SetHighLight(active)
  self.rawImage_Light.gameObject:SetActive(active);
end

function HeadIconController:SetGray(isGray)
  if self.isGray == isGray then return end

  self.isGray = isGray;

  for i = 1, 5 do
    if self.isGray then
      self.rawImage_Layers[i].color = LuaHelper.GetColor(self.rawImage_Layers[i].color.r * 255 * 0.5, self.rawImage_Layers[i].color.g * 255 * 0.5, self.rawImage_Layers[i].color.b * 255 * 0.5, 255);
    else
      self.rawImage_Layers[i].color = LuaHelper.GetColor(self.rawImage_Layers[i].color.r * 255 * 2, self.rawImage_Layers[i].color.g * 255 * 2, self.rawImage_Layers[i].color.b * 255 * 2, 255);
    end
  end
end

function HeadIconController:ChangeIconColor(part, r, g, b)
  if part == EHeadIconPart.Hair then
    self:ChangeColor(EHeadIconPart.Hair, baseHairColor, r, g, b);
    self:ChangeColor(EHeadIconPart.HairLight, baseHairLightColor, r, g, b);
  elseif part == EHeadIconPart.Skin then
    self:ChangeColor(EHeadIconPart.Skin, baseBodyColor, r, g, b);
  end
end

function HeadIconController:ChangeColor(part, baseColor, r, g, b)
  local colorR, colorG, colorB;
  
  colorR = (r - 4) * 25 + baseColor.r * 255;
  colorR = math.min(colorR, 250);
  colorR = math.max(colorR, 5);
  
  colorG = (g - 4) * 25 + baseColor.g * 255;
  colorG = math.min(colorG, 250);
  colorG = math.max(colorG, 5);
  
  colorB = (b - 4) * 25 + baseColor.b * 255;
  colorB = math.min(colorB, 250);
  colorB = math.max(colorB, 5);
  
  if self.isGray then
    self.rawImage_Layers[part].color = LuaHelper.GetColor(colorR * 0.5, colorG * 0.5, colorB * 0.5, 255);
  else
    self.rawImage_Layers[part].color = LuaHelper.GetColor(colorR, colorG, colorB, 255);
  end
end


