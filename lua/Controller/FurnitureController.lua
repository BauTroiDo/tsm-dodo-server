
EFurnitureDirection = {
  LeftDown = 0,   --面左下
  RightDown = 1,  --面右下
  RightUp = 2,    --面右上
  LeftUp = 3,     --面左上
}

EFurnitureType = {
  Ground = 1, --放地上
  Wall = 2,   --放牆上
}

EFurnitureKind = {
  All = 0,
  Wall = 1,           --牆面
  Floor = 2,          --地板
  Table = 3,          --桌子
  Chair = 4,          --椅子
  Window = 5,         --窗戶
  Cupboard = 6,       --櫥櫃
  Bathroom = 7,       --浴室
  Bed = 8,            --床
  WallDecoration = 9, --牆飾
  Plant = 10,         --綠植
  Obstacle = 11,      --隔斷
  Decoration = 12,    --裝飾品
  Sundry = 13,        --雜物
  Carpet = 14,        --地毯
}

EFurnitureInteractiveSetting = {
  NotInteractive = 0, --不可互動
  PlayGIF = 1,        --播動圖
  PlayMusic = 2,      --播音樂
  Active = 3,         --動作
  UnActive = 4,       --與家具無接觸的動作
  Emoji = 5,          --表情
  MessageBoard = 6,   --留言板
  ShowMessage = 7,    --顯示訊息
  ShowEffect = 8,     --顯示效果
}

EFurnitureDynamic = {
  NotDynamic = 0, --非動態家具
  Dynamic = 1,    --動態家具
}

EFurnitureEffect = {
  NoEffect = 0,       --無特效
  PlayOnStart = 1,    --循環特效
  PlayWhenClick = 2,  --點擊特效
}

EFurnitureTexture = {
  LeftDownTexture = 0,            --面左下圖(包含Dynamic圖)
  RightUpTexture = 1,             --面右上圖(包含Dynamic圖)
  LeftDownInteractiveTexture = 2, --面左下互動圖
  RightUpInteractiveTexture = 3,  --面右上互動圖
}

EFurnitureStatus = {
  Original = 0,     --原始狀態
  CanPlace = 1,     --可放置
  CanChange = 2,    --可跟原本的家具交換
  CanNotPlace = 3,  --不能放置
}

FurnitureController = {}
FurnitureController.__index = FurnitureController;

FurnitureController.InteractiveFunctions = {}
FurnitureController.CheckCancelInteractiveFunctions = {}

function FurnitureController.New(index, Id)
  local self = {};
  setmetatable(self, FurnitureController);

  if furnitureDatas[Id] == nil then return end
  self.index = index;
  self.Id = Id;
  self.data = furnitureDatas[Id];
  self.direction = EFurnitureDirection.LeftDown;
  self.oldDirection = EFurnitureDirection.LeftDown;
  self.occupyBlocks = {};
  self.onSetting = false;
  self.oldFurniture = nil;
  self.position = Vector2.one;

  --設定起始位置
  if self.data.type == EFurnitureType.Ground then
    self.blockX = 1;
    self.blockY = 1;
    self.blockZ = 0;
  elseif self.data.type == EFurnitureType.Wall then
    self.blockX = 0;
    self.blockY = 1;
    self.blockZ = 1;
  end

  self.occupyX, self.occupyY, self.occupyZ = self:GetOccupyBlockDirection(self.direction);
  self.interactiveKind = self:GetInteractiveKind();
  self.interactiveWithRoleKind = self:GetInteractiveWithRoleKind();
  self.effectType = self:GetEffectKind();
  self.interactivePoint = self:GetInteractivePoint();

  self.oldOccupyBlocks = {};
  self.frameNumber = 0;
  self.frameCount = 1;
  self.interactiveFrameCount = 1;
  self.currentPlayRound_GIF = 0;
  self.isInteracting = false;
  self.audioChannel = nil;
  self.isSelected = false;
  self.effect = {};
  self:Initialize();

  --儲存匿名函式
  self.PlayInteractiveAnimationFunction = function() self:PlayInteractiveAnimation() end;
  self.PlayDynamicAnimationFunction = function() self:PlayDynamicAnimation() end;
  self.CheckCancelInteractiveFunction = function() self:CheckCancelInteractive() end;
  self.PlayEffectFunction = function() self:PlayAutoEffect() end
  self.PlayInteractiveEffectFunction = function() self:PlayInteractiveEffect() end

  self.jmpDrawer_Body:GenerateAndDraw(npcDatas[self.data.Id]:GetAtlasParams());

  if npcDatas[self.data.Id].maskId ~= 0 then
    self.jmpDrawer_BodyMask:GenerateAndDraw(npcDatas[self.data.Id]:GetMaskAtlasParams());
  end

  self:ChangePosition(self.blockX, self.blockY, self.blockZ); --中心點位置
  return self;
end

