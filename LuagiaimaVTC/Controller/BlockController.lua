BlockController = {}
BlockController.__index = BlockController;

function BlockController.New(blockX, blockY, value)
  local self = {};
  setmetatable(self, BlockController);

  self.gameObject = poolMgr:Get("BaseBlock");
  self.gameObject.transform:SetParent(MapManager.blockRoot.transform);
  
  self.rectTransform = self.gameObject:GetComponent("RectTransform");
  self.rectTransform.anchoredPosition = Vector2.New(MapManager.BLOCK_UNIT * (blockX - 1) + MapManager.centerLeft, -(MapManager.BLOCK_UNIT * (blockY - 1) + MapManager.centerTop));
  self.rectTransform.sizeDelta = Vector2.New(20, 20);
  self.rectTransform.localScale = Vector3.one;

  self.image = self.gameObject:GetComponent("Image");

  self.blockX = blockX;
  self.blockY = blockY;

  self:SetValue(value);

  local event = self.gameObject:GetComponent("UIEvent");
  event:SetListener(EventTriggerType.PointerClick, BlockController.Block_OnClick);
  event.parameter = self;

  return self;
end

function BlockController:SetValue(value)
  self.value = value;

  self:UpdateColor();
end

function BlockController:UpdateColor()
  if self.value == 0 then
    self.image.color = LuaHelper.GetColor(0, 255, 0, 50);
  elseif bit.band(self.value, 1) == 1 then
    self.image.color = LuaHelper.GetColor(255, 0, 0, 50);
  elseif bit.band(self.value, 2) == 2 then
    self.image.color = LuaHelper.GetColor(0, 0, 255, 50);
  elseif bit.band(self.value, 4) == 4 then
    self.image.color = LuaHelper.GetColor(255, 0, 0, 50);
  else
    self.image.color = LuaHelper.GetColor(255, 255, 255, 50);
  end
end

function BlockController:Destroy()
  poolMgr:Release("BaseBlock", self.gameObject);
end

function BlockController.Block_OnClick(uiEvent)
  if not UI.IsVisible(UIBlockEditor) then return end

  local block = uiEvent.parameter;

  block:SetValue(UIBlockEditor.currentColor);
  UIBlockEditor.SetBlock(block.blockX, block.blockY, UIBlockEditor.currentColor);
end