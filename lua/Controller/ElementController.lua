ElementController = {}
ElementController.__index = ElementController;

ElementController.ElementMaterial = nil;

function ElementController.Initialize()
  CGResourceManager.Load(
    "ElementMaterial",
    "",
    function(assetName, asset)
      ElementController.ElementMaterial = asset;
    end
  );
end

function ElementController.New(elementInfo)
  local wemData = wemDatas[elementInfo.picId];
  if wemData == nil or wemData.alpha == 0 then --alpha���� 0�B�b�����W���X���i��
    return;
  end

  local self = {};
  setmetatable(self, ElementController);

  self.gameObject = poolMgr:Get("BaseElement");
  self.gameObject.name = string.format("Element_%d", elementInfo.picId);
  self.gameObject.transform:SetParent(MapManager.elementRoot.transform);
  
  self.rectTransform = self.gameObject:GetComponent("RectTransform");
  self.rectTransform.localScale = Vector3.one;
  self.rectTransform.anchoredPosition = Vector2.New(elementInfo.position.x + MapManager.GetFixCenterLeft(), -(elementInfo.position.y + MapManager.GetFixCenterTop()));

  self.rawImage = self.gameObject:GetComponent("UIRawImageEx");  
  self.rawImage:StopChangeTexture();
  self.rawImage.uvRect = UI.fullRect;

  self.canvas = self.gameObject:GetComponent("Canvas");

  self.rawImage.material = ElementController.ElementMaterial;
  self.alpha = 255;
  self.material = nil;

  if wemData ~= nil then
    if wemData.frame > 1 then
      self.rectTransform.sizeDelta = Vector2.New(wemData.width, wemData.height / wemData.frame);
      self.rawImage:ContinuedPlay(tostring(elementInfo.picId), 1, wemData.frame, 0.001 * wemData.gapTime, -1);
    else
      TextureManager.SetPng(ETextureUseType.Scene, tostring(elementInfo.picId), self.rawImage);
      self.rectTransform.sizeDelta = Vector2.New(wemData.width, wemData.height);
    end
    
    if wemData.level < 3 then
      self.canvas.sortingOrder = 1;
    elseif wemData.level > 3 then
      self.canvas.sortingOrder = MapManager.mapHeight;
    else
      self.canvas.sortingOrder = math.max(1, -self.rectTransform.anchoredPosition.y + (wemData.indexY * MapManager.BLOCK_UNIT));
    end

    if wemData.alpha ~= 255 then
      self.alpha  = wemData.alpha;
      self.material = poolMgr:Get("LightMaterial");
      self.material.shader = UnityEngine.Shader.Find("Custom/LightShader");
      self.rawImage.material = self.material;
      self.material:SetFloat("_InvisibleAlpha", wemData.alpha / EffectLight.MAX_ALPHA); 
    end
  end

  return self;
end

function ElementController.SetMaterial()
  ElementController.ElementMaterial:SetColor("_Color", MapManager.mapData.color);
end

function ElementController:Destroy()
  if self.alpha ~= 255 then
    poolMgr:Release("LightMaterial", self.material);
    self.rawImage.material = nil; 
  end
  poolMgr:Release("BaseElement", self.gameObject);
  
  self.rawImage.texture = nil;
end