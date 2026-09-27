CommonAwardData = {}
CommonAwardData.__index = DispatchAwardData;

CommonAwardData.AwardCount = 6;

CommonAwardDataKind = {
  item = 1,
  luckyBag = 2,
}

function CommonAwardData.New(reader)
  local self = {};
  setmetatable(self, DispatchAwardData);

  local id = reader:ReadUInt16();--索引

  self.award = {};
  for i = 1, CommonAwardData.AwardCount do
    self.award[i] = {};
    self.award[i].awardKind = reader:ReadByte();
    self.award[i].itemId = reader:ReadUInt16();
    self.award[i].itemCount = reader:ReadUInt32();
  end

  return id, self;
end