function FurnitureController:Initialize()
  self.gameObject = poolMgr:Get("BaseFurniture");
  self.gameObject.name = string.Concat("Furniture_", self.index);
  self.gameObjectTransform = self.gameObject.transform;
  self.gameObjectTransform:SetParent(Furniture.furnitureRoot.transform);
  self.gameObjectTransform.localScale = Vector3.one;


  self.material = poolMgr:Get("FurnitureMaterial");
  self.material:SetColor("_Color", Color.White);
  self.material:SetColor("_TexColor", Color.White);
  self.material:SetFloat("_Outline", 0);
  self.material:SetFloat("_Invisible", 0);
  self.material:SetFloat("_InvisibleAlpha", 1);

  --region Body
  self.canvas = self.gameObject:GetComponent(typeof(Canvas));
  self.bodyObjectTransform = self.gameObjectTransform:Find("Body");

  self.rawImageBodyObject = self.bodyObjectTransform:Find("RawImage_Body").gameObject;
  self.rawImageBodyTransform = self.rawImageBodyObject.transform;
  self.rawImage_Body = self.rawImageBodyObject:GetComponent(typeof(RawImage));
  self.rawImage_Body.enabled = false;
  self.rawImage_Body.material = self.material;
  self.rawImage_Body.raycastTarget = true;
  --self.raycastMask_Body = self.rawImage_Body.transform:GetComponent("UIRawImageRaycastMask");

  self.jmpDrawer_Body = self.rawImageBodyObject:GetComponent(typeof(JmpDrawer));
  self.jmpDrawer_Body.enabled = false;
  self.jmpDrawer_Body.luaListener = function()
    if self.jmpDrawer_Body == nil then return end
    self:SetTexture(self:GetTextureNumber(false), 0);
    self.frameCount = self.jmpDrawer_Body:GetFrameCount(0);
    self.rawImage_Body.enabled = true;

    if self.effectType ~= EFurnitureEffect.NoEffect then
      self:SetEffectTexture();
    end

  end

  self.rawImageBodyMaskObject = self.bodyObjectTransform:Find("RawImage_BodyMask").gameObject;
  self.canvas_BodyMask = self.rawImageBodyMaskObject:GetComponent(typeof(Canvas));
  self.rawImageBodyMaskTransform = self.rawImageBodyMaskObject.transform;
  self.rawImage_BodyMask = self.rawImageBodyMaskObject:GetComponent(typeof(RawImage));
  self.rawImage_BodyMask.enabled = true;
  self.rawImage_BodyMask.material = self.material;

  self.jmpDrawer_BodyMask = self.rawImageBodyMaskObject:GetComponent(typeof(JmpDrawer));
  self.jmpDrawer_BodyMask.enabled = false;
  self.jmpDrawer_BodyMask.luaListener = function()
    self:SetTexture(self:GetTextureNumber(false), 0);
  end

  self.event = self.bodyObjectTransform:Find("RawImage_Body"):GetComponent(typeof(UIEvent));
  self.event:SetListener(EventTriggerType.PointerClick, self.OnClick);
  self.event.parameter = self;
  --endregion

  --region HUD
  local hudObject = self.gameObjectTransform:Find("HUD").gameObject;
  local layout = hudObject.transform:Find("Layout");

  local image_Select = layout:Find("Image_Select"):GetComponent(typeof(Image));
  image_Select.gameObject:SetActive(false);

  layout = layout.transform:Find("Layout");

  --self.hudVisibleObject = layout.gameObject;

  local text_Name = layout:Find("Text_Name"):GetComponent(typeof(Text));
  text_Name.text = npcDatas[self.data.Id].name;
  text_Name.enabled = false;
  --endregion

  --region Effect
  self.effect.gameObject = self.rawImageBodyTransform:Find("RawImage_Effect").gameObject;
  self.effect.transform = self.effect.gameObject.transform;
  self.effect.rectTransform = self.effect.gameObject:GetComponent("RectTransform");
  self.effect.canvas = self.effect.gameObject:GetComponent(typeof(Canvas));
  self.effect.rawImage = self.effect.gameObject:GetComponent(typeof(RawImage));

  if self.effectType ~= EFurnitureEffect.NoEffect then
    local data = furnitureEffectDatas[self.data.effectId];

    self.effect.alpha = 32;
    self.effect.effectScale = data.effectScale;
    self.effect.effectOffset = data.effectOffset;

    self.effect.nowPage = 0;
    self.effect.startPage = data.effectStartFrame;
    self.effect.endPage = data.effectEndFrame;
    self.effect.allPage = data.effectTotalFrame;
    self.effect.interval = data.effectInterval;
    self.effect.totalRound = data.playRound;
    self.effect.playRound = 0;
    self.effect.frameCount = 0;

    self.effect.material = poolMgr:Get("LightMaterial");
    self.effect.material.shader = UnityEngine.Shader.Find("Custom/LightShader");
    self.effect.material:SetFloat("_InvisibleAlpha", self.effect.alpha / EffectLight.MAX_ALPHA);
    self.effect.material:SetColor("_Color", Color.White);
    self.effect.material:SetFloat("_AddColor", 1);
    self.effect.rawImage.material = self.effect.material;

    self.effect.fileName = nil;
    self.effect.fileName = string.format("L%5d", self.data.effectId)
    local file = string.Concat(self.effect.fileName, "_", tostring(self.effect.startPage));
    TextureManager.SetPng(ETextureUseType.EffectLight, file, self.effect.rawImage);
    self.effect.rawImage.uvRect = Rect.New(0,0,1,1);

    self.effect.width = 0;
    self.effect.height= 0;

    if self.effect.rawImage.texture ~= nil then
      if textureSizeDatas[file] ~= nil then
        self.effect.width = textureSizeDatas[file].width;
        self.effect.height = textureSizeDatas[file].height;
      else
        self.effect.width = self.effect.rawImage.texture.width;
        self.effect.height = self.effect.rawImage.texture.height;
      end

      self.effect.rectTransform.sizeDelta = Vector2.New(self.effect.width, self.effect.height);
    end
  end

  self.effect.rawImage.enabled = false;
--endregion

end

function FurnitureController:Destroy()
  CGTimer.RemoveListener(self.CheckCancelInteractiveFunction);
  self:CancelInteractiveAnimation();
  self:CancelDynamicAnimation();
  self:CancelAutoEffect();
  self:CancelInteractiveEffect();

  if self.gameObject ~= nil then
    poolMgr:Release("BaseFurniture", self.gameObject);
    self.gameObject = nil;
    self.gameObjectTransform = nil;
  end

  if self.material ~= nil then
    poolMgr:Release("FurnitureMaterial", self.material);
    self.material = nil;
  end

  if self.jmpDrawer_Body ~= nil then
    self.jmpDrawer_Body:Disable();
    self.jmpDrawer_Body = nil;
  end

  if self.jmpDrawer_BodyMask ~= nil then
    self.jmpDrawer_BodyMask:Disable();
    self.jmpDrawer_BodyMask = nil;
  end

  if self.effect.material ~= nil then
    poolMgr:Release("LightMaterial", self.effect.material);
    self.effect.material = nil;
  end

  self.isInteracting = false;
  self.isSelected = false;
  self.interactivePoint = {};
  self.effect = {};
  self = nil;
end

function FurnitureController.OnClick(uiEvent)
  local self = uiEvent.parameter;

  if HouseEditorManager.IsOpen() then
    --編輯家具
    UI.Open(UIFurnitureInteractive, self);
    UIHouseEditor.UpdateFurnitureInfo(self.Id);
    self.onSetting = true;
  else
    --家具互動
    if UIJoystick.IsDrag() or UIJoystick.IsClick() then return end
    FurnitureManager.OnClickFurniture(self);
  end
end

function FurnitureController:Selected()
  self.material:SetColor("_Color", Color.Yellow);
  self.material:SetFloat("_Outline", 1);
  self.isSelected = true;
end

function FurnitureController:IsSelected()
  return self.isSelected;
end

function FurnitureController:Deselect()
  self:SetOutline(EFurnitureStatus.Original);
  self.isSelected = false;
end

function FurnitureController:GetInteractiveKind()
  local interactiveKind = {};
  local kind = self.data.interactive;
  local count = 1;

  while kind ~= 0 do
    if kind % 2 ~= 0 then
      kind = kind - 1;
      interactiveKind[count] = count;
    end

    kind = kind / 2;
    count = count + 1;

    if count > 50 then
      break;
    end
  end

  return interactiveKind;
end

function FurnitureController:GetInteractiveWithRoleKind()
  local kind = {};

  for k, v in pairs(self.interactiveKind) do
    if v == EFurnitureInteractiveSetting.Active then
      kind[k] = v;
    end
  end

  return kind;
