WemData ={}
WemData.__index = WemData

function WemData.New(reader)
  local self = {};
  setmetatable(self, TsElementData);

  self.id = reader:ReadInt32();
  self.indexX = reader:ReadInt32();
  self.indexY = reader:ReadInt32();
  self.fAnim = 1;
  self.frame = reader:ReadByte();
  self.gapTime = reader:ReadUInt16();
  self.width = reader:ReadUInt16();
  self.height = reader:ReadUInt16();
  self.alpha = reader:ReadByte();
  self.level = reader:ReadByte();
  
  return self;
end