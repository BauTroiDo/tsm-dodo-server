DoorData = {}
DoorData.__index = DoorData

function DoorData.New(reader) 
  local self = {};
  setmetatable(self, DoorData);
  
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

  self.imgInfo = {};
  self.imgInfo.imageKind = reader:ReadByte();
  self.imgInfo.position = Vector2.New(reader:ReadUInt16(), reader:ReadUInt16());
  
  self.close = reader:ReadBoolean();      

  if self.gridX < 0 then
    self.gridX = 0;
  end

  if self.gridY < 0 then
    self.gridY = 0;
  end

  return self;
end