end

function FurnitureController:GetEffectKind()
  if not table.Contains(self.interactiveKind, EFurnitureInteractiveSetting.ShowEffect) then
    return EFurnitureEffect.NoEffect;
  end

  local data = furnitureEffectDatas[self.data.effectId];
  for k, v in pairs(self.interactiveKind) do
    if v == EFurnitureInteractiveSetting.ShowEffect then
      if data == nil then
        logError("家具"..self.Id.."效果表內無此id: "..self.data.effectId);
        return EFurnitureEffect.NoEffect;
      end

      if data.dynamic == 1 then
        return EFurnitureEffect.PlayOnStart;
      else
        return EFurnitureEffect.PlayWhenClick;
      end
    end
  end
end

function FurnitureController:GetInteractivePoint()
  local interactivePoint = {};

  for i = 1, self.data.interactivePointCount do
    interactivePoint[i] = {};
    interactivePoint[i].occupy = false;
    interactivePoint[i].playerIndex = nil;

    if i == 1 then
      interactivePoint[i].x = self.data.firstInteractivePoint_X;
      interactivePoint[i].y = self.data.firstInteractivePoint_Y;
    elseif i == 2 then
      interactivePoint[i].x = self.data.secondInteractivePoint_X;
      interactivePoint[i].y = self.data.secondInteractivePoint_Y;
    end
  end

  return interactivePoint;
end

--region furniture edit
function FurnitureController:ConfirmPosition()
  self:SetOutline(EFurnitureStatus.Original);
  self:ClearOldBlockProperty();
  self:SetOccupyBlockProperty();
  self.oldDirection = self.direction;
  self.onSetting = false;
end

function FurnitureController:SetOutline(status)
  if self.gameObject == nil then return end
  local color;
  local outlineValue = 1;

  if status == EFurnitureStatus.Original then
    color = Color.Alpha;
    outlineValue = 0;
  elseif status == EFurnitureStatus.CanPlace then
    color = Color.Green;
  elseif status == EFurnitureStatus.CanChange then
    color = Color.Orange;
  elseif status == EFurnitureStatus.CanNotPlace then
    color = Color.Red;
  else
    color = status;
  end

  local blockX, blockY, blockZ;

  --改格子的顏色
  if HouseEditorManager.IsOpen() then
    for i = 0, self:NumberOfOccupyBlocks() - 1 do
      blockX = self.occupyBlocks[i].x;
      blockY = self.occupyBlocks[i].y;
      blockZ = self.occupyBlocks[i].z;

      HouseEditorManager.ChangeBlockColor(blockX, blockY, blockZ, color);
    end
  end

  self.material:SetColor("_Color", color);
  self.material:SetFloat("_Outline", outlineValue);
end

function FurnitureController:SetTexture(textureNumber, textureFrame)
  if self.jmpDrawer_Body == nil then return end
  if self.jmpDrawer_BodyMask == nil then return end
  local scaleX = 1;

  if self.direction == EFurnitureDirection.RightDown or self.direction == EFurnitureDirection.LeftUp then
    scaleX = - 1;
  end

  self.jmpDrawer_Body:Draw(textureNumber, textureFrame);
  self.jmpDrawer_BodyMask:Draw(textureNumber, textureFrame);
  self:SetTexturePosition();

  --轉向
  if self.rawImageBodyObject ~= nil and self.rawImageBodyMaskObject ~= nil then
    self.rawImageBodyTransform.localScale = Vector3.New(scaleX, 1, 1);
    self.rawImageBodyMaskTransform.localScale = Vector3.New(scaleX, 1, 1);
  end
end

function FurnitureController:SetTexturePosition()
  --家具圖片位置調正
  local midPosition = self:GetMiddlePosition();
  local botPosition = self:GetBottomPosition();
  local offsetX = npcDatas[self.data.Id].picOffsetX;
  local offsetY = npcDatas[self.data.Id].picOffsetY;

  if self.direction == EFurnitureDirection.RightDown or self.direction == EFurnitureDirection.RightUp then
    offsetX = - offsetX;
  end

  local newPosition = Vector2.New(midPosition.x + offsetX, botPosition.y + offsetY);

  self.rawImageBodyTransform.localPosition = self.gameObjectTransform:InverseTransformPoint(newPosition);
  self.rawImageBodyMaskTransform.localPosition = self.gameObjectTransform:InverseTransformPoint(newPosition);
  self:ChangeOrderInLayer(-midPosition.y);
end

function FurnitureController:SetEffectTexture()
  local scale = self.effect.effectScale * math.abs(self.jmpDrawer_Body.transform.localScale.x);
  self.effect.rectTransform.localScale = Vector3.one * scale;
  self.effect.transform.localPosition = Vector3.New(0,self.effect.effectOffset);
end

function FurnitureController:SetAlpha(alpha)
  if alpha == 1 then
    self.material:SetInt("_Invisible", 0);
  else
    self.material:SetInt("_Invisible", 1);
  end

  self.material:SetFloat("_InvisibleAlpha", alpha);
end

function FurnitureController:SetRaycastTarget(enable)
  self.rawImage_Body.raycastTarget = enable;
end

function FurnitureController:SetOccupyBlock()
  if self.gameObject == nil then return end
  self:ClearOldBlock();

  local x, y, z;
  for occupyX = 0, math.clamp(self.occupyX - 1, 0, self.occupyX)  do
    for occupyY = 0, math.clamp(self.occupyY - 1, 0, self.occupyY) do
      for occupyZ = 0, math.clamp(self.occupyZ - 1, 0, self.occupyZ) do
        x = self.blockX + occupyX;
        y = self.blockY + occupyY;
        z = self.blockZ + occupyZ;
        self.occupyBlocks[self:NumberOfOccupyBlocks()] = {x = x, y = y, z = z};
      end
    end
  end
end

function FurnitureController:ClearOldBlock()
  if self.gameObject == nil then return end
  if self:NumberOfOccupyBlocks() == 0 then return end
  local blockX, blockY, blockZ;

  if HouseEditorManager.IsOpen() then
    for i = 0, self:NumberOfOccupyBlocks() - 1 do
      blockX = self.occupyBlocks[i].x;
      blockY = self.occupyBlocks[i].y;
      blockZ = self.occupyBlocks[i].z;
      HouseEditorManager.ChangeBlockColor(blockX, blockY, blockZ, Color.White);
    end
  end

  self.occupyBlocks = {};
end

function FurnitureController:HasOldBlock()
  return table.Count(self.oldOccupyBlocks) > 0;
end

