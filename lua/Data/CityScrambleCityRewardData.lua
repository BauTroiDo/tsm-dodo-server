CityScrambleCityRewardData = {}
CityScrambleCityRewardData.__index = CityScrambleCityRewardData;

function CityScrambleCityRewardData.New(reader)
  local self = {};
  setmetatable(self, CityScrambleCityRewardData);

  self.cityID = reader:ReadUInt16(); --城鎮ID
  self.cityKind = reader:ReadByte();
  self.rewards = {};
  for i = 1, 3 do
    self.rewards[i] = {};
    self.rewards[i].itemID = reader:ReadUInt16();
    self.rewards[i].itemCount = reader:ReadUInt16();
  end

  return self.cityID, self.cityKind, self;
end