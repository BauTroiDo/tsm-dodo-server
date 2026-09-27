StrongholdCityType = {
  Capital = 1,
  Fort = 2,
  Camp = 3,
  Village = 4,
  DrumHouse = 5
}

StrongholdNPCData = {};
StrongholdNPCData.__index = StrongholdNPCData;

function StrongholdNPCData.New(reader)
  local self = {};
  setmetatable(self, StrongholdNPCData);

  self.npcList = {};

  for i = 1, 5 do
    self.npcList[i] = reader:ReadUInt16();
  end

  self.type = reader:ReadByte();
  self.orderNumber = reader:ReadUInt16();
  self.count = reader:ReadUInt16();

  return self;
end