function FurnitureController:RecoverToOldPosition()
  if not self:HasOldBlock() then return end
  self:SetPositionAndDirection(self.oldOccupyBlocks[0].x, self.oldOccupyBlocks[0].y, self.oldOccupyBlocks[0].z, self.oldDirection);
end

function FurnitureController:ClearOldBlockProperty()
  if self.gameObject == nil then return end
  local furnitureKindType = HouseEditorManager.GetFurnitureKindType(self.data.kind);

  for k, v in pairs(self.oldOccupyBlocks) do
    HouseEditorManager.ChangeBlockFurniture(v.x, v.y, v.z, nil, furnitureKindType);
    HouseEditorManager.ChangeBlockProperty(v.x, v.y, v.z, EBlockProperty.Empty, furnitureKindType);

    if furnitureKindType == EFurnitureKindType.Furniture then
      HouseEditorManager.ChangeBlockObstacle(v.x, v.y, v.z, 0);
    end
  end

  self:SetOldOccupyBlock();
end

function FurnitureController:SetOldOccupyBlock()
  for k, v in pairs(self.occupyBlocks) do
    self.oldOccupyBlocks[k] = v;
  end
  self.oldDirection = self.direction;
end

--確定改變位置完後，才改property
function FurnitureController:SetOccupyBlockProperty()
  if self.gameObject == nil then return end
  local blockX, blockY, blockZ;
  local furnitureKindType = HouseEditorManager.GetFurnitureKindType(self.data.kind);

  for i = 0, self:NumberOfOccupyBlocks() - 1 do
    blockX = self.occupyBlocks[i].x;
    blockY = self.occupyBlocks[i].y;
    blockZ = self.occupyBlocks[i].z;
    HouseEditorManager.ChangeBlockFurniture(blockX, blockY, blockZ, self, furnitureKindType);
    HouseEditorManager.ChangeBlockProperty(blockX, blockY, blockZ, EBlockProperty.Furniture, furnitureKindType);

    if furnitureKindType == EFurnitureKindType.Furniture and self.data.type == EFurnitureType.Ground then
      HouseEditorManager.ChangeBlockObstacle(blockX, blockY, blockZ, 4);
    end
  end
end

function FurnitureController:GetMiddlePosition()
  local midPosition;

  if self.rawImageBodyObject ~= nil then
    if self.occupyBlocks ~= nil then
      local vectorOriginal = HouseEditorManager.GetBlockPosition(self.occupyBlocks[0].x, self.occupyBlocks[0].y, self.occupyBlocks[0].z);
      local occupyNumber = self:NumberOfOccupyBlocks() - 1;
      local vectorVertex = HouseEditorManager.GetBlockPosition(self.occupyBlocks[occupyNumber].x, self.occupyBlocks[occupyNumber].y, self.occupyBlocks[occupyNumber].z);
      midPosition = (vectorOriginal + vectorVertex) / 2;
    end
  end

  return midPosition;
end

function FurnitureController:GetBottomPosition()
  local bottomPosition;
  local occupyNumber;
  local vectorVertex;
  local distanceY = 1;

  if self.rawImageBodyObject ~= nil then
    if self.occupyBlocks ~= nil then
      if self.data.type == EFurnitureType.Wall then
        occupyNumber = (self.occupyX + self.occupyY - 1) * self.occupyZ;
        distanceY = 1.5;
      else
        occupyNumber = self:NumberOfOccupyBlocks() - 1;
      end

      vectorVertex = HouseEditorManager.GetBlockPosition(self.occupyBlocks[occupyNumber].x, self.occupyBlocks[occupyNumber].y, self.occupyBlocks[occupyNumber].z);
      bottomPosition = Vector2.New(vectorVertex.x, vectorVertex.y - HouseEditorManager.blockSize * Mathf.Sin(HouseEditorManager.blockAngle * Mathf.PI / 180) * distanceY);
    end
  end

  return bottomPosition;
end

function FurnitureController:GetOccupyBlockDirection(direction)
  local x = 0;
  local y = 0;
  local z = 0;

  if direction == EFurnitureDirection.LeftDown or direction == EFurnitureDirection.RightUp then
    if self.data.type == EFurnitureType.Ground then
      x = self.data.occupyBlockX;
      y = self.data.occupyBlockY;
      z = 0;
    elseif self.data.type == EFurnitureType.Wall then
      x = 0;
      y = self.data.occupyBlockY;
      z = self.data.occupyBlockX;
    end
  elseif direction == EFurnitureDirection.RightDown or direction == EFurnitureDirection.LeftUp then
    if self.data.type == EFurnitureType.Ground then
      x = self.data.occupyBlockY;
      y = self.data.occupyBlockX;
      z = 0;
    elseif self.data.type == EFurnitureType.Wall then
      x = self.data.occupyBlockY;
      y = 0;
      z = self.data.occupyBlockX;
    end
  end

  return x, y, z
end

--獲取圖片編號
function FurnitureController:GetTextureNumber(interactive)
  local textureNumber = EFurnitureTexture.LeftDownTexture;

  if self.direction == EFurnitureDirection.LeftDown then
    textureNumber = EFurnitureTexture.LeftDownTexture;

    if interactive then
      textureNumber = EFurnitureTexture.LeftDownInteractiveTexture;
    end
  elseif self.direction == EFurnitureDirection.LeftUp then
    textureNumber = EFurnitureTexture.RightUpTexture;

    if interactive then
      textureNumber = EFurnitureTexture.RightUpInteractiveTexture;
    end
  elseif self.direction == EFurnitureDirection.RightDown then
    textureNumber = EFurnitureTexture.LeftDownTexture;

    if interactive then
      textureNumber = EFurnitureTexture.LeftDownInteractiveTexture;
    end
  elseif self.direction == EFurnitureDirection.RightUp then
    textureNumber = EFurnitureTexture.RightUpTexture;

    if interactive then
      textureNumber = EFurnitureTexture.RightUpInteractiveTexture;
    end
  end

  --沒抓到圖
  local frameCount = self.jmpDrawer_Body:GetFrameCount(textureNumber);
  if frameCount == 0 then
    if self.direction == EFurnitureDirection.LeftUp or self.direction == EFurnitureDirection.RightUp then
      textureNumber = EFurnitureTexture.LeftDownTexture;

      if interactive then
        textureNumber = EFurnitureTexture.LeftDownInteractiveTexture;
      end
    end
  end

  return textureNumber
end

