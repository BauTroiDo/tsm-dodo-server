SceneFightData = {};
SceneFightData.__index = SceneFightData;

function SceneFightData.New(reader)
  local self = {};
  setmetatable(self, SceneFightData);

  self.group = reader:ReadByte();
  self.groupName = reader:ReadUInt32();
  self.sceneId = reader:ReadUInt16();
  self.x = reader:ReadUInt16();
  self.y = reader:ReadUInt16();
  self.npcLow = reader:ReadUInt16();
  self.npcHigh = reader:ReadUInt16();
  self.element = reader:ReadByte();
  self.infoStr = reader:ReadUInt32();
  self.mark = reader:ReadUInt16();
  self.open = reader:ReadByte();
  self.areaId = reader:ReadByte();
  self.info = reader:ReadByte();

  return self;
end