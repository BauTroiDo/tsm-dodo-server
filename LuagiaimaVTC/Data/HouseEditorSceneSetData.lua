HouseEditorSceneSetData = {}
HouseEditorSceneSetData.__index = HouseEditorSceneSetData;

function HouseEditorSceneSetData.New(reader)
  local self = {};
  setmetatable(self, HouseEditorSceneSetData);

  self.Id = reader:ReadUInt16(); --[2]編號
  self.originalX = reader:ReadInt16(); --[3]X:起始點位置
  self.originalY = reader:ReadInt16(); --[4]Y:起始點位置
  self.sizeX = reader:ReadByte(); --[5]X格子數
  self.sizeY = reader:ReadByte(); --[6]X格子數
  self.sizeZ = reader:ReadByte(); --[7]Z格子數
  self.maxAmountOfFurnitureOnScene = reader:ReadInt16(); --[8]場景家具數量上限

  return  self.Id,self;
end