function FurnitureController:SetPositionAndDirection(blockX, blockY, blockZ, direction)
  if self.gameObject == nil then return end
  if blockX == nil or blockY == nil or blockZ == nil or direction == nil then return end

  local newPosition = HouseEditorManager.GetBlockPosition(blockX, blockY, blockZ);
  if newPosition == nil then return end
  self.blockX = blockX;
  self.blockY = blockY;
  self.blockZ = blockZ;
  self.direction = direction;
  self.gameObjectTransform.localPosition = newPosition;
  self.occupyX, self.occupyY, self.occupyZ = self:GetOccupyBlockDirection(direction);
  self:SetOccupyBlock();
  self.position = Vector2.New(self:GetMiddlePosition().x , -self:GetMiddlePosition().y);
  self:SetTexture(self:GetTextureNumber(false), 0);
  self:ChangeOrderInLayer(-self:GetMiddlePosition().y);
end

function FurnitureController:ChangePosition(blockX, blockY, blockZ)
  if self.gameObject == nil then return end
  if blockX == nil or blockY == nil or blockZ == nil then return end

  local newPosition = HouseEditorManager.GetBlockPosition(blockX, blockY, blockZ);
  if newPosition == nil then return end
  self.blockX = blockX;
  self.blockY = blockY;
  self.blockZ = blockZ;
  self.gameObjectTransform.localPosition = newPosition;

  --裝潢移動到另一邊的牆自動換方向
  if self.blockZ ~= 0 then
    if self.blockX == 0 then
      self:ChangeDirection(EFurnitureDirection.LeftDown);
    elseif self.blockY == 0 then
      self:ChangeDirection(EFurnitureDirection.RightDown);
    end
  end

  self:SetOccupyBlock();
  self.position = Vector2.New(self:GetMiddlePosition().x , -self:GetMiddlePosition().y);
  self:ChangeOrderInLayer(-self:GetMiddlePosition().y);
end

function FurnitureController:ChangeDirection(direction)
  if self.gameObject == nil then return end
  if self.direction == direction then return end
  if direction == nil then return end
  if not self:CheckDirectionInBlock(direction) then return end

  self.direction = direction;
  self.occupyX, self.occupyY, self.occupyZ = self:GetOccupyBlockDirection(direction);
  self:SetOccupyBlock();
  self:SetTexture(self:GetTextureNumber(false), 0);
end

function FurnitureController:ChangeOrderInLayer(order)
  if order == nil then return end

  local furnitureKindType = HouseEditorManager.GetFurnitureKindType(self.data.kind);

  if self.direction == EFurnitureDirection.LeftDown or self.direction == EFurnitureDirection.RightDown then
    order = order + self.blockX;
  elseif self.direction == EFurnitureDirection.LeftUp or self.direction == EFurnitureDirection.RightUp then
    order = order + self.blockY;
  end

  if furnitureKindType == EFurnitureKindType.Decorate then
    order = 1;
  elseif furnitureKindType == EFurnitureKindType.Carpet then
    order = 100;
  end

  order = order + self.data.addedOrder;
  self.canvas.sortingOrder = order;
  self.canvas_BodyMask.sortingOrder = order + 2;

  if self.effectType ~= EFurnitureEffect.NoEffect then
    self.effect.canvas.sortingOrder = order + 3;
  end

end

--可不可以放置的狀態檢查
function FurnitureController:CheckStatus()
  local status = EFurnitureStatus.CanPlace;
  local occupyFurniture = {};
  local furnitureKindType = HouseEditorManager.GetFurnitureKindType(self.data.kind);

  for i = 0, self:NumberOfOccupyBlocks() - 1 do
    local property;
    local furniture;

    property = HouseEditorManager.GetBlockProperty(self.occupyBlocks[i].x, self.occupyBlocks[i].y, self.occupyBlocks[i].z, furnitureKindType);
    furniture =  HouseEditorManager.GetBlockFurniture(self.occupyBlocks[i].x, self.occupyBlocks[i].y, self.occupyBlocks[i].z, furnitureKindType);

    if property ~= EBlockProperty.Empty and furniture ~= self then
      status = EFurnitureStatus.CanNotPlace;
    end

    if property == EBlockProperty.Furniture and furniture ~= self then
      occupyFurniture[furniture.index] = furniture;
      self.oldFurniture = HouseEditorManager.GetBlockFurniture(self.occupyBlocks[i].x, self.occupyBlocks[i].y, self.occupyBlocks[i].z, furnitureKindType);
    end
  end

  if table.Count(occupyFurniture) == 1 and self.oldFurniture ~= nil then
    if self.blockX + self.occupyX >= self.oldFurniture.blockX + self.oldFurniture.occupyX and
      self.blockY + self.occupyY >= self.oldFurniture.blockY + self.oldFurniture.occupyY and
      self.blockZ + self.occupyZ >= self.oldFurniture.blockZ + self.oldFurniture.occupyZ and
      self.blockX <= self.oldFurniture.blockX and
      self.blockY <= self.oldFurniture.blockY and
      self.blockZ <= self.oldFurniture.blockZ then
      status = EFurnitureStatus.CanChange;
    end
  end

  if status == EFurnitureStatus.CanPlace or status == EFurnitureStatus.CanChange then
    if furnitureKindType == EFurnitureKindType.Furniture and self.data.type == EFurnitureType.Ground and FurnitureManager.CanPlaceOnGround() then
      status = EFurnitureStatus.CanNotPlace;
    end
  end

  return status
end

--確保在牆邊轉向不會超出格子
function FurnitureController:CheckDirectionInBlock(direction)
  if self.direction == direction then return end
  local occupyX, occupyY, occupyZ = self:GetOccupyBlockDirection(direction);

  if self.blockX + occupyX - 1 > HouseEditorManager.sizeX or
    self.blockY + occupyY - 1 > HouseEditorManager.sizeY or
    self.blockZ + occupyZ - 1 > HouseEditorManager.sizeZ then
    ShowCenterMessage(string.Get(22595));
    return false
  end

  return true
end

function FurnitureController:ClearOldFurniture()
  if self.oldFurniture == nil then return end
  FurnitureManager.RemoveFurnitureOnScene(self.oldFurniture);
  self.oldFurniture:Destroy();
end

function FurnitureController:NumberOfOccupyBlocks()
  if self.occupyBlocks == nil then return end
  local count = 0;
  for _ in pairs(self.occupyBlocks) do count = count + 1 end
  return count
end

function FurnitureController:ResetName()
  self.gameObject.name = string.Concat("Furniture_", self.index);
end
--endregion

--region Interactive
function FurnitureController:Interactive()
  MarkManager.StopNavigation();
  Role.SetMoveTarget(nil);
  Role.player:StopMove();

  if table.Count(self.interactiveKind) > 0 then
    if table.Count(self.interactiveWithRoleKind) > 0 then
      self:TeleportToInteractivePoint();
    else
      self:SendFurnitureInteractive(true);
    end
  end
end

