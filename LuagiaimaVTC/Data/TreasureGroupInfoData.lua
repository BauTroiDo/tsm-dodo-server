TreasureGroupInfoData = {}
TreasureGroupInfoData.__index = TreasureGroupInfoData;

function TreasureGroupInfoData.New(reader)
  local self = {};
  setmetatable(self, TreasureGroupInfoData);

  self.id = reader:ReadUInt16();
  self.string = reader:ReadUInt32();
  self.treasureId = {};

  for i = 1, 6 do
    self.treasureId[i] = reader:ReadUInt16();
  end

  return self.id, self;
end