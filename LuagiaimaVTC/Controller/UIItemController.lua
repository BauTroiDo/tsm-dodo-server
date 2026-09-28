
UIItemController = {}
UIItemController.__index = UIItemController;

function UIItemController.New(parent, eventHandler, eventParameter)
  local self = {};
  setmetatable(self, UIItemController);

  self.itemId = 0;
  self.itemQuant = 0;
  self.itemSave = nil;

  self.gameObject = poolMgr:Get("BaseUIItem");
  self.transform = self.gameObject.transform;
  
  self.rectTransform = self.gameObject:GetComponent("RectTransform");
  self.rectTransform:SetParent(parent);
  self.rectTransform:SetSiblingIndex(0);
  self.rectTransform.anchorMin = Vector2.zero;
  self.rectTransform.anchorMax = Vector2.one;
  self.rectTransform.anchoredPosition = Vector2.zero;
  self.rectTransform.sizeDelta = Vector2.zero;
  self.rectTransform.localScale = Vector2.one;

  self.image_BG = self.transform:Find("Image_BG"):GetComponent("Image");
  self.image_BGColor = self.image_BG.color;
  self.image_Select = self.transform:Find("Image_Select"):GetComponent("Image");
  self.image_Lock = self.transform:Find("Image_Lock"):GetComponent("Image");

  self.rawImage_Icon = self.transform:Find("RawImage_Icon"):GetComponent("RawImage");
  self.rawImage_Light = self.transform:Find("RawImage_Light"):GetComponent("UIRawImage");

  self.meridianStar = {};
  self.meridianStar.root = self.transform:Find("MeridianStar").gameObject;
  self.meridianStar.stars = {};

  for i = 1, 5 do
    self.meridianStar.stars[i] = self.meridianStar.root.transform:Find(string.Concat("Image_Star (", i, ")")):GetComponent("Image");
  end


  self.text_Quant = self.transform:Find("Text_Quant"):GetComponent("Text");
  self.text_EnhanceLv = self.transform:Find("Text_EnhanceLv"):GetComponent("Text");

  self.event_Icon = self.rawImage_Icon.transform:GetComponent("UIEvent");
  self.event_Lock = self.image_Lock.transform:GetComponent("UIEvent");

  self.image_RedDot = self.transform:Find("Image_Dot"):GetComponent("Image");
  self.image_CheckMark = self.transform:Find("Image_CheckMark"):GetComponent("Image");
  
  self:SetItem(0);
  self:SetEvent(eventHandler, eventParameter);

  self.visualIndex = 0;

  return self;
end

function UIItemController:Destroy()
  poolMgr:Release("BaseUIItem", self.gameObject);
end

