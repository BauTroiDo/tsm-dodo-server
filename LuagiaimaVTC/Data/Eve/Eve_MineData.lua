MineData = {}
MineData.__index = MineData

function MineData.New(reader) 
  local self = {};
  setmetatable(self, MineData);
  
  self.id = reader:ReadUInt16();

  self.eventCount = reader:ReadUInt16();
  self.events = {};	
  for i = 1, self.eventCount do
	  self.events[i] = reader:ReadByte();		
  end

  self.gridX = reader:ReadInt32();
  self.gridY = reader:ReadInt32();
  self.gridWidth = reader:ReadInt32();
  self.gridHeight = reader:ReadInt32();

  self.sizeKind = reader:ReadByte();

  return self;
end