function FurnitureController:InteractiveWithCheckOccupyPoint(interactive, player)
  if player == nil then return end
  if table.Count(self.interactiveKind) == 0 then return end

  if interactive == true then
    if player == Role.player then
      self:AddCheckCancelInteractiveListener();
    end
  else
    if player == Role.player then
      CGTimer.RemoveListener(self.CheckCancelInteractiveFunction);
    end
    self:Deselect();
  end

  if self:CheckInteractivePoint(interactive) then
    for kind in pairs(self.interactiveKind) do
      self.InteractiveFunctions[kind](self, interactive, player);
    end

    self.isInteracting = interactive;

    if table.Count(self.interactiveWithRoleKind) == 0 then
      self:SendFurnitureInteractive(interactive);
    end
  elseif interactive == false then
    --如果位置上還有人，只取消玩家玩家動作
    for kind in pairs(self.interactiveWithRoleKind) do
      self.InteractiveFunctions[kind](self, interactive, player);
    end
  end

  self:PlayerIsInteracting(player, interactive);
end

function FurnitureController:CancelInteractive()
  if table.Count(self.interactivePoint) > 0 then
    self:RemoveOccupyPoint(Role.playerId);
    self:InteractiveWithCheckOccupyPoint(false, Role.player);
  else
    for kind in pairs(self.interactiveKind) do
      self.InteractiveFunctions[kind](self, false, Role.player);
    end
    self:Deselect();
    self.isInteracting = false;
    CGTimer.RemoveListener(self.CheckCancelInteractiveFunction);
    self:SendFurnitureInteractive(false);
  end
end

function FurnitureController:CancelAllPlayerInteractive()
  if table.Count(self.interactivePoint) > 0 then
    for k, v in pairs(self.interactivePoint) do
      local index = v.playerIndex;
      self:RemoveOccupyPoint(index);
      self:InteractiveWithCheckOccupyPoint(false, Role.players[index]);
    end
  else
    for kind in pairs(self.interactiveKind) do
      for k, v in pairs(Role.players) do
        self.InteractiveFunctions[kind](self, false, v);
      end
    end
    self.isInteracting = false;
    CGTimer.RemoveListener(self.CheckCancelInteractiveFunction);
  end
end

function FurnitureController:UpdateCollision()
  if table.Count(self.interactiveWithRoleKind) == 0 then return end

  for k, point in pairs(self.interactivePoint) do
    local noOneOccupyCount = 0;
    local playersCount = table.Count(Role.players);
    local interactivePosition = self:GetInteractivePointPosition(point)

    for kk, player in pairs(Role.players) do
      if (player.position - interactivePosition).magnitude <= 2 then
        if not point.occupy then
          self:InteractiveWithCheckOccupyPoint(true, player);
          FurnitureManager.AddInteractingPlayer(player.index, self);
          point.occupy = true;
          point.playerIndex = player.index;
        else
          --兩人在同一點上，ID大的傳走
          if tonumber(point.playerIndex) > tonumber(player.index) then
            self:SendToEmptyPosition(Role.players[point.playerIndex]);
            point.occupy = false;
            point.playerIndex = nil;
          elseif tonumber(point.playerIndex) < tonumber(player.index) then
            self:SendToEmptyPosition(Role.players[player.index]);
            point.occupy = false;
            point.playerIndex = nil;
          end
        end
      else
        noOneOccupyCount = noOneOccupyCount + 1;
      end
    end

    if noOneOccupyCount >= playersCount then
      point.occupy = false;
      point.playerIndex = nil;
    end
  end
end

function FurnitureController:CheckInteractivePoint(interacting)
  --取消互動時，確認還有沒有人在位置上
  --開始互動時，確認還有沒有位置
  for k, v in pairs(self.interactivePoint) do
    if v.occupy == not interacting then
      return interacting
    end
  end
  return not interacting
end

function FurnitureController:RemoveOccupyPoint(playerIndex)
  if playerIndex == nil then return end
  if table.Count(self.interactiveKind) == 0 then return end

  for k, v in pairs(self.interactivePoint) do
    if v.playerIndex == playerIndex then
      v.playerIndex = nil;
      v.occupy = false;
    end
  end
end

function FurnitureController:TeleportToInteractivePoint()
  local occupyPoint = 0;
  local totalPoint = self.data.interactivePointCount;

  for k, v in pairs(self.interactivePoint) do
    if not v.occupy then
      local newPosition = self:GetInteractivePointPosition(v);
        HouseManager.TeleportPlayer(Role.player, newPosition);
      break
    else
      occupyPoint = occupyPoint + 1;
    end
  end

  if occupyPoint >= totalPoint then
    ShowCenterMessage(string.Get(23582));
    self:Deselect();
  end
end

function FurnitureController:SendToEmptyPosition(player)
  if player == nil then return end
  if player ~= Role.player then return end

  local startX, startY = MapManager.GetBlock(self.position);
  local targetBlockX, targetBlockY = MapManager.GetStartEmptyBlock(startX, startY);

  if not MapManager.IsObstacle(targetBlockX, targetBlockY) then
    local newPosition = Vector2.New(MapManager.GetPosition(Vector2.New(targetBlockX, targetBlockY)));
    player:SetAnimationForceId(ERolePose.None);
    HouseManager.TeleportPlayer(player, newPosition);
  end
end

function FurnitureController:SendFurnitureInteractive(interactive)
  --C:106-010 <家具互動> 房間主人ID(1) +房屋編號(1) +樓層(1) +玩家ID(8) +家具座標X(1) +家具座標Y(1) +家具座標Z(1) +家具類型(1) +是否互動(1)
  sendBuffer:Clear();
  sendBuffer:WriteInt64(HouseManager.GetHouseOwnerId());
  sendBuffer:WriteByte(HouseManager.GetCurrentBuilding());
  sendBuffer:WriteByte(HouseManager.GetCurrentFloor());
  sendBuffer:WriteInt64(Role.playerId);
  sendBuffer:WriteUInt16(self.blockX);
  sendBuffer:WriteUInt16(self.blockY);
  sendBuffer:WriteUInt16(self.blockZ);
  sendBuffer:WriteByte(HouseEditorManager.GetFurnitureKindType(self.data.kind));
  sendBuffer:WriteBoolean(interactive);
  Network.Send(106, 10, sendBuffer);
end

function FurnitureController:ReceiveInteractive(id, interactive)
  if id == nil then return end

  for interactiveKind in pairs(self.interactiveKind) do
    self.InteractiveFunctions[interactiveKind](self, interactive, Role.players[id]);
  end

  self.isInteracting = interactive;

  if interactive then
    self:Selected();
    if id == Role.playerId then
      self:AddCheckCancelInteractiveListener();
    end
  else
    if id == Role.playerId then
      CGTimer.RemoveListener(self.CheckCancelInteractiveFunction);
    end
    self:Deselect();
  end
end