function UIItemController:SetItem(itemId, quant, itemSave, alwaysShowNumber)
  self.itemId = itemId;
  self.itemQuant = quant;
  self.itemSave = itemSave;

  self.image_BG.gameObject:SetActive(false);
  self.image_Lock.gameObject:SetActive(false);
  self.image_Select.gameObject:SetActive(false);

  self.rawImage_Icon.gameObject:SetActive(false);
  self.rawImage_Light.gameObject:SetActive(false);
  self.image_RedDot.gameObject:SetActive(false);
  self.image_CheckMark.gameObject:SetActive(false);

  self.meridianStar.root:SetActive(false);

  self.text_Quant.text = "";
  self.text_EnhanceLv.text = "";

  if itemId == nil then return end

  local itemData = itemDatas[itemId];
  if itemData == nil then return end

  itemData:SetIcon(self.rawImage_Icon);
  
  if itemSave ~= nil and itemSave.damage ~= nil then
    if itemSave.damage >= Item.durable then
      if itemDatas[itemSave.Id].fitType == EItemFitType.Equip_Hand then
      itemDatas[10037]:SetIcon(self.rawImage_Icon);--損壞的鋤頭
      else
      itemDatas[23024]:SetIcon(self.rawImage_Icon);--損壞的
      end
    end
    if itemSave.damage >= EThingsDurability.Damaged then
      self.image_Lock.gameObject:SetActive(true);
      self.image_Lock.sprite = UI.GetSprite("icon040");
      LuaHelper.SetColor(self.image_Lock, Color.Red);
    elseif itemSave.damage >= EThingsDurability.Normal and itemSave.damage < EThingsDurability.Damaged then
      self.image_Lock.gameObject:SetActive(true);
      self.image_Lock.sprite = UI.GetSprite("icon040");
      LuaHelper.SetColor(self.image_Lock, Color.White);
    end
  end

  self.rawImage_Icon.gameObject:SetActive(true);

  self.image_BG.gameObject:SetActive(Item.IsStyle(itemData.fitType) or Item.IsUniqueBlood(itemData.kind));

  if Item.IsUniqueBlood(itemData.kind) then
    if itemSave ~= nil and itemSave.starLv ~= nil then
      local starLv = itemSave.starLv;
      self.meridianStar.root:SetActive(true);

      for i = 1, 5 do
        if i > starLv then
          self.meridianStar.stars[i].enabled = false;
        else
          self.meridianStar.stars[i].enabled = true;
          self.meridianStar.stars[i].sprite = UniqueBlood.GetStarSprite(itemSave.quality);
        end
      end

      if itemSave.isLock then
        self.text_Quant.text = "L";
      end

      if itemSave.level == 1 then
        self.text_EnhanceLv.text = "";
      else
        self.text_EnhanceLv.text = string.Concat("+", itemSave.level);
      end
    else
      self.meridianStar.root:SetActive(false);
    end

    self.image_BG.sprite = UI.GetSprite(UniqueBlood.GetIconId(itemId));
    self.image_BG.color = UniqueBlood.GetColor(itemId);
  else
    self.image_BG.sprite = nil;
    self.image_BG.color = self.image_BGColor;
    self.meridianStar.root:SetActive(false);
  end

  if itemSave ~= nil and itemSave.stoneLv ~= nil and itemSave.stoneLv >= 1 and itemSave.stoneLv <= Item.MaxStoneLv then
    local picName = "";
    if Between(itemSave.stoneLv, 1, 5) then
      picName = "Light_green01";
    elseif Between(itemSave.stoneLv, 6, 10) then
      picName = "Light_bule01";
    elseif Between(itemSave.stoneLv, 11, 15) then
      picName = "Light_purple01";
    end
  
    TextureManager.SetPng(ETextureUseType.UI, picName, self.rawImage_Light);
  
    self.rawImage_Light:ShiftUV(1, 3, 0.5, -1);

    self.rawImage_Light.gameObject:SetActive(true);
  else
    self.rawImage_Light.gameObject:SetActive(false);
  end

  if quant ~= nil and (alwaysShowNumber == true or quant > 1) then
    self.text_Quant.text = quant;
  end

  if itemSave ~= nil and itemSave.bagIndex ~= nil and Define.IsDebugMode() and UIBag.showIndex then
    self.text_Quant.text = string.Concat(self.text_Quant.text, string.GetColorText(string.Concat("(", itemSave.bagIndex, ")"), Color.Red));
  end

  if quant ~= nil and itemData:IsDirectUse() then
    if quant ~= 0 then
      self.text_Quant.text = itemData.attribute[1].value * quant;
    end
  end

  if quant ~= nil and Contains(itemId, 65391, 65392) then  --經驗值%
    self.text_Quant.text = string.Concat(itemData.attribute[1].value * quant, "%");
  end

  if itemData.specialAbility == EItemUseKind.ItemUse_222 then
    self.text_Quant.text = "";
  end

  --升階等級
  if itemSave ~= nil and itemSave.Reinforced ~= 0 and Item.IsEquip(itemData.fitType) then
    self.text_Quant.text = string.Concat(self.text_Quant.text, string.GetColorText(string.Concat("+", itemSave.Reinforced), Color.Yellow));
  end

  if itemSave ~= nil and itemSave.enhanceLv ~= 0 and not Item.IsStyle(itemData.fitType) and not Item.IsUniqueBlood(itemData.kind) then
    self.text_EnhanceLv.text = string.Concat("+", itemSave.enhanceLv);
  end

  if itemSave ~= nil and itemSave.styleLv ~=nil and itemSave.styleLv ~= 0 and Item.IsStyle(itemData.fitType) then
    self.text_EnhanceLv.text = string.Concat("+", itemSave.styleLv);
  end
end

function UIItemController:SetEvent(eventHandler, eventParameter, index)
  self.event_Icon:ClearListener();
  self.event_Lock:ClearListener();

  if eventHandler ~= nil then
    self.event_Icon:SetListener(EventTriggerType.PointerClick, eventHandler);
    self.event_Icon.parameter = eventParameter;
    self.visualIndex = index;

    self.event_Lock:SetListener(EventTriggerType.PointerClick, eventHandler);
    self.event_Lock.parameter = eventParameter;
    self.visualIndex = index;
  else
    self.event_Icon:SetListener(EventTriggerType.PointerClick, UIItemController.OnClick);
    self.event_Icon.parameter = self;

    self.event_Lock:SetListener(EventTriggerType.PointerClick, UIItemController.OnClick);
    self.event_Lock.parameter = self;
  end
end

function UIItemController:GetVisualIndex()
  return self.visualIndex;
end

function UIItemController:SetLock(value)
  self.image_Lock.gameObject:SetActive(value);

  if value then
    self.image_Lock.sprite = UI.GetSprite("bod008");
    LuaHelper.SetColor(self.image_Lock, Color.White);
  end
end

function UIItemController:SetSelect(value)
  self.image_Select.gameObject:SetActive(value);
  
  if not value then return end

  self.image_Select.raycastTarget = false;
  self.image_Select.color = LuaHelper.GetColor(0, 255, 0, 120);
end

function UIItemController:SetInUse(value)
  self.image_Select.gameObject:SetActive(value);

  if not value then return end

  self.image_Select.raycastTarget = value;
  self.image_Select.color = LuaHelper.GetColor(128, 128, 128, 200);
end

function UIItemController:SetRedDot(value)
  self.image_RedDot.gameObject:SetActive(value);
end

function UIItemController:SetCheckMark(value)
  self.image_CheckMark.gameObject:SetActive(value);
end

function UIItemController:SetLight(value)
  self.rawImage_Light.gameObject:SetActive(value);
end

function UIItemController:SetIcon(value)
  self.rawImage_Icon.gameObject:SetActive(value);
end

function UIItemController:SetIconTexture(iconId)
  UI.SetIcon(iconId, self.rawImage_Icon);
end

function UIItemController:SetImageBG(value)
  self.image_BG.gameObject:SetActive(value);
end

function UIItemController:SetBaseUIItemSizeDelta(hight, width)
  self.transform.sizeDelta = Vector2.New(hight,width);
end

function UIItemController.OnClick(uiEvent)
  local self = uiEvent.parameter;

  if self.itemId == nil then return end

  UIItemInfo.Show(self.itemId, self.itemSave);
end
