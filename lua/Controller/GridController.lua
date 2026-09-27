GridController = {}
GridController.__index = GridController;

GridController.SceneMaterial = nil;

function GridController.Initialize()
  CGResourceManager.Load(
    "SceneMaterial",
    "",
    function(assetName, asset)
      GridController.SceneMaterial = asset;
    end
  );
end

function GridController.New(gridInfo, offset)
  local self = {};
  setmetatable(self, GridController);
  self.picName = tostring(gridInfo.picId);
  self.pos = {};
  self.pos.x = gridInfo.position.x;
  self.pos.y = gridInfo.position.y;
  if offset ~= nil then
    self.picName = string.Concat(self.picName, "_", tostring(offset.serial)); 
    self.pos.x = self.pos.x + offset.x;
    self.pos.y = self.pos.y + offset.y;
  end

  self.gameObject = poolMgr:Get("BaseScene");
  self.gameObject.name = string.format("Scene_%s", self.picName);
  self.gameObject.transform:SetParent(MapManager.gridRoot.transform);

  self.canvas = self.gameObject:GetComponent("Canvas");
  self.canvas.sortingOrder = 0;

  local texture = TextureManager.GetJpg(ETextureUseType.Scene, self.picName);

  local rectTransform = self.gameObject:GetComponent("RectTransform");      
  rectTransform.localScale = Vector3.one;
  rectTransform.anchoredPosition = Vector2.New(self.pos.x + MapManager.GetFixCenterLeft(), -(self.pos.y + MapManager.GetFixCenterTop()));
  if texture ~= nil then
    self.sizeData = textureSizeDatas[self.picName];
    if self.sizeData ~= nil then
      rectTransform.sizeDelta = Vector2.New(self.sizeData.width, self.sizeData.height);
    else
      rectTransform.sizeDelta = Vector2.New(texture.width, texture.height);
    end
  end
  
  self.rawImage = self.gameObject:GetComponent("RawImage");
  self.rawImage.texture = texture;

  self.rawImage.material = GridController.SceneMaterial;

  return self;
end

function GridController.SetMaterial()
  GridController.SceneMaterial:SetColor("_Color", MapManager.mapData.color);
end

function GridController:Destroy()
  poolMgr:Release("BaseScene", self.gameObject);
  
  self.rawImage.texture = nil;
end