function FurnitureController:AddCheckCancelInteractiveListener()
  if not CGTimer.ContainsListener(self.CheckCancelInteractiveFunction) then
    CGTimer.AddListener(self.CheckCancelInteractiveFunction, 0, false);
  end
end

--互動時結束條件判斷
function FurnitureController:CheckCancelInteractive()
  for interactiveKind in pairs(self.interactiveKind) do
      if self.CheckCancelInteractiveFunctions[interactiveKind](self) then
        self:CancelInteractive();
      end
  end
end

function FurnitureController:PlayerIsInteracting(player, isInteracting)
  if isInteracting == false then
    FurnitureManager.RemoveInteractingPlayer(player.index);
    if table.Count(self.interactivePoint) > 0 then
      self:SendToEmptyPosition(player);
    end
  else
    player.canvas.sortingOrder = self.canvas.sortingOrder + 1;
  end

  if player == Role.player then
    FurnitureManager.SetPlayerIsInteracting(isInteracting);
  end
end

function FurnitureController:GetInteractivePointPosition(point)
  local direction_X = point.x;
  local direction_Y = point.y;

  if self.direction == EFurnitureDirection.RightDown then
    direction_X = -direction_X;
  elseif self.direction == EFurnitureDirection.RightUp then
    direction_X = -direction_X;
  end

  return Vector2.New(self.rawImageBodyTransform.position.x + direction_X, -(self.rawImageBodyTransform.position.y + direction_Y))
end

function FurnitureController:GetInteractivePointPositionByPlayer(playerIndex)
  for k, v in pairs(self.interactivePoint) do
    if v.playerIndex == playerIndex then
      return self:GetInteractivePointPosition(v)
    end
  end

  return nil
end

function FurnitureController:TransferDirectionToRoleDirection()
  local roleDirection = 0;
  roleDirection = (self.direction * 2 + 3) % 8;
  return roleDirection
end
--endregion

--region InteractiveFunctions
FurnitureController.InteractiveFunctions[EFurnitureInteractiveSetting.PlayGIF] = function(self, isPlay, player)
  if isPlay then
    if self.data.dynamic == EFurnitureDynamic.Dynamic then
      self:CancelDynamicAnimation();
    end
    self:AddInteractiveAnimation();
  else
    if self.data.dynamic == EFurnitureDynamic.Dynamic then
      self:AddDynamicAnimation();
    end
    self:CancelInteractiveAnimation();
    self:SetTexture(self:GetTextureNumber(false), 0);
  end
end

FurnitureController.InteractiveFunctions[EFurnitureInteractiveSetting.PlayMusic] = function(self, isPlay)
  if isPlay then
    if self.data.replay_Music == 0 then
      self.audioChannel = AudioManager.PlayFurnitureMusicOnce(self.data.musicName);
    else
      self.audioChannel = AudioManager.PlayFurnitureMusicLoop(self.data.musicName);
    end
    if self.audioChannel == nil then
      ShowCenterMessage(string.Get(23557));
      return
    end
  else
    AudioManager.StopFurnitureMusic(self.audioChannel);
  end
end

FurnitureController.InteractiveFunctions[EFurnitureInteractiveSetting.Active] = function(self, isPlay, player)
  if player == nil then return end

  if self.data.action == 13 then
    player:SetOnTheSea(isPlay);
  elseif isPlay then
    local pose = ERolePose.None;
    local dir = player.direction;

    if self.data.action == 1 then
      dir = self:TransferDirectionToRoleDirection();
      pose = ERolePose.Sit;
    elseif self.data.action == 2 then
      pose = ERolePose.Wave;
    elseif self.data.action == 3 then
      pose = ERolePose.Hit;
    elseif self.data.action == 4 then
      pose = ERolePose.Prepare;
    elseif self.data.action == 5 then
      dir = (self.direction * 2 + 7) % 8;
      pose = ERolePose.Lie;
    elseif self.data.action == 6 then
      pose = ERolePose.Jump;
    elseif self.data.action == 7 then
      pose = ERolePose.Magic;
    elseif self.data.action == 8 then
      pose = ERolePose.Attack;
    elseif self.data.action == 9 then
      pose = ERolePose.Walk;
    elseif self.data.action == 10 then
      pose = ERolePose.Stand;
    elseif self.data.action == 11 then
      pose = ERolePose.Special;
    elseif self.data.action == 12 then
      pose = ERolePose.Archery;
    end

    player:SetDirection(dir);
    player:SetAnimationForceId(pose);
  else
    player:SetAnimationForceId(ERolePose.None);
  end
end

FurnitureController.InteractiveFunctions[EFurnitureInteractiveSetting.UnActive] = function(self, isPlay, player)
  if player == nil then return end;

  local pose = ERolePose.None;

  if isPlay then
    if self.data.action == 1 then
      pose = ERolePose.Sit;
    elseif self.data.action == 2 then
      pose = ERolePose.Wave;
    elseif self.data.action == 3 then
      pose = ERolePose.Hit;
    elseif self.data.action == 4 then
      pose = ERolePose.Prepare;
    elseif self.data.action == 5 then
      pose = ERolePose.Lie;
    elseif self.data.action == 6 then
      pose = ERolePose.Jump;
    elseif self.data.action == 7 then
      pose = ERolePose.Magic;
    elseif self.data.action == 8 then
      pose = ERolePose.Attack;
    elseif self.data.action == 9 then
      pose = ERolePose.Walk;
    elseif self.data.action == 10 then
      pose = ERolePose.Stand;
    elseif self.data.action == 11 then
      pose = ERolePose.Special;
    elseif self.data.action == 12 then
      pose = ERolePose.Archery;
    end
  else
    pose = ERolePose.None;
  end

  player:SetAnimationForceId(pose);
end

FurnitureController.InteractiveFunctions[EFurnitureInteractiveSetting.Emoji] = function(self, isPlay, player)
  if isPlay then
    player:PlayEmoji(99 + self.data.emoji)
  else
    player:StopEmoji();
  end
end

FurnitureController.InteractiveFunctions[EFurnitureInteractiveSetting.MessageBoard] = function(self, isPlay)
  if isPlay then
    UI.Open(UIMessageboard);
  else
    UI.Close(UIMessageboard);
  end
end

FurnitureController.InteractiveFunctions[EFurnitureInteractiveSetting.ShowMessage] = function(self, isPlay)
  if isPlay then
    ShowCenterMessage(self.data.message);
  end
end

FurnitureController.InteractiveFunctions[EFurnitureInteractiveSetting.ShowEffect] = function(self, isPlay)
  if isPlay then
    if self.effectType == EFurnitureEffect.PlayWhenClick then
      self:AddInteractiveEffect();
    end
  else
    if self.effectType == EFurnitureEffect.PlayWhenClick then
      self:CancelInteractiveEffect();
    end
  end
end
--endregion

