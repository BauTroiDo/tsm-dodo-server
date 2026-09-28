LeaderboardData = {};
LeaderboardData.__index = LeaderboardData;

function LeaderboardData.New(reader)
  local self = {};
  setmetatable(self, LeaderboardData);

  local id = reader:ReadByte();
  self.mainTagText = reader:ReadUInt32();
  self.subTagText = reader:ReadUInt32();
  self.scoreText = reader:ReadUInt32();
  self.nameText = reader:ReadUInt32();
  self.awardId = reader:ReadByte();
  self.scoreFormat = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()));
  self.stateEnable = reader:ReadBoolean();
  self.rankAwards = {};
  for i = 1, 3 do
    self.rankAwards[i] = {};
    self.rankAwards[i].flagId = reader:ReadUInt16();
    self.rankAwards[i].titleId = 0;
  end

  self.bitFunction = reader:ReadByte();

  return id, self;
end