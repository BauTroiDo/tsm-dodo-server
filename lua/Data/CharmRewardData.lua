CharmRewardData = {}
CharmRewardData.__index = CharmRewardData

function CharmRewardData.New(reader)
  local self = {};
  setmetatable(self, CharmRewardData);

  self.kind = reader:ReadByte();
  self.level = reader:ReadUInt16();
  self.exp = reader:ReadUInt32();
  self.itemId = reader:ReadUInt16();
  self.quant = reader:ReadUInt16();
  self.completeFlag = reader:ReadUInt16();

  return self;
end

function CharmRewardData:CheckGetFlag()
  return BitFlag.Get(self.completeFlag);
end

function CharmRewardData:GetExp()
  return self.exp;
end

function CharmRewardData:CheckRewardCanGetByScore(score)
  if not self:CheckGetFlag() then
    if self.exp <= score then
      return true;
    else
      return false;
    end
  else
    return false;
  end
end