--region CheckCancelInteractiveFunctions
FurnitureController.CheckCancelInteractiveFunctions[EFurnitureInteractiveSetting.PlayGIF] = function(self)
  if self.data.replay_GIF == 1 then
    return false;
  elseif self.currentPlayRound_GIF >= self.data.playRound_GIF then
    return true;
  end
end

FurnitureController.CheckCancelInteractiveFunctions[EFurnitureInteractiveSetting.PlayMusic] = function(self)
  if self.audioChannel == nil then return true end

  if not AudioManager.GetChannelIsPlaying(self.audioChannel) then
    self.audioChannel = nil;
    return true;
  end
end

FurnitureController.CheckCancelInteractiveFunctions[EFurnitureInteractiveSetting.Active] = function(self)
  if UIJoystick.IsDrag() or UIJoystick.IsClick() then
    UIJoystick.StopClickEffect();
    return true;
  end
end

FurnitureController.CheckCancelInteractiveFunctions[EFurnitureInteractiveSetting.UnActive] = function(self)
  if UIJoystick.IsDrag() or UIJoystick.IsClick() then
    UIJoystick.StopClickEffect();
    return true;
  end
end

FurnitureController.CheckCancelInteractiveFunctions[EFurnitureInteractiveSetting.Emoji] = function(self, player)
  if UIJoystick.IsDrag() or UIJoystick.IsClick() then
    UIJoystick.StopClickEffect();
    return true;
  end
end

FurnitureController.CheckCancelInteractiveFunctions[EFurnitureInteractiveSetting.MessageBoard] = function(self)
  if not UI.IsVisible(UIMessageboard) then
    return true;
  end
end

FurnitureController.CheckCancelInteractiveFunctions[EFurnitureInteractiveSetting.ShowMessage] = function(self)
  if not CGTimer.ContainsListener(UICenterMessage.UpdateUI) then
    if table.Contains(self.interactiveKind, EFurnitureInteractiveSetting.PlayGIF) then
      return false;
    end

    return true;
  end
end

FurnitureController.CheckCancelInteractiveFunctions[EFurnitureInteractiveSetting.ShowEffect] = function(self)
  if self.effectType == EFurnitureEffect.PlayOnStart then
    return false;
  elseif self.effectType == EFurnitureEffect.PlayWhenClick then
    if self.effect.playRound >= self.effect.totalRound then
      return true;
    end
  end
end
--endregion

--region Animation
function FurnitureController:AddInteractiveAnimation()
  if self.data.playSpeed_GIF == 0 then return end
  self.frameNumber = 0;
  self.currentPlayRound_GIF = 0;
  self.interactiveFrameCount = self.jmpDrawer_Body:GetFrameCount(self:GetTextureNumber(true));

  local interval = string.format("%.2f", 1 / self.data.playSpeed_GIF);

  if not CGTimer.ContainsListener(self.PlayInteractiveAnimationFunction) then
    CGTimer.AddListener(self.PlayInteractiveAnimationFunction, interval, false);
  end
end

function FurnitureController:PlayInteractiveAnimation()
  if self == nil then return end

  if self.frameNumber + 1 >= self.interactiveFrameCount then
    self.currentPlayRound_GIF = self.currentPlayRound_GIF + 1;
  end

  self.frameNumber = (self.frameNumber + 1) % self.interactiveFrameCount;
  self:SetTexture(self:GetTextureNumber(true), self.frameNumber);
end

function FurnitureController:CancelInteractiveAnimation()
  CGTimer.RemoveListener(self.PlayInteractiveAnimationFunction);
end

--DynamicAnimation
function FurnitureController:AddDynamicAnimation()
  self.frameNumber = 0;

  if not CGTimer.ContainsListener(self.PlayDynamicAnimationFunction) then
    CGTimer.AddListener(self.PlayDynamicAnimationFunction, 0.2, false);
  end
end

function FurnitureController:PlayDynamicAnimation()
  if self == nil then return end

  self.frameNumber = (self.frameNumber + 1) % self.frameCount;
  self:SetTexture(self:GetTextureNumber(false), self.frameNumber);
end

function FurnitureController:CancelDynamicAnimation()
  CGTimer.RemoveListener(self.PlayDynamicAnimationFunction);
end

--Effect
function FurnitureController:AddEffectAnimation()
  self.effect.nowPage = 0;
  self.effect.frameCount = 0;
  self.effect.rawImage.enabled = true;

  if not CGTimer.ContainsListener(self.PlayEffectFunction) then
    CGTimer.AddListener(self.PlayEffectFunction, self.effect.interval, false);
  end
end

function FurnitureController:PlayAutoEffect()
  if self == nil then return end

  if self.effect.nowPage < self.effect.allPage then
    self.effect.nowPage = self.effect.frameCount + self.effect.startPage;
    TextureManager.SetPng(ETextureUseType.EffectLight, string.Concat(self.effect.fileName, "_", tostring(self.effect.nowPage)), self.effect.rawImage);
    self.effect.frameCount = self.effect.frameCount + 1;
  else
    self.effect.nowPage = 0;
    self.effect.frameCount = 0;
  end
end

function FurnitureController:CancelAutoEffect()
  self.effect.rawImage.enabled = false;
  CGTimer.RemoveListener(self.PlayEffectFunction);
end

function FurnitureController:AddInteractiveEffect()
  self.effect.nowPage = 0;
  self.effect.frameCount = 0;
  self.effect.playRound = 0;
  TextureManager.SetPng(ETextureUseType.EffectLight, string.Concat(self.effect.fileName, "_", tostring(self.effect.startPage)), self.effect.rawImage);
  self.effect.rawImage.enabled = true;

  if not CGTimer.ContainsListener(self.PlayInteractiveEffectFunction) then
    CGTimer.AddListener(self.PlayInteractiveEffectFunction, self.effect.interval, false);
  end
end

function FurnitureController:PlayInteractiveEffect()
  if self == nil then return end

  local pageCount = self.effect.allPage;
  if self.effect.endPage < self.effect.allPage then
    pageCount = self.effect.endPage;
  end

  if self.effect.nowPage < pageCount then
    self.effect.nowPage = self.effect.frameCount + self.effect.startPage;
    TextureManager.SetPng(ETextureUseType.EffectLight, string.Concat(self.effect.fileName, "_", tostring(self.effect.nowPage)), self.effect.rawImage);
    self.effect.frameCount = self.effect.frameCount + 1;
  else
    self.effect.nowPage = 0;
    self.effect.frameCount = 0;
    self.effect.playRound = self.effect.playRound + 1;
  end
end

function FurnitureController:CancelInteractiveEffect()
  self.effect.rawImage.enabled = false;
  CGTimer.RemoveListener(self.PlayInteractiveEffectFunction);
end
--endregion
