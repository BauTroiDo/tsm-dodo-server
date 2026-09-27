GoodsData = {}
GoodsData.__index = GoodsData

function GoodsData.New(reader) 
  local self = {};
  setmetatable(self, GoodsData);
  
  self.NO = reader:ReadByte();
  self.DBID = reader:ReadUInt16();
  self.PosX = reader:ReadInt32();
  self.PosY = reader:ReadInt32();
  self.HideTime = reader:ReadUInt16();

  return self;
end