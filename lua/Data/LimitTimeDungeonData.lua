LimitTimeDungeonData = {};
LimitTimeDungeonData.__index = LimitTimeDungeonData;

function LimitTimeDungeonData.New(reader)
  local self = {};
  setmetatable(self, LimitTimeDungeonData);

  self.index = reader:ReadByte();
  self.msg = reader:ReadUInt32();
  self.sceneId = reader:ReadUInt16();
  self.limitIndex = reader:ReadUInt16();
  self.limitTime = reader:ReadUInt16();
  self.limitLv = reader:ReadUInt16();
  self.missionId = reader:ReadUInt16();

  return self;
end