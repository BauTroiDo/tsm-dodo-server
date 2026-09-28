
FireworkPositionData = {}
FireworkPositionData.__index = FireworkPositionData;

FireworkPositionData.MaxPosition = 3;

function FireworkPositionData.New(reader)
  local self = {};
  setmetatable(self, FireworkPositionData);

  self.position = {}
  for i = 1, FireworkPositionData.MaxPosition do 
    self.position[i] = {}
    self.position[i].x = reader:ReadUInt16();
    self.position[i].y = reader:ReadUInt16();
  end

  return self;
end