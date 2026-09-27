GroupData = {}
GroupData.__index = GroupData

function GroupData.New(reader) 
  local self = {};
  setmetatable(self, GroupData);
  
  self.EveNo = reader:ReadUInt16();
  self.EventNo = reader:ReadUInt16();
  self.ConditionNo = reader:ReadByte();
  self.UseMode = reader:ReadByte();
  self.MemberNum = reader:ReadByte();
  self.MemberNoAy = {}
  for i = 1, self.MemberNum do
	self.MemberNoAy[i] = reader:ReadByte();
  end
  self.ProbabilityRateAy = {}
  for i = 1, self.MemberNum do
    self.ProbabilityRateAy[i] = reader:ReadByte();
  end		
  self.PickMember = reader:ReadByte();

  return self;
end
