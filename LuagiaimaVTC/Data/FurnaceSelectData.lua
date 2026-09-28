FurnaceSelectData = {};
FurnaceSelectData.__index = FurnaceSelectData;

function FurnaceSelectData.New(reader)
  local self = {};
  setmetatable(self, FurnaceSelectData);

  self.Id = reader:ReadUInt16();
  self.kind = reader:ReadByte();
  self.sample = reader:ReadUInt16();

  self.awardKind = {};
  self.awardPro = {};

  for i = 1, 6 do
    self.awardKind[i] = reader:ReadByte();
    self.awardPro[i] = self.awardKind[i];
  end

  return self;
end